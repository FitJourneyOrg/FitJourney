package dev.rafael.server.features.stats

import dev.rafael.server.features.stats.AchievementPolicy.Conquista
import dev.rafael.server.features.stats.AchievementPolicy.Progresso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A regra decide o que o usuário ganha — e, sendo pura, dá pra exercitar cada limiar sem banco.
 */
class AchievementPolicyTest {

    private fun progresso(sessoes: Int = 0, streak: Int = 0, nivel: Int = 1) =
        Progresso(sessoesValidas = sessoes, streakDias = streak, nivel = nivel)

    @Test
    fun `usuario zerado nao ganha nada`() {
        assertTrue(AchievementPolicy.alcancadas(progresso()).isEmpty())
    }

    @Test
    fun `primeira sessao valida ja concede`() {
        assertEquals(
            setOf(Conquista.PRIMEIRO_TREINO),
            AchievementPolicy.alcancadas(progresso(sessoes = 1)),
        )
    }

    @Test
    fun `alcancar um limiar concede tambem os anteriores`() {
        // Quem chega em 50 sem nunca ter aberto o app antes tem direito a 1, 10 e 50 — a
        // conquista mede o marco, não o instante em que o app olhou.
        val r = AchievementPolicy.alcancadas(progresso(sessoes = 50))

        assertEquals(
            setOf(Conquista.PRIMEIRO_TREINO, Conquista.TREINOS_10, Conquista.TREINOS_50),
            r,
        )
    }

    @Test
    fun `limiar e maior ou igual, nao maior`() {
        assertTrue(Conquista.TREINOS_10 in AchievementPolicy.alcancadas(progresso(sessoes = 10)))
        assertTrue(Conquista.TREINOS_10 !in AchievementPolicy.alcancadas(progresso(sessoes = 9)))
    }

    @Test
    fun `metricas sao independentes`() {
        // Streak alto com poucas sessões é possível: dia de descanso agendado conta como
        // cumprido (ver XpPolicy.streak). Uma métrica não pode arrastar a outra.
        val r = AchievementPolicy.alcancadas(progresso(sessoes = 1, streak = 30))

        assertTrue(Conquista.STREAK_7 in r)
        assertTrue(Conquista.STREAK_30 in r)
        assertTrue(Conquista.TREINOS_10 !in r)
    }

    @Test
    fun `nivel concede pela faixa alcancada`() {
        val r = AchievementPolicy.alcancadas(progresso(nivel = 10))

        assertTrue(Conquista.NIVEL_5 in r)
        assertTrue(Conquista.NIVEL_10 in r)
    }

    @Test
    fun `aConceder subtrai o que ja esta no banco`() {
        val r = AchievementPolicy.aConceder(
            progresso = progresso(sessoes = 10),
            jaConcedidas = setOf(Conquista.PRIMEIRO_TREINO),
        )

        assertEquals(setOf(Conquista.TREINOS_10), r, "não pode reconceder o que já existe")
    }

    @Test
    fun `nada a conceder quando tudo ja foi dado`() {
        // Idempotência: a avaliação roda a cada sessão registrada e não pode gerar escrita
        // nem notificação repetida.
        val p = progresso(sessoes = 10)

        assertTrue(AchievementPolicy.aConceder(p, AchievementPolicy.alcancadas(p)).isEmpty())
    }

    @Test
    fun `retroativo concede tudo de uma vez na primeira avaliacao`() {
        // Quem já tinha 60 treinos quando a feature nasceu: sem migration de backfill, a
        // subtração contra um banco vazio entrega as quatro de sessões na primeira passada.
        val r = AchievementPolicy.aConceder(progresso(sessoes = 60), jaConcedidas = emptySet())

        assertEquals(3, r.count { it.metrica == AchievementPolicy.Metrica.SESSOES })
        assertTrue(Conquista.TREINOS_100 !in r)
    }

    @Test
    fun `regressao de progresso nao retira o que ja foi concedido`() {
        // Streak quebra o tempo todo. `aConceder` nunca devolve remoção — quem decide o que
        // some seria o chamador, e ninguém remove. É o ponto inteiro de persistir.
        val r = AchievementPolicy.aConceder(
            progresso = progresso(sessoes = 10, streak = 0),
            jaConcedidas = setOf(Conquista.STREAK_7),
        )

        assertTrue(Conquista.STREAK_7 !in r)
        assertEquals(setOf(Conquista.PRIMEIRO_TREINO, Conquista.TREINOS_10), r)
    }
}
