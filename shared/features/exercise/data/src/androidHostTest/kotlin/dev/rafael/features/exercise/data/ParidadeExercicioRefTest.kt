package dev.rafael.features.exercise.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.rafael.core.database.ExerciseLookupImpl
import dev.rafael.core.database.FitJourneyDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paridade `Exercise` (feature exercise) × `ExerciseRef` (core:catalog), débito B12.
 *
 * ## Por que existe
 *
 * Os dois tipos são montados em lugares diferentes a partir da MESMA linha da tabela `exercise`:
 * `ExerciseRow.toDomainOrNull()` aqui e `ExerciseLookupImpl.byIds` em core:database (que lê uma
 * projeção de 4 colunas, `selectByIds`). A duplicação é de propósito (ARCH #16: feature não
 * depende de feature, então Session e Workout recebem a porta estreita), mas deixa um risco:
 * mudar uma coluna de mídia e acertar só um dos dois mapeamentos. Nada quebra na compilação,
 * a tela de sessão só passa a mostrar a miniatura errada.
 *
 * SQLite em memória com as `.sqm` reais, como em `ProgramLocalDataSourceTest`. JVM puro, por
 * isso em `androidHostTest`.
 */
class ParidadeExercicioRefTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: FitJourneyDatabase

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FitJourneyDatabase.Schema.create(driver)
        db = FitJourneyDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun inserir(id: String, name: String, thumb: String, video: String) {
        db.exerciseQueries.insertOrReplace(
            id = id, name = name, category = "CHEST", description = "desc",
            videoRef = video, thumbRef = thumb, primaryMuscles = null, secondaryMuscles = null,
        )
    }

    @Test
    fun `ref e dominio concordam nos quatro campos da porta estreita`() = runTest {
        inserir("ex-1", "Supino reto", thumb = "t/supino.png", video = "v/supino.mp4")
        inserir("ex-2", "Agachamento", thumb = "t/agacha.png", video = "v/agacha.mp4")

        val refs = ExerciseLookupImpl(db).byIds(listOf("ex-1", "ex-2"))

        listOf("ex-1", "ex-2").forEach { id ->
            val dominio = db.exerciseQueries.selectById(id).executeAsOne().toDomainOrNull()!!
            val ref = refs.getValue(id)
            assertEquals(dominio.id, ref.id)
            assertEquals(dominio.name, ref.name)
            assertEquals(dominio.thumbRef, ref.thumbRef)
            assertEquals(dominio.videoRef, ref.videoRef)
        }
    }

    @Test
    fun `id inexistente nao aparece no lookup e lista vazia nao consulta o banco`() = runTest {
        inserir("ex-1", "Supino reto", thumb = "t.png", video = "v.mp4")
        val lookup = ExerciseLookupImpl(db)

        val refs = lookup.byIds(listOf("ex-1", "fantasma"))

        assertEquals(setOf("ex-1"), refs.keys)
        assertTrue(lookup.byIds(emptyList()).isEmpty())
    }
}
