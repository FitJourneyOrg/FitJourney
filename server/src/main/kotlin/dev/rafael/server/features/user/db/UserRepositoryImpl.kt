package dev.rafael.server.features.user.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.i18n.IdiomaPolicy
import dev.rafael.server.features.user.models.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class UserRepositoryImpl : UserRepository {

    override suspend fun findByFirebaseUid(firebaseUid: String): AppResult<User?> =
        dbQuery {
            UsersTable.selectAll()
                .where { UsersTable.firebaseUid eq firebaseUid }
                .singleOrNull()
                ?.toUser()
        }

    override suspend fun findById(userId: Uuid): AppResult<User?> =
        dbQuery {
            UsersTable.selectAll()
                .where { UsersTable.id eq userId }
                .singleOrNull()
                ?.toUser()
        }

    override suspend fun create(
        id: Uuid,
        firebaseUid: String,
        email: String?,
        displayName: String,
        code: String,
    ): AppResult<User> =
        dbQuery {
            UsersTable.insert {
                it[UsersTable.id] = id
                it[UsersTable.firebaseUid] = firebaseUid
                it[UsersTable.email] = email
                it[UsersTable.isPremium] = false
                it[UsersTable.displayName] = displayName
                it[UsersTable.code] = code
                // V47: explícito, mesmo havendo DEFAULT na coluna. O objeto devolvido abaixo
                // afirma `Idioma.PADRAO`, e deixar o banco decidir em silêncio abriria a chance de
                // os dois discordarem no dia em que o DEFAULT mudar.
                it[UsersTable.locale] = Idioma.PADRAO.tag
            }
            User(
                id = id,
                firebaseUid = firebaseUid,
                email = email,
                isPremium = false,
                displayName = displayName,
                code = code,
                idioma = Idioma.PADRAO,
            )
        }

    override suspend fun findByCode(code: String): AppResult<User?> =
        dbQuery {
            UsersTable.selectAll()
                .where { UsersTable.code eq code }
                .singleOrNull()
                ?.toUser()
        }

    override suspend fun updateCode(userId: Uuid, code: String): AppResult<User?> =
        dbQuery {
            val n = UsersTable.update({ UsersTable.id eq userId }) { it[UsersTable.code] = code }
            if (n == 0) null
            else UsersTable.selectAll().where { UsersTable.id eq userId }.single().toUser()
        }

    override suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?> =
        dbQuery {
            val n = UsersTable.update({ UsersTable.id eq userId }) { it[isPremium] = premium }
            if (n == 0) null
            else UsersTable.selectAll().where { UsersTable.id eq userId }.single().toUser()
        }

    override suspend fun updateDisplayName(userId: Uuid, displayName: String): AppResult<User?> =
        dbQuery {
            val n = UsersTable.update({ UsersTable.id eq userId }) {
                it[UsersTable.displayName] = displayName
            }
            if (n == 0) null
            else UsersTable.selectAll().where { UsersTable.id eq userId }.single().toUser()
        }

    override suspend fun updateIdioma(userId: Uuid, idioma: Idioma): AppResult<User?> =
        dbQuery {
            val n = UsersTable.update({ UsersTable.id eq userId }) {
                it[locale] = idioma.tag
            }
            if (n == 0) null
            else UsersTable.selectAll().where { UsersTable.id eq userId }.single().toUser()
        }

    /** Exposed é bloqueante -> IO. Qualquer exceção do banco vira AppError.Unexpected (não vaza). */
    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}

private fun ResultRow.toUser(): User = User(
    id = this[UsersTable.id],
    firebaseUid = this[UsersTable.firebaseUid],
    email = this[UsersTable.email],
    isPremium = this[UsersTable.isPremium],
    displayName = this[UsersTable.displayName],
    // CHAR(8) no Postgres vem preenchido com espaços à direita se algo gravar menos de 8.
    // O `trim()` é rede de segurança: o CHECK da V40 já exige exatamente 8, mas uma
    // comparação que falhasse por espaço invisível seria muito cara de diagnosticar.
    code = this[UsersTable.code].trim(),
    // V47. A conversão passa pelo IdiomaPolicy num lugar SÓ, e ela nunca falha: valor inesperado
    // na coluna vira português em vez de derrubar a leitura do usuário. O CHECK da V47 deveria
    // impedir que chegue aqui, mas quem escreve por script passa por dentro deste mapeamento.
    idioma = IdiomaPolicy.de(this[UsersTable.locale]),
)