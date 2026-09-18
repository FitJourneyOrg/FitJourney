package dev.rafael.features.exercise.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.core.database.Exercise
import dev.rafael.core.database.FitJourneyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class ExerciseLocalDataSource(private val db: FitJourneyDatabase) {
    private val queries = db.exerciseQueries
    private val cache = db.cacheQueries

    fun observeAll(): Flow<List<Exercise>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default)

    fun observeByCategory(category: String): Flow<List<Exercise>> =
        queries.selectByCategory(category).asFlow().mapToList(Dispatchers.Default)

    /** Detalhe pontual — sem baixar o catálogo. */
    fun readById(id: String): Exercise? = queries.selectById(id).executeAsOneOrNull()

    /** Catálogo vazio = nunca sincronizou neste aparelho; aí o TTL não vale. */
    fun isEmpty(): Boolean = queries.countAll().executeAsOne() == 0L

    /**
     * ⭐ **Em QUE IDIOMA está o catálogo que está guardado aqui** (fatia H).
     *
     * A tabela `exercise` guarda um nome por exercício, não um por idioma: cada `replaceAll`
     * substitui tudo. Então "tenho catálogo" e "tenho catálogo NESTE idioma" são perguntas
     * diferentes, e só a segunda serve para decidir se dá para pular a rede.
     *
     * `null` = nunca baixou, ou baixou antes desta fatia existir. Nos dois casos, rebaixa.
     */
    fun idiomaGuardado(): String? = cache.get(CHAVE_IDIOMA).executeAsOneOrNull()

    /**
     * ⚠️ **O idioma é gravado na MESMA transação das linhas.**
     *
     * Fora dela, uma falha entre o insert e o registro do idioma deixaria o banco dizendo que tem
     * português com as 963 linhas em inglês dentro — e o app confiaria nele para sempre, porque o
     * carimbo estaria fresco.
     *
     * > **Metadado sobre um conjunto de linhas pertence à transação que escreveu as linhas.**
     */
    fun replaceAll(dtos: List<ExerciseDto>, idioma: String) {
        queries.transaction {
            queries.deleteAll()
            dtos.forEach { d ->
                queries.insertOrReplace(
                    id = d.id, name = d.name, category = d.category.name,
                    description = d.description, videoRef = d.videoRef, thumbRef = d.thumbRef,
                )
            }
            cache.put(CHAVE_IDIOMA, idioma)
        }
    }

    private companion object {
        /**
         * Sem uid: o catálogo é do APARELHO, não da conta (o carimbo dele usa `Escopo.GLOBAL` pela
         * mesma razão). Duas contas no mesmo aparelho compartilham catálogo e compartilham idioma
         * baixado, e isso é correto — quem escolhe o idioma é o aparelho.
         */
        const val CHAVE_IDIOMA = "exercises:idioma"
    }
}