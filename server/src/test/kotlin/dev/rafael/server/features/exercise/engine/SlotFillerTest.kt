package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.server.features.exercise.models.Exercise
import dev.rafael.server.features.exercise.models.MovementPattern
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Testa o preenchedor de slots (F.4) — puro, pool de Exercise montado à mão.
 * Prova o núcleo do ARCH #28: base-first, fallback pra variação, dedup por padrão,
 * filtro por papel (isolamento = não-composto) e não repetição no dia.
 */
class SlotFillerTest {

    // ---- helpers ----
    private fun ex(
        name: String,
        primary: List<MuscleGroup> = listOf(MuscleGroup.CHEST),
        compound: Boolean = true,
        pattern: MovementPattern? = MovementPattern.HORIZONTAL_PUSH,
        base: Boolean = true,
        category: ExerciseCategory = ExerciseCategory.CHEST,
        level: Level = Level.INTERMEDIATE,
    ) = Exercise(
        id = Uuid.random(),
        name = name,
        category = category,
        description = null,
        videoRef = "",
        thumbRef = "",
        isCompound = compound,
        movementPattern = pattern,
        primaryMuscles = primary,
        level = level,
        isBase = base,
    )

    private fun slot(
        target: TargetMuscle = TargetMuscle.CHEST,
        role: SlotRole = SlotRole.COMPOSTO_PESADO,
    ) = Slot(target, role, sets = 3, repRange = "5-8", restSeconds = 150, rir = 2)

    private fun fill(pool: List<Exercise>, vararg slots: Slot) =
        SlotFiller(seed = 1L).fillDay(
            day = DaySkeleton("Teste", slots.toList()),
            pool = pool,
            focusMuscles = emptySet(),
            userLevel = "INTERMEDIATE",
            alreadyUsed = mutableSetOf(),
        )

    @Test
    fun `base vem antes da variacao quando ambos casam`() {
        val variacao = ex("Pullover", base = false)
        val principal = ex("Supino", base = true)

        val out = fill(listOf(variacao, principal), slot())

        assertEquals(1, out.size)
        assertTrue(out.first().exercise.isBase, "deveria escolher o exercício base")
        assertEquals("Supino", out.first().exercise.name)
    }

    @Test
    fun `cai pra variacao quando nao ha base no slot`() {
        val variacao = ex("Pullover", base = false)
        val variacao2 = ex("Pullover barra", base = false)

        val out = fill(listOf(variacao, variacao2), slot())

        assertEquals(1, out.size)
        assertFalse(out.first().exercise.isBase, "sem base, cai pra variação")
    }

    @Test
    fun `isolamento escolhe exercicio nao-composto`() {
        val composto = ex("Supino", compound = true)
        val isolamento = ex("Crucifixo", compound = false)

        val out = fill(listOf(composto, isolamento), slot(role = SlotRole.ISOLAMENTO))

        assertEquals(1, out.size)
        assertFalse(out.first().exercise.isCompound == true, "slot de isolamento só pega não-composto")
        assertEquals("Crucifixo", out.first().exercise.name)
    }

    @Test
    fun `nao repete o mesmo exercicio no dia`() {
        val a = ex("Supino")
        val b = ex("Supino inclinado")

        val out = fill(listOf(a, b), slot(), slot())

        assertEquals(2, out.size)
        assertTrue(out[0].exercise.id != out[1].exercise.id, "não pode repetir o mesmo exercício")
    }

    @Test
    fun `dedup por padrao evita dois exercicios do mesmo movimento`() {
        // 2 do mesmo padrão (HORIZONTAL_PUSH) + 1 de padrão diferente (VERTICAL_PUSH).
        // A penalidade de padrão empurra o 2º slot pro movimento ainda não usado.
        val hp1 = ex("Supino", pattern = MovementPattern.HORIZONTAL_PUSH)
        val hp2 = ex("Supino inclinado", pattern = MovementPattern.HORIZONTAL_PUSH)
        val vp = ex("Paralelas", pattern = MovementPattern.VERTICAL_PUSH)

        val out = fill(listOf(hp1, hp2, vp), slot(), slot())

        assertEquals(2, out.size)
        assertTrue(
            out[0].exercise.movementPattern != out[1].exercise.movementPattern,
            "os dois escolhidos deveriam ter padrões de movimento distintos",
        )
    }

    @Test
    fun `slot sem candidato compativel e pulado`() {
        val soCostas = ex("Remada", primary = listOf(MuscleGroup.BACK), category = ExerciseCategory.BACK)

        val out = fill(listOf(soCostas), slot(target = TargetMuscle.CHEST))

        assertTrue(out.isEmpty(), "sem candidato pro alvo, o slot é pulado")
    }
}
