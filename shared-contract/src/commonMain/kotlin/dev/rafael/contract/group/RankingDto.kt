package dev.rafael.contract.group

import kotlinx.serialization.Serializable

/**
 * Uma linha do ranking do grupo (7.2, fatia C).
 *
 * **A fronteira de privacidade é este DTO.** [REGRA] #18: grupo pontua por CONTAGEM DE CHECK-INS,
 * nunca por XP. E a 9.3: nada de XP, nível ou histórico individual atravessa a fronteira do grupo.
 * Por isso aqui só existem identificação, contagem e posição — não há campo de XP a esquecer de
 * omitir, porque ele não existe.
 *
 * E-mail nunca ([REGRA] #33).
 */
@Serializable
data class RankingEntryDto(
    /** 1-based. Vem RESOLVIDA do servidor: a tela não numera lista, para não discordar do empate. */
    val position: Int,
    val userId: String,
    val displayName: String,
    /** Check-ins que contam: tudo menos `INVALIDADO` (6.8 — em análise continua contando). */
    val checkIns: Int,
    /**
     * Sou eu? Resolvido no servidor, como o `myRole` e o `mine` do check-in — a tela não compara
     * ids para decidir o que destacar.
     */
    val mine: Boolean = false,
)
