package dev.rafael.server.features.profile

import dev.rafael.contract.profile.ageGateSatisfied
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gate de idade (#24). <18 exige aceite de supervisão; >=69 é só informativo; nulo não bloqueia. */
class AgeGateTest {

    @Test
    fun `maior de idade passa sem aceite`() {
        assertTrue(ageGateSatisfied(18, minorSupervised = false))
        assertTrue(ageGateSatisfied(30, minorSupervised = false))
        assertTrue(ageGateSatisfied(72, minorSupervised = false))   // >=69 não bloqueia (informativo)
    }

    @Test
    fun `menor de 18 so passa com aceite de supervisao`() {
        assertFalse(ageGateSatisfied(16, minorSupervised = false))
        assertTrue(ageGateSatisfied(16, minorSupervised = true))
    }

    @Test
    fun `idade nula nao bloqueia (perfil legado)`() {
        assertTrue(ageGateSatisfied(null, minorSupervised = false))
    }
}
