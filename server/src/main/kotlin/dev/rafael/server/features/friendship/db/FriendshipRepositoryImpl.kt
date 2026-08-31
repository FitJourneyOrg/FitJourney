package dev.rafael.server.features.friendship.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.friendship.models.Amizade
import dev.rafael.server.features.friendship.models.PedidoRecebido
import dev.rafael.server.features.friendship.models.Pessoa
import dev.rafael.server.features.friendship.services.FriendshipPolicy
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class FriendshipRepositoryImpl : FriendshipRepository {

    override suspend fun entre(um: Uuid, outro: Uuid): AppResult<Amizade?> = dbQuery {
        val (a, b) = FriendshipPolicy.par(um, outro)
        FriendshipsTable.selectAll()
            .where { (FriendshipsTable.userA eq a) and (FriendshipsTable.userB eq b) }
            .singleOrNull()
            ?.toAmizade()
    }

    override suspend fun pedir(quemPede: Uuid, alvo: Uuid, quando: LocalDateTime): AppResult<Boolean> =
        dbQuery {
            val (a, b) = FriendshipPolicy.par(quemPede, alvo)
            // `insertIgnore` e não `insert`: os dois se adicionando no mesmo instante escrevem a
            // MESMA chave primária (é o ponto do par canônico), e sem isto o segundo viraria 500.
            // Devolver `false` deixa o serviço dizer "já existe um pedido", que é o que houve.
            val r = FriendshipsTable.insertIgnore {
                it[userA] = a
                it[userB] = b
                it[requestedBy] = quemPede
                it[status] = FriendshipPolicy.Estado.PENDENTE.name
                it[createdAt] = quando
            }
            r.insertedCount > 0
        }

    override suspend fun responder(
        um: Uuid,
        outro: Uuid,
        novo: FriendshipPolicy.Estado,
        quando: LocalDateTime,
    ): AppResult<Boolean> = dbQuery {
        val (a, b) = FriendshipPolicy.par(um, outro)
        // O `status eq PENDENTE` no WHERE é o que torna a resposta atômica: se a linha já foi
        // respondida entre a leitura do serviço e este update, nenhuma linha casa e devolvemos
        // `false` em vez de sobrescrever a decisão de alguém.
        val n = FriendshipsTable.update({
            (FriendshipsTable.userA eq a) and
                (FriendshipsTable.userB eq b) and
                (FriendshipsTable.status eq FriendshipPolicy.Estado.PENDENTE.name)
        }) {
            it[status] = novo.name
            it[respondedAt] = quando
        }
        n > 0
    }

    override suspend fun apagar(um: Uuid, outro: Uuid): AppResult<Boolean> = dbQuery {
        val (a, b) = FriendshipPolicy.par(um, outro)
        FriendshipsTable.deleteWhere {
            (userA eq a) and (userB eq b)
        } > 0
    }

    override suspend fun contarAmizades(userId: Uuid): AppResult<Int> = dbQuery {
        FriendshipsTable
            .selectAll()
            .where {
                ((FriendshipsTable.userA eq userId) or (FriendshipsTable.userB eq userId)) and
                    (FriendshipsTable.status eq FriendshipPolicy.Estado.ACEITA.name)
            }
            .count()
            .toInt()
    }

    override suspend fun amigos(userId: Uuid): AppResult<List<Pessoa>> = dbQuery {
        pessoasDoOutroLado(userId, FriendshipPolicy.Estado.ACEITA)
            .map { it.first }
            // Alfabética e case-insensitive: a lista existe para PROCURAR alguém. Ordenar por
            // data de amizade daria uma ordem que muda sozinha e na qual ninguém sabe buscar.
            .sortedBy { it.displayName.lowercase() }
    }

    override suspend fun pedidosRecebidos(userId: Uuid): AppResult<List<PedidoRecebido>> = dbQuery {
        pessoasDoOutroLado(userId, FriendshipPolicy.Estado.PENDENTE)
            // Só o que EU não mandei. O filtro é aqui e não no SQL porque `requested_by <> :eu`
            // custaria a mesma varredura, e a intenção fica mais legível em Kotlin.
            .filter { (_, quemPediu, _) -> quemPediu != userId }
            .map { (pessoa, _, quando) -> PedidoRecebido(pessoa, quando) }
            .sortedByDescending { it.createdAt }
    }

    // ---- bloqueio ----

    override suspend fun haBloqueioEntre(um: Uuid, outro: Uuid): AppResult<Boolean> = dbQuery {
        BlocksTable.selectAll()
            .where {
                ((BlocksTable.blockerId eq um) and (BlocksTable.blockedId eq outro)) or
                    ((BlocksTable.blockerId eq outro) and (BlocksTable.blockedId eq um))
            }
            .any()
    }

    override suspend fun bloqueouMe(alvo: Uuid, quemPergunta: Uuid): AppResult<Boolean> = dbQuery {
        BlocksTable.selectAll()
            .where { (BlocksTable.blockerId eq alvo) and (BlocksTable.blockedId eq quemPergunta) }
            .any()
    }

    override suspend fun bloquear(
        bloqueador: Uuid,
        bloqueado: Uuid,
        quando: LocalDateTime,
    ): AppResult<Unit> = dbQuery {
        // UMA transação para os dois efeitos. Separadas, uma falha no meio deixaria a pessoa
        // bloqueada com um pedido "aguardando resposta" que jamais seria respondido.
        val (a, b) = FriendshipPolicy.par(bloqueador, bloqueado)
        // O retorno é ignorado de propósito: não havia amizade é caso NORMAL, não erro.
        @Suppress("UNUSED_EXPRESSION")
        FriendshipsTable.deleteWhere { (userA eq a) and (userB eq b) }.let { }

        BlocksTable.insertIgnore {
            it[blockerId] = bloqueador
            it[blockedId] = bloqueado
            it[createdAt] = quando
        }
        Unit
    }

    override suspend fun desbloquear(bloqueador: Uuid, bloqueado: Uuid): AppResult<Boolean> =
        dbQuery {
            BlocksTable.deleteWhere {
                (blockerId eq bloqueador) and (blockedId eq bloqueado)
            } > 0
        }

    override suspend fun bloqueados(userId: Uuid): AppResult<List<Pessoa>> = dbQuery {
        // Join EXPLÍCITO: `innerJoin` implícito depende de FK declarada no Exposed, e as tabelas
        // aqui declaram a FK no SQL (V40), não no objeto Table — de propósito, para o Kotlin não
        // ter uma segunda definição de schema que pode divergir da migration.
        BlocksTable
            .join(UsersTable, JoinType.INNER, onColumn = BlocksTable.blockedId, otherColumn = UsersTable.id)
            .select(BlocksTable.blockedId, UsersTable.displayName, BlocksTable.createdAt)
            .where { BlocksTable.blockerId eq userId }
            // Mais recente primeiro: quem abre a lista quase sempre quer desfazer o último.
            .orderBy(BlocksTable.createdAt to SortOrder.DESC)
            .map { Pessoa(it[BlocksTable.blockedId], it[UsersTable.displayName]) }
    }

    // ---- privados ----

    /**
     * As linhas do usuário num estado, já resolvendo O NOME de quem está do outro lado.
     *
     * O join é feito duas vezes, uma por lado do par, e o resultado é somado — consequência
     * direta da ordem canônica: `eu` pode ser `user_a` ou `user_b`, e não há um "lado do outro"
     * fixo para juntar. É o preço da PK que impede pedido cruzado, e é barato: os dois lados são
     * consultas por índice (`friendships_user_a_idx` e `_b_idx`).
     */
    private fun pessoasDoOutroLado(
        userId: Uuid,
        estado: FriendshipPolicy.Estado,
    ): List<Triple<Pessoa, Uuid, LocalDateTime>> {
            val comoA = FriendshipsTable
                .join(UsersTable, JoinType.INNER,
                    onColumn = FriendshipsTable.userB, otherColumn = UsersTable.id)
                .select(
                    FriendshipsTable.userB, UsersTable.displayName,
                    FriendshipsTable.requestedBy, FriendshipsTable.createdAt,
                )
                .where {
                    (FriendshipsTable.userA eq userId) and (FriendshipsTable.status eq estado.name)
                }
                .map {
                    Triple(
                        Pessoa(it[FriendshipsTable.userB], it[UsersTable.displayName]),
                        it[FriendshipsTable.requestedBy],
                        it[FriendshipsTable.createdAt],
                    )
                }

            val comoB = FriendshipsTable
                .join(UsersTable, JoinType.INNER,
                    onColumn = FriendshipsTable.userA, otherColumn = UsersTable.id)
                .select(
                    FriendshipsTable.userA, UsersTable.displayName,
                    FriendshipsTable.requestedBy, FriendshipsTable.createdAt,
                )
                .where {
                    (FriendshipsTable.userB eq userId) and (FriendshipsTable.status eq estado.name)
                }
                .map {
                    Triple(
                        Pessoa(it[FriendshipsTable.userA], it[UsersTable.displayName]),
                        it[FriendshipsTable.requestedBy],
                        it[FriendshipsTable.createdAt],
                    )
                }

            return comoA + comoB
    }

    private fun ResultRow.toAmizade() = Amizade(
        userA = this[FriendshipsTable.userA],
        userB = this[FriendshipsTable.userB],
        requestedBy = this[FriendshipsTable.requestedBy],
        status = FriendshipPolicy.Estado.valueOf(this[FriendshipsTable.status]),
        createdAt = this[FriendshipsTable.createdAt],
        respondedAt = this[FriendshipsTable.respondedAt],
    )

    /** Exposed é bloqueante -> IO. Exceção do banco vira `AppError.Unexpected` e não vaza. */
    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
