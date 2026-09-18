package dev.rafael.server.features.exercise.services

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.engine.ExercisePreFilter
import dev.rafael.server.features.exercise.models.toDto
import kotlin.uuid.Uuid
import dev.rafael.contract.error.ErrorCodes

class ExerciseService(
    private val repository: ExerciseRepository,
    private val preFilter: ExercisePreFilter,
) {

    suspend fun listAll(idioma: Idioma): AppResult<List<ExerciseDto>> =
        repository.findAll(idioma).map { list -> list.map { it.toDto() } }

    suspend fun listByCategory(category: ExerciseCategory, idioma: Idioma): AppResult<List<ExerciseDto>> =
        repository.findByCategory(category, idioma).map { list -> list.map { it.toDto() } }

    /**
     * Alternativas de mesma assinatura funcional (matching "balanceado", ARCH da troca):
     * mesmo movimento + composto/isolamento + músculo primário, dentro do ambiente/nível/
     * limitações do usuário (reusa o pré-filtro da geração). Exclui o próprio exercício.
     *
     * ## ⭐ Aqui a tradução entra na BORDA, e é o único lugar do app onde isso acontece
     *
     * O `poolFor` é do motor: ele lê a `ExercisesTable` crua e decide por taxonomia, sem saber que
     * idiomas existem. Só que **o resultado dele aparece na tela** — `alt.name`, no detalhe do
     * treino — e essa é a exceção que quase passou batido, porque em todo outro caminho a saída do
     * motor só vira estrutura de programa.
     *
     * Duas saídas possíveis, e a escolha importa:
     *
     * | | |
     * |---|---|
     * | passar `idioma` para o `poolFor` | o motor vira bilíngue para servir um caso de UI |
     * | traduzir depois do filtro (esta) | uma consulta a mais, sobre o punhado que sobrou |
     *
     * > **Tradução é assunto da borda de apresentação; motor que a conhece vira motor bilíngue sem
     * > precisar.**
     *
     * O `target` vem do repositório COM idioma, e não do pool, porque ele é lido por id — e ler por
     * id já é o caminho que sabe traduzir.
     */
    suspend fun alternatives(
        exerciseId: Uuid,
        environment: TrainingEnvironment,
        level: Level,
        limitations: List<BodyLimitation>,
        idioma: Idioma,
    ): AppResult<List<ExerciseDto>> =
        repository.findById(exerciseId, idioma).flatMap { target ->
            if (target == null) {
                AppError.NotFound("Exercício não encontrado", code = ErrorCodes.EXERCICIO_NAO_EXISTE).asFailure()
            } else {
                val pool = preFilter.poolFor(environment, limitations, level)
                val alternativas = pool.filter { c ->
                    c.id != exerciseId &&
                        c.movementPattern == target.movementPattern &&
                        c.isCompound == target.isCompound &&
                        target.primaryMuscles.any { it in c.primaryMuscles }
                }
                // Devolve mapa só do que EXISTE traduzido; o resto cai no piso pelo `?:`.
                repository.nomesTraduzidos(alternativas.map { it.id }, idioma).map { nomes ->
                    alternativas.map { alt ->
                        alt.copy(name = nomes[alt.id] ?: alt.name).toDto()
                    }
                }
            }
        }
}
