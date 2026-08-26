package dev.rafael.server.checkin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupType
import dev.rafael.contract.group.ScoringModel
import dev.rafael.core.result.AppResult
import dev.rafael.server.db.Migrations
import dev.rafael.server.features.checkin.db.CheckInRepositoryImpl
import dev.rafael.server.features.checkin.db.CheckInsTable
import dev.rafael.server.features.checkin.models.NovoCheckIn
import dev.rafael.server.features.group.db.GroupRepositoryImpl
import dev.rafael.server.features.group.db.GroupsTable
import dev.rafael.server.features.group.db.NovoGrupo
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Check-in contra Postgres REAL (fatia B) e a regra 2.5-A (fatia A.4).
 *
 * O `CheckInPolicy` já cobre as regras de forma pura, e o `CheckInService` já foi testado com
 * dublês. **Aqui se testa o que só o banco prova** — e que nenhum fake pode provar:
 *
 * - o `UNIQUE (group_id, user_id, local_date)`, que é quem decide o "um por dia" quando duas
 *   requisições estão em voo ao mesmo tempo
 * - os três `CHECK` da V38, que impedem linha sem sentido
 * - o `ON DELETE CASCADE`, que faz o grupo levar os check-ins junto
 * - a **atomicidade** do `deleteIfSoleMember`: a condição mora dentro do `DELETE`, e o dublê da
 *   suíte de unidade avisa no cabeçalho que não a reproduz
 *
 * O último é o mais importante do arquivo: é uma operação **irreversível**.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckInIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
    private lateinit var ds: HikariDataSource
    private val checkIns = CheckInRepositoryImpl()
    private val grupos = GroupRepositoryImpl()

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
        Migrations.run(ds)   // inclui V38
        Database.connect(ds)
    }

    @AfterAll
    fun teardown() {
        ds.close()
        postgres.stop()
    }

    // ---- utilidades ----

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun novoUsuario(nome: String = "Atleta"): Uuid {
        val id = Uuid.random()
        transaction {
            UsersTable.insert {
                it[UsersTable.id] = id
                it[firebaseUid] = "uid-$id"
                it[email] = "$id@teste.local"
                it[displayName] = nome
            }
        }
        return id
    }

    private suspend fun grupoDe(dono: Uuid, regras: Set<GroupRule> = setOf(GroupRule.FOTO)) = ok(
        grupos.create(
            NovoGrupo(
                id = Uuid.random(),
                type = GroupType.DESAFIO,
                scoringModel = ScoringModel.CONTAGEM_CHECKINS,
                title = "Desafio",
                description = null,
                startDate = LocalDate.parse("2026-09-01"),
                endDate = LocalDate.parse("2026-09-30"),
                timezone = TimeZone.of("America/Sao_Paulo"),
                rules = regras,
                createdBy = dono,
            ),
        ),
    )

    /**
     * A referência de foto é ÚNICA por check-in, e isso não é enfeite.
     *
     * Com uma constante compartilhada, o `refsVivas()` — que devolve um `Set` — continuava
     * contendo a ref depois de purgar um check-in, porque os outros apontavam para a mesma. A
     * asserção estava certa e o dado é que não representava nada.
     *
     * De quebra, o schema PERMITE dois check-ins com a mesma foto, e nesse caso o recolhimento de
     * órfãos mantém o arquivo enquanto qualquer linha o referenciar — comportamento correto, que
     * um dado de teste compartilhado escondia em vez de exercitar.
     */
    private fun novo(
        grupo: Uuid,
        usuario: Uuid,
        dia: String = "2026-09-10",
        foto: String? = refUnica(),
        local: String? = null,
    ) = NovoCheckIn(
        id = Uuid.random(),
        groupId = grupo,
        userId = usuario,
        localDate = LocalDate.parse(dia),
        createdAt = LocalDateTime.parse("${dia}T12:00:00"),
        photoRef = foto,
        placeName = local,
        placeLat = local?.let { -23.55 },
        placeLng = local?.let { -46.63 },
    )

    /** No formato que o `ArmazenamentoEmDisco` valida: `aa/bb/<32 hex>.jpg`. */
    private fun refUnica(): String {
        val nome = Uuid.random().toString().replace("-", "")
        return "${nome.take(2)}/${nome.drop(2).take(2)}/$nome.jpg"
    }

    private fun linhasDoGrupo(grupo: Uuid): Int = transaction {
        CheckInsTable.selectAll().where { CheckInsTable.groupId eq grupo }.count().toInt()
    }

    // ---- o índice único: "um por pessoa/dia/grupo" (4.3) ----

    @Test
    fun `o segundo check-in do mesmo dia NAO entra`() = runBlocking {
        // Esta é a garantia de verdade do "um por dia". O service confere antes, mas duas
        // requisições em voo passam as duas pela conferência — só o índice decide o empate.
        val dono = novoUsuario()
        val grupo = grupoDe(dono)

        assertTrue(ok(checkIns.criar(novo(grupo.id, dono))))
        assertFalse(ok(checkIns.criar(novo(grupo.id, dono))), "o índice tinha que recusar")

        assertEquals(1, linhasDoGrupo(grupo.id))
    }

    @Test
    fun `dias diferentes entram os dois`() = runBlocking {
        val dono = novoUsuario()
        val grupo = grupoDe(dono)

        assertTrue(ok(checkIns.criar(novo(grupo.id, dono, dia = "2026-09-10"))))
        assertTrue(ok(checkIns.criar(novo(grupo.id, dono, dia = "2026-09-11"))))

        assertEquals(2, linhasDoGrupo(grupo.id))
    }

    @Test
    fun `duas pessoas no mesmo dia entram as duas`() = runBlocking {
        // O índice é por (grupo, usuário, dia) — se faltasse o usuário na chave, o primeiro a
        // treinar bloquearia o desafio inteiro.
        val dono = novoUsuario()
        val outro = novoUsuario()
        val grupo = grupoDe(dono)
        ok(grupos.join(grupo.id, outro))

        assertTrue(ok(checkIns.criar(novo(grupo.id, dono))))
        assertTrue(ok(checkIns.criar(novo(grupo.id, outro))))

        assertEquals(2, linhasDoGrupo(grupo.id))
    }

    @Test
    fun `a mesma pessoa faz check-in em grupos diferentes no mesmo dia`() = runBlocking {
        // Decisão: "1 check-in por GRUPO por dia; sem teto de grupos". Sem o group_id na chave,
        // participar de dois desafios seria escolher em qual treinar.
        val dono = novoUsuario()
        val a = grupoDe(dono)
        val b = grupoDe(dono)

        assertTrue(ok(checkIns.criar(novo(a.id, dono))))
        assertTrue(ok(checkIns.criar(novo(b.id, dono))))

        assertEquals(1, linhasDoGrupo(a.id))
        assertEquals(1, linhasDoGrupo(b.id))
    }

    @Test
    fun `apagar LIBERA o slot do dia`() = runBlocking {
        // 4.11. É o que faz a regra conversar com o índice: sem a liberação, apagar a foto
        // tremida deixaria a pessoa sem poder refazer — arrependimento viraria armadilha.
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        val primeiro = novo(grupo.id, dono)
        ok(checkIns.criar(primeiro))

        ok(checkIns.apagar(primeiro.id))

        assertTrue(ok(checkIns.criar(novo(grupo.id, dono))), "o dia tinha que estar livre")
        assertEquals(1, linhasDoGrupo(grupo.id))
    }

    // ---- os CHECK da V38 ----

    @Test
    fun `nome de local sem coordenada e recusado pelo banco`() {
        // O CHECK existe porque meia localização é dado que a tela não sabe apresentar. O service
        // já valida antes; isto garante que ninguém contorne por outro caminho.
        val dono = novoUsuario()
        assertThrows<Exception> {
            transaction {
                CheckInsTable.insert {
                    it[id] = Uuid.random()
                    it[groupId] = runBlocking { grupoDe(dono) }.id
                    it[userId] = dono
                    it[localDate] = LocalDate.parse("2026-09-10")
                    it[createdAt] = LocalDateTime.parse("2026-09-10T12:00:00")
                    it[status] = CheckInStatus.VALIDO.name
                    it[placeName] = "Academia"
                    // placeLat e placeLng ficam nulos de propósito
                }
            }
        }
    }

    @Test
    fun `foto purgada sem foto e recusada pelo banco`() {
        // Sem este CHECK, uma linha poderia dizer "a foto expirou" sem nunca ter tido foto — e o
        // feed não teria como distinguir "nunca teve" de "expirou".
        val dono = novoUsuario()
        assertThrows<Exception> {
            transaction {
                CheckInsTable.insert {
                    it[id] = Uuid.random()
                    it[groupId] = runBlocking { grupoDe(dono) }.id
                    it[userId] = dono
                    it[localDate] = LocalDate.parse("2026-09-10")
                    it[createdAt] = LocalDateTime.parse("2026-09-10T12:00:00")
                    it[status] = CheckInStatus.VALIDO.name
                    it[photoRef] = null
                    it[photoPurgedAt] = LocalDateTime.parse("2026-12-10T12:00:00")
                }
            }
        }
    }

    @Test
    fun `status fora do vocabulario e recusado pelo banco`() {
        // TEXT com CHECK, e não enum do Postgres: acrescentar valor não custa migration de tipo,
        // mas o vocabulário continua fechado — estado nunca é texto livre.
        val dono = novoUsuario()
        assertThrows<Exception> {
            transaction {
                CheckInsTable.insert {
                    it[id] = Uuid.random()
                    it[groupId] = runBlocking { grupoDe(dono) }.id
                    it[userId] = dono
                    it[localDate] = LocalDate.parse("2026-09-10")
                    it[createdAt] = LocalDateTime.parse("2026-09-10T12:00:00")
                    it[status] = "APROVADO_POR_VOTACAO"   // revogado pela emenda do #17
                }
            }
        }
    }

    @Test
    fun `a coordenada e truncada a 2 casas pelo proprio tipo da coluna`() = runBlocking {
        // NUMERIC(5,2) é a última linha de defesa do invariante "a coordenada exata nunca é
        // gravada". Mesmo que alguém, um dia, esqueça de arredondar no service, o TIPO não
        // consegue guardar mais casas.
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        transaction {
            CheckInsTable.insert {
                it[id] = Uuid.random()
                it[groupId] = grupo.id
                it[userId] = dono
                it[localDate] = LocalDate.parse("2026-09-10")
                it[createdAt] = LocalDateTime.parse("2026-09-10T12:00:00")
                it[status] = CheckInStatus.VALIDO.name
                it[placeName] = "Ipiranga"
                it[placeLat] = BigDecimal("-23.5505199")
                it[placeLng] = BigDecimal("-46.6333094")
            }
        }

        val lat = transaction {
            CheckInsTable.selectAll().where { CheckInsTable.groupId eq grupo.id }
                .single()[CheckInsTable.placeLat]
        }
        assertEquals(2, lat!!.scale(), "a coluna não pode guardar mais que 2 casas")
    }

    // ---- cascata ----

    @Test
    fun `apagar o grupo leva os check-ins junto`() = runBlocking {
        // O cascade é do schema (V38), não do código. Sem ele, apagar um grupo deixaria linhas
        // órfãs apontando para um grupo que não existe mais.
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        ok(checkIns.criar(novo(grupo.id, dono)))
        assertEquals(1, linhasDoGrupo(grupo.id))

        transaction { GroupsTable.deleteWhere { GroupsTable.id eq grupo.id } }

        assertEquals(0, linhasDoGrupo(grupo.id))
    }

    // ---- 2.5-A: o admin sozinho sai e o desafio vai junto ----

    @Test
    fun `admin sozinho apaga o grupo, e a cascata leva os check-ins`() = runBlocking {
        // A regra 2.5-A. Operação IRREVERSÍVEL — e a condição mora dentro do DELETE, num
        // `notExists` que nenhum dublê reproduz.
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        ok(checkIns.criar(novo(grupo.id, dono)))

        assertTrue(ok(grupos.deleteIfSoleMember(grupo.id, dono)))

        assertNull(ok(grupos.findById(grupo.id)))
        assertEquals(0, linhasDoGrupo(grupo.id))
    }

    @Test
    fun `com outro membro, o admin NAO apaga o grupo`() = runBlocking {
        // O `false` é o que faz a recusa de 2.5 voltar no service. Se o DELETE apagasse assim
        // mesmo, o desafio sumiria debaixo de quem acabou de entrar pelo código.
        val dono = novoUsuario()
        val outro = novoUsuario()
        val grupo = grupoDe(dono)
        ok(grupos.join(grupo.id, outro))
        ok(checkIns.criar(novo(grupo.id, outro)))

        assertFalse(ok(grupos.deleteIfSoleMember(grupo.id, dono)))

        assertNotNull(ok(grupos.findById(grupo.id)))
        assertEquals(1, linhasDoGrupo(grupo.id), "o check-in de quem ficou continua lá")
    }

    @Test
    fun `apagar o proprio grupo nao encosta em outro`() = runBlocking {
        val dono = novoUsuario()
        val a = grupoDe(dono)
        val b = grupoDe(dono)
        ok(checkIns.criar(novo(b.id, dono)))

        ok(grupos.deleteIfSoleMember(a.id, dono))

        assertNotNull(ok(grupos.findById(b.id)))
        assertEquals(1, linhasDoGrupo(b.id))
    }

    // ---- o feed (8.0) ----

    @Test
    fun `o feed vem do mais recente para o mais antigo`() = runBlocking {
        val dono = novoUsuario("Dono")
        val grupo = grupoDe(dono)
        ok(checkIns.criar(novo(grupo.id, dono, dia = "2026-09-10")))
        ok(checkIns.criar(novo(grupo.id, dono, dia = "2026-09-12")))

        val feed = ok(checkIns.doGrupo(grupo.id, limite = 10))

        assertEquals(
            listOf(LocalDate.parse("2026-09-12"), LocalDate.parse("2026-09-10")),
            feed.map { it.checkIn.localDate },
        )
        assertEquals("Dono", feed.first().displayName, "o nome vem no MESMO select, sem N+1")
    }

    @Test
    fun `o cursor devolve so o que e mais antigo que ele`() = runBlocking {
        // Cursor e não deslocamento: com item novo chegando por cima a cada 10s (polling), a
        // "página 2" por OFFSET repetiria ou pularia linhas.
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        ok(checkIns.criar(novo(grupo.id, dono, dia = "2026-09-10")))
        ok(checkIns.criar(novo(grupo.id, dono, dia = "2026-09-12")))

        val pagina = ok(
            checkIns.doGrupo(grupo.id, limite = 10, antesDe = LocalDateTime.parse("2026-09-12T00:00:00")),
        )

        assertEquals(1, pagina.size)
        assertEquals(LocalDate.parse("2026-09-10"), pagina.single().checkIn.localDate)
    }

    @Test
    fun `o feed respeita o limite`() = runBlocking {
        val dono = novoUsuario()
        val grupo = grupoDe(dono)
        repeat(5) { i -> ok(checkIns.criar(novo(grupo.id, dono, dia = "2026-09-1$i"))) }

        assertEquals(2, ok(checkIns.doGrupo(grupo.id, limite = 2)).size)
    }

    @Test
    fun `o feed de um grupo nao mostra check-in de outro`() = runBlocking {
        // A fronteira mais básica da fase, e a que seria mais grave se falhasse: check-in é
        // conteúdo de gente, e vazar entre grupos é vazar foto para desconhecidos.
        val dono = novoUsuario()
        val a = grupoDe(dono)
        val b = grupoDe(dono)
        ok(checkIns.criar(novo(a.id, dono)))

        assertTrue(ok(checkIns.doGrupo(b.id, limite = 10)).isEmpty())
    }

    // ---- purga de mídia (4.8, emendada) ----

    /** Um desafio que ACABOU há [diasAtras] dias, para exercitar a carência. */
    private suspend fun grupoEncerradoHa(dono: Uuid, diasAtras: Int) = ok(
        grupos.create(
            NovoGrupo(
                id = Uuid.random(),
                type = GroupType.DESAFIO,
                scoringModel = ScoringModel.CONTAGEM_CHECKINS,
                title = "Encerrado",
                description = null,
                startDate = hoje().minus(DatePeriod(days = diasAtras + 30)),
                endDate = hoje().minus(DatePeriod(days = diasAtras)),
                timezone = TimeZone.of("America/Sao_Paulo"),
                rules = setOf(GroupRule.FOTO),
                createdBy = dono,
            ),
        ),
    )

    private fun hoje(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.UTC).date

    @Test
    fun `so entra na purga a foto de desafio encerrado ALEM da carencia`() = runBlocking {
        // A âncora é o fim do DESAFIO, e não um prazo contado do check-in. A 4.8 dizia "90 dias"
        // e conflitava com a criação de grupo, que não tem duração máxima: um desafio de 180 dias
        // perderia as fotos do primeiro mês enquanto ainda estava rolando.
        val dono = novoUsuario()
        val velho = grupoEncerradoHa(dono, diasAtras = 40)     // além dos 30 de carência
        val recente = grupoEncerradoHa(dono, diasAtras = 5)    // ainda na carência
        val rolando = grupoDe(dono)                            // AGENDADO/ATIVO
        ok(checkIns.criar(novo(velho.id, dono, dia = velho.startDate.toString())))
        ok(checkIns.criar(novo(recente.id, dono, dia = recente.startDate.toString())))
        ok(checkIns.criar(novo(rolando.id, dono)))

        val paraPurgar = ok(checkIns.comFotoExpirada(carenciaEmDias = 30, limite = 100))

        assertEquals(1, paraPurgar.size, "só o desafio encerrado além da carência")
    }

    @Test
    fun `marcar purgado anula nome e coordenada sem violar o CHECK`() = runBlocking {
        // O `check_ins_local_completo` exige que nome e coordenada andem juntos. Anular só um
        // deles faria o UPDATE explodir — e a purga é justamente onde os três saem de uma vez.
        val dono = novoUsuario()
        val grupo = grupoEncerradoHa(dono, diasAtras = 40)
        val comLocal = novo(grupo.id, dono, dia = grupo.startDate.toString(), local = "Ipiranga")
        ok(checkIns.criar(comLocal))

        ok(checkIns.marcarPurgados(listOf(comLocal.id), LocalDateTime.parse("2026-08-25T12:00:00")))

        val linha = transaction {
            CheckInsTable.selectAll().where { CheckInsTable.id eq comLocal.id }.single()
        }
        assertNotNull(linha[CheckInsTable.photoPurgedAt])
        assertNull(linha[CheckInsTable.placeName], "o nome do lugar é dado pessoal e sai junto")
        assertNull(linha[CheckInsTable.placeLat])
        assertNull(linha[CheckInsTable.placeLng])
        // A LINHA fica: ~200 bytes que sustentam a contagem, o ranking e as conquistas.
        assertNotNull(linha[CheckInsTable.photoRef], "a ref fica: distingue 'expirou' de 'nunca teve'")
    }

    @Test
    fun `o que ja foi purgado nao volta para a purga nem conta como ref viva`() = runBlocking {
        val dono = novoUsuario()
        val grupo = grupoEncerradoHa(dono, diasAtras = 40)
        val alvo = novo(grupo.id, dono, dia = grupo.startDate.toString())
        ok(checkIns.criar(alvo))

        assertTrue(ok(checkIns.refsVivas()).contains(alvo.photoRef))
        ok(checkIns.marcarPurgados(listOf(alvo.id), LocalDateTime.parse("2026-08-25T12:00:00")))

        assertTrue(ok(checkIns.comFotoExpirada(30, 100)).none { it.id == alvo.id })
        assertFalse(
            ok(checkIns.refsVivas()).contains(alvo.photoRef),
            "ref purgada contando como viva impediria o recolhimento do arquivo para sempre",
        )
    }

    // ---- doDia: o que alimenta o `myCheckInToday` ----

    @Test
    fun `doDia acha o meu check-in do dia e ignora o dos outros`() = runBlocking {
        val dono = novoUsuario()
        val outro = novoUsuario()
        val grupo = grupoDe(dono)
        ok(grupos.join(grupo.id, outro))
        val meu = novo(grupo.id, dono, dia = "2026-09-10")
        ok(checkIns.criar(meu))
        ok(checkIns.criar(novo(grupo.id, outro, dia = "2026-09-10")))

        assertEquals(meu.id, ok(checkIns.doDia(grupo.id, dono, LocalDate.parse("2026-09-10"))))
        assertNull(ok(checkIns.doDia(grupo.id, dono, LocalDate.parse("2026-09-11"))))
    }
}
