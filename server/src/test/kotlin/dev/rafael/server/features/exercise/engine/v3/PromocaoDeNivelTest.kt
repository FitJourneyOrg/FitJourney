package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Evento
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.exercise.engine.v3.PromocaoDeNivel.Diagnostico
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 11 — casos à mão de [PromocaoDeNivel], explicando o PORQUÊ de cada regra.
 * Espelha as classes `DiagnosticoDePromocaoDoIniciante`, `DiagnosticoDePromocaoDoPerfil`,
 * `InvariantesDoDiagnosticoDePromocao`, `DiagnosticoDePromocaoAoAvancado` e
 * `PodeSugerirPromocaoDeNovo` de `test_progressao_v3.py`. A grade byte-a-byte (tipos `PD`/`PP`/
 * `PA`/`PN` de `referencia_progressao.txt`) está em [ProgressaoContraOOraculoTest] — mesma lição
 * da Fatia 9: dois lados concordando não é prova, os casos à mão dizem o motivo.
 */
class PromocaoDeNivelTest {

    private val SOBE = Evento.Carga(Decisao.SOBE)
    private val MANTEM = Evento.Carga(Decisao.MANTEM)
    private val DELOAD = Evento.Deload

    private fun repete(evento: Evento, vezes: Int) = List(vezes) { evento }

    // --------------------------------------------------------- diagnóstico do iniciante (1 âncora)

    @Test
    fun `sem deload nenhum nao ha o que diagnosticar`() {
        assertNull(PromocaoDeNivel.diagnosticoIniciante(listOf(SOBE, SOBE, MANTEM)))
        assertNull(
            PromocaoDeNivel.diagnosticoIniciante(listOf(SOBE, MANTEM, MANTEM)),
            "estagnou mas ainda nao deloadou -- isto dispara o 1o deload, nao diagnostica nada",
        )
    }

    @Test
    fun `dentro da janela pos-deload ainda nao diagnostica`() {
        val h = repete(MANTEM, 3) + listOf(DELOAD, MANTEM, MANTEM)
        assertNull(PromocaoDeNivel.diagnosticoIniciante(h))
    }

    @Test
    fun `subiu e reestagnou promove`() {
        val h = repete(MANTEM, 3) + listOf(DELOAD, SOBE, MANTEM, MANTEM, MANTEM)
        assertEquals(Diagnostico.PROMOVER, PromocaoDeNivel.diagnosticoIniciante(h))
    }

    @Test
    fun `ainda subindo depois do deload nao diagnostica`() {
        val h = repete(MANTEM, 3) + listOf(DELOAD, SOBE, SOBE)
        assertNull(PromocaoDeNivel.diagnosticoIniciante(h), "RETOMOU, nao reestagnou ainda: nada a diagnosticar")
    }

    @Test
    fun `nunca subiu depois do 1o deload e recuperacao sem aviso extra`() {
        val h = repete(MANTEM, 3) + listOf(DELOAD, MANTEM, MANTEM, MANTEM)
        assertEquals(Diagnostico.RECUPERACAO, PromocaoDeNivel.diagnosticoIniciante(h))
    }

    @Test
    fun `dois deloads consecutivos sem retomada vira recuperacao com aviso`() {
        val h = repete(MANTEM, 3) + listOf(DELOAD, MANTEM, MANTEM, MANTEM, DELOAD, MANTEM, MANTEM, MANTEM)
        assertEquals(Diagnostico.RECUPERACAO_SEM_RETOMADA, PromocaoDeNivel.diagnosticoIniciante(h))
    }

    @Test
    fun `retomada entre dois deloads nao conta como sem retomada`() {
        val h = repete(MANTEM, 3) + listOf(DELOAD, SOBE, MANTEM, MANTEM, MANTEM, DELOAD, MANTEM, MANTEM, MANTEM)
        assertEquals(
            Diagnostico.RECUPERACAO, PromocaoDeNivel.diagnosticoIniciante(h),
            "houve retomada antes do ultimo deload -- nao e 'dois deloads CONSECUTIVOS sem retomada'",
        )
    }

    // ------------------------------------------------------------------ diagnóstico do perfil (agregação)

    @Test
    fun `sem nenhum diagnostico nao ha nada a reportar`() {
        assertNull(PromocaoDeNivel.diagnosticoDoPerfil(listOf(null, null, null)))
    }

    @Test
    fun `uma ancora promovendo nao basta com mais de uma ancora no perfil`() {
        // 1 PROMOVER nao fecha o limiar de 2 (perfil com 3 ancoras) -- cai para o pior caso entre
        // as outras, que e exatamente o que deve acontecer (nao fica mudo).
        assertEquals(
            Diagnostico.RECUPERACAO,
            PromocaoDeNivel.diagnosticoDoPerfil(listOf(null, Diagnostico.RECUPERACAO, Diagnostico.PROMOVER)),
        )
    }

    @Test
    fun `duas ancoras promovendo promove`() {
        assertEquals(
            Diagnostico.PROMOVER,
            PromocaoDeNivel.diagnosticoDoPerfil(listOf(Diagnostico.PROMOVER, Diagnostico.RECUPERACAO, Diagnostico.PROMOVER)),
        )
    }

