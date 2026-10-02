package dev.rafael.server.features.stats

import dev.rafael.contract.stats.ConquistaIds
import dev.rafael.server.features.stats.AchievementPolicy.Conquista
import dev.rafael.server.features.stats.AchievementPolicy.Progresso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A regra decide o que o usuário ganha — e, sendo pura, dá pra exercitar cada limiar sem banco.
 */
class AchievementPolicyTest {

    private fun progresso(sessoes: Int = 0, streak: Int = 0, nivel: Int = 1, cargaKg: Double = 0.0) =
        Progresso(sessoesValidas = sessoes, streakDias = streak, nivel = nivel, cargaTotalKg = cargaKg)

    /**
     * ⭐ **O enum do servidor e o vocabulário do contrato são o MESMO conjunto** (G.5, ARCH #37).
     *
     * Este teste é a metade servidor da cobertura de textos; a outra é o `TextosDeConquistaTest`
     * no cliente, que percorre o [ConquistaIds] cobrando frase para cada id.
     *
     * Sem ele a corrente arrebenta no elo do meio, **e arrebenta calada**: uma conquista nova aqui
     * sem entrada lá seria concedida, gravada no banco e mandada no DTO, e a tela simplesmente não
     * a desenharia — o `TextosDeConquista.de` devolve `null` e o cliente pula o id que não conhece,
     * que é o comportamento certo para app antigo e o errado para conquista recém-criada.
     *
     * > **Medalha que o servidor concede e a tela não mostra é pior que medalha que não existe.**
     *
     * Nos dois sentidos, porque os dois erros acontecem: id sobrando no contrato é lixo de uma
     * conquista apagada, e o tradutor recebe uma frase que ninguém vai ler.
     */
    @Test
    fun `o vocabulario do contrato cobre exatamente o enum do servidor`() {
        assertEquals(
            Conquista.entries.map { it.name }.toSet(),
            ConquistaIds.TODOS,
            "AchievementPolicy.Conquista e ConquistaIds.TODOS divergiram. " +
                "Conquista nova precisa da constante no contrato e da frase no strings.xml.",
        )
    }

    /**
     * O alvo é o que sobrou de regra no enum depois que o texto saiu, e alvo zero ou negativo
     * faria `current / target` estourar na barra de progresso da tela.
     */
    @Test
    fun `toda conquista tem alvo positivo`() {
        val invalidas = Conquista.entries.filter { it.alvo <= 0 }
        assertTrue(invalidas.isEmpty(), "conquista com alvo não positivo: $invalidas")
    }

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

    // ---- carga acumulada (J.2) ---------------------------------------------

    @Test
    fun `carga e medida em toneladas, nao em quilos`() {
        // 10.000 kg = 10 t: alcanca a de 1 t e a de 10 t, nao a de 50 t
        val alcancadas = AchievementPolicy.alcancadas(progresso(cargaKg = 10_000.0))
        assertTrue(Conquista.CARGA_1T in alcancadas)
        assertTrue(Conquista.CARGA_10T in alcancadas)
        assertTrue(Conquista.CARGA_50T !in alcancadas)
    }

    @Test
    fun `tonelada incompleta nao concede - 999 kg ainda e zero tonelada`() {
        assertTrue(Conquista.CARGA_1T !in AchievementPolicy.alcancadas(progresso(cargaKg = 999.0)))
    }

    @Test
    fun `quem so treina peso corporal nao alcanca medalha de carga`() {
        // carga zero, mas 80 sessoes: ganha as de sessao e nenhuma de carga
        val alcancadas = AchievementPolicy.alcancadas(progresso(sessoes = 80, cargaKg = 0.0))
        assertTrue(Conquista.TREINOS_50 in alcancadas)
        assertTrue(alcancadas.none { it.metrica == AchievementPolicy.Metrica.CARGA })
    }

    @Test
    fun `carga retroativa concede todas as faixas de uma vez`() {
        // 175,8 t: as quatro primeiras faixas, nao a de 250 t
        val novas = AchievementPolicy.aConceder(progresso(cargaKg = 175_876.0), jaConcedidas = emptySet())
        val deCarga = novas.filter { it.metrica == AchievementPolicy.Metrica.CARGA }
        assertEquals(4, deCarga.size)
        assertTrue(Conquista.CARGA_250T !in novas)
    }

    @Test
    fun `carga nao contamina as outras metricas`() {
        val alcancadas = AchievementPolicy.alcancadas(progresso(cargaKg = 500_000.0))
        assertTrue(alcancadas.all { it.metrica == AchievementPolicy.Metrica.CARGA })
    }

}
