package dev.rafael.server.features.checkin.db

import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.map
import dev.rafael.server.features.checkin.models.CheckIn
import dev.rafael.server.features.checkin.models.CheckInComAutor
import dev.rafael.server.features.checkin.models.NovoCheckIn
import dev.rafael.server.features.group.db.GroupMembersTable
import dev.rafael.server.features.group.db.GroupsTable
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

class CheckInRepositoryImpl : CheckInRepository {

    override suspend fun criar(novo: NovoCheckIn): AppResult<Boolean> = dbQuery {
        transaction {
            // `insertIgnore` + releitura, em vez de "SELECT e depois INSERT".
            //
            // Quem decide o empate é o índice único `(group_id, user_id, local_date)`. Um SELECT
            // antes deixaria a janela clássica: duas requisições passam pela checagem, as duas
            // inserem, e uma estoura com 500 na cara do usuário. Aqui a segunda simplesmente não
            // insere, e a releitura conta o que aconteceu.
            CheckInsTable.insertIgnore {
                it[id] = novo.id
                it[groupId] = novo.groupId
                it[userId] = novo.userId
                it[localDate] = novo.localDate
                it[createdAt] = novo.createdAt
                it[status] = CheckInStatus.VALIDO.name       // nasce válido (4.9)
                it[photoRef] = novo.photoRef
                it[placeName] = novo.placeName
                it[placeLat] = novo.placeLat?.let(BigDecimal::valueOf)
                it[placeLng] = novo.placeLng?.let(BigDecimal::valueOf)
            }

            val doDia = CheckInsTable.selectAll().where {
                (CheckInsTable.groupId eq novo.groupId) and
                    (CheckInsTable.userId eq novo.userId) and
                    (CheckInsTable.localDate eq novo.localDate)
            }.singleOrNull()

            // A linha que está lá é a MINHA? Se for outra, alguém (ou eu mesmo, num toque duplo)
            // chegou primeiro.
            doDia?.get(CheckInsTable.id) == novo.id
        }
    }

    override suspend fun doDia(groupId: Uuid, userId: Uuid, dia: LocalDate): AppResult<Uuid?> = dbQuery {
        transaction {
            CheckInsTable.selectAll().where {
                (CheckInsTable.groupId eq groupId) and
                    (CheckInsTable.userId eq userId) and
                    (CheckInsTable.localDate eq dia)
            }.singleOrNull()?.get(CheckInsTable.id)
        }
    }

    override suspend fun porId(id: Uuid): AppResult<CheckInComAutor?> = dbQuery {
        transaction {
            (CheckInsTable innerJoin UsersTable)
                .selectAll()
                .where { CheckInsTable.id eq id }
                .map(::toComAutor)
                .singleOrNull()
        }
    }

    override suspend fun doGrupo(
        groupId: Uuid,
        limite: Int,
        antesDe: LocalDateTime?,
    ): AppResult<List<CheckInComAutor>> = dbQuery {
        transaction {
            (CheckInsTable innerJoin UsersTable)
                .selectAll()
                .where {
                    val doGrupo = CheckInsTable.groupId eq groupId
                    if (antesDe == null) doGrupo else doGrupo and (CheckInsTable.createdAt less antesDe)
                }
                .orderBy(CheckInsTable.createdAt to SortOrder.DESC)   // 8.0.4
                .limit(limite)
                .map(::toComAutor)
        }
    }

    /**
     * Apaga a LINHA, e é isso que LIBERA o slot do dia (4.11). Marcar como "removido" manteria o
     * índice único ocupado e transformaria o arrependimento numa armadilha.
     *
     * O número de linhas afetadas é descartado no `map`: apagar o que já não existe não é erro —
     * o botão pode ser tocado duas vezes, e quem confere o dono é o service, antes de chegar aqui.
     */
    override suspend fun apagar(id: Uuid): AppResult<Unit> =
        dbQuery { transaction { CheckInsTable.deleteWhere { CheckInsTable.id eq id } } }.map { }

