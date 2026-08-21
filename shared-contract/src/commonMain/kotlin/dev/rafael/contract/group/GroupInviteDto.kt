package dev.rafael.contract.group

import kotlinx.serialization.Serializable

/**
 * O que se vê ANTES de entrar (decisão 2-B.0) — a **única leitura pública** da Fase 6.
 *
 * Note o que NÃO está aqui: lista de membros, check-ins, ranking e o próprio `code`. Quem ainda
 * não entrou vê o suficiente para decidir, e nada além. É a diferença entre um convite e um
 * vazamento.
 *
 * **É onde mora o opt-in do #17.** [rules] aparece porque a pessoa precisa saber que o grupo
 * exige localização (ou foto) **antes** de aceitar — é isso que transforma a regra obrigatória
 * em escolha informada, e não em imposição descoberta depois de entrar.
 */
@Serializable
data class GroupPreviewDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val bannerUrl: String? = null,
    val memberCount: Int,
    val startDate: String,
    val endDate: String,
    val timezone: String,
    val rules: List<GroupRule> = emptyList(),
    val state: GroupState,
    /**
     * Dá para entrar AGORA? A tela desabilita o botão em vez de deixar a pessoa tocar e levar
     * um erro — e [blockedReason] diz por quê, senão o botão cinza é um mistério.
     */
    val joinable: Boolean,
    val blockedReason: JoinBlock? = null,
)

/**
 * Por que não dá para entrar. Enum e não texto: a tela escolhe a frase (a política de erro do
 * #31 diz que quem conhece a plataforma é quem escreve o texto), e o servidor diz o motivo.
 */
@Serializable
enum class JoinBlock {
    /** O desafio já começou. `AGENDADO` é a única janela de entrada (2-B). */
    JA_COMECOU,

    /** Já terminou. */
    ENCERRADO,

    /** Teto de 50 membros (2.2). */
    LOTADO,

    /** Já é membro — a tela leva para o grupo em vez de oferecer entrada. */
    JA_E_MEMBRO,

    /** O link foi revogado ou venceu. O código do grupo pode continuar valendo. */
    CONVITE_INVALIDO,
}

/** Entrada por código digitado (2.1). O link usa o token na URL, não este corpo. */
@Serializable
data class JoinByCodeRequest(val code: String)

/** Link de convite ativo do grupo. `token` é UUID: não é para ser digitado — para isso há o código. */
@Serializable
data class GroupInviteDto(
    val token: String,
    /** ISO-8601. Menor entre 7 dias e o início do grupo. */
    val expiresAt: String,
)
