package dev.rafael.server.features.program.services

import dev.rafael.contract.workout.WorkoutOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A regra de acesso do ARCH #23 isolada — é a MESMA que o blur (esconde na lista) e o
 * `requireReadable` (recusa o recurso) consultam.
 *
 * Por que este arquivo existe: o corte vivia inline no [ProgramBlur] (`index == 0`), então
 * `GET /workouts/{id}` ficou sem trava nenhuma — a listagem trancava e o recurso entregava o
 * treino completo a quem pedisse pelo id. Um bloqueio que só existe na vitrine não é bloqueio.
 */
class ProgramAccessTest {

    @Test
    fun `free em programa IA so ve o primeiro treino`() {
        assertTrue(ProgramAccess.liberado(WorkoutOrigin.AI, isPremium = false, index = 0))
        assertFalse(ProgramAccess.liberado(WorkoutOrigin.AI, isPremium = false, index = 1))
        assertFalse(ProgramAccess.liberado(WorkoutOrigin.AI, isPremium = false, index = 4))
    }

    @Test
    fun `premium ve todos os treinos`() {
        (0..4).forEach { i ->
            assertTrue(ProgramAccess.liberado(WorkoutOrigin.AI, isPremium = true, index = i))
        }
    }

    @Test
    fun `programa manual e livre mesmo para free`() {
        // ARCH #25: o usuário montou, o conteúdo é dele. O gate é só sobre programa IA.
        (0..4).forEach { i ->
            assertTrue(ProgramAccess.liberado(WorkoutOrigin.MANUAL, isPremium = false, index = i))
        }
    }

    @Test
    fun `blur e acesso concordam em todos os indices`() {
        // O ponto do ProgramAccess: se blur e gate divergirem, um lado vaza conteúdo pago e o
        // outro tranca o dia grátis. Este teste falha no instante em que alguém reimplementar
        // a regra em um dos dois.
        val programa = ProgramBlurTestFixtures.programaIa(dias = 5)
        val comBlur = ProgramBlur.apply(programa, isPremium = false)

        comBlur.workouts.forEachIndexed { index, w ->
            assertEquals(
                ProgramAccess.liberado(WorkoutOrigin.AI, isPremium = false, index),
                !w.locked,
                "índice $index: blur e ProgramAccess discordam",
            )
        }
    }
}
