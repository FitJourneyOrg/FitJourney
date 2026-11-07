package dev.rafael.server.features.profile

import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TimeOffTraining
import dev.rafael.contract.profile.TrainingConsistency
import dev.rafael.contract.profile.nivelDerivado
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Fatia 12: nível deixou de ser autodeclarado. Iniciante se nunca treinou, treina há menos
 * de 6 meses, OU parou há mais de 6 meses -- senão intermediário. Nunca avançado (entrada
 * máxima é intermediário; avançado só vem de `PromocaoDeNivel`, por histórico).
 */
class NivelDerivadoTest {

    @Test
    fun `nunca treinou e iniciante mesmo sem ter parado`() {
        assertEquals(
            Level.BEGINNER,
            nivelDerivado(TrainingConsistency.NEVER_TRAINED, TimeOffTraining.NONE),
        )
    }

    @Test
    fun `menos de 6 meses consistente e iniciante mesmo sem ter parado`() {
        assertEquals(
            Level.BEGINNER,
            nivelDerivado(TrainingConsistency.LESS_THAN_6_MONTHS, TimeOffTraining.NONE),
        )
    }

    @Test
    fun `parado ha mais de 6 meses e iniciante mesmo com anos de consistencia`() {
        assertEquals(
            Level.BEGINNER,
            nivelDerivado(TrainingConsistency.MORE_THAN_2_YEARS, TimeOffTraining.MORE_THAN_6_MONTHS),
        )
    }

    @Test
    fun `consistente ha 6 meses a 2 anos e sem parar muito e intermediario`() {
        assertEquals(
            Level.INTERMEDIATE,
            nivelDerivado(TrainingConsistency.SIX_MONTHS_TO_2_YEARS, TimeOffTraining.UP_TO_1_MONTH),
        )
    }

    @Test
    fun `mais de 2 anos consistente nunca vira avancado pelo cadastro -- teto e intermediario`() {
        assertEquals(
            Level.INTERMEDIATE,
            nivelDerivado(TrainingConsistency.MORE_THAN_2_YEARS, TimeOffTraining.NONE),
        )
    }
}
