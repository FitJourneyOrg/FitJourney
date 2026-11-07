package dev.rafael.server.features.exercise.services

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.workout.Papel
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.contract.workout.WorkoutExerciseDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.error.FalhasFake
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.ExercicioParaProgressao
import dev.rafael.server.features.exercise.engine.v3.FakeProgressaoRepository
import dev.rafael.server.features.exercise.engine.v3.Progressao
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorRepeticao
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.models.Profile
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Fatia "sugestao-de-progressao" (2026-11-06). Tier 2: o pós-processamento do `GET /workouts/{id}`
 * com fakes — mesmo padrão do `ProgressaoAplicadaServiceTest`. A prova central aqui é a de falha
 * silenciosa (KDoc do serviço): nenhum cenário deste arquivo lança nem devolve `AppResult.Failure`.
 */
class SugestaoDeProgressaoServiceTest {

    private val userId = Uuid.random()
    private val exercicioId = Uuid.random()

    private class FakeProfileRepository(private val perfil: Profile?, private val falhar: Boolean = false) : ProfileRepository {
        override suspend fun findByUserId(userId: Uuid): AppResult<Profile?> =
            if (falhar) AppError.Unexpected("falhou de propósito").asFailure() else perfil.asSuccess()
        override suspend fun upsert(profile: Profile): AppResult<Profile> = error("não usado")
    }

    private class FakeExerciseRepository(
        private val catalogo: Map<Uuid, ExercicioParaProgressao>,
        private val falhar: Boolean = false,
    ) : ExerciseRepository {
        override suspend fun findAll(idioma: Idioma) = error("não usado")
        override suspend fun findByCategory(category: ExerciseCategory, idioma: Idioma) = error("não usado")
        override suspend fun findById(id: Uuid, idioma: Idioma) = error("não usado")
        override suspend fun existsByIds(ids: List<Uuid>) = error("não usado")
        override suspend fun paraAnalise(ids: List<Uuid>) = error("não usado")
        override suspend fun nomesTraduzidos(ids: List<Uuid>, idioma: Idioma) = error("não usado")
        override suspend fun ancorasAvancadas(ids: List<Uuid>) = error("não usado")
        override suspend fun paraProgressao(ids: List<Uuid>): AppResult<Map<Uuid, ExercicioParaProgressao>> =
            if (falhar) AppError.Unexpected("falhou de propósito").asFailure() else catalogo.filterKeys { it in ids }.asSuccess()
    }

    private fun perfil(goal: Goal = Goal.GAIN_MUSCLE) = Profile(
        userId = userId, goal = goal, level = Level.BEGINNER, daysPerWeek = 3,
        splitPreference = null, unavailableDays = emptyList(), focusAreas = emptyList(),
        weightKg = null, heightCm = null, age = null, minorSupervised = false,
        environment = null, health = null, limitations = emptyList(), accessories = null,
        onboardingCompleted = true,
    )

    private fun treino(papel: Papel?, exercicio: Uuid = exercicioId) = WorkoutDto(
        id = Uuid.random().toString(), name = "Treino A",
        exercises = listOf(WorkoutExerciseDto(exerciseId = exercicio.toString(), orderIndex = 0, papel = papel, sets = emptyList())),
    )

    private val catalogoCarga =
        mapOf(exercicioId to ExercicioParaProgressao(incremento = 2.5, barraLivre = false, cargaInvertida = false, equipment = "BARBELL"))
    private val catalogoRepeticao =
        mapOf(exercicioId to ExercicioParaProgressao(incremento = null, barraLivre = false, cargaInvertida = false, equipment = "BODYWEIGHT"))
    private val catalogoNivel =
        mapOf(exercicioId to ExercicioParaProgressao(incremento = null, barraLivre = false, cargaInvertida = false, equipment = "BAND"))

    private fun servico(
        exerciseRepo: ExerciseRepository = FakeExerciseRepository(catalogoCarga),
        progressaoRepo: FakeProgressaoRepository = FakeProgressaoRepository(),
        profileRepo: ProfileRepository = FakeProfileRepository(perfil()),
        falhas: FalhasFake = FalhasFake(),
    ) = SugestaoDeProgressaoService(exerciseRepo, progressaoRepo, profileRepo, falhas)

    @Test
    fun `eixo carga com estado calibrado preenche cargaSugeridaKg, reps ficam nulos`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        progressao.salvar(userId, exercicioId, Progressao.Estado(carga = 60.0, exposicoes = 3))
        val svc = servico(progressaoRepo = progressao)

