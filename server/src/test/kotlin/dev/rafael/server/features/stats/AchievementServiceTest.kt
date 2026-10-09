package dev.rafael.server.features.stats

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.error.FalhasFake
import dev.rafael.server.features.stats.db.AchievementRepository
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * `avaliarAposSessao` (débito "notificação de desbloqueio", fechado em 2026-09-24).
 *
 * Usa [ProgressoDeStats] fake em vez de um [StatsService] real — é a porta estreita que existe
 * justamente pra isto: testar a orquestração de conquista sem arrastar `ProgramService` e o
 * motor (`WorkoutGenerator`), que não têm nada a ver com este comportamento.
 */
class AchievementServiceTest {

    private val user = User(
        id = Uuid.random(),
        firebaseUid = "fb",
        email = null,
        isPremium = false,
        displayName = "Atleta-teste",
        code = "TESTE234",
        activeProgramId = null,
    )

    private val falhas = FalhasFake()

    private inner class FakeUserRepo : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) = AppResult.Success<User?>(user)
        override suspend fun findById(userId: Uuid) = AppResult.Success<User?>(user)
        override suspend fun findByCode(code: String) = AppResult.Success<User?>(user)
        override suspend fun updateCode(userId: Uuid, code: String) = AppResult.Success<User?>(user)
        override suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String, code: String) =
            AppResult.Success(user)
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = AppResult.Success<User?>(user)
        override suspend fun setActiveProgram(userId: Uuid, programId: Uuid?) = AppResult.Success<User?>(user)
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) =
            AppResult.Success<User?>(user)
        override suspend fun updateIdioma(userId: Uuid, idioma: Idioma) =
            AppResult.Success<User?>(user)
    }

    private class FakeAchievementRepo(
        concedidasIniciais: Set<String> = emptySet(),
    ) : AchievementRepository {
        val concedidas = linkedMapOf<String, LocalDateTime>().apply {
            concedidasIniciais.forEach { put(it, LocalDateTime(2026, 1, 1, 0, 0)) }
        }
        override suspend fun listByUser(userId: Uuid) = concedidas.toMap().asSuccess()
        override suspend fun grant(userId: Uuid, achievementIds: Set<String>): AppResult<Unit> {
            achievementIds.forEach { concedidas.putIfAbsent(it, LocalDateTime(2026, 1, 1, 12, 0)) }
            return Unit.asSuccess()
        }
    }

    private fun statsQueDaProgresso(sessoes: Int, streak: Int = 0, nivel: Int = 1): ProgressoDeStats =
        ProgressoDeStats { _, _ ->
            UserStatsDto(
                xp = 0,
                level = nivel,
                xpInLevel = 0,
                xpForNextLevel = 100,
                streakDays = streak,
                totalSessions = sessoes,
                sessionsThisWeek = 0,
            ).asSuccess()
        }

    @Test
    fun `avaliarAposSessao avisa so a conquista nova`() = runBlocking {
        val avisos = mutableListOf<Pair<Uuid, String>>()
        val service = AchievementService(
            userService = UserService(FakeUserRepo()),
            stats = statsQueDaProgresso(sessoes = 1),
            falhas = falhas,
            repository = FakeAchievementRepo(),
            avisarDesbloqueio = { destinatario, id -> avisos.add(destinatario to id) },
        )

        service.avaliarAposSessao("fb", null)

        assertEquals(listOf(user.id to "PRIMEIRO_TREINO"), avisos)
    }

    @Test
    fun `avaliarAposSessao nao avisa o que ja estava concedido`() = runBlocking {
        val avisos = mutableListOf<Pair<Uuid, String>>()
        val service = AchievementService(
            userService = UserService(FakeUserRepo()),
            stats = statsQueDaProgresso(sessoes = 1),   // continua batendo só PRIMEIRO_TREINO
            falhas = falhas,
            repository = FakeAchievementRepo(concedidasIniciais = setOf("PRIMEIRO_TREINO")),
            avisarDesbloqueio = { destinatario, id -> avisos.add(destinatario to id) },
        )

        service.avaliarAposSessao("fb", null)

        assertTrue(avisos.isEmpty(), "quem já tinha a conquista não recebe aviso de novo")
    }

    @Test
    fun `forUser nunca avisa mesmo concedendo retroativo`() = runBlocking {
        // [INV] a leitura dá crédito retroativo (ver KDoc), mas não é "acabei de conquistar
        // agora" — notificar aqui inundaria quem abre a tela pela primeira vez após uma migration.
        val avisos = mutableListOf<Pair<Uuid, String>>()
        val service = AchievementService(
            userService = UserService(FakeUserRepo()),
            stats = statsQueDaProgresso(sessoes = 50),
            falhas = falhas,
            repository = FakeAchievementRepo(),
            avisarDesbloqueio = { destinatario, id -> avisos.add(destinatario to id) },
        )

        val r = service.forUser("fb", null)

        assertIs<AppResult.Success<*>>(r)
        assertTrue(avisos.isEmpty(), "GET nunca dispara notificação, só a escrita")
    }

    @Test
    fun `avaliarAposSessao engole falha do stats sem lancar e sem avisar`() = runBlocking {
        val avisos = mutableListOf<Pair<Uuid, String>>()
        val statsQueFalha = ProgressoDeStats { _, _ ->
            AppError.Validation("indisponível", code = "X").asFailure()
        }
        val service = AchievementService(
            userService = UserService(FakeUserRepo()),
            stats = statsQueFalha,
            falhas = falhas,
            repository = FakeAchievementRepo(),
            avisarDesbloqueio = { destinatario, id -> avisos.add(destinatario to id) },
        )

        service.avaliarAposSessao("fb", null)   // não pode lançar

        assertTrue(avisos.isEmpty(), "sem progresso, não há como saber o que é novo — não avisa nada")
        assertEquals(1, falhas.registradas.size, "engolir sem registrar seria falha silenciosa")
        assertTrue(falhas.registradas.single().second is AppError)
    }

    @Test
    fun `avaliarAposSessao registra a excecao do aviso e nao propaga`() = runBlocking {
        val boom = IllegalStateException("push fora")
        val service = AchievementService(
            userService = UserService(FakeUserRepo()),
            stats = statsQueDaProgresso(sessoes = 1),
            falhas = falhas,
            repository = FakeAchievementRepo(),
            avisarDesbloqueio = { _, _ -> throw boom },
        )

        service.avaliarAposSessao("fb", null)   // não pode lançar

        assertEquals(listOf<Any?>(boom), falhas.registradas.map { it.second })
    }

    @Test
    fun `avaliarAposSessao feliz nao registra falha`() = runBlocking {
        val service = AchievementService(
            userService = UserService(FakeUserRepo()),
            stats = statsQueDaProgresso(sessoes = 1),
            falhas = falhas,
            repository = FakeAchievementRepo(),
            avisarDesbloqueio = { _, _ -> },
        )

        service.avaliarAposSessao("fb", null)

        assertTrue(falhas.registradas.isEmpty())
    }
}
