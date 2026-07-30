package dev.rafael.server.features.exercise.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeekSpreadTest {

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