    @Test
    fun `com uma unica ancora no perfil ela sozinha basta`() {
        assertEquals(Diagnostico.PROMOVER, PromocaoDeNivel.diagnosticoDoPerfil(listOf(Diagnostico.PROMOVER)))
        assertEquals(Diagnostico.RECUPERACAO, PromocaoDeNivel.diagnosticoDoPerfil(listOf(Diagnostico.RECUPERACAO)))
    }

    @Test
    fun `sem duas promovendo o pior caso de recuperacao vence`() {
        assertEquals(
            Diagnostico.RECUPERACAO_SEM_RETOMADA,
            PromocaoDeNivel.diagnosticoDoPerfil(
                listOf(null, Diagnostico.PROMOVER, Diagnostico.RECUPERACAO, Diagnostico.RECUPERACAO_SEM_RETOMADA),
            ),
            "1 PROMOVER nao basta (perfil com 4 ancoras); entre as recuperacoes, o pior caso vence",
        )
    }

    @Test
    fun `so recuperacao simples`() {
        assertEquals(Diagnostico.RECUPERACAO, PromocaoDeNivel.diagnosticoDoPerfil(listOf(null, Diagnostico.RECUPERACAO)))
    }

    // --------------------------------------------------------------------------------- invariante

    /**
     * `[INV]` trancado -- mesmo padrão do `ombro_vira_isolamento` (Fatia 10): número MEDIDO na
     * matriz de hoje, não suposto. Se isto quebrar, é sinal de que o ramo de 1 âncora em
     * [PromocaoDeNivel.diagnosticoDoPerfil] passou a ter caso vivo — investigar, não só
     * atualizar o número. Espelha `test_nenhum_beginner_tem_menos_de_5_ancoras`.
     */
    @Test
    fun `nenhum beginner da matriz de 720 tem menos de 5 ancoras`() {
        val contagens = Oraculo.referencia()
            .filter { it.id.contains("-BEGINNER-") }
            .map { ref -> ref.vagas.count { it.exercicio.isNotEmpty() } }
        assertEquals(240, contagens.size)
        assertTrue(
            contagens.min() >= 5,
            "se isto cair abaixo de 5, o ramo de 1 ancora em diagnosticoDoPerfil ganhou caso vivo -- nao e so numero novo",
        )
        assertEquals(14, contagens.sorted()[contagens.size / 2], "mediana registrada na Fatia 11")
    }

    // --------------------------------------------------------------------- diagnóstico ao avançado

    @Test
    fun `1rm subiu nao diagnostica`() {
        assertNull(PromocaoDeNivel.diagnosticoAvancado(100.0, 105.0, reducoesNoPeriodo = 0))
    }

    @Test
    fun `sem 1rm nao diagnostica`() {
        assertNull(PromocaoDeNivel.diagnosticoAvancado(null, 100.0, reducoesNoPeriodo = 0))
        assertNull(PromocaoDeNivel.diagnosticoAvancado(100.0, null, reducoesNoPeriodo = 0))
    }

    @Test
    fun `sem ganho e poucas reducoes promove`() {
        assertEquals(Diagnostico.PROMOVER, PromocaoDeNivel.diagnosticoAvancado(100.0, 100.0, reducoesNoPeriodo = 0))
        assertEquals(Diagnostico.PROMOVER, PromocaoDeNivel.diagnosticoAvancado(100.0, 98.0, reducoesNoPeriodo = 1))
    }

    @Test
    fun `sem ganho e muitas reducoes e recuperacao`() {
        assertEquals(Diagnostico.RECUPERACAO, PromocaoDeNivel.diagnosticoAvancado(100.0, 95.0, reducoesNoPeriodo = 2))
    }

    @Test
    fun `limite de poucas reducoes e configuravel`() {
        assertEquals(
            Diagnostico.RECUPERACAO,
            PromocaoDeNivel.diagnosticoAvancado(100.0, 100.0, reducoesNoPeriodo = 2, limitePoucasReducoes = 1),
        )
        assertEquals(
            Diagnostico.PROMOVER,
            PromocaoDeNivel.diagnosticoAvancado(100.0, 100.0, reducoesNoPeriodo = 2, limitePoucasReducoes = 2),
        )
    }

    // ------------------------------------------------------------------------ pode sugerir de novo

    @Test
    fun `nunca recusou sempre pode`() {
        assertTrue(PromocaoDeNivel.podeSugerirDeNovo(mesocicloAtual = 1, mesocicloNaRecusa = null))
    }

    @Test
    fun `mesociclo seguinte ao da recusa ainda nao pode`() {
        assertFalse(PromocaoDeNivel.podeSugerirDeNovo(mesocicloAtual = 5, mesocicloNaRecusa = 4))
    }

    @Test
    fun `dois mesociclos depois da recusa pode`() {
        assertTrue(PromocaoDeNivel.podeSugerirDeNovo(mesocicloAtual = 6, mesocicloNaRecusa = 4))
    }

    @Test
    fun `mesmo mesociclo da recusa nao pode`() {
        assertFalse(PromocaoDeNivel.podeSugerirDeNovo(mesocicloAtual = 4, mesocicloNaRecusa = 4))
    }
}
