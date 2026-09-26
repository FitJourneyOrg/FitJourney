package dev.rafael.server.features.group.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupState
import dev.rafael.contract.group.JoinBlock
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

        assertEquals(ErrorCodes.GRUPO_INICIO_MUITO_CEDO, erros(r)["startDate"])
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

        assertEquals(
            ErrorCodes.GRUPO_FIM_ANTES_DO_INICIO,
            erros(r)["endDate"],
            "desafio de duração zero não é desafio",
        )
    }

    @Test
    fun `titulo vazio e recusado`() {
        assertEquals(
            ErrorCodes.GRUPO_TITULO_VAZIO,
            erros(GroupPolicy.validarCriacao(pedido(titulo = "   "), hoje))["title"],
        )
    }

    @Test
    fun `titulo longo demais e recusado`() {
        val r = GroupPolicy.validarCriacao(pedido(titulo = "a".repeat(GroupPolicy.TITULO_MAX + 1)), hoje)
        assertEquals(ErrorCodes.GRUPO_TITULO_LONGO, erros(r)["title"])
    }

    @Test
    fun `descricao longa demais e recusada com o codigo certo`() {
        val r = GroupPolicy.validarCriacao(
            pedido(descricao = "a".repeat(GroupPolicy.DESCRICAO_MAX + 1)),
            hoje,
        )
        assertEquals(ErrorCodes.GRUPO_DESCRICAO_LONGA, erros(r)["description"])
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
        assertEquals(ErrorCodes.GRUPO_FUSO_INVALIDO, erros(r)["timezone"])
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
            assertEquals(ErrorCodes.GRUPO_FUSO_INVALIDO, erros(r)["timezone"], "'$offset' não é fuso nomeado")
        }
    }

    @Test
    fun `fusos nomeados sem horario de verao sao aceitos`() {
        // A PRIMEIRA versão desta regra exigia "UTC ou barra" e recusava `GMT` — que é o fuso
        // que o emulador reporta. Validação estrita demais bloqueia o usuário honesto sem
        // impedir nada: o que quebra sob horário de verão é OFFSET, não ausência de barra.
        listOf("UTC", "GMT", "America/Sao_Paulo", "Europe/Lisbon").forEach { fuso ->
            assertTrue(
                GroupPolicy.validarCriacao(pedido(fuso = fuso), hoje) is AppResult.Success,
                "'$fuso' é fuso nomeado legítimo",
            )
        }
    }

    @Test
    fun `EMOJI_DO_DIA sem FOTO e recusado`() {
        // [INVARIANTE] reproduzir um emoji exige onde mostrá-lo. Sem a amarração dá para
        // configurar um grupo impossível de cumprir.
        val r = GroupPolicy.validarCriacao(pedido(regras = listOf(GroupRule.EMOJI_DO_DIA)), hoje)
        assertEquals(ErrorCodes.GRUPO_REGRA_EMOJI_SEM_FOTO, erros(r)["rules"])
    }

    @Test
    fun `EMOJI_DO_DIA com FOTO passa`() {
        val r = GroupPolicy.validarCriacao(
            pedido(regras = listOf(GroupRule.EMOJI_DO_DIA, GroupRule.FOTO)),
            hoje,
        )
        assertTrue(r is AppResult.Success)
    }

    // ---- entrada (fatia A.2) ----

    @Test
    fun `so da para entrar com o grupo AGENDADO`() {
        // AGENDADO é a ÚNICA janela de entrada (2-B). Depois que começa, quem está fora fica
        // fora — e é por isso que a validação da criação exige início a partir de amanhã.
        assertEquals(null, GroupPolicy.impedimentoParaEntrar(GroupState.AGENDADO, membros = 3, jaEMembro = false))
        assertEquals(
            JoinBlock.JA_COMECOU,
            GroupPolicy.impedimentoParaEntrar(GroupState.ATIVO, membros = 3, jaEMembro = false),
        )
        assertEquals(
            JoinBlock.ENCERRADO,
            GroupPolicy.impedimentoParaEntrar(GroupState.ENCERRADO, membros = 3, jaEMembro = false),
        )
    }

    @Test
    fun `o teto de 50 fecha a entrada`() {
        assertEquals(
            null,
            GroupPolicy.impedimentoParaEntrar(GroupState.AGENDADO, GroupPolicy.MAX_MEMBROS - 1, false),
        )
        assertEquals(
            JoinBlock.LOTADO,
            GroupPolicy.impedimentoParaEntrar(GroupState.AGENDADO, GroupPolicy.MAX_MEMBROS, false),
        )
    }

    @Test
    fun `ja ser membro vence os outros impedimentos`() {
        // Quem já está dentro e toca no link de novo tem de ver "você já participa", não
        // "lotado" nem "já começou" — a mensagem certa é sobre ELE, não sobre o grupo.
        assertEquals(
            JoinBlock.JA_E_MEMBRO,
            GroupPolicy.impedimentoParaEntrar(GroupState.ATIVO, GroupPolicy.MAX_MEMBROS, jaEMembro = true),
        )
    }

    // ---- validade do convite ----

    @Test
    fun `o convite vale 7 dias quando o grupo comeca depois disso`() {
        val agora = momento("2026-09-01T12:00:00Z")
        val validade = GroupPolicy.validadeDoConvite(agora, LocalDate.parse("2026-10-01"), sp)

        assertEquals(momento("2026-09-08T12:00:00Z"), validade)
    }

    @Test
    fun `o convite vence no INICIO quando o grupo comeca antes dos 7 dias`() {
        // Um link que "funciona" e leva a uma recusa é pior que um link vencido: o vencido
        // explica o que houve, o outro parece defeito do app.
        val agora = momento("2026-09-01T12:00:00Z")
        val validade = GroupPolicy.validadeDoConvite(agora, LocalDate.parse("2026-09-03"), sp)

        // 03/09 00:00 em São Paulo = 03/09 03:00Z
        assertEquals(momento("2026-09-03T03:00:00Z"), validade)
    }

    @Test
    fun `GYM_PASS e recusado enquanto nao houver contrato`() {
        // O tipo existe no motor de propósito (a fatia D não pode assumir que só há regras que
        // o app controla), mas escolher a regra hoje criaria um grupo impossível de cumprir.
        val r = GroupPolicy.validarCriacao(pedido(regras = listOf(GroupRule.GYM_PASS)), hoje)
        assertEquals(ErrorCodes.GRUPO_REGRA_GYMPASS_INDISPONIVEL, erros(r)["rules"])
    }

    // ---- débito "erroDoCampo devolve frase do servidor" (debitos.md, P2, 2026-09-23) ----

    /** Caminho de falha que faltava: nenhum teste cobria data que não parseia (só data que parseia e viola regra). */
    @Test
    fun `data de inicio que nao parseia e recusada com codigo proprio, diferente de muito cedo`() {
        val r = GroupPolicy.validarCriacao(pedido(inicioIso = "trinta-e-um-de-fevereiro"), hoje)
        assertEquals(ErrorCodes.GRUPO_DATA_INICIO_INVALIDA, erros(r)["startDate"])
    }

    @Test
    fun `data de fim que nao parseia e recusada com codigo proprio, diferente de fim antes do inicio`() {
        val r = GroupPolicy.validarCriacao(pedido(fimIso = "nao-e-uma-data"), hoje)
        assertEquals(ErrorCodes.GRUPO_DATA_FIM_INVALIDA, erros(r)["endDate"])
    }
}
