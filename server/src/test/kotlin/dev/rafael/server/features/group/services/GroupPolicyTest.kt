package dev.rafael.server.features.group.services

import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupState
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * As regras do GRUPO (ARCH #33, fatia A.1).
 *
 * Este é o teste que substitui a coluna `status` e o job de virada: se `estado()` estiver certo
 * em todas as bordas, não há o que divergir, porque não existe estado persistido para divergir.
 */
class GroupPolicyTest {

    private val sp = TimeZone.of("America/Sao_Paulo")
    private val inicio = LocalDate.parse("2026-09-01")
    private val fim = LocalDate.parse("2026-09-30")

    private fun momento(iso: String) = Instant.parse(iso)

    private fun erros(r: AppResult<*>): Map<String, String> =
        ((r as AppResult.Failure).error as AppError.Validation).fieldErrors

    // ---- estado derivado ----

    @Test
    fun `antes do inicio esta AGENDADO`() {
        assertEquals(
            GroupState.AGENDADO,
            GroupPolicy.estado(inicio, fim, momento("2026-08-31T12:00:00Z"), sp),
        )
    }

    @Test
    fun `o dia do inicio ja esta ATIVO`() {
        // Borda: o grupo vale desde o PRIMEIRO instante do dia de início.
        assertEquals(
            GroupState.ATIVO,
            GroupPolicy.estado(inicio, fim, momento("2026-09-01T03:00:01Z"), sp),
        )
    }

    @Test
    fun `o dia do fim ainda esta ATIVO`() {
        // Borda oposta: quem for treinar no último dia tem o dia inteiro.
        assertEquals(
            GroupState.ATIVO,
            GroupPolicy.estado(inicio, fim, momento("2026-10-01T02:59:00Z"), sp),
        )
    }

    @Test
    fun `depois do fim esta ENCERRADO`() {
        assertEquals(
            GroupState.ENCERRADO,
            GroupPolicy.estado(inicio, fim, momento("2026-10-01T12:00:00Z"), sp),
        )
    }

    @Test
    fun `o dia vira no fuso DO GRUPO, nao no do servidor`() {
        // 2026-09-01T02:00Z ainda é 31/08 em São Paulo (UTC-3). Um servidor em UTC diria
        // "ATIVO"; o grupo diz AGENDADO, e é o grupo que manda — senão "um check-in por dia"
        // significaria coisas diferentes para pessoas diferentes no mesmo grupo (4.6).
        val quando = momento("2026-09-01T02:00:00Z")

        assertEquals(GroupState.ATIVO, GroupPolicy.estado(inicio, fim, quando, TimeZone.UTC))
        assertEquals(GroupState.AGENDADO, GroupPolicy.estado(inicio, fim, quando, sp))
    }

    // ---- código de entrada ----

    @Test
    fun `o codigo nao tem caracteres ambiguos`() {
        // O código é ditado em voz alta e digitado à mão. Confundir 0 com O manda a pessoa para
        // "grupo não encontrado" — ou, pior, para outro grupo.
        val amostra = (1..500).joinToString("") { GroupPolicy.gerarCodigo(Random(it)) }

        listOf('O', '0', 'I', '1').forEach { proibido ->
            assertTrue(proibido !in amostra, "o alfabeto não pode conter '$proibido'")
        }
    }

    @Test
    fun `o codigo tem 6 caracteres`() {
        assertEquals(6, GroupPolicy.gerarCodigo(Random(42)).length)
    }

    // ---- validação do formulário ----

    private fun pedido(
        titulo: String = "Setembro sem desculpa",
        inicioIso: String = "2026-09-10",
        fimIso: String = "2026-09-30",
        fuso: String = "America/Sao_Paulo",
        regras: List<GroupRule> = emptyList(),
        descricao: String? = null,
    ) = CreateGroupRequest(
        title = titulo,
        description = descricao,
        startDate = inicioIso,
        endDate = fimIso,
        timezone = fuso,
        rules = regras,
    )

    /** 09:00 em São Paulo de 2026-09-01. */
    private val hoje = momento("2026-09-01T12:00:00Z")

    @Test
    fun `pedido valido passa e vem normalizado`() {
        val r = GroupPolicy.validarCriacao(pedido(titulo = "  Setembro   sem desculpa "), hoje)

        val v = (r as AppResult.Success).value
        assertEquals("Setembro sem desculpa", v.titulo, "espaços colapsados, como no display_name")
        assertEquals(LocalDate.parse("2026-09-10"), v.inicio)
    }

    @Test
    fun `nao deixa comecar HOJE`() {
        // O furo que só aparece juntando as peças: AGENDADO é a ÚNICA janela de entrada (2-B).
        // Começar hoje faria o grupo nascer ATIVO, com janela de convite de duração zero — e o
        // convite é o gargalo do produto (2-B.0). Grupo que nasce vazio, nasce morto.
        val r = GroupPolicy.validarCriacao(pedido(inicioIso = "2026-09-01"), hoje)

        assertTrue("startDate" in erros(r))
    }

    @Test
    fun `amanha e aceito`() {
        val r = GroupPolicy.validarCriacao(pedido(inicioIso = "2026-09-02"), hoje)

        assertTrue(r is AppResult.Success, "amanhã já dá tempo de convidar")
    }

    @Test
    fun `o limite de hoje respeita o fuso do grupo`() {
        // 2026-09-02T02:00Z é 01/09 em São Paulo, mas 02/09 em UTC. Começar em 02/09 é "amanhã"
        // para o grupo paulista e "hoje" para o de fuso UTC.
        val quando = momento("2026-09-02T02:00:00Z")

        assertTrue(GroupPolicy.validarCriacao(pedido(inicioIso = "2026-09-02"), quando) is AppResult.Success)
        assertTrue(
            GroupPolicy.validarCriacao(pedido(inicioIso = "2026-09-02", fuso = "UTC"), quando)
                is AppResult.Failure,
        )
    }

    @Test
    fun `fim tem de ser depois do inicio`() {
        val r = GroupPolicy.validarCriacao(pedido(inicioIso = "2026-09-10", fimIso = "2026-09-10"), hoje)

        assertTrue("endDate" in erros(r), "desafio de duração zero não é desafio")
    }

    @Test
    fun `titulo vazio e recusado`() {
        assertTrue("title" in erros(GroupPolicy.validarCriacao(pedido(titulo = "   "), hoje)))
    }

    @Test
    fun `titulo longo demais e recusado`() {
        val r = GroupPolicy.validarCriacao(pedido(titulo = "a".repeat(GroupPolicy.TITULO_MAX + 1)), hoje)
        assertTrue("title" in erros(r))
    }

    @Test
    fun `descricao em branco vira null, nao string vazia`() {
        // Sem isto, a tela precisaria testar `descricao != null && descricao.isNotBlank()` em
        // todo lugar que a exibe — e um dos lugares seria esquecido.
        val v = (GroupPolicy.validarCriacao(pedido(descricao = "   "), hoje) as AppResult.Success).value
        assertEquals(null, v.descricao)
    }

    @Test
    fun `fuso invalido e recusado`() {
        val r = GroupPolicy.validarCriacao(pedido(fuso = "Marte/Olympus"), hoje)
        assertTrue("timezone" in erros(r))
    }

    @Test
    fun `offset no lugar de IANA e recusado`() {
        // '-03:00' até parece funcionar, e quebra no horário de verão — meses depois, calado.
        //
        // Este teste reprovou a PRIMEIRA versão da validação: `TimeZone.of("-03:00")` não lança,
        // devolve um FixedOffsetTimeZone sem reclamar. A regra estava no comentário e não no
        // código.
        listOf("-03:00", "+05:30", "UTC-3", "GMT+2").forEach { offset ->
            val r = GroupPolicy.validarCriacao(pedido(fuso = offset), hoje)
            assertTrue("timezone" in erros(r), "'$offset' não é fuso nomeado")
        }
    }

    @Test
    fun `UTC e aceito`() {
        // Legítimo, e não cai na armadilha do offset: UTC não tem horário de verão, que é
        // exatamente o problema de que a regra protege.
        assertTrue(GroupPolicy.validarCriacao(pedido(fuso = "UTC"), hoje) is AppResult.Success)
    }

    @Test
    fun `EMOJI_DO_DIA sem FOTO e recusado`() {
        // [INVARIANTE] reproduzir um emoji exige onde mostrá-lo. Sem a amarração dá para
        // configurar um grupo impossível de cumprir.
        val r = GroupPolicy.validarCriacao(pedido(regras = listOf(GroupRule.EMOJI_DO_DIA)), hoje)
        assertTrue("rules" in erros(r))
    }

    @Test
    fun `EMOJI_DO_DIA com FOTO passa`() {
        val r = GroupPolicy.validarCriacao(
            pedido(regras = listOf(GroupRule.EMOJI_DO_DIA, GroupRule.FOTO)),
            hoje,
        )
        assertTrue(r is AppResult.Success)
    }

    @Test
    fun `GYM_PASS e recusado enquanto nao houver contrato`() {
        // O tipo existe no motor de propósito (a fatia D não pode assumir que só há regras que
        // o app controla), mas escolher a regra hoje criaria um grupo impossível de cumprir.
        val r = GroupPolicy.validarCriacao(pedido(regras = listOf(GroupRule.GYM_PASS)), hoje)
        assertTrue("rules" in erros(r))
    }
}
