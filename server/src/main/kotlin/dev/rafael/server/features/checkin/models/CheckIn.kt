package dev.rafael.server.features.checkin.models

import dev.rafael.contract.checkin.CheckInStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * O check-in como o SERVIDOR o lê.
 *
 * **A coordenada não está aqui, e isso é a defesa.** Ela é gravada por [NovoCheckIn] e nunca mais
 * sai do banco (5.2) — o modelo de LEITURA simplesmente não tem onde carregá-la. Assim o
 * invariante "a coordenada nunca aparece na tela" não depende de ninguém lembrar de omitir um
 * campo no mapper: não existe campo para omitir.
 */
data class CheckIn(
    val id: Uuid,
    val groupId: Uuid,
    val userId: Uuid,
    /** Dia civil no fuso do grupo (4.6). */
    val localDate: LocalDate,
    val createdAt: LocalDateTime,
    val status: CheckInStatus,
    val photoRef: String?,
    /** Preenchido pela purga dos 90 dias (4.8): teve foto, e ela expirou. */
    val photoPurgedAt: LocalDateTime?,
    val placeName: String?,
) {
    /** Tem foto para servir AGORA. Distingue "nunca teve" de "expirou" — as duas dão `null` no DTO. */
    val fotoViva: Boolean get() = photoRef != null && photoPurgedAt == null
}

/** O que se grava. Aqui a coordenada existe; no [CheckIn] não. */
data class NovoCheckIn(
    val id: Uuid,
    val groupId: Uuid,
    val userId: Uuid,
    val localDate: LocalDate,
    val createdAt: LocalDateTime,
    val photoRef: String?,
    val placeName: String?,
    /** JÁ arredondada por `CheckInPolicy.arredondar` — o repositório não corrige ninguém. */
    val placeLat: Double?,
    val placeLng: Double?,
)

/**
 * Check-in + o nome de quem fez.
 *
 * O nome vem no mesmo `SELECT` de propósito: o feed mostra até algumas dezenas de itens, e uma
 * consulta por autor seria N+1 na tela mais visitada da fase.
 */
data class CheckInComAutor(
    val checkIn: CheckIn,
    val displayName: String,
)