    override suspend fun ranking(groupId: Uuid): AppResult<List<LinhaDoRanking>> = dbQuery {
        transaction {
            val total = CheckInsTable.id.count().alias("total")
            // O desempate: o instante do ÚLTIMO check-in. Com contagens iguais, quem o tem mais
            // antigo chegou àquela pontuação primeiro.
            val ultimo = CheckInsTable.createdAt.max().alias("ultimo")

            (GroupMembersTable innerJoin UsersTable)
                .join(
                    CheckInsTable,
                    JoinType.LEFT,
                    // LEFT JOIN com as três condições no ON, e não no WHERE: no WHERE, o filtro de
                    // status transformaria o LEFT em INNER e sumiria com quem não tem check-in.
                    onColumn = GroupMembersTable.userId,
                    otherColumn = CheckInsTable.userId,
                    additionalConstraint = {
                        (CheckInsTable.groupId eq groupId) and
                            (CheckInsTable.status neq CheckInStatus.INVALIDADO.name)
                    },
                )
                .select(
                    GroupMembersTable.userId,
                    UsersTable.displayName,
                    GroupMembersTable.joinedAt,
                    total,
                    ultimo,
                )
                .where { GroupMembersTable.groupId eq groupId }
                .groupBy(GroupMembersTable.userId, UsersTable.displayName, GroupMembersTable.joinedAt)
                .orderBy(
                    // 1. A PONTUAÇÃO ([REGRA] #18: contagem de check-ins).
                    total to SortOrder.DESC,
                    // 2. Quem ATINGIU a pontuação primeiro. NULLS LAST porque quem nunca fez
                    //    check-in tem `ultimo` nulo — sem isso o Postgres o poria em primeiro.
                    ultimo to SortOrder.ASC_NULLS_LAST,
                    // 3. Quem está no desafio há mais tempo.
                    //
                    //    Este critério existe por causa do DIA 1: cinquenta pessoas com zero
                    //    check-ins empatam nos dois primeiros critérios, e sem um terceiro o
                    //    Postgres devolveria em ordem arbitrária — que muda entre consultas. Com o
                    //    polling de 10s, a lista se reembaralharia sozinha na tela.
                    GroupMembersTable.joinedAt to SortOrder.ASC,
                    // 4. DETERMINISMO, não justiça. Duas pessoas que entraram no mesmo instante
                    //    ainda empatariam; isto garante que a ordem entre elas nunca mude. Nunca é
                    //    apresentado como critério a ninguém, porque não significa nada.
                    GroupMembersTable.userId to SortOrder.ASC,
                )
                .map {
                    LinhaDoRanking(
                        userId = it[GroupMembersTable.userId],
                        displayName = it[UsersTable.displayName],
                        checkIns = it[total].toInt(),
                    )
                }
        }
    }

    // ---- purga de mídia (4.8, emendada) ----

    override suspend fun comFotoExpirada(carenciaEmDias: Int, limite: Int): AppResult<List<FotoExpirada>> = dbQuery {
        transaction {
            (CheckInsTable innerJoin GroupsTable)
                .selectAll()
                .where {
                    CheckInsTable.photoRef.isNotNull() and
                        CheckInsTable.photoPurgedAt.isNull() and
                        // `end_date + carência < hoje`. A conta é em dia CIVIL e ignora o fuso do
                        // grupo de propósito: a diferença é de no máximo um dia, e com 30 dias de
                        // carência isso é ruído. Trazer o fuso para cá custaria uma função por
                        // linha e impediria o índice.
                        (GroupsTable.endDate less hojeMenos(carenciaEmDias))
                }
                .limit(limite)
                .mapNotNull { linha ->
                    linha[CheckInsTable.photoRef]?.let { FotoExpirada(linha[CheckInsTable.id], it) }
                }
        }
    }

    override suspend fun marcarPurgados(ids: List<Uuid>, agora: LocalDateTime): AppResult<Unit> = dbQuery {
        if (ids.isEmpty()) return@dbQuery Unit
        transaction {
            CheckInsTable.update({ CheckInsTable.id inList ids }) {
                it[photoPurgedAt] = agora
                // O CHECK `check_ins_local_completo` exige que nome e coordenada andem juntos —
                // anular os três de uma vez é o que mantém a linha válida.
                it[placeName] = null
                it[placeLat] = null
                it[placeLng] = null
            }
        }
    }

    override suspend fun refsVivas(): AppResult<Set<String>> = dbQuery {
        transaction {
            CheckInsTable
                .select(CheckInsTable.photoRef)
                .where { CheckInsTable.photoRef.isNotNull() and CheckInsTable.photoPurgedAt.isNull() }
                .mapNotNull { it[CheckInsTable.photoRef] }
                .toSet()
        }
    }

    /** Data civil de hoje menos N dias, no relógio do servidor. */
    private fun hojeMenos(dias: Int): LocalDate =
        Clock.System.now().minus(dias.days).toLocalDateTime(TimeZone.UTC).date

    private fun toComAutor(linha: ResultRow) = CheckInComAutor(
        checkIn = CheckIn(
            id = linha[CheckInsTable.id],
            groupId = linha[CheckInsTable.groupId],
            userId = linha[CheckInsTable.userId],
            localDate = linha[CheckInsTable.localDate],
            createdAt = linha[CheckInsTable.createdAt],
            status = runCatching { CheckInStatus.valueOf(linha[CheckInsTable.status]) }
                .getOrDefault(CheckInStatus.VALIDO),
            photoRef = linha[CheckInsTable.photoRef],
            photoPurgedAt = linha[CheckInsTable.photoPurgedAt],
            placeName = linha[CheckInsTable.placeName],
        ),
        // A coordenada está na linha lida e NÃO é copiada: o `CheckIn` não tem onde guardá-la.
        displayName = linha[UsersTable.displayName],
    )

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
