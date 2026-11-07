package dev.rafael.server.features.exercise.services

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.workout.Papel
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.error.FalhasFake
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.ExercicioParaProgressao
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante
import dev.rafael.server.features.exercise.engine.v3.FakeMesocicloRepository
import dev.rafael.server.features.exercise.engine.v3.FakeProgressaoRepository
import dev.rafael.server.features.exercise.engine.v3.Progressao
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorRepeticao
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.models.Profile
import dev.rafael.server.features.session.models.SetLog
import dev.rafael.server.features.session.models.WorkoutSession
import dev.rafael.server.features.workout.db.WorkoutRepository
import dev.rafael.server.features.workout.models.Workout
import dev.rafael.server.features.workout.models.WorkoutExercise
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * `ProgressaoAplicadaService` — fio novo que liga `SessionService.record()` ao motor de
 * progressão (fatia "rir-chega-e-registra", `docs/trabalho/rir-plano.md`). Fakes à mão
 * (commonTest/iOS — sem MockK); reaproveita [FakeProgressaoRepository]/[FakeMesocicloRepository]
 * (Fatia 10b) e [FalhasFake], mesmo padrão do `PromotionServiceTest`.
 */
class ProgressaoAplicadaServiceTest {

    private val userId = Uuid.random()
    private val exercicioId = Uuid.random()
    private val workoutId = Uuid.random()
    private val agora = LocalDateTime(2026, 11, 5, 12, 0)

    private class FakeProfileRepository(private val perfil: Profile?) : ProfileRepository {
        override suspend fun findByUserId(userId: Uuid): AppResult<Profile?> = perfil.asSuccess()
        override suspend fun upsert(profile: Profile): AppResult<Profile> = error("não usado")
    }

    private class FakeWorkoutRepository(private val workout: Workout?) : WorkoutRepository {
        override suspend fun create(userId: Uuid, workout: Workout, programId: Uuid, dayOfWeek: Int) = error("não usado")
        override suspend fun findAllByUser(userId: Uuid) = error("não usado")
        override suspend fun findById(userId: Uuid, workoutId: Uuid): AppResult<Workout?> = workout.asSuccess()
        override suspend fun update(userId: Uuid, workoutId: Uuid, workout: Workout) = error("não usado")
        override suspend fun delete(userId: Uuid, workoutId: Uuid) = error("não usado")
    }

    private class FakeExerciseRepository(private val catalogo: Map<Uuid, ExercicioParaProgressao>) : ExerciseRepository {
        override suspend fun findAll(idioma: Idioma) = error("não usado")
        override suspend fun findByCategory(category: ExerciseCategory, idioma: Idioma) = error("não usado")
        override suspend fun findById(id: Uuid, idioma: Idioma) = error("não usado")
        override suspend fun existsByIds(ids: List<Uuid>) = error("não usado")
        override suspend fun paraAnalise(ids: List<Uuid>) = error("não usado")
        override suspend fun nomesTraduzidos(ids: List<Uuid>, idioma: Idioma) = error("não usado")
        override suspend fun ancorasAvancadas(ids: List<Uuid>) = error("não usado")
        override suspend fun paraProgressao(ids: List<Uuid>): AppResult<Map<Uuid, ExercicioParaProgressao>> =
            catalogo.filterKeys { it in ids }.asSuccess()
    }

    private fun perfil(goal: Goal = Goal.GAIN_MUSCLE) = Profile(
        userId = userId, goal = goal, level = Level.BEGINNER, daysPerWeek = 3,
        splitPreference = null, unavailableDays = emptyList(), focusAreas = emptyList(),
        weightKg = null, heightCm = null, age = null, minorSupervised = false,
        environment = null, health = null, limitations = emptyList(), accessories = null,
        onboardingCompleted = true,
    )

    private fun workoutComVaga(papel: Papel?, orderIndex: Int = 0, exercicio: Uuid = exercicioId) = Workout(
        id = workoutId, userId = userId, name = "Treino A", programId = Uuid.random(), dayOfWeek = 1,
        exercises = listOf(
            WorkoutExercise(
                id = Uuid.random(), exerciseId = exercicio, orderIndex = orderIndex,
                restSeconds = 90, rir = null, papel = papel, sets = emptyList(),
            ),
        ),
        createdAt = agora, updatedAt = agora,
    )

    private fun sessao(sets: List<SetLog>, idDoWorkout: Uuid? = workoutId) = WorkoutSession(
        id = Uuid.random(), userId = userId, programId = null, workoutId = idDoWorkout,
        workoutName = "Treino A", startedAt = agora, finishedAt = agora, sets = sets,
    )

