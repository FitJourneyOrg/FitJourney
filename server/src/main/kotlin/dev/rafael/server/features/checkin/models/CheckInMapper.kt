package dev.rafael.server.features.checkin.models

import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.server.features.checkin.services.CheckInPolicy
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Linha → DTO.
 *
 * **A fronteira de privacidade é este mapper**, e ele não tem como errar: o [CheckIn] que entra
 * aqui não carrega coordenada nenhuma (5.2), nem XP, nem e-mail. Não há campo a esquecer.
 */
fun CheckInComAutor.toDto(
    quemPede: Uuid,
    agora: Instant,
    fusoDoGrupo: TimeZone,
): CheckInDto {
    val meu = checkIn.userId == quemPede
    return CheckInDto(
        id = checkIn.id.toString(),
        groupId = checkIn.groupId.toString(),
        userId = checkIn.userId.toString(),
        displayName = displayName,
        // O banco guarda TIMESTAMP sem fuso, em UTC — mesma convenção das outras tabelas.
        createdAt = checkIn.createdAt.toInstant(TimeZone.UTC).toString(),
        localDate = checkIn.localDate.toString(),
        status = checkIn.status,
        // Caminho relativo à base da API, não à base de mídia: a rota é AUTENTICADA e confere
        // filiação a cada leitura. `null` quando não há foto ou quando ela expirou (4.8) — a tela
        // não precisa saber qual dos dois, e o servidor é quem tem `photo_purged_at` para saber.
        photoUrl = if (checkIn.fotoViva) "/checkins/${checkIn.id}/foto" else null,
        placeName = checkIn.placeName,
        mine = meu,
        // Derivado na hora, como o estado do grupo. A tela pode ficar aberta atravessando a
        // meia-noite, então o `DELETE` confere de novo — isto evita OFERECER o botão, não
        // substitui a regra.
        canDelete = meu && CheckInPolicy.podeApagar(checkIn.localDate, agora, fusoDoGrupo),
    )
}
