package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.core.database.Exercise as ExerciseRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Testa ExerciseDto.toDomain() (rede, puro) e ExerciseRow.toDomainOrNull() (linha do cache
 * local, ver 5.sqm) — os dois caminhos que alimentam o domínio.
 */
class ExerciseMapperTest {

    private fun dto(description: String? = "desc") = ExerciseDto(
        id = "ex-1", name = "Supino", category = ExerciseCategory.CHEST,
        description = description, videoRef = "v.mp4", thumbRef = "t.png",
        primaryMuscles = listOf(MuscleGroup.CHEST), secondaryMuscles = listOf(MuscleGroup.TRICEPS),
        equipment = "BARBELL", movementPattern = "HORIZONTAL_PUSH",
        isCompound = true, unilateral = false, prescriptionType = "REPS", level = Level.INTERMEDIATE,
    )

    private fun row(
        category: String = "CHEST",
        primaryMuscles: String? = null,
        secondaryMuscles: String? = null,
    ) = ExerciseRow(
        id = "ex-1", name = "Supino", category = category,
        description = "desc", videoRef = "v.mp4", thumbRef = "t.png",
        primaryMuscles = primaryMuscles, secondaryMuscles = secondaryMuscles,
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

    @Test
    fun `toDomainOrNull decodifica os musculos gravados pelo replaceAll`() {
        val e = row(
            primaryMuscles = "[\"CHEST\"]",
            secondaryMuscles = "[\"TRICEPS\",\"SHOULDERS\"]",
        ).toDomainOrNull()

        assertEquals(listOf(MuscleGroup.CHEST), e?.primaryMuscles)
        assertEquals(listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS), e?.secondaryMuscles)
    }

    @Test
    fun `toDomainOrNull trata coluna nula como lista vazia (linha anterior a 5-sqm)`() {
        val e = row(primaryMuscles = null, secondaryMuscles = null).toDomainOrNull()

        assertEquals(emptyList(), e?.primaryMuscles)
        assertEquals(emptyList(), e?.secondaryMuscles)
    }

    @Test
    fun `toDomainOrNull trata JSON corrompido como lista vazia, nunca lanca`() {
        val e = row(primaryMuscles = "{isto nao eh json valido").toDomainOrNull()

        assertEquals(emptyList(), e?.primaryMuscles)
    }

    @Test
    fun `toDomainOrNull continua nulo pra categoria fora do enum, com musculos presentes`() {
        val e = row(category = "CATEGORIA_QUE_NAO_EXISTE", primaryMuscles = "[\"CHEST\"]").toDomainOrNull()

        assertNull(e)
    }

    @Test
    fun `toDomainOrNull nao preenche a taxonomia que o cache nao guarda`() {
        val e = row(primaryMuscles = "[\"CHEST\"]").toDomainOrNull()

        assertNull(e?.equipment)
        assertNull(e?.movementPattern)
        assertNull(e?.isCompound)
        assertNull(e?.level)
    }
}
