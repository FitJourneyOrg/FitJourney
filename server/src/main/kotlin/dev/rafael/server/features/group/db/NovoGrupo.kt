package dev.rafael.server.features.group.db

import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupType
import dev.rafael.contract.group.ScoringModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid

/**
 * Dados de criação já VALIDADOS (`GroupPolicy.validarCriacao`).
 *
 * Existe para o repositório não receber o `CreateGroupRequest` cru: se recebesse, ele teria de
 * confiar que alguém validou antes — e essa confiança é onde entra dado inválido no banco.
 * Aqui os tipos já dizem que a validação aconteceu (`LocalDate` em vez de `String`,
 * `TimeZone` em vez de texto).
 *
 * `code` não está aqui: é sorteado dentro do repositório, que é quem sabe lidar com colisão.
 */
data class NovoGrupo(
    val id: Uuid,
    val type: GroupType,
    val scoringModel: ScoringModel,
    val title: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timezone: TimeZone,
    val rules: Set<GroupRule>,
    val createdBy: Uuid,
)
