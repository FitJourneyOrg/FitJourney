package dev.rafael.server.features.exercise.services

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.core.result.AppResult
import dev.rafael.server.error.Falhas
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.ProgressaoRepository
import dev.rafael.server.features.exercise.engine.v3.SugestaoDeProgressao
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.workout.models.comSugestao
import kotlin.uuid.Uuid

/**
 * Fatia "sugestao-de-progressao" (2026-11-06, `docs/trabalho/sugestao-de-progressao-plano.md`).
 * Pós-processamento igual ao `comOrigem()` (`WorkoutRoutes.completarOrigem`), com UMA diferença
 * de propósito: falha aqui NUNCA quebra `GET /workouts/{id}` — é enriquecimento, não dado
 * essencial. Sem `Profile`/`Goal`, sem papel, sem linha no catálogo, ou erro de leitura em
 * qualquer repositório: aquele exercício fica com os três campos `null`
 * (`SugestaoDeProgressao.Sugestao.VAZIA`) — decisão já no plano ("null... = sem sugestão... ou
 * qualquer falha silenciosa do lado do servidor").
 *
 * N pequeno de propósito (teto do motor é poucos exercícios por sessão) — sem `Profile` global
 * nem `Goal`, a leitura inteira é pulada (mesmo piso do `ProgressaoAplicadaService`: sem `Goal`
 * não dá pra escolher a faixa de prescrição pra NENHUM exercício).
 */
class SugestaoDeProgressaoService(
    private val exerciseRepository: ExerciseRepository,
    private val progressaoRepository: ProgressaoRepository,
    private val profileRepository: ProfileRepository,
    private val falhas: Falhas,
) {

    suspend fun completarSugestoes(userId: Uuid, dto: WorkoutDto): WorkoutDto {
        if (dto.exercises.isEmpty()) return dto

        val goal = profileRepository.findByUserId(userId)
            .comLogDeFalha("Não resolvi o perfil pra sugestão de progressão do treino ${dto.id}")
            ?.goal ?: return dto

        val ids = dto.exercises.mapNotNull { runCatching { Uuid.parse(it.exerciseId) }.getOrNull() }
        val catalogo = exerciseRepository.paraProgressao(ids)
            .comLogDeFalha("Não resolvi o catálogo pra sugestão de progressão do treino ${dto.id}")
            ?: return dto

        val exercicios = dto.exercises.map { exercicio ->
            val exerciseId = runCatching { Uuid.parse(exercicio.exerciseId) }.getOrNull()
                ?: return@map exercicio
            val cat = catalogo[exerciseId] ?: return@map exercicio
            // Só busca o eixo que o catálogo decide pra este exercício (mesmo piso do
            // ProgressaoAplicadaService): consultar os dois sempre gastaria uma leitura inútil
            // E, numa falha de banco, logaria duas vezes o mesmo exercício.
            val estadoCarga = if (cat.incremento != null) {
                progressaoRepository.buscar(userId, exerciseId)
                    .comLogDeFalha("Não resolvi o estado de carga do exercício $exerciseId pra sugestão")
            } else {
                null
            }
            val estadoRepeticao = if (cat.incremento == null && cat.equipment == "BODYWEIGHT") {
                progressaoRepository.buscarPorRepeticao(userId, exerciseId)
                    .comLogDeFalha("Não resolvi o estado de repetição do exercício $exerciseId pra sugestão")
            } else {
                null
            }
            val sugestao = SugestaoDeProgressao.calcular(
                incremento = cat.incremento,
                equipment = cat.equipment,
                papel = exercicio.papel,
                goal = goal,
                estadoCarga = estadoCarga,
                estadoRepeticao = estadoRepeticao,
            )
            exercicio.comSugestao(sugestao)
        }
        return dto.copy(exercises = exercicios)
    }

    /** Falha real (erro de banco) vira log e `null`; a sugestão daquele exercício fica vazia. */
    private fun <T> AppResult<T>.comLogDeFalha(contexto: String): T? = when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> {
            falhas.registrar(contexto, error)
            null
        }
    }
}
