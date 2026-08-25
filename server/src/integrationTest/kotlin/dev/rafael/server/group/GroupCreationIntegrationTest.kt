package dev.rafael.server.group

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
import dev.rafael.server.features.group.services.GroupPolicy
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
 * Criação de grupo contra Postgres REAL (ARCH #33, fatia A.1).
 *
 * POR QUE contra o banco: as três garantias desta fatia não estão no Kotlin.
 *  - **grupo e admin na mesma transação** — só o banco prova que não sobrou grupo órfão;
 *  - **código único** — a garantia é o `UNIQUE`, não o sorteio;
 *  - **fim > início** — é `CHECK`, e existe justamente para escrita direta, que o Kotlin não vê.
 *
 * A `GroupPolicy` já é testada pura. Aqui se testa o que sobra — e a lição da fatia B do outbox
 * foi exatamente essa: os quatro defeitos daquela bateria estavam todos fora da lógica pura.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupCreationIntegrationTest {

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
        Migrations.run(ds)   // inclui V36
        Database.connect(ds)
    }

    @AfterAll
    fun teardown() {
        ds.close()
        postgres.stop()
    }

    /** Cada teste cria o SEU usuário — mesma lição do teste de conquistas: isolar pela CHAVE. */
    private fun novoUsuario(): Uuid {
        val id = Uuid.random()
        transaction {
            UsersTable.insert {
                it[UsersTable.id] = id
                it[firebaseUid] = "uid-$id"
                it[email] = "$id@teste.local"
                it[displayName] = "Atleta-" + id.toString().replace("-", "").take(6)
            }
        }
        return id
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun novoGrupo(
        dono: Uuid,
        titulo: String = "Setembro sem desculpa",
        regras: Set<GroupRule> = setOf(GroupRule.FOTO),
    ) = NovoGrupo(
        id = Uuid.random(),
        type = GroupType.DESAFIO,
        scoringModel = ScoringModel.CONTAGEM_CHECKINS,
        title = titulo,
        description = null,
        startDate = LocalDate.parse("2026-09-10"),
        endDate = LocalDate.parse("2026-09-30"),
        timezone = TimeZone.of("America/Sao_Paulo"),
        rules = regras,
        createdBy = dono,
    )

    @Test
    fun `criar deixa o dono como ADMIN, na mesma transacao`() = runBlocking {
        val dono = novoUsuario()

        val grupo = ok(repo.create(novoGrupo(dono)))

        assertEquals(MemberRole.ADMIN.name, ok(repo.roleOf(grupo.id, dono)))
        assertEquals(1, grupo.memberCount, "o criador já conta no teto de 50")
    }

    @Test
    fun `o codigo vem preenchido e dentro do alfabeto sem ambiguidade`() = runBlocking {
        val grupo = ok(repo.create(novoGrupo(novoUsuario())))

        assertEquals(GroupPolicy.TAMANHO_DO_CODIGO, grupo.code.length)
        grupo.code.forEach { c ->
            assertTrue(c in GroupPolicy.ALFABETO, "'$c' não pertence ao alfabeto do código")
        }
    }

    @Test
    fun `codigos de grupos diferentes nao se repetem`() = runBlocking {
        val dono = novoUsuario()
        val codigos = (1..20).map { ok(repo.create(novoGrupo(dono, titulo = "Grupo $it"))).code }

        assertEquals(codigos.size, codigos.toSet().size, "código é a porta de entrada: colidir manda a pessoa para o grupo errado")
    }

    @Test
    fun `as regras sobrevivem ao round-trip`() = runBlocking {
        val criado = ok(repo.create(novoGrupo(novoUsuario(), regras = setOf(GroupRule.FOTO, GroupRule.EMOJI_DO_DIA))))

        val lido = ok(repo.findById(criado.id))

        assertNotNull(lido)
        assertEquals(setOf(GroupRule.FOTO, GroupRule.EMOJI_DO_DIA), lido!!.rules)
    }

    @Test
    fun `grupo sem regra nenhuma e valido`() = runBlocking {
        // Caminho comum: um desafio de "só aparecer" não exige foto nem local. Sem este teste,
        // o `batchInsert` de lista vazia passaria despercebido.
        val criado = ok(repo.create(novoGrupo(novoUsuario(), regras = emptySet())))

        assertEquals(emptySet<GroupRule>(), ok(repo.findById(criado.id))!!.rules)
    }

    @Test
    fun `listByMember traz os meus e NAO os dos outros`() = runBlocking {
        // [REGRA] tudo chaveado por usuário. A tela de Grupos é a primeira da aba: um vazamento
        // aqui mostraria o desafio de um desconhecido na lista de alguém.
        val eu = novoUsuario()
        val outro = novoUsuario()
        val meu = ok(repo.create(novoGrupo(eu, titulo = "O meu")))
        ok(repo.create(novoGrupo(outro, titulo = "O dele")))

        val meus = ok(repo.listByMember(eu))

        assertEquals(listOf(meu.id), meus.map { it.id })
    }

    @Test
    fun `quem nao e membro nao tem papel`() = runBlocking {
        val grupo = ok(repo.create(novoGrupo(novoUsuario())))

        assertNull(ok(repo.roleOf(grupo.id, novoUsuario())))
    }

    @Test
    fun `o banco recusa fim anterior ao inicio`() = runBlocking {
        // O CHECK existe para o que o Kotlin NÃO cobre: script, psql, correção manual. A
        // GroupPolicy já barra pelo formulário; isto prova a segunda linha de defesa.
        val dono = novoUsuario()
        // SQL cru, fora do Exposed: o ponto é justamente escrever SEM passar pelo Kotlin.
        val erro = runCatching {
            ds.connection.use { c ->
                c.createStatement().use { s ->
                    s.executeUpdate(
                        """
                        INSERT INTO groups (id, code, type, scoring_model, title,
                                            start_date, end_date, timezone, created_by)
                        VALUES ('${Uuid.random()}', 'ZZZZZZ', 'DESAFIO', 'CONTAGEM_CHECKINS',
                                'Invertido', '2026-09-30', '2026-09-10',
                                'America/Sao_Paulo', '$dono')
                        """.trimIndent(),
                    )
                }
                c.commit()
            }
        }.exceptionOrNull()

        assertNotNull(erro, "desafio de duração negativa não pode existir nem por escrita direta")
        assertTrue(
            erro!!.message?.contains("groups_periodo_valido") == true,
            "a recusa tem de vir do CHECK nomeado: ${erro.message}",
        )
    }
}
