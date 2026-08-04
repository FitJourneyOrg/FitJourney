package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.SplitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekSpreadTest {

    @Test
    fun `default curado ancora na segunda por nro de dias`() {
        assertEquals(listOf(1, 4), WeekSpread.daysFor(2))                 // Seg, Qui
        assertEquals(listOf(1, 3, 5), WeekSpread.daysFor(3))             // Seg, Qua, Sex
        assertEquals(listOf(1, 2, 4, 5, 6), WeekSpread.daysFor(5))       // folga Qua e Dom
        assertEquals(listOf(1, 2, 3, 4, 5, 6), WeekSpread.daysFor(6))    // Seg–Sáb, folga Dom
    }

    @Test
    fun `4 dias bifurca full body (espacado) vs split (2x2)`() {
        assertEquals(listOf(1, 2, 4, 5), WeekSpread.daysFor(4, SplitType.UPPER_LOWER.label))
        assertEquals(listOf(1, 3, 5, 7), WeekSpread.daysFor(4, SplitType.FULL_BODY.label))
    }

    @Test
    fun `dias off ignoram o default e caem no espacamento`() {
        val days = WeekSpread.daysFor(3, SplitType.FULL_BODY.label, unavailable = setOf(1, 2, 3))
        assertEquals(3, days.size)
        assertTrue(days.none { it in setOf(1, 2, 3) }, "não pode cair em dia off; deu $days")
    }

    @Test
    fun `distribui a quantidade certa de dias distintos em 1 a 7`() {
        (1..6).forEach { count ->
            val days = WeekSpread.daysFor(count)
            assertEquals(count, days.size, "count=$count")
            assertEquals(count, days.toSet().size, "dias distintos p/ count=$count")
            assertTrue(days.all { it in 1..7 }, "dentro de 1..7 p/ count=$count")
        }
    }

    @Test
    fun `deixa folga entre treinos (nao consecutivos) quando ha espaco`() {
        // 3x em 7 dias: nunca deveria empilhar os 3 em dias seguidos
        val days = WeekSpread.daysFor(3).sorted()
        val maxGap = (1 until days.size).maxOf { days[it] - days[it - 1] }
        assertTrue(maxGap >= 2, "esperava ao menos uma folga; deu $days")
    }

    @Test
    fun `evita os dias indisponiveis`() {
        val off = setOf(1, 2, 3)   // não treina Seg/Ter/Qua
        val days = WeekSpread.daysFor(3, unavailable = off)
        assertEquals(3, days.size)
        assertTrue(days.none { it in off }, "não pode cair em dia off; deu $days")
    }

    @Test
    fun `quando nao ha folga a distribuir devolve os disponiveis`() {
        val off = setOf(6, 7)                       // sobram 5 dias (1..5)
        val days = WeekSpread.daysFor(5, unavailable = off)
        assertEquals(listOf(1, 2, 3, 4, 5), days)
    }
}
