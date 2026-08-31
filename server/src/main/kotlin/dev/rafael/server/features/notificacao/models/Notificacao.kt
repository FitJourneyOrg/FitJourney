package dev.rafael.server.features.notificacao.models

import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Uma notificação gravada (F.1).
 *
 * O texto vem JÁ RENDERIZADO — `"Rafael quer ser seu amigo"`, com o nome dentro. Guardar só o id
 * e montar a frase na leitura faria a notificação de ontem dizer o nome de hoje, contando uma
 * história que não aconteceu.
 *
 * **Notificação é registro do que houve, não consulta ao presente.** Mesma família da decisão do
 * `unlocked_at` das conquistas (#32): o que foi concedido fica como foi.
 */
data class Notificacao(
    val id: Uuid,
    val userId: Uuid,
    val tipo: String,
    val titulo: String,
    val corpo: String,
    val dados: Map<String, String>,
    val lidaEm: LocalDateTime?,
    val criadaEm: LocalDateTime,
)
