package dev.rafael.server.group

import dev.rafael.server.CodigoDeTeste

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupType
import dev.rafael.contract.group.MemberRole
import dev.rafael.contract.group.ScoringModel
import dev.rafael.core.result.AppResult
import dev.rafael.server.db.Migrations
import dev.rafael.server.features.group.db.GroupRepositoryImpl
import dev.rafael.server.features.group.db.NovoGrupo
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.uuid.Uuid

/**
 * Entrada, saída e convites contra Postgres REAL (ARCH #33, fatia A.2).
 *
 * A `GroupPolicy` já cobre "pode entrar?" de forma pura. Aqui se testa o que só o banco prova:
 * a idempotência do `join`, a saída que preserva histórico, e — a mais importante — o
 * "um convite ativo por grupo", que depende de revogar e criar na MESMA transação.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupMembershipIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var ds: HikariDataSource
    private val repo = GroupRepositoryImpl()

    @BeforeAll
    fun setup() {
        postgres.start()
        ds = HikariConfig().apply {
            jdbcUrl = postgres.jdbcUrl
            username = postgres.username
            password = postgres.password
            driverClassName = "org.postgresql.Driver"
            isAutoCommit = false
        }.let(::HikariDataSource)
        Migrations.run(ds)   // inclui V37
        Database.connect(ds)
    }

    @AfterAll
    fun teardown() {
        ds.close()
        postgres.stop()
    }

    private fun novoUsuario(nome: String = "Atleta"): Uuid {
        val id = Uuid.random()
        transaction {
            UsersTable.insert {
                it[UsersTable.id] = id
                it[firebaseUid] = "uid-$id"
                it[email] = "$id@teste.local"
                it[displayName] = nome
                it[code] = CodigoDeTeste.de(id)   // V40: NOT NULL + UNIQUE + CHECK
            }
        }
        return id
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private suspend fun grupoDe(dono: Uuid) = ok(
        repo.create(
            NovoGrupo(
                id = Uuid.random(),
                type = GroupType.DESAFIO,
                scoringModel = ScoringModel.CONTAGEM_CHECKINS,
                title = "Desafio",
                description = null,
                startDate = LocalDate.parse("2026-09-10"),
                endDate = LocalDate.parse("2026-09-30"),
                timezone = TimeZone.of("America/Sao_Paulo"),
                rules = setOf(GroupRule.FOTO),
                createdBy = dono,
            ),
        ),
    )

    private val agora = LocalDateTime.parse("2026-09-01T12:00:00")

    // ---- entrada ----

    @Test
    fun `entrar duas vezes nao quebra nem duplica`() = runBlocking {
        // Dois toques no botão, ou um retry de rede, não podem virar erro na cara de quem já
        // entrou — nem uma segunda linha que faria o teto de 50 contar errado.
        val grupo = grupoDe(novoUsuario())
        val visitante = novoUsuario()

        ok(repo.join(grupo.id, visitante))
        ok(repo.join(grupo.id, visitante))

        assertEquals(2, ok(repo.findById(grupo.id))!!.memberCount, "dono + visitante, uma vez cada")
    }

    @Test
    fun `entrar por codigo funciona em minuscula`() = runBlocking {
        // Ninguém digita código em maiúscula. Recusar por causa disso seria hostil justamente
        // com quem está tentando entrar.
        val grupo = grupoDe(novoUsuario())

        val achado = ok(repo.findByCode(grupo.code.lowercase()))

        assertEquals(grupo.id, achado?.id)
    }

    @Test
    fun `codigo inexistente devolve null, nao erro`() = runBlocking {
        assertNull(ok(repo.findByCode("ZZZZZZ")))
    }

    @Test
    fun `sair remove o vinculo`() = runBlocking {
        val grupo = grupoDe(novoUsuario())
        val visitante = novoUsuario()
        ok(repo.join(grupo.id, visitante))

        ok(repo.leave(grupo.id, visitante))

        assertNull(ok(repo.roleOf(grupo.id, visitante)))
        assertEquals(1, ok(repo.findById(grupo.id))!!.memberCount)
    }

    @Test
    fun `sair de um grupo nao afeta o outro`() = runBlocking {
        // O `deleteWhere` filtra por grupo E usuário. Sem o primeiro filtro, sair de um grupo
        // tiraria a pessoa de todos — e o teste que só olha um grupo não perceberia.
        val dono = novoUsuario()
        val a = grupoDe(dono)
        val b = grupoDe(dono)
        val visitante = novoUsuario()
        ok(repo.join(a.id, visitante))
        ok(repo.join(b.id, visitante))

        ok(repo.leave(a.id, visitante))

        assertNull(ok(repo.roleOf(a.id, visitante)))
        assertEquals(MemberRole.MEMBRO.name, ok(repo.roleOf(b.id, visitante)))
    }

    @Test
    fun `membros vem do mais antigo para o mais novo`() = runBlocking {
        // É a ordem que a reivindicação de admin usa (2.12): prioridade ao mais antigo.
        val dono = novoUsuario("Dono")
        val grupo = grupoDe(dono)
        val segundo = novoUsuario("Segundo")
        ok(repo.join(grupo.id, segundo))

        val membros = ok(repo.members(grupo.id))

        assertEquals(listOf("Dono", "Segundo"), membros.map { it.displayName })
        assertEquals(MemberRole.ADMIN.name, membros.first().role)
    }

    @Test
    fun `transferir admin troca os dois papeis`() = runBlocking {
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        val outro = novoUsuario()
        ok(repo.join(grupo.id, outro))

        ok(repo.setRole(grupo.id, outro, MemberRole.ADMIN.name))
        ok(repo.setRole(grupo.id, dono, MemberRole.MEMBRO.name))

        assertEquals(MemberRole.ADMIN.name, ok(repo.roleOf(grupo.id, outro)))
        assertEquals(MemberRole.MEMBRO.name, ok(repo.roleOf(grupo.id, dono)))
    }

    // ---- convites ----

    @Test
    fun `gerar um convite novo REVOGA o anterior`() = runBlocking {
        // A garantia de "um convite ativo por grupo". Se revogar e criar fossem duas operações
        // separadas, uma falha no meio deixaria dois links válidos — e "revogar o link"
        // deixaria de significar o que promete.
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        val primeiro = Uuid.random()
        val segundo = Uuid.random()

        ok(repo.createInvite(primeiro, grupo.id, dono, agora, agora))
        ok(repo.createInvite(segundo, grupo.id, dono, agora, agora))

        assertEquals(segundo, ok(repo.activeInvite(grupo.id))?.token)
        assertNotNull(ok(repo.findInvite(primeiro))?.revokedAt, "o anterior tem de ficar revogado")
    }

    @Test
    fun `revogar nao apaga a linha`() = runBlocking {
        // Append-only: a linha responde "por que aquele link parou de funcionar?" seis meses
        // depois, no primeiro conflito real entre usuários.
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        val token = Uuid.random()
        ok(repo.createInvite(token, grupo.id, dono, agora, agora))

        ok(repo.revokeInvites(grupo.id, agora))

        assertNull(ok(repo.activeInvite(grupo.id)))
        assertNotNull(ok(repo.findInvite(token)), "a linha continua lá, só anulada")
    }

    @Test
    fun `revogar o convite de um grupo nao mexe no do outro`() = runBlocking {
        val dono = novoUsuario()
        val a = grupoDe(dono)
        val b = grupoDe(dono)
        ok(repo.createInvite(Uuid.random(), a.id, dono, agora, agora))
        val doB = Uuid.random()
        ok(repo.createInvite(doB, b.id, dono, agora, agora))

        ok(repo.revokeInvites(a.id, agora))

        assertNull(ok(repo.activeInvite(a.id)))
        assertEquals(doB, ok(repo.activeInvite(b.id))?.token)
    }

    @Test
    fun `token desconhecido devolve null`() = runBlocking {
        assertTrue(ok(repo.findInvite(Uuid.random())) == null)
    }
}
