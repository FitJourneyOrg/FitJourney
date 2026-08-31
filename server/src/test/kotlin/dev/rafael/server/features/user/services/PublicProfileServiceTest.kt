package dev.rafael.server.features.user.services

import dev.rafael.contract.friendship.FriendStatus
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.stats.AchievementPolicy
import dev.rafael.server.features.stats.StatsService
import dev.rafael.server.features.stats.db.AchievementRepository
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Perfil público de terceiro (C.1, #34 + emenda 9.3-A).
 *
 * O teste que mais importa aqui é o [json_do_perfil_nao_carrega_nada_privado]: os outros checam
 * comportamento, aquele checa a FRONTEIRA. Se um dia alguém trocar o `PublicProfileDto` pelo
 * `UserStatsDto` "para reaproveitar", é ele que acusa.
 */
class PublicProfileServiceTest {

    private val eu = User(Uuid.random(), "fb-eu", "eu@x.com", false, "Eu", "AAAA2222")
    private val outro = User(Uuid.random(), "fb-outro", "outro@x.com", true, "Fulano", "BBBB3333")

    private inner class FakeUsers : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String): AppResult<User?> =
            listOf(eu, outro).firstOrNull { it.firebaseUid == firebaseUid }.asSuccess()

        override suspend fun findById(userId: Uuid): AppResult<User?> =
            listOf(eu, outro).firstOrNull { it.id == userId }.asSuccess()

        override suspend fun create(
            id: Uuid,
            firebaseUid: String,
            email: String?,
            displayName: String,
            code: String,
        ) = error("não deveria ser chamado")

        override suspend fun findByCode(code: String): AppResult<User?> =
            listOf(eu, outro).firstOrNull { it.code == code }.asSuccess()

        override suspend fun updateCode(userId: Uuid, code: String) = error("não deveria ser chamado")

        override suspend fun setPremium(userId: Uuid, premium: Boolean) = error("não deveria ser chamado")
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) =
            error("não deveria ser chamado")
    }

    private class FakeConquistas(private val concedidas: Map<String, LocalDateTime>) : AchievementRepository {
        var concedeuAlgo = false
        override suspend fun listByUser(userId: Uuid) = concedidas.asSuccess()
        override suspend fun grant(userId: Uuid, achievementIds: Set<String>): AppResult<Unit> {
            concedeuAlgo = true
            return Unit.asSuccess()
        }
    }

    /**
     * Uma linha, e é o ponto da porta estreita: sem ela este `servico()` teria que montar um
     * `StatsService` com `ProgramService` com `WorkoutGenerator` — motor de treino inteiro, para
     * perguntar o XP de alguém.
     */
    private fun servico(
        conquistas: FakeConquistas = FakeConquistas(emptyMap()),
        gamificacao: StatsService.Gamificacao = StatsService.Gamificacao(xp = 0, nivel = 1),
        relacao: PublicProfileService.Relacao =
            PublicProfileService.Relacao(FriendStatus.NENHUMA, meBloqueou = false),
    ): PublicProfileService {
        val users = FakeUsers()
        return PublicProfileService(
            userService = UserService(users),
            users = users,
            gamificacaoDe = { gamificacao.asSuccess() },
            achievements = conquistas,
            relacaoCom = { _, _ -> relacao.asSuccess() },
        )
    }

    @Test
    fun `perfil de terceiro traz nome, nivel e XP`(): Unit = runBlocking {
        val r = servico(gamificacao = StatsService.Gamificacao(xp = 1200, nivel = 4))
            .porId("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(r is AppResult.Success)
        assertEquals("Fulano", r.value.displayName)
        assertEquals(4, r.value.level, "nível vem da gamificação, sem passar pelo streak")
        assertEquals(1200, r.value.xp)
    }

    @Test
    fun `me e false no perfil dos outros e true no meu`(): Unit = runBlocking {
        val doOutro = servico().porId("fb-eu", "eu@x.com", outro.id.toString())
        val meu = servico().porId("fb-eu", "eu@x.com", eu.id.toString())

        assertTrue(doOutro is AppResult.Success && !doOutro.value.me)
        assertTrue(meu is AppResult.Success && meu.value.me, "o servidor resolve, a tela não compara ids")
    }

    @Test
    fun `so as conquistas desbloqueadas aparecem, com titulo e descricao`(): Unit = runBlocking {
        val quando = LocalDateTime(2026, 8, 20, 10, 0)
        val conquistas = FakeConquistas(mapOf(AchievementPolicy.Conquista.PRIMEIRO_TREINO.name to quando))

        val r = servico(conquistas).porId("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(r is AppResult.Success)
        assertEquals(1, r.value.achievements.size, "as 8 bloqueadas NÃO entram — progresso é privado")
        val m = r.value.achievements.single()
        assertEquals("PRIMEIRO_TREINO", m.id)
        assertEquals("Começou", m.title)
        assertEquals(quando.toString(), m.unlockedAt)
    }

    @Test
    fun `abrir o perfil de alguem NAO concede conquista a ele`(): Unit = runBlocking {
        val conquistas = FakeConquistas(emptyMap())

        servico(conquistas).porId("fb-eu", "eu@x.com", outro.id.toString())

        assertFalse(
            conquistas.concedeuAlgo,
            "leitura de terceiro é caminho de LEITURA: ninguém ganha medalha pela curiosidade alheia",
        )
    }

    @Test
    fun `id inexistente e id malformado respondem os dois 404`(): Unit = runBlocking {
        val fantasma = servico().porId("fb-eu", "eu@x.com", Uuid.random().toString())
        val lixo = servico().porId("fb-eu", "eu@x.com", "nao-e-um-uuid")

        assertTrue(fantasma is AppResult.Failure && fantasma.error is AppError.NotFound)
        assertTrue(
            lixo is AppResult.Failure && lixo.error is AppError.NotFound,
            "id malformado NÃO vira 400: 404 nos dois casos não deixa sondar quem existe",
        )
    }

    @Test
    fun `conquista removida do codigo e ignorada em silencio`(): Unit = runBlocking {
        val conquistas = FakeConquistas(mapOf("CONQUISTA_QUE_NAO_EXISTE_MAIS" to LocalDateTime(2026, 1, 1, 0, 0)))

        val r = servico(conquistas).porId("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(r is AppResult.Success, "linha órfã não derruba o perfil")
        assertTrue(r.value.achievements.isEmpty())
    }

    /**
     * [INVARIANTE] Quem foi BLOQUEADO recebe perfil indisponível (emenda 35.6).
     *
     * O nome verdadeiro **não atravessa o fio**: a tela não recebe dado que precise lembrar de
     * esconder. É a mesma fronteira da 9.3-A aplicada a outro eixo.
     */
    @Test
    fun `quem foi bloqueado recebe perfil indisponivel e sem o nome`(): Unit = runBlocking {
        val bloqueado = PublicProfileService.Relacao(FriendStatus.NENHUMA, meBloqueou = true)

        val r = servico(relacao = bloqueado).porId("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(r is AppResult.Success)
        assertFalse(r.value.available)
        assertEquals("", r.value.displayName, "o nome verdadeiro NÃO sai do servidor")
        assertEquals(0, r.value.level, "0 é sentinela — o nível real começa em 1 (#16)")
        assertEquals(0, r.value.xp)
        assertTrue(r.value.achievements.isEmpty())

        val json = Json.encodeToString(r.value)
        assertFalse(json.contains("Fulano"), "o nome vazou no JSON: $json")
    }

    /**
     * O perfil indisponível é **idêntico** ao de uma conta excluída — e é isso que impede o
     * bloqueio de virar recado. Se a resposta fosse diferente, quem foi bloqueado descobriria.
     */
    @Test
    fun `o perfil indisponivel nao diz que houve bloqueio`(): Unit = runBlocking {
        val r = servico(relacao = PublicProfileService.Relacao(FriendStatus.NENHUMA, meBloqueou = true))
            .porId("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(r is AppResult.Success)
        val json = Json.encodeToString(r.value).lowercase()
        listOf("bloque", "blocked", "banid").forEach {
            assertFalse(json.contains(it), "o JSON revelou o bloqueio com `$it`: $json")
        }
    }

    @Test
    fun `quem BLOQUEOU continua vendo o perfil, com o botao de desbloquear`(): Unit = runBlocking {
        val euBloqueei = PublicProfileService.Relacao(FriendStatus.BLOQUEADO_POR_MIM, meBloqueou = false)

        val r = servico(relacao = euBloqueei).porId("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(r is AppResult.Success)
        assertTrue(r.value.available, "assimétrico: sem isso a lista de bloqueados vira adivinhação")
        assertEquals("Fulano", r.value.displayName)
        assertEquals(FriendStatus.BLOQUEADO_POR_MIM, r.value.friendStatus)
    }

    /**
     * [INVARIANTE] A fronteira da 9.3-A, verificada no JSON e não na intenção.
     *
     * Olho o texto serializado de propósito. Um teste que checasse campo por campo passaria a
     * mentir no dia em que alguém acrescentasse um campo novo ao DTO — este falha.
     */
    @Test
    fun `json do perfil nao carrega nada privado`(): Unit = runBlocking {
        val r = servico().porId("fb-eu", "eu@x.com", outro.id.toString())
        assertTrue(r is AppResult.Success)

        val json = Json.encodeToString(r.value)

        listOf(
            "email", "outro@x.com",           // identidade de contato
            "isPremium", "premium",           // situação comercial (#25)
            "firebaseUid",                    // credencial
            "streak", "totalSessions", "sessionsThisWeek", "trainedToday", // histórico de treino
            "weight", "height", "age", "goal", "screening",                // ficha e triagem
            "current", "target",              // progresso das conquistas
        ).forEach { proibido ->
            assertFalse(
                json.contains(proibido, ignoreCase = true),
                "o perfil público vazou `$proibido` — a 9.3-A abriu nome, nível, XP e conquistas, e NADA além. JSON: $json",
            )
        }
    }
}
