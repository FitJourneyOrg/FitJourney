package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.engine.v3.SessionComposition.Papel
import dev.rafael.server.features.exercise.engine.v3.SessionComposition.Vaga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Motor v3, Fatia 5 (`docs/trabalho/motor-v3-fatia-5.md`) — prova `SessionComposition` contra
 * `server/src/test/oracle/motor_v3.py::compor_sessao()`, usando como entrada as sessões REAIS de
 * `WeekStructure.distribuirPelosDias()` da Fatia 4 (fixtures A/B/C, já verificadas lá).
 *
 * Não cobre o ponto fixo do indireto — decisão de escopo registrada no KDoc de
 * `SessionComposition` e no `historico/motor-v3-fatia-5.md`.
 */
class SessionCompositionTest {

    @Test
    fun `fixture A, Upper A - dispara ombro_teto_do_desenvolvimento e bate serie a serie com o python`() {
        val series = mapOf(
            TargetMuscle.BACK to 10, TargetMuscle.CHEST to 9, TargetMuscle.SHOULDERS to 8,
            TargetMuscle.TRICEPS to 3, TargetMuscle.BICEPS to 4,
        )
        val disparos = mutableMapOf<String, Int>()
        val vagas = SessionComposition.comporSessao(series, disparos)

        assertEquals(
            listOf(
                Vaga(TargetMuscle.CHEST, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.CHEST, Papel.ACESSORIO, 3),
                Vaga(TargetMuscle.CHEST, Papel.ISOLAMENTO, 2),
                Vaga(TargetMuscle.BACK, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.BACK, Papel.ACESSORIO, 4),
                Vaga(TargetMuscle.BACK, Papel.ISOLAMENTO, 2),
                Vaga(TargetMuscle.SHOULDERS, Papel.COMPOSTO, 3),
                Vaga(TargetMuscle.SHOULDERS, Papel.ISOLAMENTO, 3),
                Vaga(TargetMuscle.SHOULDERS, Papel.ISOLAMENTO, 2),
                Vaga(TargetMuscle.BICEPS, Papel.ISOLAMENTO, 4),
                Vaga(TargetMuscle.TRICEPS, Papel.ISOLAMENTO, 3),
            ),
            vagas,
        )
        assertEquals(mapOf("ombro_teto_do_desenvolvimento" to 1), disparos)
    }

    @Test
    fun `fixture A, Lower A - sem regra de ombro, sem disparo`() {
        val series = mapOf(TargetMuscle.QUADS to 8, TargetMuscle.POSTERIOR to 7, TargetMuscle.CALVES to 6)
        val disparos = mutableMapOf<String, Int>()
        val vagas = SessionComposition.comporSessao(series, disparos)

        assertEquals(
            listOf(
                Vaga(TargetMuscle.QUADS, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.QUADS, Papel.ISOLAMENTO, 4),
                Vaga(TargetMuscle.POSTERIOR, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.POSTERIOR, Papel.ISOLAMENTO, 3),
                Vaga(TargetMuscle.CALVES, Papel.ISOLAMENTO, 4),
                Vaga(TargetMuscle.CALVES, Papel.ISOLAMENTO, 2),
            ),
            vagas,
        )
        assertEquals(emptyMap(), disparos)
    }

    @Test
    fun `fixture A, Lower B - GLUTES com 4 series vira 1 unico composto`() {
        val series = mapOf(
            TargetMuscle.QUADS to 8, TargetMuscle.POSTERIOR to 7,
            TargetMuscle.GLUTES to 4, TargetMuscle.CALVES to 6,
        )
        val vagas = SessionComposition.comporSessao(series)

        assertEquals(
            listOf(
                Vaga(TargetMuscle.QUADS, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.QUADS, Papel.ISOLAMENTO, 4),
                Vaga(TargetMuscle.POSTERIOR, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.POSTERIOR, Papel.ISOLAMENTO, 3),
                Vaga(TargetMuscle.CALVES, Papel.ISOLAMENTO, 4),
                Vaga(TargetMuscle.CALVES, Papel.ISOLAMENTO, 2),
                Vaga(TargetMuscle.GLUTES, Papel.COMPOSTO, 4),
            ),
            vagas,
        )
    }

