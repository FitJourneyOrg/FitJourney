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
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
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
