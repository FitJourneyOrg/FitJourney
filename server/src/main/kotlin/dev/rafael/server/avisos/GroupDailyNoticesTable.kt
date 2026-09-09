package dev.rafael.server.avisos

import dev.rafael.server.features.group.db.GroupsTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Espelha `group_daily_notices` da V46 (fatia F).
 *
 * **A PK composta É a regra** "no máximo um aviso por grupo por dia por tipo" (10.6). Não há
 * checagem no Kotlin porque não precisa: o banco recusa o segundo, e é isso que faz o teto
 * sobreviver a reinício do servidor e a duas instâncias rodando o laço ao mesmo tempo.
 *
 * Mora em `server.avisos` e não em `features/group` porque quem escreve nela é o varredor, que
 * atravessa três features — pô-la dentro de uma delas daria a impressão errada de dono.
 */
object GroupDailyNoticesTable : Table("group_daily_notices") {
    val groupId = uuid("group_id").references(GroupsTable.id)

    /** O dia civil no fuso do GRUPO, como `check_ins.local_date` — e pelo mesmo motivo. */
    val day = date("day")

    /** Vocabulário fechado no banco. Espelha o enum `AvisosDiarios.Tipo`. */
    val kind = text("kind")

    val sentAt = datetime("sent_at")

    override val primaryKey = PrimaryKey(groupId, day, kind)
}
