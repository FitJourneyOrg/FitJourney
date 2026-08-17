package dev.rafael.features.workout.data

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.network.TokenProvider
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Armazenamento local do detalhe do treino — TABELA REAL `workout` (ARCH #30, passo 4).
 *
 * Antes era um blob no kv_cache com chave "workout:{id}". Agora divide a mesma tabela que o
 * sync de programas popula: o GET /programs já traz os treinos aninhados, então abrir um
 * treino offline funciona mesmo sem nunca tê-lo aberto online.
 *
 * [REGRA] chaveado por uid (ARCH #30).
 */
class WorkoutLocalDataSource(
    db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
) {
    private val q = db.workoutQueries

    /**
     * Sim, a feature de treino escreve na tabela de agenda. As TABELAS são do `core:database`,
     * não da feature — quem não pode depender de quem é módulo, não linha de SQL. E o inverso
     * já acontecia: o sync de programas grava em `workout`.
     */
    private val qAgenda = db.programQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val exerciciosSerializer = ListSerializer(WorkoutExerciseDto.serializer())

    private suspend fun uid(): String = tokenProvider.currentUid() ?: ""

    /**
     * A tabela `workout` tem DUAS fontes de escrita, com completude diferente:
     *   - `GET /programs`      → traz o flag de bloqueio do ARCH #23 (autoridade)
     *   - `GET /workouts/{id}` → traz o detalhe do treino
     *
     * Por isso o `locked` só pode SUBIR aqui, nunca cair: uma resposta de detalhe não é
     * autoridade sobre entitlement e não pode destravar o que a listagem trancou. Sem esta
     * trava, a Home — que busca o treino do dia só para estimar a duração — gravava
     * `locked = false` por cima e o dia trancado aparecia liberado na tela de programa.
     */
    suspend fun save(id: String, dto: WorkoutDto) {
        val dono = uid()
        val trancadoAntes = q.lerTreino(id, dono).executeAsOneOrNull()?.locked == 1L
        q.salvarTreino(
            id = id,
            uid = dono,
            programId = dto.programId,
            name = dto.name,
            origin = dto.origin.name,
            locked = if (dto.locked || trancadoAntes) 1L else 0L,
            lockedExerciseCount = dto.lockedExerciseCount.toLong(),
            exerciseCount = dto.exercises.size.toLong(),
            exercisesJson = json.encodeToString(exerciciosSerializer, dto.exercises),
        )
    }

    /** Exclusão otimista (B.3): some da tela na hora, o DELETE sai pela fila. */
    suspend fun excluir(id: String) {
        val dono = uid()
        q.transaction {
            qAgenda.excluirDaAgenda(id, dono)   // senão fica entrada órfã apontando p/ nada
            q.excluirTreino(id, dono)
        }
    }

    /**
     * Posiciona o treino no dia da semana, LOCALMENTE.
     *
     * Sem isto o treino criado offline existe na tabela `workout` mas não aparece na tela de
     * programa: a visão de semana monta os 7 dias a partir de `program_schedule` e mostra
     * "Descanso" onde não há entrada. Era o que acontecia — o treino sumia da vista mesmo
     * estando salvo, que é pior que um erro visível.
     *
     * Online isto é feito pelo servidor ao receber o POST; aqui é a cópia otimista.
     */
    suspend fun agendar(programId: String, workoutId: String, dayOfWeek: Int) {
        qAgenda.salvarAgenda(
            programId = programId,
            workoutId = workoutId,
            uid = uid(),
            dayOfWeek = dayOfWeek.toLong(),
        )
    }

    suspend fun read(id: String): WorkoutDto? {
        val linha = q.lerTreino(id, uid()).executeAsOneOrNull() ?: return null
        return WorkoutDto(
            id = linha.id,
            name = linha.name,
            origin = runCatching { WorkoutOrigin.valueOf(linha.origin) }.getOrDefault(WorkoutOrigin.AI),
            programId = linha.programId,
            exercises = runCatching {
                json.decodeFromString(exerciciosSerializer, linha.exercisesJson)
            }.getOrDefault(emptyList()),
            locked = linha.locked == 1L,
            lockedExerciseCount = linha.lockedExerciseCount.toInt(),
        )
    }
}
