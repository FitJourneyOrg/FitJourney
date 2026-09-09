package dev.rafael.server.features.checkin.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.map
import dev.rafael.server.features.checkin.models.Comentario
import dev.rafael.server.features.checkin.models.NovoComentario
import dev.rafael.server.features.checkin.models.ReacaoAgrupada
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.Uuid

class SocialRepositoryImpl : SocialRepository {

    // ---- comentários ----

    override suspend fun comentar(novo: NovoComentario): AppResult<Comentario> = dbQuery {
        transaction {
            CheckInCommentsTable.insert {
                it[id] = novo.id
                it[checkInId] = novo.checkInId
                it[groupId] = novo.groupId
                it[userId] = novo.userId
                it[body] = novo.body
                it[createdAt] = novo.createdAt
            }

            // Relê com o JOIN para trazer o `displayName`: o serviço devolve o comentário pronto
            // para a tela, e buscar o nome depois seria uma segunda ida ao banco pelo dado que
            // acabou de ser gravado.
            comentarioPorId(novo.id)!!
        }
    }

    override suspend fun comentarios(checkInId: Uuid): AppResult<List<Comentario>> = dbQuery {
        transaction {
            (CheckInCommentsTable innerJoin UsersTable)
                .selectAll()
                .where { CheckInCommentsTable.checkInId eq checkInId }
                // ASC: conversa se lê na ordem em que aconteceu, ao contrário do feed.
                .orderBy(CheckInCommentsTable.createdAt to SortOrder.ASC)
                .map { it.toComentario() }
        }
    }

    override suspend fun comentario(id: Uuid): AppResult<Comentario?> = dbQuery {
        transaction { comentarioPorId(id) }
    }

    override suspend fun apagarComentario(id: Uuid): AppResult<Unit> = dbQuery {
        transaction { CheckInCommentsTable.deleteWhere { CheckInCommentsTable.id eq id } }
    }.map { }

    /**
     * Contagem em LOTE — um `GROUP BY` para o feed inteiro.
     *
     * Uma consulta por card seria o N+1 que o `seed_volume.sql` revelou em `meusGrupos`. Código
     * novo imita código existente, então este método nasce com a forma certa.
     */
    override suspend fun contarComentarios(checkInIds: List<Uuid>): AppResult<Map<Uuid, Int>> =
        dbQuery {
            if (checkInIds.isEmpty()) return@dbQuery emptyMap()
            transaction {
                CheckInCommentsTable
                    .select(CheckInCommentsTable.checkInId, CheckInCommentsTable.id.count())
                    .where { CheckInCommentsTable.checkInId inList checkInIds }
                    .groupBy(CheckInCommentsTable.checkInId)
                    .associate {
                        it[CheckInCommentsTable.checkInId] to
                            it[CheckInCommentsTable.id.count()].toInt()
                    }
            }
        }

    // ---- reações ----

    /**
     * Põe ou troca. `upsert` na PK composta — sem consultar antes.
     *
     * [INV] "uma reação por pessoa por check-in" (8.2) é da PK, não deste código: se alguém remover
     * este `upsert` e puser um `insert`, o banco recusa a segunda em vez de duplicar.
     */
    override suspend fun reagir(
        checkInId: Uuid,
        groupId: Uuid,
        userId: Uuid,
        emoji: String,
        quando: LocalDateTime,
    ): AppResult<Unit> = dbQuery {
        transaction {
            CheckInReactionsTable.upsert {
                it[CheckInReactionsTable.checkInId] = checkInId
                it[CheckInReactionsTable.groupId] = groupId
                it[CheckInReactionsTable.userId] = userId
                it[CheckInReactionsTable.emoji] = emoji
                it[createdAt] = quando
            }
        }
    }.map { }

    /** Idempotente: tirar o que não existe não é erro, é o mesmo resultado. */
    override suspend fun desreagir(checkInId: Uuid, userId: Uuid): AppResult<Unit> = dbQuery {
        transaction {
            CheckInReactionsTable.deleteWhere {
                (CheckInReactionsTable.checkInId eq checkInId) and
                    (CheckInReactionsTable.userId eq userId)
            }
        }
    }.map { }

    /**
     * Agrupado no BANCO, não em memória.
     *
     * Trazer as linhas cruas para contar no Kotlin faria o feed baixar até 50 reações por card. O
     * `souEu` sai da mesma consulta, comparando o `user_id` no agrupamento — pedir "as reações" e
     * depois "a minha" seriam duas idas para desenhar um botão.
     */
    override suspend fun reacoes(
        checkInIds: List<Uuid>,
        doUsuario: Uuid,
    ): AppResult<Map<Uuid, List<ReacaoAgrupada>>> = dbQuery {
        if (checkInIds.isEmpty()) return@dbQuery emptyMap()
        transaction {
            CheckInReactionsTable
                .selectAll()
                .where { CheckInReactionsTable.checkInId inList checkInIds }
                .map {
                    Triple(
                        it[CheckInReactionsTable.checkInId],
                        it[CheckInReactionsTable.emoji],
                        it[CheckInReactionsTable.userId],
                    )
                }
                .groupBy { it.first }
                .mapValues { (_, linhas) ->
                    linhas.groupBy { it.second }
                        .map { (emoji, doEmoji) ->
                            ReacaoAgrupada(
                                emoji = emoji,
                                quantidade = doEmoji.size,
                                souEu = doEmoji.any { it.third == doUsuario },
                            )
                        }
                        // Ordem estável: sem isto a fileira de reações dança entre respostas, e o
                        // botão muda de lugar debaixo do dedo. Mesma razão do `rules.sortedBy`.
                        .sortedWith(compareByDescending<ReacaoAgrupada> { it.quantidade }.thenBy { it.emoji })
                }
        }
    }

    // ---- utilidades ----

    private fun comentarioPorId(id: Uuid): Comentario? =
        (CheckInCommentsTable innerJoin UsersTable)
            .selectAll()
            .where { CheckInCommentsTable.id eq id }
            .singleOrNull()
            ?.toComentario()

    private fun ResultRow.toComentario() = Comentario(
        id = this[CheckInCommentsTable.id],
        checkInId = this[CheckInCommentsTable.checkInId],
        groupId = this[CheckInCommentsTable.groupId],
        userId = this[CheckInCommentsTable.userId],
        displayName = this[UsersTable.displayName],
        body = this[CheckInCommentsTable.body],
        createdAt = this[CheckInCommentsTable.createdAt],
    )

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