    private val catalogoCompostoIncremento25 =
        mapOf(exercicioId to ExercicioParaProgressao(incremento = 2.5, barraLivre = false, cargaInvertida = false, equipment = "BARBELL"))

    private fun servico(
        exerciseRepo: ExerciseRepository = FakeExerciseRepository(catalogoCompostoIncremento25),
        progressaoRepo: FakeProgressaoRepository = FakeProgressaoRepository(),
        mesocicloRepo: FakeMesocicloRepository = FakeMesocicloRepository(),
        workoutRepo: WorkoutRepository = FakeWorkoutRepository(workoutComVaga(Papel.COMPOSTO)),
        profileRepo: ProfileRepository = FakeProfileRepository(perfil()),
        falhas: FalhasFake = FalhasFake(),
    ) = ProgressaoAplicadaService(exerciseRepo, progressaoRepo, mesocicloRepo, workoutRepo, profileRepo, falhas)

    @Test
    fun `1a exposicao calibra a partir do peso logado e registra Carga MANTEM`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        val svc = servico(progressaoRepo = progressao)
        val sets = listOf(
            SetLog(exercicioId, orderIndex = 0, setIndex = 1, targetReps = 8, repsDone = 8, weightKg = 40.0, done = true, rir = 4),
            SetLog(exercicioId, orderIndex = 0, setIndex = 2, targetReps = 8, repsDone = 8, weightKg = 40.0, done = true, rir = 2),
        )

        svc.aplicar(sessao(sets))

