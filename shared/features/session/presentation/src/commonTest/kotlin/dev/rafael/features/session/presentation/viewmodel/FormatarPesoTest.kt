package dev.rafael.features.session.presentation.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O formato do peso é puro e tem teste próprio porque é onde o erro é SILENCIOSO: somar 2,5 dez
 * vezes em `Double` não dá exatamente 25, e sem arredondar a tela mostraria `24.999999999999996`.
 */
class FormatarPesoTest {

    @Test
    fun `numero redondo nao carrega decimal`() {
        assertEquals("45", formatarPeso(45.0))
        assertEquals("0", formatarPeso(0.0))
    }

    @Test
    fun `meio quilo aparece`() {
        assertEquals("42.5", formatarPeso(42.5))
        assertEquals("2.5", formatarPeso(2.5))
    }

    @Test
    fun `somar o passo dez vezes nao vaza erro de ponto flutuante`() {
        var kg = 0.0
        repeat(10) { kg += WorkoutSessionViewModel.PASSO_DO_PESO_KG }
        assertEquals("25", formatarPeso(kg), "o Double acumulou erro e ele chegou na tela")
    }

    @Test
    fun `arredonda para o decimo mais proximo`() {
        assertEquals("42.5", formatarPeso(42.54))
        assertEquals("42.6", formatarPeso(42.55))
    }
}
