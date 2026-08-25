package dev.rafael.contract.checkin

import kotlinx.serialization.Serializable

/**
 * Estado do check-in (ARCH #33 emendado).
 *
 * Sem votação: nasce `VALIDO` (4.9). `EM_ANALISE` e `INVALIDADO` só passam a acontecer na fatia E,
 * com a denúncia — mas o vocabulário nasce fechado para o estado nunca ser texto livre.
 */
@Serializable
enum class CheckInStatus { VALIDO, EM_ANALISE, INVALIDADO }

/**
 * Um check-in, como o feed do grupo o vê (8.0.2).
 *
 * **A FRONTEIRA DE PRIVACIDADE É ESTE ARQUIVO, não um `if` na tela.** O que não está aqui não
 * atravessa: nada de e-mail ([REGRA] #33), nada de XP, nível ou histórico individual (9.3), e
 * nenhuma coordenada — a 5.2 guarda a latitude arredondada no banco e ela **nunca** sai. O grupo
 * vê o NOME que a pessoa escreveu, e só.
 */
@Serializable
data class CheckInDto(
    val id: String,
    val groupId: String,
    val userId: String,
    /** Nome, nunca e-mail. É o que a lista de membros já mostra. */
    val displayName: String,
    /** ISO-8601. Relógio do SERVIDOR (4.5) — o do aparelho não entra em nada. */
    val createdAt: String,
    /** `AAAA-MM-DD`, o dia civil no fuso do GRUPO (4.6). É a chave do "um por dia". */
    val localDate: String,
    val status: CheckInStatus,

    /**
     * Caminho **relativo à base da API**, ex.: `/checkins/{id}/foto`. Não é a base de mídia: a
     * rota é autenticada e confere filiação a cada leitura, porque foto de check-in é gente.
     *
     * `null` cobre dois casos de propósito — o grupo não exige foto, ou os 90 dias passaram
     * (4.8). Quem decide isso é o servidor, que tem `photo_purged_at`; a tela só desenha.
     */
    val photoUrl: String? = null,

    /** O texto que a pessoa escreveu (5.2). `null` quando o grupo não exige localização. */
    val placeName: String? = null,

    /**
     * É meu? Resolvido no servidor, como o `myRole` do grupo — a tela não compara ids para
     * decidir o que mostrar.
     */
    val mine: Boolean = false,

    /**
     * Ainda dá para apagar (4.11)? Verdadeiro só no MESMO dia, no fuso do grupo, e só para o dono.
     *
     * Derivado, não guardado: é função de (dia do check-in, agora, fuso). Mesma escolha do estado
     * do grupo — e, como lá, a tela pode ficar aberta até depois da virada, então o servidor
     * recusa de novo na hora do `DELETE`. Isto aqui evita oferecer o botão, não substitui a regra.
     */
    val canDelete: Boolean = false,
)