        val estado = (progressao.buscar(userId, exercicioId) as AppResult.Success).value
        assertTrue(estado != null && estado.carga != null, "calibração grava uma carga")
        assertEquals(1, estado!!.exposicoes)
        assertEquals(1, progressao.linhas)
    }

    @Test
    fun `exposicao seguinte chama decidir e registra a Decisao real`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        // Semeia o Estado da 1ª exposição (sessão ANTERIOR, id diferente da sessão testada).
        progressao.registrar(
            userId, exercicioId, Uuid.random(),
            Progressao.Estado(carga = 40.0, exposicoes = 1),
            AncoraDoIniciante.Evento.Carga(Progressao.Decisao.MANTEM),
            mesociclo = 1,
        )
        val svc = servico(progressaoRepo = progressao)
        // GAIN_MUSCLE + COMPOSTO: faixa 6-9 reps, RIR alvo 2 — no topo, sobe.
        val sets = listOf(
            SetLog(exercicioId, orderIndex = 0, setIndex = 1, targetReps = 9, repsDone = 9, weightKg = 40.0, done = true, rir = 2),
        )

        svc.aplicar(sessao(sets))

        val estado = (progressao.buscar(userId, exercicioId) as AppResult.Success).value!!
        assertTrue(estado.carga!! > 40.0, "SOBE tem que aumentar a carga (exercício não invertido)")
        assertEquals(2, estado.exposicoes)
    }

    @Test
    fun `sem Profile pula a sessao inteira, sem lancar e sem registrar nada`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        val svc = servico(progressaoRepo = progressao, profileRepo = FakeProfileRepository(null))
        val sets = listOf(SetLog(exercicioId, orderIndex = 0, setIndex = 1, targetReps = 8, repsDone = 8, weightKg = 40.0, done = true, rir = 3))

        svc.aplicar(sessao(sets))

        assertEquals(0, progressao.linhas)
    }

    @Test
    fun `sem papel na vaga de origem pula so aquele exercicio (caminho de falha)`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        // Treino manual: WorkoutExercise existe mas sem prescrição automática -> papel null.
        val svc = servico(progressaoRepo = progressao, workoutRepo = FakeWorkoutRepository(workoutComVaga(papel = null)))
        val sets = listOf(SetLog(exercicioId, orderIndex = 0, setIndex = 1, targetReps = 8, repsDone = 8, weightKg = 40.0, done = true, rir = 3))

        svc.aplicar(sessao(sets))

        assertEquals(0, progressao.linhas)
    }

    @Test
    fun `bi-set - exercicio repetido em duas vagas, so a 1a (menor orderIndex) conta`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        val workout = Workout(
            id = workoutId, userId = userId, name = "Treino A", programId = Uuid.random(), dayOfWeek = 1,
            exercises = listOf(
                WorkoutExercise(Uuid.random(), exercicioId, orderIndex = 0, restSeconds = 90, rir = null, papel = Papel.COMPOSTO, sets = emptyList()),
                WorkoutExercise(Uuid.random(), exercicioId, orderIndex = 2, restSeconds = 90, rir = null, papel = Papel.ACESSORIO, sets = emptyList()),
            ),
            createdAt = agora, updatedAt = agora,
        )
        val svc = servico(progressaoRepo = progressao, workoutRepo = FakeWorkoutRepository(workout))
        val sets = listOf(
            // vaga 0 (1ª ocorrência): entra na progressão.
            SetLog(exercicioId, orderIndex = 0, setIndex = 1, targetReps = 8, repsDone = 8, weightKg = 40.0, done = true, rir = 4),
            // vaga 2 (bi-set, mesmo exercício de novo): ignorada pela progressão.
            SetLog(exercicioId, orderIndex = 2, setIndex = 1, targetReps = 8, repsDone = 8, weightKg = 999.0, done = true, rir = 4),
        )

        svc.aplicar(sessao(sets))

        // Se a vaga 2 (peso 999) tivesse entrado, a calibração teria partido de 999, não de 40.
        val estado = (progressao.buscar(userId, exercicioId) as AppResult.Success).value!!
        assertTrue(estado.carga!! < 100.0, "só a vaga 0 (peso 40) deveria ter entrado na calibração")
        assertEquals(1, progressao.linhas)
    }

    // ------------------------------------------------------------------------- progressao-por-repeticao

    @Test
    fun `BODYWEIGHT sem incremento usa ProgressaoPorRepeticao, sem calibracao`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        val catalogo = mapOf(exercicioId to ExercicioParaProgressao(incremento = null, barraLivre = false, cargaInvertida = false, equipment = "BODYWEIGHT"))
        val svc = servico(
            exerciseRepo = FakeExerciseRepository(catalogo),
            progressaoRepo = progressao,
            workoutRepo = FakeWorkoutRepository(workoutComVaga(Papel.ISOLAMENTO)),
        )
        // GAIN_MUSCLE + ISOLAMENTO: faixa 12-16, RIR alvo 1 -- bate o topo, sobe.
        val sets = listOf(SetLog(exercicioId, orderIndex = 0, setIndex = 1, targetReps = 16, repsDone = 16, weightKg = null, done = true, rir = 1))

        svc.aplicar(sessao(sets))

        val estado = (progressao.buscarPorRepeticao(userId, exercicioId) as AppResult.Success).value
        assertTrue(estado != null, "1ª exposicao ja e estado valido -- nao existe calibracao por reps")
        assertEquals(13, estado!!.repsMin)
        assertEquals(17, estado.repsMax)
        assertEquals(1, progressao.linhasRepeticao)
        assertEquals(0, progressao.linhas, "evento de repeticao nao deve ir pro mapa de carga")
    }

    @Test
    fun `BODYWEIGHT composto com RIR grava sinalForca -- ancora do avancado deixa de ficar sem dado`() = runBlocking {
        // fatia "ancoras-avancado-bodyweight" (2026-11-06): Barra Fixa e afins sao COMPOSTO sem
        // carga -- sinalForca e o que alimenta diagnosticoAvancadoPorAncora no lugar do umRm.
        val progressao = FakeProgressaoRepository()
        val catalogo = mapOf(exercicioId to ExercicioParaProgressao(incremento = null, barraLivre = false, cargaInvertida = false, equipment = "BODYWEIGHT"))
        val svc = servico(
            exerciseRepo = FakeExerciseRepository(catalogo),
            progressaoRepo = progressao,
            workoutRepo = FakeWorkoutRepository(workoutComVaga(Papel.COMPOSTO)),
        )
        val sets = listOf(SetLog(exercicioId, orderIndex = 0, setIndex = 1, targetReps = 6, repsDone = 6, weightKg = null, done = true, rir = 2))

        svc.aplicar(sessao(sets))

        val estado = (progressao.buscarPorRepeticao(userId, exercicioId) as AppResult.Success).value
        assertTrue(estado?.sinalForca != null, "sem sinalForca a ancora cai em DADO INSUFICIENTE de novo")
        assertEquals(8.0, estado!!.sinalForca) // 6 reps + 2 RIR
    }

    @Test
    fun `BAND sem incremento continua sem progressao automatica -- caminho de falha (nao registra nada)`() = runBlocking {
        val progressao = FakeProgressaoRepository()
        val catalogo = mapOf(exercicioId to ExercicioParaProgressao(incremento = null, barraLivre = false, cargaInvertida = false, equipment = "BAND"))
        val svc = servico(exerciseRepo = FakeExerciseRepository(catalogo), progressaoRepo = progressao)
        val sets = listOf(SetLog(exercicioId, orderIndex = 0, setIndex = 1, targetReps = 15, repsDone = 15, weightKg = null, done = true, rir = 1))

        svc.aplicar(sessao(sets))

        assertEquals(0, progressao.linhas)
    }
}
