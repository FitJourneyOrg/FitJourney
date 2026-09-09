package dev.rafael.server.notificacao

import dev.rafael.server.BancoDeTeste
import dev.rafael.server.Semear
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.notificacao.db.DeviceTokenRepositoryImpl
import dev.rafael.server.features.notificacao.db.DeviceTokensTable
import dev.rafael.server.features.notificacao.db.NotificationRepositoryImpl
import dev.rafael.server.features.notificacao.db.NotificationsTable
import dev.rafael.server.features.notificacao.models.Notificacao
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.uuid.Uuid

/**
 * Notificação e token de aparelho contra Postgres REAL (V41 e V42).
 *
 * ## O arquivo que deveria ter existido antes da F.1
 *
 * O **defeito nº 1** daquela fatia foi `text("data")` sobre uma coluna `JSONB`: o Postgres não faz
 * cast implícito de `varchar` para `jsonb` em parâmetro preparado, e **nenhuma linha era gravada**.
 * Custou horas de bateria manual e só apareceu no primeiro pedido de amizade real — porque nenhum
 * teste do servidor gravava notificação.
 *
 * Um teste de integração o teria pego em segundos. É o argumento mais forte deste arquivo, e por
 * isso ele começa por aí.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificacaoIntegrationTest {

    private val notificacoes = NotificationRepositoryImpl()
    private val tokens = DeviceTokenRepositoryImpl()

    private val agora = LocalDateTime.parse("2026-09-09T12:00:00")

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun notificacao(
        dono: Uuid,
        dados: Map<String, String> = mapOf("tipo" to "PEDIDO_DE_AMIZADE"),
        quando: LocalDateTime = agora,
        lida: LocalDateTime? = null,
    ) = Notificacao(
        id = Uuid.random(),
        userId = dono,
        tipo = "PEDIDO_DE_AMIZADE",
        titulo = "Novo pedido de amizade",
        corpo = "Alguém quer ser seu amigo",
        dados = dados,
        lidaEm = lida,
        criadaEm = quando,
    )

    // ---- V42: a coluna JSONB ----

    /**
     * ⭐ O DEFEITO Nº 1 DA F.1, agora com rede.
     *
     * `text("data")` sobre `JSONB` falha no `INSERT` — não no `SELECT`, não na compilação. O
     * `JsonbTextColumnType` existe só por causa disto, e este teste é o que impede alguém de
     * "simplificar" de volta para `text`.
     */
    @Test
    fun `grava notificacao com JSONB e le de volta`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val dados = mapOf("tipo" to "PEDIDO_DE_AMIZADE", "fromUserId" to Uuid.random().toString())

        ok(notificacoes.criar(notificacao(dono, dados)))

        val lida = ok(notificacoes.doUsuario(dono, 10)).single()
        assertEquals(dados, lida.dados, "o mapa volta inteiro — se o INSERT falhasse, a lista viria vazia")
    }

    /** `data` vazio também é JSON válido (`{}`), e é o caso de todo aviso sem deep link. */
    @Test
    fun `dados vazios gravam e voltam vazios`(): Unit = runBlocking {
        val dono = Semear.usuario()

        ok(notificacoes.criar(notificacao(dono, dados = emptyMap())))

        assertTrue(ok(notificacoes.doUsuario(dono, 10)).single().dados.isEmpty())
    }

    /**
     * O conteúdo é realmente **`jsonb`** no banco, não texto.
     *
     * Sem esta asserção, o teste acima passaria com a coluna sendo `TEXT` — e a V42 declara `JSONB`
     * de propósito, para uma consulta futura por `data->>'tipo'` ser possível.
     */
    @Test
    fun `a coluna data e jsonb de verdade`() {
        val dono = Semear.usuario()
        runBlocking { ok(notificacoes.criar(notificacao(dono, mapOf("tipo" to "X")))) }

        val tipoNoBanco = BancoDeTeste.dataSource.connection.use { c ->
            c.createStatement().use { st ->
                st.executeQuery(
                    "select data_type from information_schema.columns " +
                        "where table_name = 'notifications' and column_name = 'data'",
                ).use { rs -> rs.next(); rs.getString(1) }
            }
        }

        assertEquals("jsonb", tipoNoBanco)
    }

    // ---- V42: leitura, contagem e marcação ----

    @Test
    fun `mais recente primeiro, respeitando o teto`(): Unit = runBlocking {
        val dono = Semear.usuario()
        ok(notificacoes.criar(notificacao(dono, quando = LocalDateTime.parse("2026-09-01T10:00:00"))))
        ok(notificacoes.criar(notificacao(dono, quando = LocalDateTime.parse("2026-09-05T10:00:00"))))
        ok(notificacoes.criar(notificacao(dono, quando = LocalDateTime.parse("2026-09-03T10:00:00"))))

        val lista = ok(notificacoes.doUsuario(dono, limite = 2))

        assertEquals(2, lista.size)
        assertEquals(LocalDateTime.parse("2026-09-05T10:00:00"), lista.first().criadaEm)
    }

    /**
     * ⭐ `marcarTodasComoLidas` **não mexe no que já estava lido**.
     *
     * O `WHERE read_at IS NULL` é a diferença entre "quando li isto" e "quando abri a tela pela
     * última vez". Sem ele, o carimbo de leitura de uma notificação de janeiro seria reescrito toda
     * vez que a pessoa abrisse a central.
     */
    @Test
    fun `marcar como lidas preserva o carimbo antigo`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val jaLida = LocalDateTime.parse("2026-01-01T08:00:00")
        ok(notificacoes.criar(notificacao(dono, lida = jaLida)))
        ok(notificacoes.criar(notificacao(dono)))

        val marcadas = ok(notificacoes.marcarTodasComoLidas(dono, agora))

        assertEquals(1, marcadas, "só a não lida")
        val carimbos = transaction {
            NotificationsTable.selectAll()
                .where { NotificationsTable.userId eq dono }
                .map { it[NotificationsTable.readAt] }
        }
        assertTrue(jaLida in carimbos, "o carimbo antigo sobreviveu")
        assertTrue(agora in carimbos)
    }

    @Test
    fun `nao lidas conta so as minhas`(): Unit = runBlocking {
        val eu = Semear.usuario()
        val outro = Semear.usuario()
        ok(notificacoes.criar(notificacao(eu)))
        ok(notificacoes.criar(notificacao(eu)))
        ok(notificacoes.criar(notificacao(outro)))

        assertEquals(2, ok(notificacoes.naoLidas(eu)))
    }

    /** A purga dos 6 meses corta pela data e não leva o que ainda vale. */
    @Test
    fun `purga apaga so o que passou do corte`(): Unit = runBlocking {
        val dono = Semear.usuario()
        ok(notificacoes.criar(notificacao(dono, quando = LocalDateTime.parse("2025-01-01T10:00:00"))))
        ok(notificacoes.criar(notificacao(dono, quando = agora)))

        val apagadas = ok(notificacoes.purgar(LocalDateTime.parse("2026-03-01T00:00:00")))

        assertEquals(1, apagadas)
        assertEquals(1, ok(notificacoes.doUsuario(dono, 10)).size)
    }

    // ---- V41: o token é identidade de INSTALAÇÃO ----

    /**
     * ⭐ `ON CONFLICT (token) DO UPDATE SET user_id` — o mesmo aparelho, outra conta.
     *
     * É o caso de emprestar o celular, e é **normal**, não exceção. Com uma coluna `fcm_token` em
     * `users`, quem instalasse o app num segundo aparelho pararia de receber no primeiro; com o
     * token como PK e o `upsert`, o aparelho simplesmente troca de dono.
     *
     * Nenhum fake prova isto: é uma cláusula de SQL.
     */
    @Test
    fun `o mesmo token troca de dono em vez de duplicar`(): Unit = runBlocking {
        val primeiro = Semear.usuario()
        val segundo = Semear.usuario()
        val token = "token-do-aparelho"

        ok(tokens.registrar(token, primeiro, agora))
        ok(tokens.registrar(token, segundo, agora))

        val linhas = transaction {
            DeviceTokensTable.selectAll().where { DeviceTokensTable.token eq token }
                .map { it[DeviceTokensTable.userId] }
        }
        assertEquals(listOf(segundo), linhas, "uma linha só, com o dono novo")
    }

    /**
     * O `created_at` NÃO é atualizado no re-registro.
     *
     * Ele registra quando o **aparelho** apareceu pela primeira vez; sobrescrevê-lo apagaria essa
     * informação a cada abertura do app. É a diferença entre `created_at` e `updated_at`, e só o
     * banco mostra que ela foi respeitada.
     */
    @Test
    fun `o re-registro atualiza updated_at e preserva created_at`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val token = "token-estavel"
        val nascimento = LocalDateTime.parse("2026-01-10T08:00:00")

        ok(tokens.registrar(token, dono, nascimento))
        ok(tokens.registrar(token, dono, agora))

        val linha = transaction {
            DeviceTokensTable.selectAll().where { DeviceTokensTable.token eq token }.single()
        }
        assertEquals(nascimento, linha[DeviceTokensTable.createdAt])
        assertEquals(agora, linha[DeviceTokensTable.updatedAt])
    }

    /** Uma pessoa com dois aparelhos tem duas linhas — é o motivo de o token ser a PK. */
    @Test
    fun `uma pessoa pode ter varios aparelhos`(): Unit = runBlocking {
        val dono = Semear.usuario()
        ok(tokens.registrar("celular", dono, agora))
        ok(tokens.registrar("tablet", dono, agora))

        assertEquals(2, ok(tokens.doUsuario(dono)).size)
    }

    @Test
    fun `baixa apaga so o token daquele aparelho`(): Unit = runBlocking {
        val dono = Semear.usuario()
        ok(tokens.registrar("celular", dono, agora))
        ok(tokens.registrar("tablet", dono, agora))

        ok(tokens.apagar(listOf("celular")))

        assertEquals(listOf("tablet"), ok(tokens.doUsuario(dono)))
    }

    /**
     * Conta apagada leva os tokens junto (`ON DELETE CASCADE` da V41).
     *
     * Sem isso, o aparelho de uma conta que não existe mais continuaria na tabela para sempre, e
     * cada envio ficaria mais caro carregando lixo.
     */
    @Test
    fun `apagar o usuario leva os tokens e as notificacoes`(): Unit = runBlocking {
        val dono = Semear.usuario()
        ok(tokens.registrar("some-comigo", dono, agora))
        ok(notificacoes.criar(notificacao(dono)))

        transaction {
            exec("delete from users where id = '$dono'")
        }

        assertTrue(ok(tokens.doUsuario(dono)).isEmpty())
        assertTrue(ok(notificacoes.doUsuario(dono, 10)).isEmpty())
    }
}
