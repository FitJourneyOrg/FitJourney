package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testa ExerciseDto.toDomain() — puro (usado nas alternativas, que não passam pelo
 * cache local). O toDomainOrNull() de linha do SQLDelight fica pra quando tocarmos o cache.
 */
class ExerciseMapperTest {

    private fun dto(description: String? = "desc") = ExerciseDto(
        id = "ex-1", name = "Supino", category = ExerciseCategory.CHEST,
        description = description, videoRef = "v.mp4", thumbRef = "t.png",
        primaryMuscles = listOf(MuscleGroup.CHEST), secondaryMuscles = listOf(MuscleGroup.TRICEPS),
        equipment = "BARBELL", movementPattern = "HORIZONTAL_PUSH",
        isCompound = true, unilateral = false, prescriptionType = "REPS", level = Level.INTERMEDIATE,
    )

    @Test
    fun `toDomain passa todos os campos`() {
        val e = dto().toDomain()
        assertEquals("ex-1", e.id)
        assertEquals("Supino", e.name)
        assertEquals(ExerciseCategory.CHEST, e.category)
        assertEquals("desc", e.description)
        assertEquals("v.mp4", e.videoRef)
        assertEquals("t.png", e.thumbRef)
        // taxonomia (seções do detalhe)
        assertEquals(listOf(MuscleGroup.CHEST), e.primaryMuscles)
        assertEquals(listOf(MuscleGroup.TRICEPS), e.secondaryMuscles)
        assertEquals("BARBELL", e.equipment)
        assertEquals(true, e.isCompound)
        assertEquals(Level.INTERMEDIATE, e.level)
    }

    @Test
    fun `descricao nula passa como nula`() {
        assertNull(dto(description = null).toDomain().description)
    }
}
