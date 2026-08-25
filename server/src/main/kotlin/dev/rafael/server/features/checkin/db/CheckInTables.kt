package dev.rafael.server.features.checkin.db

import dev.rafael.server.features.group.db.GroupsTable
import dev.rafael.server.features.user.db.UsersTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime

/** Espelha `V38__create_check_ins.sql` (fatia B). */
object CheckInsTable : Table("check_ins") {
    val id = uuid("id")
    val groupId = uuid("group_id").references(GroupsTable.id)
    val userId = uuid("user_id").references(UsersTable.id)

    /** Dia CIVIL no fuso do GRUPO (4.6). É a chave do "um por pessoa/dia/grupo". */
    val localDate = date("local_date")

    /** Relógio do SERVIDOR (4.5). */
    val createdAt = datetime("created_at")

    /** TEXT no banco, enum no Kotlin — mesma escolha do `role` e do `type` do grupo. */
    val status = text("status")

    /** Referência OPACA do `ArmazenamentoDeMidia`, nunca um caminho de arquivo. */
    val photoRef = text("photo_ref").nullable()

    /** Marcado pela purga dos 90 dias (4.8). A foto some; o check-in fica. */
    val photoPurgedAt = datetime("photo_purged_at").nullable()

    val placeName = varchar("place_name", 60).nullable()

    /**
     * Arredondada a 2 casas (~1 km) e **nunca** exposta — não está no `CheckInDto`. Existe só para
     * viabilizar um mapa depois sem migration (5.2).
     */
    val placeLat = decimal("place_lat", precision = 5, scale = 2).nullable()
    val placeLng = decimal("place_lng", precision = 5, scale = 2).nullable()

    override val primaryKey = PrimaryKey(id)
}
