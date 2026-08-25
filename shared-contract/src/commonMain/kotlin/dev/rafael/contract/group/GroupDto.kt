package dev.rafael.contract.group

import kotlinx.serialization.Serializable

/**
 * Tipo de grupo. **Um valor só, de propósito** (ARCH #33): o campo existe no schema e no
 * contrato desde já, então acrescentar `AMIGAVEL` ou `EQUIPES` não custa migration nem quebra
 * clientes antigos. Um booleano `is_desafio` exigiria backfill no dia em que surgisse o
 * segundo tipo — e nesta fase um papel intermediário apareceu e desapareceu antes da primeira
 * linha de código.
 */
@Serializable
enum class GroupType { DESAFIO }

/** Como se pontua. [REGRA] #18: grupo pontua por CONTAGEM DE CHECK-INS, nunca por XP. */
@Serializable
enum class ScoringModel { CONTAGEM_CHECKINS }

/** [REGRA] #33: só dois papéis. `role` e não booleano, pelo mesmo motivo do [GroupType]. */
@Serializable
enum class MemberRole { ADMIN, MEMBRO }

/**
 * Regra obrigatória: o MÍNIMO para o check-in ser aceito naquele grupo.
 *
 * `GYM_PASS` nasce **declarada e indisponível** — depende de contrato comercial com terceiro.
 * O tipo existe no motor; a implementação espera. Declarar agora evita que a fatia D seja
 * escrita assumindo que só existem regras que o app controla.
 */
@Serializable
enum class GroupRule { FOTO, LOCALIZACAO, EMOJI_DO_DIA, GYM_PASS }

/**
 * Estado do grupo — **DERIVADO** de `(início, fim, agora, fuso)`, nunca persistido (ARCH #33).
 *
 * Vem no DTO já resolvido pelo servidor: [REGRA] autoridade do servidor. O cliente não recalcula
 * com o próprio relógio, senão um aparelho com a data errada mostraria um grupo encerrado como
 * ativo — e ofereceria um check-in que o servidor recusaria.
 */
@Serializable
enum class GroupState { AGENDADO, ATIVO, ENCERRADO }

/**
 * Um grupo, como o cliente o vê.
 *
 * `memberCount` vem junto porque toda tela que mostra grupo mostra quantas pessoas tem — e uma
 * requisição por card seria N+1 na lista.
 */
@Serializable
data class GroupDto(
    val id: String,
    val code: String,
    val type: GroupType,
    val scoringModel: ScoringModel,
    val title: String,
    val description: String? = null,
    /** ISO `AAAA-MM-DD`. Dia civil no [timezone] do grupo — não instante. */
    val startDate: String,
    val endDate: String,
    /** IANA, ex.: `America/Sao_Paulo`. */
    val timezone: String,
    val rules: List<GroupRule> = emptyList(),
    val bannerUrl: String? = null,
    val state: GroupState,
    val memberCount: Int,
    /** Papel de QUEM PEDIU. Null quando o pedinte não é membro (preview do convite, A.2). */
    val myRole: MemberRole? = null,

    /**
     * O id do MEU check-in de HOJE neste grupo, ou `null` se ainda não fiz (fatia B).
     *
     * Derivado no servidor, como o [state] e o `canDelete`: "hoje" depende do fuso do GRUPO e do
     * relógio do SERVIDOR (4.6), e o cliente não tem nenhum dos dois.
     *
     * Existe para a tela **não oferecer** o check-in quando ele já foi feito. Sem isso, a pessoa
     * tira a foto, espera o GPS, edita o texto, envia — e só aí descobre que o dia já estava
     * usado. Descobrir depois do trabalho é o mesmo defeito do botão de convidar num desafio já
     * começado: oferecer e desmentir.
     *
     * Id e não booleano pelo mesmo motivo do `role` (2.17): com o id, a tela pode levar direto ao
     * item no feed sem uma segunda pergunta.
     */
    val myCheckInToday: String? = null,
)

/**
 * Criação de grupo (formulário da decisão 2-A).
 *
 * `code`, `id` e `state` não estão aqui de propósito: o código é gerado pelo **servidor** (2.17)
 * e o estado é derivado. Deixar o cliente propor qualquer um dos três seria abrir mão da
 * autoridade do servidor por conveniência de formulário.
 */
@Serializable
data class CreateGroupRequest(
    val title: String,
    val description: String? = null,
    val startDate: String,
    val endDate: String,
    val timezone: String,
    val rules: List<GroupRule> = emptyList(),
    val type: GroupType = GroupType.DESAFIO,
    val scoringModel: ScoringModel = ScoringModel.CONTAGEM_CHECKINS,
)
