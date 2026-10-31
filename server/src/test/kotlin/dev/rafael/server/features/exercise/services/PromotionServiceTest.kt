package dev.rafael.server.features.exercise.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.PromotionDeclineRepository
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Evento
import dev.rafael.server.features.exercise.engine.v3.FakeMesocicloRepository
import dev.rafael.server.features.exercise.engine.v3.FakeProgressaoRepository
import dev.rafael.server.features.exercise.engine.v3.Mesociclo
import dev.rafael.server.features.exercise.engine.v3.Progressao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.models.Profile
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Service/rota da promoção de nível (iniciante→intermediário), fatia nova pós-11. Fakes à mão
 * (commonTest/iOS): reaproveita [FakeProgressaoRepository]/[FakeMesocicloRepository] da Fatia 10b
 * ([dev.rafael.server.features.exercise.engine.v3]); `ProfileRepository`/`PromotionDeclineRepository`
 * ganham fakes mínimos aqui, mesmo padrão inline do `ProfileServiceTest`.
 */
class PromotionServiceTest {

    private val userId = Uuid.random()
    private val firebaseUid = "uid-promotion-test"
    private val exercicioId = Uuid.random()

    private val userRepo = object : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) =
            User(userId, firebaseUid, null, false, "Fulano", "ABCD1234", activeProgramId = null).asSuccess()
        override suspend fun findById(userId: Uuid) = error("não deveria ser chamado")
        override suspend fun findByCode(code: String) = error("não deveria ser chamado")
        override suspend fun updateCode(userId: Uuid, code: String) = error("não deveria ser chamado")
        override suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String, code: String) =
            error("não deveria ser chamado")
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = error("não deveria ser chamado")
        override suspend fun setActiveProgram(userId: Uuid, programId: Uuid?) = error("não deveria ser chamado")
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) = error("não deveria ser chamado")
        override suspend fun updateIdioma(userId: Uuid, idioma: dev.rafael.contract.i18n.Idioma) =
            error("não deveria ser chamado")
    }

    private class FakeProfileRepository(private var perfil: Profile?) : ProfileRepository {
        override suspend fun findByUserId(userId: Uuid): AppResult<Profile?> = perfil.asSuccess()
        override suspend fun upsert(profile: Profile): AppResult<Profile> = error("não deveria ser chamado")
    }

    private class FakePromotionDeclineRepository : PromotionDeclineRepository {
        private val recusas = mutableMapOf<Pair<Uuid, Level>, Int>()
        override suspend fun mesocicloDaUltimaRecusa(usuarioId: Uuid, nivelProposto: Level): AppResult<Int?> =
            recusas[usuarioId to nivelProposto].asSuccess()
        override suspend fun registrarRecusa(usuarioId: Uuid, nivelProposto: Level, mesocicloAtual: Int): AppResult<Unit> {
            recusas[usuarioId to nivelProposto] = mesocicloAtual
            return Unit.asSuccess()
        }
    }

    /** Marca, dos ids pedidos, quais são "compostos com relevancia=1" -- sem banco de verdade. */
    private class FakeExerciseRepository(private val ancorasAvancadas: Set<Uuid> = emptySet()) : ExerciseRepository {
        override suspend fun ancorasAvancadas(ids: List<Uuid>): AppResult<List<Uuid>> =
            ids.filter { it in ancorasAvancadas }.asSuccess()
        override suspend fun findAll(idioma: dev.rafael.contract.i18n.Idioma) = error("não usado")
        override suspend fun findByCategory(category: dev.rafael.contract.exercise.ExerciseCategory, idioma: dev.rafael.contract.i18n.Idioma) = error("não usado")
        override suspend fun findById(id: Uuid, idioma: dev.rafael.contract.i18n.Idioma) = error("não usado")
        override suspend fun existsByIds(ids: List<Uuid>) = error("não usado")
        override suspend fun paraAnalise(ids: List<Uuid>) = error("não usado")
        override suspend fun nomesTraduzidos(ids: List<Uuid>, idioma: dev.rafael.contract.i18n.Idioma) = error("não usado")
    }

    private fun perfil(level: Level) = Profile(
        userId = userId, goal = Goal.GAIN_MUSCLE, level = level, daysPerWeek = 3,
        splitPreference = null, unavailableDays = emptyList(), focusAreas = emptyList(),
        weightKg = null, heightCm = null, age = null, minorSupervised = false,
        environment = null, health = null, limitations = emptyList(), accessories = null,
        onboardingCompleted = true,
    )

    /** Registra `[Deload, SOBE, MANTEM, MANTEM, MANTEM]` — estagna, sobe, reestagna: PROMOVER. */
    private suspend fun semearAncoraQuePromove(progressao: FakeProgressaoRepository, mesociclo: Int) {
        progressao.registrar(userId, exercicioId, Uuid.random(), Progressao.Estado(), Evento.Deload, mesociclo)
        progressao.registrar(userId, exercicioId, Uuid.random(), Progressao.Estado(), Evento.Carga(Decisao.SOBE), mesociclo)
        repeat(3) {
            progressao.registrar(userId, exercicioId, Uuid.random(), Progressao.Estado(), Evento.Carga(Decisao.MANTEM), mesociclo)
        }
    }

    private fun service(
        profile: Profile?,
        progressao: FakeProgressaoRepository = FakeProgressaoRepository(),
        exercicios: ExerciseRepository = FakeExerciseRepository(),
        mesociclo: FakeMesocicloRepository = FakeMesocicloRepository(),
        recusa: PromotionDeclineRepository = FakePromotionDeclineRepository(),
    ) = PromotionService(UserService(userRepo), FakeProfileRepository(profile), progressao, exercicios, mesociclo, recusa)

    /** Um evento no mesociclo [mesociclo], com o snapshot de 1RM [umRm] (V72) -- base dos testes do avançado. */
    private suspend fun semearEvento(
        progressao: FakeProgressaoRepository,
        exercicioId: Uuid,
        mesociclo: Int,
        umRm: Double,
        decisao: Decisao = Decisao.MANTEM,
    ) {
        progressao.registrar(userId, exercicioId, Uuid.random(), Progressao.Estado(umRm = umRm), Evento.Carga(decisao), mesociclo)
    }

    @Test
    fun `sem perfil nao sugere nada`() = runBlocking {
        val resultado = service(profile = null).status(firebaseUid, null)
        assertNull((resultado as AppResult.Success).value.suggestion)
    }

    @Test
    fun `nivel ADVANCED nao tenta diagnostico nenhum`() = runBlocking {
        val resultado = service(profile = perfil(Level.ADVANCED)).status(firebaseUid, null)
        assertNull((resultado as AppResult.Success).value.suggestion)
    }

    @Test
    fun `INTERMEDIATE sem mesociclo anterior e dado insuficiente, nao diagnostico negativo`() = runBlocking {
        // mesocicloAtual = 1 (sem estado salvo) -- nao ha mesociclo ANTERIOR pra comparar 1RM.
        val resultado = service(profile = perfil(Level.INTERMEDIATE)).status(firebaseUid, null)
        assertNull((resultado as AppResult.Success).value.suggestion)
    }

    @Test
    fun `INTERMEDIATE sem ancora composta de relevancia 1 nao sugere nada`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        // evento existe no mesociclo atual, mas o ExerciseRepository (padrao, sem ancoras avancadas
        // marcadas) nao reconhece este exercicio como composto-relevancia-1.
        semearEvento(progressao, exercicioId, mesociclo = 2, umRm = 100.0)
        val mesociclo = FakeMesocicloRepository()
        mesociclo.salvar(userId, Mesociclo(numero = 2))
        val resultado = service(profile = perfil(Level.INTERMEDIATE), progressao = progressao, mesociclo = mesociclo)
            .status(firebaseUid, null)
        assertNull((resultado as AppResult.Success).value.suggestion)
    }

    @Test
    fun `avancado, 1 ancora, sem ganho e poucas reducoes -- maioria de 1 e 1, sugere ADVANCED`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        semearEvento(progressao, exercicioId, mesociclo = 1, umRm = 100.0)   // inicio do mesociclo anterior
        semearEvento(progressao, exercicioId, mesociclo = 2, umRm = 100.0)   // atual: sem ganho
        val mesociclo = FakeMesocicloRepository()
        mesociclo.salvar(userId, Mesociclo(numero = 2))
        val exercicios = FakeExerciseRepository(ancorasAvancadas = setOf(exercicioId))
        val resultado = service(
            profile = perfil(Level.INTERMEDIATE), progressao = progressao, exercicios = exercicios, mesociclo = mesociclo,
        ).status(firebaseUid, null)
        assertEquals(Level.ADVANCED, (resultado as AppResult.Success).value.suggestion?.nivelProposto)
    }

    @Test
    fun `avancado, 2 ancoras, so 1 em PROMOVER -- maioria de 2 e 2, nao sugere`() = runBlocking {
        val ancora2 = Uuid.random()
        val progressao = FakeProgressaoRepository()
        semearEvento(progressao, exercicioId, mesociclo = 1, umRm = 100.0)
        semearEvento(progressao, exercicioId, mesociclo = 2, umRm = 100.0)       // sem ganho -> PROMOVER
        semearEvento(progressao, ancora2, mesociclo = 1, umRm = 50.0)
        semearEvento(progressao, ancora2, mesociclo = 2, umRm = 60.0)            // ganhou -> null, nao RECUPERACAO
        val mesociclo = FakeMesocicloRepository()
        mesociclo.salvar(userId, Mesociclo(numero = 2))
        val exercicios = FakeExerciseRepository(ancorasAvancadas = setOf(exercicioId, ancora2))
        val resultado = service(
            profile = perfil(Level.INTERMEDIATE), progressao = progressao, exercicios = exercicios, mesociclo = mesociclo,
        ).status(firebaseUid, null)
        assertNull((resultado as AppResult.Success).value.suggestion)
    }

    @Test
    fun `avancado recusado neste mesociclo nao sugere de novo`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        semearEvento(progressao, exercicioId, mesociclo = 2, umRm = 100.0)
        semearEvento(progressao, exercicioId, mesociclo = 3, umRm = 100.0)
        val mesociclo = FakeMesocicloRepository()
        mesociclo.salvar(userId, Mesociclo(numero = 3))
        val exercicios = FakeExerciseRepository(ancorasAvancadas = setOf(exercicioId))
        val recusa = FakePromotionDeclineRepository()
        recusa.registrarRecusa(userId, Level.ADVANCED, mesocicloAtual = 3)
        val resultado = service(
            profile = perfil(Level.INTERMEDIATE), progressao = progressao, exercicios = exercicios,
            mesociclo = mesociclo, recusa = recusa,
        ).status(firebaseUid, null)
        assertNull((resultado as AppResult.Success).value.suggestion)
    }

    @Test
    fun `sem evento de progressao no mesociclo atual nao sugere nada`() = runBlocking {
        val resultado = service(profile = perfil(Level.BEGINNER)).status(firebaseUid, null)
        assertNull((resultado as AppResult.Success).value.suggestion)
    }

    @Test
    fun `ancora PROMOVER sugere INTERMEDIATE`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        semearAncoraQuePromove(progressao, mesociclo = 1)
        val resultado = service(profile = perfil(Level.BEGINNER), progressao = progressao).status(firebaseUid, null)
        val sugestao = (resultado as AppResult.Success).value.suggestion
        assertEquals(Level.INTERMEDIATE, sugestao?.nivelProposto)
    }

    @Test
    fun `recusada neste mesociclo nao sugere de novo`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        semearAncoraQuePromove(progressao, mesociclo = 1)
        val recusa = FakePromotionDeclineRepository()
        recusa.registrarRecusa(userId, Level.INTERMEDIATE, mesocicloAtual = 1)
        val resultado = service(profile = perfil(Level.BEGINNER), progressao = progressao, recusa = recusa)
            .status(firebaseUid, null)
        assertNull((resultado as AppResult.Success).value.suggestion)
    }

    @Test
    fun `passado o mesociclo seguinte a recusa, sugere de novo`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        semearAncoraQuePromove(progressao, mesociclo = 3)
        val mesociclo = FakeMesocicloRepository()
        mesociclo.salvar(userId, Mesociclo(numero = 3))
        val recusa = FakePromotionDeclineRepository()
        recusa.registrarRecusa(userId, Level.INTERMEDIATE, mesocicloAtual = 1)   // recusou no 1
        val resultado = service(profile = perfil(Level.BEGINNER), progressao = progressao, mesociclo = mesociclo, recusa = recusa)
            .status(firebaseUid, null)
        assertEquals(Level.INTERMEDIATE, (resultado as AppResult.Success).value.suggestion?.nivelProposto)
    }

    @Test
    fun `decline sem sugestao pendente falha com 409 SEM_SUGESTAO_DE_PROMOCAO`() = runBlocking {
        val resultado = service(profile = perfil(Level.BEGINNER)).decline(firebaseUid, null)
        val erro = assertIs<AppResult.Failure>(resultado).error
        val conflito = assertIs<AppError.Conflict>(erro)
        assertEquals(ErrorCodes.SEM_SUGESTAO_DE_PROMOCAO, conflito.code)
    }

    @Test
    fun `decline com sugestao pendente grava a recusa no mesociclo atual`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        semearAncoraQuePromove(progressao, mesociclo = 2)
        val mesociclo = FakeMesocicloRepository()
        mesociclo.salvar(userId, Mesociclo(numero = 2))
        val recusa = FakePromotionDeclineRepository()
        val servico = service(profile = perfil(Level.BEGINNER), progressao = progressao, mesociclo = mesociclo, recusa = recusa)

        assertIs<AppResult.Success<Unit>>(servico.decline(firebaseUid, null))
        assertEquals(2, (recusa.mesocicloDaUltimaRecusa(userId, Level.INTERMEDIATE) as AppResult.Success).value)
        // Recusado agora: a próxima leitura não sugere de novo dentro do mesmo mesociclo.
        assertNull((servico.status(firebaseUid, null) as AppResult.Success).value.suggestion)
    }
}