        val resultado = svc.completarSugestoes(userId, treino(Papel.COMPOSTO))

        val exercicio = resultado.exercises.single()
        assertEquals(60.0, exercicio.cargaSugeridaKg)
        assertNull(exercicio.repsMinSugerido)
        assertNull(exercicio.repsMaxSugerido)
    }

    @Test
    fun `eixo repeticao sem estado -- 1a exposicao -- sugere a faixa original da prescricao`() = runBlocking {
        val svc = servico(exerciseRepo = FakeExerciseRepository(catalogoRepeticao))

        // GAIN_MUSCLE + ISOLAMENTO: PrescricaoV3.de -> Faixa(12, 16, 60, 1).
        val resultado = svc.completarSugestoes(userId, treino(Papel.ISOLAMENTO))

        val exercicio = resultado.exercises.single()
        assertNull(exercicio.cargaSugeridaKg)
        assertEquals(12, exercicio.repsMinSugerido)
        assertEquals(16, exercicio.repsMaxSugerido)
    }

    // ---- fatia "mostrar sugestão de nível" (2026-11-07) ----

    @Test
    fun `eixo nivel (BAND) sugere a faixa original, sem nunca consultar ProgressaoRepository`() = runBlocking {
        val progressao = FakeProgressaoRepository().apply { falhar = true }   // se for consultado, o teste falha
        val svc = servico(exerciseRepo = FakeExerciseRepository(catalogoNivel), progressaoRepo = progressao)

        // GAIN_MUSCLE + ACESSORIO: PrescricaoV3.de -> Faixa(8, 12, 90, 1).
        val resultado = svc.completarSugestoes(userId, treino(Papel.ACESSORIO))

        val exercicio = resultado.exercises.single()
        assertNull(exercicio.cargaSugeridaKg)
        assertEquals(8, exercicio.repsMinSugerido)
        assertEquals(12, exercicio.repsMaxSugerido)
    }

    @Test
    fun `papel nulo -- treino manual -- exercicio fica sem sugestao (caminho de falha)`() = runBlocking {
        val svc = servico()

        val resultado = svc.completarSugestoes(userId, treino(papel = null))

        val exercicio = resultado.exercises.single()
        assertNull(exercicio.cargaSugeridaKg)
        assertNull(exercicio.repsMinSugerido)
        assertNull(exercicio.repsMaxSugerido)
    }

    @Test
    fun `sem Profile -- nunca completou o questionario -- treino volta intacto, sem lancar`() = runBlocking {
        val svc = servico(profileRepo = FakeProfileRepository(null))

        val resultado = svc.completarSugestoes(userId, treino(Papel.COMPOSTO))

        assertNull(resultado.exercises.single().cargaSugeridaKg)
    }

    @Test
    fun `erro de banco no catalogo -- caminho de falha -- loga e devolve o treino intacto, sem lancar`() = runBlocking {
        val falhas = FalhasFake()
        val svc = servico(exerciseRepo = FakeExerciseRepository(catalogoCarga, falhar = true), falhas = falhas)

        val resultado = svc.completarSugestoes(userId, treino(Papel.COMPOSTO))

        assertNull(resultado.exercises.single().cargaSugeridaKg)
        assertEquals(1, falhas.registradas.size)
    }

    @Test
    fun `exercicio sem linha no catalogo -- nunca deveria acontecer -- fica sem sugestao`() = runBlocking {
        val svc = servico(exerciseRepo = FakeExerciseRepository(emptyMap()))

        val resultado = svc.completarSugestoes(userId, treino(Papel.COMPOSTO))

        assertNull(resultado.exercises.single().cargaSugeridaKg)
    }

    @Test
    fun `erro de banco no estado de carga -- exercicio fica sem sugestao, resto do fluxo intacto`() = runBlocking {
        val falhas = FalhasFake()
        val progressao = FakeProgressaoRepository().apply { falhar = true }
        val svc = servico(progressaoRepo = progressao, falhas = falhas)

        val resultado = svc.completarSugestoes(userId, treino(Papel.COMPOSTO))

        assertNull(resultado.exercises.single().cargaSugeridaKg)
        assertEquals(1, falhas.registradas.size)
    }

    @Test
    fun `treino sem exercicios -- lista vazia -- devolve o treino sem nem consultar repositorio`() = runBlocking {
        val svc = servico()
        val vazio = WorkoutDto(id = Uuid.random().toString(), name = "Treino vazio", exercises = emptyList())

        val resultado = svc.completarSugestoes(userId, vazio)

        assertEquals(emptyList(), resultado.exercises)
    }
}
