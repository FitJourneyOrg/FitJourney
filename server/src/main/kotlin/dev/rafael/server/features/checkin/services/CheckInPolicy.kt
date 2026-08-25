package dev.rafael.server.features.checkin.services

import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * As regras do check-in, em Kotlin puro (fatia B).
 *
 * Mesma escolha do [dev.rafael.server.features.group.services.GroupPolicy]: sem banco, sem HTTP,
 * sem relógio próprio — tudo entra por parâmetro. É o que permite testar a virada do dia, o fuso
 * e a recusa estrutural sem subir Postgres.
 */
object CheckInPolicy {

    /**
     * O DIA do check-in (4.6): dia civil no fuso do **grupo**, nunca o do servidor nem o do
     * aparelho.
     *
     * Não é detalhe: um grupo em `America/Sao_Paulo` visto de um servidor em UTC tem três horas de
     * diferença. Quem treina às 22h em São Paulo já é "amanhã" em UTC — e o check-in cairia no dia
     * seguinte, liberando um segundo no mesmo dia e furando o "um por pessoa/dia/grupo".
     */
    fun diaDoGrupo(agora: Instant, fuso: TimeZone): LocalDate =
        agora.toLocalDateTime(fuso).date

    /**
     * Dá para fazer check-in agora? `null` = pode.
     *
     * [INV] "Check-in só é aceito com o grupo `ATIVO`". `AGENDADO` é janela de ENTRADA, não de
     * jogo; `ENCERRADO` é somente-leitura (2-B), e aceitar aqui mudaria o resultado de um desafio
     * que já acabou.
     */
    fun impedimento(estado: GroupState): CheckInBlock? = when (estado) {
        GroupState.AGENDADO -> CheckInBlock.NAO_COMECOU
        GroupState.ATIVO -> null
        GroupState.ENCERRADO -> CheckInBlock.ENCERRADO
    }

    /**
     * O que falta para cumprir as regras do grupo. Vazio = cumpre.
     *
     * [INV] "Check-in que não cumpre as regras do grupo é **recusado na criação**". A checagem é
     * **estrutural** e só isso (5.3): existe foto? existe local? Sem votação, ninguém confere o
     * CONTEÚDO — se a foto é de treino, se o emoji está certo. Isso só é questionado por denúncia,
     * na fatia E. Prometer mais do que se verifica seria pior que não verificar.
     *
     * `GYM_PASS` fica de fora: está declarado no contrato mas indisponível (depende de contrato
     * comercial), e recusar por uma regra que ninguém consegue cumprir travaria o grupo inteiro.
     */
    fun regrasNaoCumpridas(
        regras: Set<GroupRule>,
        temFoto: Boolean,
        temLocal: Boolean,
    ): Set<GroupRule> = buildSet {
        // EMOJI_DO_DIA implica FOTO (invariante): exigir o emoji é exigir a foto onde ele aparece.
        // A conferência do emoji em si é da fatia D; aqui ele só reforça a obrigação da foto.
        val exigeFoto = GroupRule.FOTO in regras || GroupRule.EMOJI_DO_DIA in regras
        if (exigeFoto && !temFoto) add(GroupRule.FOTO)
        if (GroupRule.LOCALIZACAO in regras && !temLocal) add(GroupRule.LOCALIZACAO)
    }

    /**
     * Ainda dá para apagar (4.11)? Só o dono, só no MESMO dia do grupo.
     *
     * **Por que só no mesmo dia:** atende o arrependimento honesto — foto tremida, local errado —
     * sem virar brecha para mexer no ranking dias depois. Apagar libera o slot, o que é o que faz
     * a regra conversar com o índice único; sem isso, apagar seria uma armadilha.
     */
    fun podeApagar(diaDoCheckIn: LocalDate, agora: Instant, fuso: TimeZone): Boolean =
        diaDoCheckIn == diaDoGrupo(agora, fuso)

    /** Teto da 5.2 — o nome do lugar é rótulo, não endereço. */
    const val MAX_NOME_DO_LOCAL = 60

    /**
     * Arredonda a coordenada para 2 casas (~1 km) **antes** de chegar ao banco.
     *
     * [INV] "A coordenada exata nunca é gravada". Arredondar na escrita, e não na leitura, é o que
     * torna o invariante verificável: basta olhar a tabela. Se o dado cru entrasse e fosse
     * mascarado na saída, um `SELECT` continuaria expondo onde a pessoa mora.
     */
    fun arredondar(valor: Double): Double = kotlin.math.round(valor * 100) / 100
}

/** Por que não dá para fazer check-in. Enum e não frase: a tela escreve o texto (#31). */
enum class CheckInBlock {
    /** O desafio ainda não começou. */
    NAO_COMECOU,

    /** O desafio já terminou. */
    ENCERRADO,

    /** Já existe check-in seu neste grupo hoje (4.3). */
    JA_FEZ_HOJE,
}
