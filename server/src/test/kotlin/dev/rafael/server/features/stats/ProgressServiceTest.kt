package dev.rafael.server.features.stats

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.ExercicioParaAnalise
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.session.models.SetLog
import dev.rafael.server.features.session.models.WorkoutSession
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Orquestracao da analise (J.2): o portao de plano, o estado vazio da calistenia e a
 * traducao na borda. A matematica tem teste proprio no [ProgressPolicyTest].
 */
class ProgressServiceTest {

    private val agachamento = Uuid.parse("00000001-0000-0000-0000-000000000000")
    private val prancha = Uuid.parse("00000002-0000-0000-0000-000000000000")

    /** 2026-10-01, uma quinta. */
    private val relogio: Clock = object : Clock {
        override fun now() = Instant.parse("2026-10-01T10:00:00Z")
    }

    private fun user(premium: Boolean) = User(
        id = Uuid.random(), firebaseUid = "fb", email = null,
        isPremium = premium, displayName = "Atleta-teste", code = "TESTE234",
    )

    private class FakeUserRepo(private val u: User) : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) = AppResult.Success<User?>(u)
        override suspend fun findById(userId: Uuid) = AppResult.Success<User?>(u)
        override suspend fun findByCode(code: String) = AppResult.Success<User?>(u)
        override suspend fun updateCode(userId: Uuid, code: String) = AppResult.Success<User?>(u)
        override suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String, code: String) =
            AppResult.Success(u)
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = AppResult.Success<User?>(u)
        override suspend fun setActiveProgram(userId: Uuid, programId: Uuid?) = AppResult.Success<User?>(u)
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) = AppResult.Success<User?>(u)
        override suspend fun updateIdioma(userId: Uuid, idioma: Idioma) = AppResult.Success<User?>(u)
    }

    private class FakeSessions(private val historico: List<WorkoutSession>) : SessionRepository {
        override suspend fun save(session: WorkoutSession) = Unit.asSuccess()
        override suspend fun listByUser(userId: Uuid) = historico.asSuccess()
    }

    /** Espia QUAIS ids foram pedidos — e o que prova que o free nao paga consulta de musculo. */
    private class FakeCatalogo(
        private val dados: Map<Uuid, ExercicioParaAnalise>,
        private val traducoes: Map<Uuid, String> = emptyMap(),
    ) : ExerciseRepository {
        var idsPedidos: List<Uuid> = emptyList()
        override suspend fun paraAnalise(ids: List<Uuid>): AppResult<Map<Uuid, ExercicioParaAnalise>> {
            idsPedidos = ids
            return dados.filterKeys { it in ids }.asSuccess()
        }
        override suspend fun nomesTraduzidos(ids: List<Uuid>, idioma: Idioma) =
            (if (idioma == Idioma.PADRAO) emptyMap() else traducoes.filterKeys { it in ids }).asSuccess()
        override suspend fun findAll(idioma: Idioma) = error("não usado")
        override suspend fun findByCategory(category: dev.rafael.contract.exercise.ExerciseCategory, idioma: Idioma) = error("não usado")
        override suspend fun findById(id: Uuid, idioma: Idioma) = error("não usado")
        override suspend fun existsByIds(ids: List<Uuid>) = error("não usado")
    }

    private fun serie(ex: Uuid, kg: Double?, reps: Int = 10, done: Boolean = true, ordem: Int = 0) =
        SetLog(ex, ordem, 0, reps, reps, kg, done)

    private fun sessao(dia: Int, treino: String, sets: List<SetLog>) = WorkoutSession(
        id = Uuid.random(), userId = Uuid.random(), programId = null, workoutId = null,
        workoutName = treino,
        startedAt = LocalDateTime(2026, 9, dia, 18, 0),
        finishedAt = LocalDateTime(2026, 9, dia, 19, 0),
        sets = sets,
    )

    private val catalogo = mapOf(
        agachamento to ExercicioParaAnalise("Agachamento Livre com Barra", listOf(MuscleGroup.LEGS)),
        prancha to ExercicioParaAnalise("Prancha Isométrica", emptyList()),
    )

    private fun servico(
        premium: Boolean,
        historico: List<WorkoutSession>,
        catalogoFake: FakeCatalogo = FakeCatalogo(catalogo),
    ) = ProgressService(
        userService = UserService(FakeUserRepo(user(premium))),
        sessions = FakeSessions(historico),
        exercises = catalogoFake,
        clock = relogio,
    ) to catalogoFake

    private val duasSessoesIguais = listOf(
        sessao(21, "Inferior A", listOf(serie(agachamento, 60.0))),
        sessao(28, "Inferior A", listOf(serie(agachamento, 65.0))),
    )

    // ---- o portao ----------------------------------------------------------

    @Test
    fun `free nao recebe nenhum dos blocos pagos`() = runBlocking {
        val (s, _) = servico(premium = false, historico = duasSessoesIguais)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value

        assertNull(dto.weeklyLoad)
        assertNull(dto.strengthTrend)
        assertNull(dto.setsByMuscle)
        assertTrue(dto.analysisLocked)
    }

    @Test
    fun `free ainda ve o que e gratis - total, treinos e ultimo vs anterior`() = runBlocking {
        val (s, _) = servico(premium = false, historico = duasSessoesIguais)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value

        assertEquals(650.0 + 600.0, dto.totalKg)
        assertEquals(2, dto.totalSessions)
        assertNotNull(dto.lastVsPrevious)
        assertEquals(50.0, dto.lastVsPrevious!!.exercises.first().deltaKg)
    }

    @Test
    fun `free nem paga a consulta de musculo - so os ids de nome sao pedidos`() = runBlocking {
        val espiao = FakeCatalogo(catalogo)
        val (s, _) = servico(premium = false, historico = duasSessoesIguais, catalogoFake = espiao)
        s.forUser("fb", null, Idioma.PADRAO)

        // só o agachamento, porque ele aparece na comparação; nenhum id extra para volume
        assertEquals(listOf(agachamento), espiao.idsPedidos)
    }

    @Test
    fun `premium recebe os tres blocos`() = runBlocking {
        val (s, _) = servico(premium = true, historico = duasSessoesIguais)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value

        assertEquals(ProgressService.SEMANAS, dto.weeklyLoad?.size)
        assertEquals(1, dto.strengthTrend?.size)
        assertEquals(1.0 / ProgressService.SEMANAS * 2, dto.setsByMuscle?.byMuscle?.get(MuscleGroup.LEGS))
        assertTrue(!dto.analysisLocked)
    }

    // ---- calistenia --------------------------------------------------------

    @Test
    fun `historico so de peso corporal da zero sem trancar nada para premium`() = runBlocking {
        val so = listOf(sessao(28, "Core", listOf(serie(prancha, kg = null))))
        val (s, _) = servico(premium = true, historico = so)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value

        assertEquals(0.0, dto.totalKg)
        assertEquals(1, dto.totalSessions)   // o treino aconteceu, mesmo sem carga
        assertNull(dto.sinceDate)
        assertTrue(!dto.analysisLocked)      // vazio por falta de dado, nao por plano
    }

    @Test
    fun `serie nao concluida nao entra na carga`() = runBlocking {
        val h = listOf(sessao(28, "Inferior A", listOf(serie(agachamento, 60.0, done = false))))
        val (s, _) = servico(premium = true, historico = h)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value
        assertEquals(0.0, dto.totalKg)
    }

    // ---- traducao na borda -------------------------------------------------

    @Test
    fun `nome vem traduzido quando ha traducao no idioma pedido`() = runBlocking {
        val espiao = FakeCatalogo(catalogo, traducoes = mapOf(agachamento to "Barbell Back Squat"))
        val (s, _) = servico(premium = false, historico = duasSessoesIguais, catalogoFake = espiao)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.EN)).value

        assertEquals("Barbell Back Squat", dto.lastVsPrevious!!.exercises.first().name)
    }

    @Test
    fun `sem traducao o nome cai no piso do catalogo, nao vem vazio`() = runBlocking {
        val espiao = FakeCatalogo(catalogo, traducoes = emptyMap())
        val (s, _) = servico(premium = false, historico = duasSessoesIguais, catalogoFake = espiao)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.EN)).value

        assertEquals("Agachamento Livre com Barra", dto.lastVsPrevious!!.exercises.first().name)
    }

    // ---- comparacao --------------------------------------------------------

    @Test
    fun `treinos de nomes diferentes nao viram comparacao`() = runBlocking {
        val h = listOf(
            sessao(21, "Superior A", listOf(serie(agachamento, 60.0))),
            sessao(28, "Inferior A", listOf(serie(agachamento, 65.0))),
        )
        val (s, _) = servico(premium = false, historico = h)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value
        assertNull(dto.lastVsPrevious)
    }
}
