package dev.rafael.server.features.group.models

import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupType
import dev.rafael.contract.group.MemberRole
import dev.rafael.contract.group.ScoringModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid

/**
 * O grupo como o SERVIDOR o conhece.
 *
 * Sem campo `state`: ele é derivado por `GroupPolicy.estado()` na hora de montar o DTO. Guardar
 * o estado aqui reintroduziria, em memória, o problema que a ausência da coluna `status`
 * resolve no banco — dois lugares dizendo em que fase o grupo está.
 */
data class Group(
    val id: Uuid,
    val code: String,
    val type: GroupType,
    val scoringModel: ScoringModel,
    val title: String,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val timezone: TimeZone,
    val rules: Set<GroupRule>,
    val bannerUrl: String?,
    val createdBy: Uuid,
    val memberCount: Int,
)

/** Vínculo de uma pessoa com um grupo. */
data class GroupMember(
    val groupId: Uuid,
    val userId: Uuid,
    val role: MemberRole,
)
