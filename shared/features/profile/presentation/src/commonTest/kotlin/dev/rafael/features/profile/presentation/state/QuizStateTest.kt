package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `QuizState` puro (tier 2 do plano "acessórios no onboarding", §6) -- sem `viewModelScope`,
 * sem fake de repositório: `visibleSteps` e `toProfileOrNull()` são `val`/`fun` de dados, e é
 * isso que os torna testáveis sem subir o `QuizViewModel` inteiro.
 */
class QuizStateTest {

    private fun completo(environment: TrainingEnvironment?, accessories: List<Accessory> = listOf(Accessory.ELEVATED_SURFACE)) =
        QuizState(
            goal = Goal.GAIN_MUSCLE,
            level = Level.INTERMEDIATE,
            daysPerWeek = 3,
            age = 30,
            environment = environment,
            accessories = accessories,
        )

    // ---- visibleSteps: ACADEMIA não mostra o checklist, CASA mostra ----

    @Test
    fun `academia nao mostra o passo de acessorios`() {
        assertFalse(QuizStep.ACCESSORIES in completo(TrainingEnvironment.ACADEMIA).visibleSteps)
    }

    @Test
    fun `casa mostra o passo de acessorios`() {
        assertTrue(QuizStep.ACCESSORIES in completo(TrainingEnvironment.CASA).visibleSteps)
    }

    /** Ambiente ainda não escolhido (começo do quiz) não é CASA -- o passo fica escondido. */
    @Test
    fun `ambiente nulo nao mostra o passo de acessorios`() {
        assertFalse(QuizStep.ACCESSORIES in completo(null).visibleSteps)
    }

    // ---- toProfileOrNull: o que foi marcado chega no Profile (que vira o ProfileDto) ----

    @Test
    fun `casa manda os acessorios marcados`() {
        val marcados = listOf(Accessory.BENCH, Accessory.STEP)
        val perfil = completo(TrainingEnvironment.CASA, marcados).toProfileOrNull()

        assertEquals(marcados, perfil?.accessories)
    }

    /** Desmarcar tudo em CASA manda `[]`, não `null` -- é a resposta real de quem respondeu. */
    @Test
    fun `casa sem nada marcado manda lista vazia, nao nula`() {
        val perfil = completo(TrainingEnvironment.CASA, emptyList()).toProfileOrNull()

        assertEquals(emptyList<Accessory>(), perfil?.accessories)
    }

    /** Academia nunca pergunta -- o que sobrou no estado (o default) não pode vazar pro servidor. */
    @Test
    fun `academia manda null mesmo com o default marcado no estado`() {
        val perfil = completo(TrainingEnvironment.ACADEMIA).toProfileOrNull()

        assertNull(perfil?.accessories)
    }

    /** Caminho de falha: obrigatório faltando (aqui, `goal`) devolve `null`, nunca um Profile capenga. */
    @Test
    fun `obrigatorio faltando devolve nulo`() {
        val semObjetivo = completo(TrainingEnvironment.CASA).copy(goal = null)

        assertNull(semObjetivo.toProfileOrNull())
    }
}
