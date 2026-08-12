package dev.rafael.server.features.stats

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prova a política de gamificação (ARCH #16): XP por sessão, teto diário, curva de nível
 * e — o ponto ratificado — o streak que NÃO quebra em dia de descanso planejado.
 */
class XpPolicyTest {

    // ---- XP ----------------------------------------------------------------

    @Test
    fun `sessao sem serie feita nao vale XP`() {
        assertEquals(0, XpPolicy.xpDaSessao(0))
    }

    @Test
    fun `XP da sessao soma series mais bonus de treino`() {
        // 20 séries * 10 + 50 = 250 (treino típico de 6 exercícios)
        assertEquals(250, XpPolicy.xpDaSessao(20))
    }

    @Test
    fun `teto diario impede farm de sessoes repetidas`() {
        // 5 sessões de 20 séries dariam 1250; o teto corta em 500
        val xp = XpPolicy.xpDoDia(listOf(20, 20, 20, 20, 20))
        assertEquals(XpPolicy.TETO_DIARIO, xp)
    }

    // ---- níveis ------------------------------------------------------------

    @Test
    fun `comeca no nivel 1 sem XP`() {
        val p = XpPolicy.progresso(0)
        assertEquals(1, p.nivel)
        assertEquals(0, p.xpNoNivel)
        assertEquals(1000, p.xpParaProximo)
    }

    @Test
    fun `sobe de nivel ao completar o custo`() {
        val p = XpPolicy.progresso(1000)          // fecha exatamente o nível 1
        assertEquals(2, p.nivel)
        assertEquals(0, p.xpNoNivel)
        assertEquals(1250, p.xpParaProximo)       // nível 2 custa 250 a mais
    }

    @Test
    fun `cada nivel custa 250 a mais que o anterior`() {
        assertEquals(1000, XpPolicy.custoDoNivel(1))
        assertEquals(1250, XpPolicy.custoDoNivel(2))
        assertEquals(3750, XpPolicy.custoDoNivel(12))
    }

    // ---- streak ------------------------------------------------------------

    private fun d(dia: Int) = LocalDate(2026, 8, dia)

    @Test
    fun `sem sessao nenhuma o streak e zero`() {
        assertEquals(0, XpPolicy.streak(emptySet(), setOf(1, 3, 5), d(12)))
    }

    @Test
    fun `dias consecutivos treinados contam`() {
        // 10, 11, 12 de agosto (seg, ter, qua) — todos dias de treino, todos com sessão
        val streak = XpPolicy.streak(setOf(d(10), d(11), d(12)), setOf(1, 2, 3, 4, 5), d(12))
        assertEquals(3, streak)
    }

    @Test
    fun `dia de descanso planejado NAO quebra a sequencia`() {
        // treina seg(10) e qua(12); terça(11) não é dia de treino -> sequência segue
        val streak = XpPolicy.streak(setOf(d(10), d(12)), diasDeTreino = setOf(1, 3, 5), hoje = d(12))
        assertEquals(3, streak, "descanso agendado deve ser protegido (regra ratificada)")
    }

    @Test
    fun `faltar num dia de treino quebra a sequencia`() {
        // terça(11) era dia de treino e não teve sessão -> só conta hoje
        val streak = XpPolicy.streak(setOf(d(10), d(12)), diasDeTreino = setOf(1, 2, 3), hoje = d(12))
        assertEquals(1, streak)
    }

    @Test
    fun `dia de treino ainda em aberto nao quebra`() {
        // hoje(12) é dia de treino e ainda não treinou — o dia não acabou; ontem(11) conta
        val streak = XpPolicy.streak(setOf(d(10), d(11)), diasDeTreino = setOf(1, 2, 3), hoje = d(12))
        assertEquals(2, streak)
    }

    @Test
    fun `sem programa so as sessoes contam`() {
        // sem dias agendados não há descanso planejado a proteger
        val streak = XpPolicy.streak(setOf(d(10), d(12)), diasDeTreino = emptySet(), hoje = d(12))
        assertEquals(1, streak)
    }

    @Test
    fun `streak e limitado para nao varrer historico infinito`() {
        val dias = (1..30).map { d(it) }.toSet()
        assertTrue(XpPolicy.streak(dias, setOf(1, 2, 3, 4, 5, 6, 7), d(30)) <= 365)
    }
}
