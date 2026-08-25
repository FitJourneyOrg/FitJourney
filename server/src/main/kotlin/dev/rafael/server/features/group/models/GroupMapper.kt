package dev.rafael.server.features.group.models

import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.MemberRole
import dev.rafael.server.features.group.services.GroupPolicy
import kotlin.time.Instant

/**
 * Group (server) -> GroupDto (fio).
 *
 * O ESTADO é calculado aqui, no momento de responder, com o relógio do servidor e o fuso do
 * grupo. Não existe cache nem campo persistido: é a mesma resposta que qualquer outra parte do
 * sistema obteria com os mesmos dados, e é isso que torna a ausência da coluna `status` segura.
 *
 * @param agora relógio do SERVIDOR ([REGRA]).
 * @param meuPapel papel de quem pediu; null quando não é membro.
 */
/**
 * Banner PADRÃO, servido pela rota estática `/media` (a mesma dos gifs de exercício).
 *
 * O padrão é resolvido AQUI e não gravado em `groups.banner_url`: guardado, todo grupo antigo
 * apontaria para um arquivo inexistente no dia em que a imagem mudasse de nome. Derivado,
 * "tem banner próprio?" é simplesmente `banner_url != null` — e trocar o padrão é uma linha.
 *
 * A substituição pelo usuário é upload, e upload é a fatia B (junto da foto de check-in).
 *
 * DÉBITO: o arquivo ainda não existe. Ver FitJourney_DEBITOS.md.
 */
const val BANNER_PADRAO = "/media/banners/default.jpg"

/**
 * @param meuCheckInHoje id do check-in de hoje de quem pediu (fatia B), ou `null`. Default `null`
 *   porque a maioria dos caminhos — criar, listar, preview — não precisa dele: só o DETALHE usa,
 *   que é onde o botão de check-in vive. Consultar em todos custaria uma query por grupo na lista.
 */
fun Group.toDto(
    agora: Instant,
    meuPapel: MemberRole?,
    meuCheckInHoje: String? = null,
): GroupDto = GroupDto(
    id = id.toString(),
    code = code,
    type = type,
    scoringModel = scoringModel,
    title = title,
    description = description,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    timezone = timezone.id,
    rules = rules.sortedBy { it.name },   // ordem estável: sem isso a lista dança entre respostas
    bannerUrl = bannerUrl ?: BANNER_PADRAO,
    state = GroupPolicy.estado(startDate, endDate, agora, timezone),
    memberCount = memberCount,
    myRole = meuPapel,
    myCheckInToday = meuCheckInHoje,
)
