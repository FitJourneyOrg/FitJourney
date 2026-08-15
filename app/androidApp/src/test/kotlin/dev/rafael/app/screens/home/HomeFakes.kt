package dev.rafael.app.screens.home

import dev.rafael.app.data.session.HistoricoDeSessoes
import dev.rafael.app.data.session.SessaoLocal
import dev.rafael.app.data.stats.Stats
import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.contract.stats.UserStatsDto
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.model.AuthUser
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.profile.domain.model.Profile
import dev.rafael.features.profile.domain.repository.ProfileRepository
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.model.ProgramWorkout
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutSummary
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fakes da Home. Existem porque `SessionSync` e `StatsRepository` ganharam interfaces
 * ([HistoricoDeSessoes], [Stats]) — antes eram classes concretas com `FitJourneyDatabase` no
 * construtor, e um teste exigiria SQLite de verdade.
 *
 * Os dois usam `MutableStateFlow` para o teste poder EMITIR depois: é assim que se simula "o
 * sync gravou em background" e "a pendência subiu", que são os cenários onde a Home errou.
 */

class FakeHistorico : HistoricoDeSessoes {
    val historico = MutableStateFlow<List<SessaoLocal>>(emptyList())
    var flushes = 0
    var sincronizacoes = 0

    override fun observarHistorico(): Flow<List<SessaoLocal>> = historico
    override suspend fun record(dto: WorkoutSessionDto) = Unit
    override suspend fun flush() { flushes++ }
    override suspend fun sincronizarHistorico(forcar: Boolean): AppResult<Unit> {
        sincronizacoes++
        return AppResult.Success(Unit)
    }
}

class FakeStats : Stats {
    val valores = MutableStateFlow<UserStatsDto?>(null)

    /** Quantas vezes foi pedido sync, e quantas dessas FORÇARAM (ignorando o TTL). */
    var sincronizacoes = 0
    var forcadas = 0

    override fun observar(): Flow<UserStatsDto?> = valores
    override suspend fun sincronizar(forcar: Boolean) {
        sincronizacoes++
        if (forcar) forcadas++
    }
}

class FakeProgramas(
    /** Carimbo persistido simulado: "já baixei alguma vez neste aparelho". */
    var sincronizouNesteAparelho: Boolean = true,
    var resultadoList: AppResult<List<Program>> = AppResult.Success(emptyList()),
) : ProgramRepository {
    /** Banco LOCAL simulado — separado de `resultadoList` (a rede), como no fake de program. */
    val locais = MutableStateFlow<List<Program>>(emptyList())

    override fun observePrograms(): Flow<List<Program>> = locais
    override suspend fun jaSincronizou(): Boolean = sincronizouNesteAparelho
    override suspend fun list(): AppResult<List<Program>> = resultadoList
    override suspend fun refresh(): AppResult<List<Program>> = resultadoList
    override fun invalidate() = Unit
    override suspend fun generate(): AppResult<Program> = AppResult.Success(programa())
    override suspend fun createManual(name: String): AppResult<Program> = AppResult.Success(programa())
    override suspend fun rename(id: String, name: String): AppResult<Program> = AppResult.Success(programa())
    override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setSchedule(id: String, schedule: List<ProgramScheduleEntry>): AppResult<Program> =
        AppResult.Success(programa())
}

class FakeTreinos : WorkoutRepository {
    /** Registra quais treinos foram buscados — usado para provar que dia trancado NÃO é buscado. */
    val buscados = mutableListOf<String>()

    override suspend fun list(): AppResult<List<WorkoutSummary>> = AppResult.Success(emptyList())
    override suspend fun get(id: String): AppResult<Workout> {
        buscados += id
        return AppResult.Success(
            Workout(
                id = id, name = "T", programId = "p", exercises = emptyList(),
                createdAt = null, updatedAt = null,
            ),
        )
    }
    override suspend fun create(workout: Workout): AppResult<Workout> = AppResult.Success(workout)
    override suspend fun update(id: String, workout: Workout): AppResult<Workout> = AppResult.Success(workout)
    override suspend fun delete(id: String): AppResult<Unit> = AppResult.Success(Unit)
}

class FakeAuth : AuthRepository {
    var deslogou = false
    override suspend fun signIn(email: String, password: String): AppResult<AuthUser> =
        AppResult.Success(AuthUser("u", email))
    override suspend fun signUp(email: String, password: String): AppResult<AuthUser> =
        AppResult.Success(AuthUser("u", email))
    override suspend fun signOut(): AppResult<Unit> {
        deslogou = true
        return AppResult.Success(Unit)
    }
    override suspend fun isLoggedIn(): Boolean = true
    override suspend fun currentIdToken(): String? = "t"
    override suspend fun fetchMe(): AppResult<AuthUser> = AppResult.Success(AuthUser("u", "e"))
}

class FakePerfil : ProfileRepository {
    var limpouCache = false
    override suspend fun getProfile(): AppResult<Profile> = AppResult.Failure(dev.rafael.core.result.AppError.NotFound())
    override suspend fun saveProfile(profile: Profile): AppResult<Profile> = AppResult.Success(profile)
    override suspend fun cachedOnboardingCompleted(): Boolean? = true
    override suspend fun clearOnboardingCache() { limpouCache = true }
}

// ---- construtores de dado ----

fun programa(
    id: String = "p1",
    workouts: List<ProgramWorkout> = emptyList(),
    schedule: List<ProgramScheduleEntry> = emptyList(),
) = Program(
    id = id, name = "Programa", workouts = workouts, daysPerWeek = 3,
    split = "Full Body", rationale = "r", locked = false, schedule = schedule,
    createdAt = null, updatedAt = null,
)

fun stats(xp: Int = 0, streak: Int = 0, xpHoje: Int = 0) = UserStatsDto(
    xp = xp, level = 1, xpInLevel = xp, xpForNextLevel = 1000,
    streakDays = streak, totalSessions = 0, sessionsThisWeek = 0,
    trainedToday = xpHoje > 0, xpToday = xpHoje,
)

fun sessao(id: String = "s1", finishedAt: String, pendente: Boolean) = SessaoLocal(
    dto = WorkoutSessionDto(
        id = id, workoutId = "w1", workoutName = "Treino",
        startedAt = finishedAt, finishedAt = finishedAt,
    ),
    pendente = pendente,
)
