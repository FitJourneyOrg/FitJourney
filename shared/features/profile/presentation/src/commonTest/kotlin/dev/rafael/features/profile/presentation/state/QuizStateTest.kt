package dev.rafael.features.profile.presentation.state

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MinutesAvailable
import dev.rafael.contract.profile.TimeOffTraining
import dev.rafael.contract.profile.TrainingConsistency
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
            trainingConsistency = TrainingConsistency.SIX_MONTHS_TO_2_YEARS,
            timeOffTraining = TimeOffTraining.NONE,
            daysPerWeek = 3,
            minutesAvailable = MinutesAvailable.SIXTY,
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

    // ---- fatia 12 (resto, cliente): nível deixa de ser escolha livre -- vira levelDerivado ----

    @Test
    fun `nunca treinou deriva iniciante mesmo sem ter parado`() {
        val estado = completo(TrainingEnvironment.CASA).copy(
            trainingConsistency = TrainingConsistency.NEVER_TRAINED,
            timeOffTraining = TimeOffTraining.NONE,
        )

        assertEquals(Level.BEGINNER, estado.levelDerivado)
    }

    @Test
    fun `consistente e sem parar ha muito deriva intermediario`() {
        val estado = completo(TrainingEnvironment.CASA).copy(
            trainingConsistency = TrainingConsistency.MORE_THAN_2_YEARS,
            timeOffTraining = TimeOffTraining.UP_TO_1_MONTH,
        )

        assertEquals(Level.INTERMEDIATE, estado.levelDerivado)
    }

    /** Caminho de falha: faltando qualquer uma das duas perguntas, levelDerivado é nulo. */
    @Test
    fun `levelDerivado nulo enquanto uma das duas perguntas nao foi respondida`() {
        val semConsistencia = completo(TrainingEnvironment.CASA).copy(trainingConsistency = null)
        val semTempoParado = completo(TrainingEnvironment.CASA).copy(timeOffTraining = null)

        assertNull(semConsistencia.levelDerivado)
        assertNull(semTempoParado.levelDerivado)
    }

    /** toProfileOrNull não avança sem o nível derivado -- mesma regra dos outros obrigatórios. */
    @Test
    fun `sem levelDerivado toProfileOrNull devolve nulo`() {
        val semConsistencia = completo(TrainingEnvironment.CASA).copy(trainingConsistency = null)

        assertNull(semConsistencia.toProfileOrNull())
    }

    @Test
    fun `toProfileOrNull manda o nivel derivado, nao um escolhido livremente`() {
        val estado = completo(TrainingEnvironment.CASA).copy(
            trainingConsistency = TrainingConsistency.NEVER_TRAINED,
            timeOffTraining = TimeOffTraining.NONE,
        )

        assertEquals(Level.BEGINNER, estado.toProfileOrNull()?.level)
    }

    // ---- fatia 12 (resto, cliente): minutesAvailable ----

    @Test
    fun `minutes obrigatorio para avancar o passo`() {
        val semMinutos = completo(TrainingEnvironment.CASA).copy(minutesAvailable = null, step = QuizStep.MINUTES)
        val comMinutos = semMinutos.copy(minutesAvailable = MinutesAvailable.NINETY_PLUS)

        assertFalse(semMinutos.canAdvance)
        assertTrue(comMinutos.canAdvance)
    }

    @Test
    fun `toProfileOrNull manda o minutesAvailable marcado`() {
        val estado = completo(TrainingEnvironment.CASA).copy(minutesAvailable = MinutesAvailable.SEVENTY_FIVE)

        assertEquals(MinutesAvailable.SEVENTY_FIVE, estado.toProfileOrNull()?.minutesAvailable)
    }

    // ---- fatia 12 (resto, cliente): preferLightMode ("quer comecar no modo leve?") ----

    /** Pulável -- quem não toca no passo segue com "não" marcado, nunca trava o avanço. */
    @Test
    fun `light mode nao bloqueia o avanco mesmo sem tocar`() {
        val estado = completo(TrainingEnvironment.CASA).copy(step = QuizStep.LIGHT_MODE)

        assertTrue(estado.canAdvance)
        assertFalse(estado.preferLightMode)
    }

    @Test
    fun `toProfileOrNull manda o preferLightMode marcado`() {
        val estado = completo(TrainingEnvironment.CASA).copy(preferLightMode = true)

        assertEquals(true, estado.toProfileOrNull()?.preferLightMode)
    }
}
