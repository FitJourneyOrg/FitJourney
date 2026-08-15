package dev.rafael.features.program.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.program.ScheduleEntry
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.network.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Armazenamento local de programas — em TABELAS REAIS (ARCH #30, passo 4).
 *
 * Antes isto era um blob JSON no kv_cache: não dava pra consultar ("qual treino é hoje?"),
 * ordenar nem observar por Flow. Agora:
 *   - `program`          -> cabeçalho (o que a lista mostra)
 *   - `program_schedule` -> agenda (responde o treino do dia por SQL)
 *   - `workout`          -> cabeçalho do treino + exercícios em JSON
 *
 * Os exercícios continuam em JSON de propósito: são sempre lidos em bloco, nunca consultados
 * por dentro. Normalizar série/repetição seria mais 2 tabelas sem consulta que as justifique.
 *
 * [REGRA] tudo chaveado por uid (ARCH #30).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramLocalDataSource(
    private val db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
) {
    private val qPrograma = db.programQueries
    private val qTreino = db.workoutQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val exerciciosSerializer = ListSerializer(WorkoutExerciseDto.serializer())

    private suspend fun uid(): String = tokenProvider.currentUid() ?: ""

    /**
     * Substitui o estado local pelo que veio do servidor, em UMA transação.
     *
     * Apaga antes de gravar: o GET /programs devolve a verdade completa, então um programa
     * excluído em outro dispositivo precisa sumir daqui. (Quando existir escrita offline —
     * passo 5 — esta limpeza terá de PRESERVAR as linhas pendentes.)
     */
    suspend fun save(programas: List<ProgramDto>) {
        val dono = uid()
        qPrograma.transaction {
            qPrograma.limparAgenda(dono)
            qTreino.limparTreinos(dono)
            qPrograma.limparProgramas(dono)

            programas.forEach { p ->
                val programaId = p.id ?: return@forEach
                qPrograma.salvarPrograma(
                    id = programaId,
                    uid = dono,
                    name = p.name,
                    origin = p.origin.name,
                    daysPerWeek = p.daysPerWeek.toLong(),
                    split = p.split,
                    rationale = p.rationale,
                    locked = if (p.locked) 1L else 0L,
                    durationWeeks = p.durationWeeks.toLong(),
                    startedAt = p.startedAt,
                    currentWeek = p.currentWeek.toLong(),
                    updatedAt = p.updatedAt,
                )
                p.schedule.forEach { e ->
                    qPrograma.salvarAgenda(
                        programId = programaId,
                        workoutId = e.workoutId,
                        uid = dono,
                        dayOfWeek = e.dayOfWeek.toLong(),
                    )
                }
                p.workouts.forEach { w ->
                    val treinoId = w.id ?: return@forEach
                    qTreino.salvarTreino(
                        id = treinoId,
                        uid = dono,
                        programId = programaId,
                        name = w.name,
                        origin = w.origin.name,
                        locked = if (w.locked) 1L else 0L,
                        lockedExerciseCount = w.lockedExerciseCount.toLong(),
                        exerciseCount = w.exercises.size.toLong(),
                        exercisesJson = json.encodeToString(exerciciosSerializer, w.exercises),
                    )
                }
            }
        }
    }

    /** Lista local, reativa: gravou no banco, a tela atualiza. */
    fun observar(): Flow<List<ProgramDto>> =
        flow { emit(uid()) }.flatMapLatest { dono ->
            qPrograma.observarProgramas(dono)
                .asFlow()
                .mapToList(Dispatchers.Default)
                .map { linhas -> linhas.map { montarPrograma(it, dono) } }
        }

    /**
     * Leitura pontual (caminho cache-first do repositório).
     *
     * Devolve lista VAZIA quando não há programas — não null. Antes o null servia duplo papel
     * ("não tenho nada" e "nunca sincronizei"), e isso quebrava o cache-first para toda conta
     * sem programa: o carimbo dizia fresco, o read devolvia null, e o repositório ia à rede
     * mesmo assim, em toda abertura. Quem responde "nunca sincronizei" agora é o SyncStamps.
     */
    suspend fun read(): List<ProgramDto> {
        val dono = uid()
        return qPrograma.observarProgramas(dono).executeAsList().map { montarPrograma(it, dono) }
    }

    /** Detalhe de um treino (usado pelo cache do workout:data). */
    suspend fun lerTreino(id: String): WorkoutDto? {
        val dono = uid()
        val linha = qTreino.lerTreino(id, dono).executeAsOneOrNull() ?: return null
        return montarTreino(linha)
    }

    // ---- reconstrução dos DTOs a partir das linhas ----

    private fun montarPrograma(p: dev.rafael.core.database.Program, dono: String): ProgramDto {
        val treinos = qTreino.observarTreinosDoPrograma(p.id, dono).executeAsList().map { montarTreino(it) }
        val agenda = qPrograma.agendaDoPrograma(p.id, dono).executeAsList()
            .map { ScheduleEntry(workoutId = it.workoutId, dayOfWeek = it.dayOfWeek.toInt()) }
        return ProgramDto(
            id = p.id,
            name = p.name,
            origin = runCatching { WorkoutOrigin.valueOf(p.origin) }.getOrDefault(WorkoutOrigin.AI),
            workouts = treinos,
            daysPerWeek = p.daysPerWeek.toInt(),
            split = p.split,
            rationale = p.rationale,
            locked = p.locked == 1L,
            schedule = agenda,
            durationWeeks = p.durationWeeks.toInt(),
            startedAt = p.startedAt,
            currentWeek = p.currentWeek.toInt(),
            updatedAt = p.updatedAt,
        )
    }

    private fun montarTreino(w: dev.rafael.core.database.Workout): WorkoutDto = WorkoutDto(
        id = w.id,
        name = w.name,
        origin = runCatching { WorkoutOrigin.valueOf(w.origin) }.getOrDefault(WorkoutOrigin.AI),
        programId = w.programId,
        exercises = runCatching {
            json.decodeFromString(exerciciosSerializer, w.exercisesJson)
        }.getOrDefault(emptyList()),
        locked = w.locked == 1L,
        lockedExerciseCount = w.lockedExerciseCount.toInt(),
    )
}
