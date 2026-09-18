package dev.rafael.server.features.exercise.db

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.BodyLimitation
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.models.Exercise
import dev.rafael.server.features.exercise.models.Modality
import dev.rafael.server.features.exercise.models.MovementPattern
import dev.rafael.server.features.exercise.models.PrescriptionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ColumnSet
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class ExerciseRepositoryImpl : ExerciseRepository {

    /**
     * A origem da leitura: `exercises` sozinha no piso, `exercises LEFT JOIN traduções` no resto.
     *
     * ⚠️ **O `PADRAO` não faz join, e isso não é otimização prematura — é correção.**
     * `exercises.name` É o pt-BR (ver V49), então juntar com uma tabela que nunca terá linha
     * `pt-BR` seria pagar um join para receber `null` 963 vezes.
     *
     * O `LEFT` é obrigatório: com `INNER`, exercício ainda não traduzido **sumiria do catálogo**
     * em vez de aparecer no piso. Seria o defeito mais caro possível — a pessoa trocaria de idioma
     * e o app perderia exercícios, sem erro nenhum.
     *
     * > **Join que decide quem aparece na lista não é detalhe de consulta: é regra de produto
     * > escrita em SQL.**
     */
    private fun origem(idioma: Idioma): ColumnSet =
        if (idioma == Idioma.PADRAO) {
            ExercisesTable
        } else {
            ExercisesTable.join(
                otherTable = ExerciseTranslationsTable,
                joinType = JoinType.LEFT,
                onColumn = ExercisesTable.id,
                otherColumn = ExerciseTranslationsTable.exerciseId,
                additionalConstraint = { ExerciseTranslationsTable.locale eq idioma.tag },
            )
        }

    override suspend fun findAll(idioma: Idioma): AppResult<List<Exercise>> =
        dbQuery {
            origem(idioma).selectAll().map { it.toExerciseTraduzido(idioma) }
        }

    override suspend fun findByCategory(category: ExerciseCategory, idioma: Idioma): AppResult<List<Exercise>> =
        dbQuery {
            origem(idioma).selectAll()
                .where { ExercisesTable.category eq category.name }
                .map { it.toExerciseTraduzido(idioma) }
        }

    override suspend fun findById(id: Uuid, idioma: Idioma): AppResult<Exercise?> =
        dbQuery {
            origem(idioma).selectAll()
                .where { ExercisesTable.id eq id }
                .map { it.toExerciseTraduzido(idioma) }
                .singleOrNull()
        }

    override suspend fun existsByIds(ids: List<Uuid>): AppResult<Boolean> =
        dbQuery {
            if (ids.isEmpty()) return@dbQuery true
            val found = ExercisesTable.selectAll()
                .where { ExercisesTable.id inList ids }
                .count().toInt()
            found == ids.distinct().size
        }

    override suspend fun nomesTraduzidos(ids: List<Uuid>, idioma: Idioma): AppResult<Map<Uuid, String>> =
        dbQuery {
            if (ids.isEmpty() || idioma == Idioma.PADRAO) return@dbQuery emptyMap()
            ExerciseTranslationsTable.selectAll()
                .where {
                    (ExerciseTranslationsTable.exerciseId inList ids) and
                        (ExerciseTranslationsTable.locale eq idioma.tag)
                }
                .associate { it[ExerciseTranslationsTable.exerciseId] to it[ExerciseTranslationsTable.name] }
        }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}

/**
 * Lê a linha já sabendo se veio de um join.
 *
 * No `PADRAO` a coluna traduzida **não existe na consulta**, e pedi-la a um `ResultRow` estoura.
 * Por isso a checagem do idioma vem antes do `getOrNull`, e não depois.
 */
private fun ResultRow.toExerciseTraduzido(idioma: Idioma): Exercise {
    val traduzido = if (idioma == Idioma.PADRAO) null else getOrNull(ExerciseTranslationsTable.name)
    return toExercise(nomeTraduzido = traduzido)
}

/**
 * A conversão crua, sem idioma. É esta que o `ExercisePreFilter` usa: o motor decide por taxonomia
 * e o nome que ele carrega nunca chega à tela por aquele caminho, exceto via `alternatives` — que
 * traduz na borda do serviço, sobre os poucos ids que sobram do filtro.
 *
 * `nomeTraduzido` é o único ponto de entrada da tradução, e `null` cai no piso da V49.
 */
internal fun ResultRow.toExercise(nomeTraduzido: String? = null): Exercise = Exercise(
    id = this[ExercisesTable.id],
    name = nomeTraduzido ?: this[ExercisesTable.name],
    category = ExerciseCategory.valueOf(this[ExercisesTable.category]),
    description = this[ExercisesTable.description],
    videoRef = this[ExercisesTable.videoRef],
    thumbRef = this[ExercisesTable.thumbRef],
    modality = this[ExercisesTable.modality]?.let { runCatching { Modality.valueOf(it) }.getOrNull() },
    movementPattern = this[ExercisesTable.movementPattern]?.let { runCatching { MovementPattern.valueOf(it) }.getOrNull() },
    secondaryPattern = this[ExercisesTable.secondaryPattern]?.let { runCatching { MovementPattern.valueOf(it) }.getOrNull() },
    isCompound = this[ExercisesTable.isCompound],
    equipment = this[ExercisesTable.equipment],
    primaryMuscles = this[ExercisesTable.primaryMuscles].orEmpty().mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() },
    secondaryMuscles = this[ExercisesTable.secondaryMuscles].orEmpty().mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() },
    unilateral = this[ExercisesTable.unilateral],
    prescriptionType = this[ExercisesTable.prescriptionType]?.let { runCatching { PrescriptionType.valueOf(it) }.getOrNull() },
    level = this[ExercisesTable.level]?.let { runCatching { Level.valueOf(it) }.getOrNull() },
    contraindications = this[ExercisesTable.contraindications].orEmpty().mapNotNull { runCatching { BodyLimitation.valueOf(it) }.getOrNull() },
    isBase = this[ExercisesTable.isBase],
)
