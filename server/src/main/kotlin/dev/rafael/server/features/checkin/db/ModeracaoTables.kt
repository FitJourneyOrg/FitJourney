package dev.rafael.server.features.checkin.db

import dev.rafael.server.features.group.db.GroupsTable
import dev.rafael.server.features.user.db.UsersTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * Espelha `group_reports` da V45 (fatia E.2).
 *
 * **O alvo é um arco exclusivo**: exatamente uma das duas colunas está preenchida, e o `CHECK` do
 * banco garante. O Exposed não tem como expressar isso no tipo — as duas ficam anuláveis aqui, e
 * quem sabe a regra é o `CHECK`. É a diferença entre o que o ORM consegue dizer e o que o banco
 * consegue impor.
 */
object GroupReportsTable : Table("group_reports") {
    val id = uuid("id")
    val groupId = uuid("group_id").references(GroupsTable.id)

    val checkInId = uuid("check_in_id").references(CheckInsTable.id).nullable()
    val commentId = uuid("comment_id").references(CheckInCommentsTable.id).nullable()

    /**
     * Quem denunciou. **Nunca sai no DTO** (decisão de 2026-09-07): num grupo de conhecidos,
     * denúncia identificada é denúncia que ninguém faz.
     *
     * Existe porque sustenta duas coisas que a tela não sustenta — o índice "uma por pessoa" e a
     * auditoria de quem abriu o pedido.
     */
    val reporterId = uuid("reporter_id").references(UsersTable.id)

    /** Obrigatório. O `CHECK` da V45 recusa texto só de espaços. */
    val reason = varchar("reason", 300)

    val createdAt = datetime("created_at")

    /** `null` = está na fila. Marcar em vez de apagar é o que impede a redenúncia infinita. */
    val resolvedAt = datetime("resolved_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/**
 * Espelha `moderation_actions` da V45 — **append-only** (6.6).
 *
 * [INV] "Decisões do admin são imutáveis — correção é registro novo."
 *
 * Não há `update` nem `delete` sobre esta tabela em nenhum caminho do código, e a ausência é a
 * regra. Se alguém acrescentar um, o invariante morre em silêncio: o teste que protege isso é o
 * Konsist, não um `assert` de runtime.
 */
object ModerationActionsTable : Table("moderation_actions") {
    val id = uuid("id")
    val groupId = uuid("group_id").references(GroupsTable.id)

    /** Anulável: `ON DELETE SET NULL`. A conta do admin some, a decisão dele fica. */
    val adminId = uuid("admin_id").references(UsersTable.id).nullable()

    /**
     * **Sem `references`**, ao contrário de todas as outras colunas de id do projeto.
     *
     * Uma FK obrigaria a escolher entre a auditoria sumir com o alvo (CASCADE) e o alvo nunca mais
     * poder ser apagado (RESTRICT). As duas são piores do que um UUID solto: esta tabela nunca é
     * lida para montar tela, só para responder "o que aconteceu aqui?".
     */
    val targetType = text("target_type")
    val targetId = uuid("target_id")

    /** Vocabulário fechado no banco — ver o `CHECK` da V45 e o porquê da diferença para o emoji. */
    val action = text("action")

    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}