    @Test
    fun `fixture B, cap 40, Upper A - series abaixo do piso do composto vira isolamento`() {
        // SHOULDERS=3: papeis_para daria so composto de 3 (piso exato, n=1, s menor que piso do
        // composto NAO se aplica pois s=3 == piso) -> COMPOSTO puro de 3 series.
        val series = mapOf(TargetMuscle.BACK to 6, TargetMuscle.CHEST to 5, TargetMuscle.SHOULDERS to 3)
        val vagas = SessionComposition.comporSessao(series)

        assertEquals(
            listOf(
                Vaga(TargetMuscle.CHEST, Papel.COMPOSTO, 3),
                Vaga(TargetMuscle.CHEST, Papel.ISOLAMENTO, 2),
                Vaga(TargetMuscle.BACK, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.BACK, Papel.ISOLAMENTO, 2),
                Vaga(TargetMuscle.SHOULDERS, Papel.COMPOSTO, 3),
            ),
            vagas,
        )
    }

    @Test
    fun `fixture C, cap 65,33, Lower B - GLUTES com 2 series vira isolamento puro`() {
        val series = mapOf(
            TargetMuscle.QUADS to 5, TargetMuscle.POSTERIOR to 4,
            TargetMuscle.CALVES to 3, TargetMuscle.GLUTES to 2,
        )
        val vagas = SessionComposition.comporSessao(series)

        assertEquals(
            listOf(
                Vaga(TargetMuscle.QUADS, Papel.COMPOSTO, 3),
                Vaga(TargetMuscle.QUADS, Papel.ISOLAMENTO, 2),
                Vaga(TargetMuscle.POSTERIOR, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.CALVES, Papel.ISOLAMENTO, 3),
                Vaga(TargetMuscle.GLUTES, Papel.ISOLAMENTO, 2),
            ),
            vagas,
        )
    }

    @Test
    fun `regra do ombro vira isolamento quando o peito do dia nao deixa espaco minimo`() {
        // CHEST=11 -> composto 4 + acessorio 4 = 8 no dia; limite = 10-8 = 2, abaixo do minimo
        // de 3 series pro composto do ombro -> SHOULDERS inteiro vira isolamento.
        val series = mapOf(TargetMuscle.CHEST to 11, TargetMuscle.SHOULDERS to 8)
        val disparos = mutableMapOf<String, Int>()
        val vagas = SessionComposition.comporSessao(series, disparos)

        assertEquals(
            listOf(
                Vaga(TargetMuscle.CHEST, Papel.COMPOSTO, 4),
                Vaga(TargetMuscle.CHEST, Papel.ACESSORIO, 4),
                Vaga(TargetMuscle.CHEST, Papel.ISOLAMENTO, 3),
                Vaga(TargetMuscle.SHOULDERS, Papel.ISOLAMENTO, 4),
                Vaga(TargetMuscle.SHOULDERS, Papel.ISOLAMENTO, 4),
            ),
            vagas,
        )
        assertEquals(mapOf("ombro_vira_isolamento" to 1), disparos)
    }

    @Test
    fun `distribuir recusa total que nao preenche nem o piso dos papeis`() {
        assertFailsWith<IllegalArgumentException> {
            SessionComposition.distribuir(2, listOf(Papel.COMPOSTO))
        }
    }

    @Test
    fun `papeisPara isolamento puro para musculos SO_ISOLAMENTO, mesmo com muitas series`() {
        assertEquals(listOf(Papel.ISOLAMENTO, Papel.ISOLAMENTO, Papel.ISOLAMENTO), SessionComposition.papeisPara(TargetMuscle.BICEPS, 10))
        assertEquals(listOf(Papel.ISOLAMENTO), SessionComposition.papeisPara(TargetMuscle.CALVES, 3))
    }
}
