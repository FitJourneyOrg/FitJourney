package dev.rafael.server.features.stats

import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.stats.MuscleVolumeDto
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.contract.stats.TrendPointDto
import dev.rafael.contract.stats.ExerciseSummaryDto
import dev.rafael.contract.stats.ExerciseTrendDto
import dev.rafael.contract.stats.WeeklyLoadDto
import io.ktor.serialization.kotlinx.json.DefaultJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O `ProgressDto` no FIO, com o MESMO `Json` que o `install(ContentNegotiation) { json() }` usa.
 *
 * Usar um `Json {}` montado aqui provaria que o DTO serializa com ALGUMA configuracao, que nao e
 * a pergunta. A pergunta e o que o cliente recebe.
 */
class ProgressDtoSerializacaoTest {

    private val pago = ProgressDto(
        totalKg = 175_876.0,
        totalSessions = 29,
        sinceDate = "2026-08-10",
        weeklyLoad = listOf(WeeklyLoadDto("2026-09-28", 22_475.0)),
        strengthTrend = listOf(
            ExerciseTrendDto("abc", "Agachamento", listOf(TrendPointDto("2026-09-28", 88.7)), 16.7),
        ),
        setsByMuscle = MuscleVolumeDto(
            byMuscle = mapOf(MuscleGroup.LEGS to 15.8, MuscleGroup.CHEST to 7.0),
            unclassified = 1.5,
        ),
        analysisLocked = false,
        weeksWindow = 26,
        exerciseSummary = listOf(
            ExerciseSummaryDto("abc", "Agachamento", 88.7, 16.7, sets = 24, weeks = 8),
            ExerciseSummaryDto("def", "Rosca Direta", 26.7, -3.2, sets = 9, weeks = 4),
        ),
    )

    @Test
    fun `o mapa com chave de enum sobrevive a ida e volta`() {
        val texto = DefaultJson.encodeToString(ProgressDto.serializer(), pago)
        val volta = DefaultJson.decodeFromString(ProgressDto.serializer(), texto)

        assertEquals(15.8, volta.setsByMuscle?.byMuscle?.get(MuscleGroup.LEGS))
        assertEquals(7.0, volta.setsByMuscle?.byMuscle?.get(MuscleGroup.CHEST))
        assertEquals(1.5, volta.setsByMuscle?.unclassified)
    }

    /**
     * A lista paga (J.4.2) no fio, incluindo variacao NEGATIVA — o sinal e o que a tela colore,
     * e um `-3.2` que voltasse como `3.2` pintaria queda de verde.
     */
    @Test
    fun `a lista de exercicios sobrevive a ida e volta, com o sinal da variacao`() {
        val texto = DefaultJson.encodeToString(ProgressDto.serializer(), pago)
        val volta = DefaultJson.decodeFromString(ProgressDto.serializer(), texto)

        assertEquals(2, volta.exerciseSummary?.size)
        assertEquals(-3.2, volta.exerciseSummary?.get(1)?.changePercent)
        assertEquals(24, volta.exerciseSummary?.first()?.sets)
        assertEquals(26, volta.weeksWindow)
    }

    @Test
    fun `a chave do mapa vai como o SerialName do enum, nao como ordinal`() {
        // Ordinal no fio quebraria a primeira vez que alguem reordenasse o enum.
        assertTrue("\"LEGS\"" in DefaultJson.encodeToString(ProgressDto.serializer(), pago))
    }

    /**
     * ⭐ **[INV] A resposta de free nao pode carregar o numero pago em campo nenhum.**
     *
     * E o buraco que o `ProgramAccess` nasceu para fechar: a listagem trancava os dias e o
     * `GET /workouts/{id}` entregava o conteudo a quem pedisse pelo id. Trancar na tela e
     * entregar no JSON nao e trancar.
     */
    @Test
    fun `resposta de free nao tem nenhum numero dos blocos pagos`() {
        val free = ProgressDto(totalKg = 175_876.0, totalSessions = 29, analysisLocked = true)

        val texto = DefaultJson.encodeToString(ProgressDto.serializer(), free)

        listOf("weekStart", "estimated1rm", "byMuscle", "unclassified", "changePercent").forEach {
            assertFalse(it in texto, "o JSON do free nao pode conter `$it`: $texto")
        }
        assertTrue("\"analysisLocked\":true" in texto)
        assertTrue("175876" in texto, "o total continua sendo gratis")
    }

    @Test
    fun `premium sem dado e free se distinguem no fio`() {
        val premiumVazio = ProgressDto(totalKg = 0.0, totalSessions = 0, analysisLocked = false)
        val free = ProgressDto(totalKg = 0.0, totalSessions = 0, analysisLocked = true)

        // Os dois têm os blocos nulos; o que separa paywall de estado vazio é só esta flag.
        assertTrue("\"analysisLocked\":false" in DefaultJson.encodeToString(ProgressDto.serializer(), premiumVazio))
        assertTrue("\"analysisLocked\":true" in DefaultJson.encodeToString(ProgressDto.serializer(), free))
    }
}
