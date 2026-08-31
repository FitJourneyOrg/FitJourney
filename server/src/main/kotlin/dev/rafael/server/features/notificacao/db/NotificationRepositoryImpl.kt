package dev.rafael.server.features.notificacao.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.db.jsonbText
import dev.rafael.server.features.notificacao.models.Notificacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

/** Espelha `notifications` da V42. */
object NotificationsTable : Table("notifications") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val type = varchar("type", 32)
    val title = varchar("title", 120)
    val body = varchar("body", 300)

    /**
     * JSONB no banco, `String` aqui — mas via [jsonbText], não via `text`.
     *
     * A primeira versão usava `text("data")` e **nunca gravou uma linha**: o Postgres não faz cast
     * implícito de `varchar` para `jsonb` em parâmetro preparado. O defeito só apareceu no
     * primeiro pedido de amizade real, porque nenhum teste do servidor grava notificação.
     */
    val data = jsonbText("data")

    val readAt = datetime("read_at").nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}

class NotificationRepositoryImpl : NotificationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun criar(n: Notificacao): AppResult<Unit> = dbQuery {
        // O retorno do insert é ignorado: a chave é gerada por nós, não pelo banco.
        @Suppress("UNUSED_EXPRESSION")
        NotificationsTable.insert {
            it[id] = n.id
            it[userId] = n.userId
            it[type] = n.tipo
            it[title] = n.titulo
            it[body] = n.corpo
            it[data] = json.encodeToString(n.dados)
            it[readAt] = n.lidaEm
            it[createdAt] = n.criadaEm
        }.let { }
        Unit
    }

    override suspend fun doUsuario(userId: Uuid, limite: Int): AppResult<List<Notificacao>> =
        dbQuery {
            NotificationsTable.selectAll()
                .where { NotificationsTable.userId eq userId }
                .orderBy(NotificationsTable.createdAt to SortOrder.DESC)
                .limit(limite)
                .map { it.toNotificacao() }
        }

    override suspend fun naoLidas(userId: Uuid): AppResult<Int> = dbQuery {
        NotificationsTable.selectAll()
            .where { (NotificationsTable.userId eq userId) and NotificationsTable.readAt.isNull() }
            .count()
            .toInt()
    }

    override suspend fun marcarTodasComoLidas(
        userId: Uuid,
        quando: LocalDateTime,
    ): AppResult<Int> = dbQuery {
        // `readAt IS NULL` no WHERE: sem isso, reabrir a central reescreveria a data de leitura de
        // tudo que já estava lido, e "quando li isto" viraria "quando abri a tela pela última vez".
        NotificationsTable.update({
            (NotificationsTable.userId eq userId) and NotificationsTable.readAt.isNull()
        }) {
            it[readAt] = quando
        }
    }

    override suspend fun purgar(anterioresA: LocalDateTime): AppResult<Int> = dbQuery {
        // Apaga LIDAS e NÃO LIDAS igualmente: seis meses depois, uma notificação não lida também
        // deixou de importar. Preservar as não lidas para sempre faria a tabela de quem nunca
        // abre a central crescer sem limite — exatamente o caso que a retenção existe para cobrir.
        NotificationsTable.deleteWhere { NotificationsTable.createdAt less anterioresA }
    }

    private fun ResultRow.toNotificacao() = Notificacao(
        id = this[NotificationsTable.id],
        userId = this[NotificationsTable.userId],
        tipo = this[NotificationsTable.type],
        titulo = this[NotificationsTable.title],
        corpo = this[NotificationsTable.body],
        dados = runCatching {
            json.decodeFromString<Map<String, String>>(this[NotificationsTable.data])
        }.getOrDefault(emptyMap()),   // JSON corrompido não derruba a central inteira
        lidaEm = this[NotificationsTable.readAt],
        criadaEm = this[NotificationsTable.createdAt],
    )

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
