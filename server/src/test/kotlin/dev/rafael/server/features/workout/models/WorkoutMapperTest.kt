package dev.rafael.server.features.workout.models

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WorkoutMapperTest {

    private fun dto() = WorkoutDto(name = "Treino A", exercises = emptyList())

    @Test
    fun `treino de programa de IA sai com origem AI`() {
        assertEquals(WorkoutOrigin.AI, dto().comOrigem(WorkoutOrigin.AI).origin)
    }

    @Test
    fun `treino de programa manual continua MANUAL`() {
        assertEquals(WorkoutOrigin.MANUAL, dto().comOrigem(WorkoutOrigin.MANUAL).origin)
    }

    @Test
    fun `sem programa conhecido mantem o dto intacto e MANUAL`() {
        val original = dto()
        assertSame(original, original.comOrigem(null))
        assertEquals(WorkoutOrigin.MANUAL, original.comOrigem(null).origin)
    }
}
