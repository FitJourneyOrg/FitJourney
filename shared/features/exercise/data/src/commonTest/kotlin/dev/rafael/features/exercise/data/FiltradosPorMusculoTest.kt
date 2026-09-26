package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.features.exercise.domain.model.Exercise
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O filtro por músculo, isolado do banco pela mesma razão que `PrecisaRebaixarTest`
 * (ver comentário em `ExerciseRepositoryImpl.filtradosPorMusculo`): SQLite sem json1 não
 * consulta dentro da coluna JSON de `primaryMuscles`/`secondaryMuscles` (5.sqm), então o filtro
 * roda em memória e é só uma função pura — não precisa de `ExerciseLocalDataSource` (que abre
 * `FitJourneyDatabase` direto, sem interface) pra ser testado.
 */
class FiltradosPorMusculoTest {

    private fun exercicio(
        id: String,
        primaryMuscles: List<MuscleGroup> = emptyList(),
        secondaryMuscles: List<MuscleGroup> = emptyList(),
    ) = Exercise(
        id = id, name = id, category = ExerciseCategory.CHEST,
        description = null, videoRef = "v.mp4", thumbRef = "t.png",
        primaryMuscles = primaryMuscles, secondaryMuscles = secondaryMuscles,
        equipment = null, movementPattern = null,
        isCompound = null, unilateral = null, prescriptionType = null, level = null,
    )

    @Test
    fun `sem filtro devolve a lista inteira`() {
        val lista = listOf(exercicio("a"), exercicio("b"))
        assertEquals(lista, lista.filtradosPorMusculo(null))
    }

    @Test
    fun `musculo primario entra no filtro`() {
        val agachamento = exercicio("agachamento", primaryMuscles = listOf(MuscleGroup.LEGS))
        val supino = exercicio("supino", primaryMuscles = listOf(MuscleGroup.CHEST))

        assertEquals(
            listOf(agachamento),
            listOf(agachamento, supino).filtradosPorMusculo(MuscleGroup.LEGS),
        )
    }

    @Test
    fun `musculo secundario tambem entra no filtro`() {
        // panturrilha em pé: foco é perna, mas alguns catálogos listam core como secundário
        // (estabilização) — quem filtra por "core" quer ver isto também, não só o que tem
        // core como foco principal.
        val panturrilha = exercicio(
            "panturrilha",
            primaryMuscles = listOf(MuscleGroup.LEGS),
            secondaryMuscles = listOf(MuscleGroup.CORE),
        )
        val supino = exercicio("supino", primaryMuscles = listOf(MuscleGroup.CHEST))

        assertEquals(
            listOf(panturrilha),
            listOf(panturrilha, supino).filtradosPorMusculo(MuscleGroup.CORE),
        )
    }

    @Test
    fun `musculo que ninguem tem devolve lista vazia, nao erro`() {
        val lista = listOf(exercicio("a", primaryMuscles = listOf(MuscleGroup.CHEST)))
        assertEquals(emptyList(), lista.filtradosPorMusculo(MuscleGroup.GLUTES))
    }
}
