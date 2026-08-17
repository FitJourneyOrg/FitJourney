package dev.rafael.server.di

import com.google.firebase.auth.FirebaseAuth
import dev.rafael.server.auth.FirebaseTokenDecoder
import dev.rafael.server.auth.TokenDecoder
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.db.ProfileRepositoryImpl
import dev.rafael.server.features.profile.services.ProfileService
import dev.rafael.server.auth.TokenVerifier
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.db.UserRepositoryImpl
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.ExerciseRepositoryImpl
import dev.rafael.server.features.exercise.engine.DeterministicWorkoutGenerator
import dev.rafael.server.features.exercise.engine.ExercisePreFilter
import dev.rafael.server.features.exercise.engine.StructureEngine
import dev.rafael.server.features.exercise.engine.WorkoutGenerator
import dev.rafael.server.features.exercise.services.ExerciseService
import dev.rafael.server.features.program.db.ProgramRepository
import dev.rafael.server.features.program.db.ProgramRepositoryImpl
import dev.rafael.server.features.program.services.ProgramService
import dev.rafael.server.features.workout.db.WorkoutRepository
import dev.rafael.server.features.workout.db.WorkoutRepositoryImpl
import dev.rafael.server.features.workout.services.WorkoutService
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.session.db.SessionRepositoryImpl
import dev.rafael.server.features.session.services.SessionService
import dev.rafael.server.features.stats.AchievementService
import dev.rafael.server.features.stats.StatsService
import dev.rafael.server.features.stats.db.AchievementRepository
import dev.rafael.server.features.stats.db.AchievementRepositoryImpl
import org.koin.dsl.module

val appModule = module {
    single<UserRepository> { UserRepositoryImpl() }
    single { UserService(get()) }

    // Auth: FirebaseAuth.getInstance() só é válido após FirebaseAdmin.init() (roda no boot, antes).
    single { FirebaseAuth.getInstance() }
    single<TokenDecoder> { FirebaseTokenDecoder(get()) }
    single { TokenVerifier(get()) }

    // Profile (Fase 3)
    single<ProfileRepository> { ProfileRepositoryImpl() }
    single { ProfileService(get(), get()) }


    single<ExerciseRepository> { ExerciseRepositoryImpl() }
    single { ExerciseService(get(), get()) }   // repo + ExercisePreFilter (registrado abaixo)


    single<WorkoutRepository> { WorkoutRepositoryImpl() }        // <- ESTA linha sumiu
    single { WorkoutService(get(), get(), get(), get()) }


    // Motor (Fatia F) — as três peças + a interface.
    single { StructureEngine() }
    single { ExercisePreFilter() }
    single<WorkoutGenerator> { DeterministicWorkoutGenerator(get(), get()) }

    // Persistência + orquestração (G.1).
    single<ProgramRepository> { ProgramRepositoryImpl() }
    single { ProgramService(get(), get()) }

    // Sessão de treino (Fase 5 — execução).
    single<SessionRepository> { SessionRepositoryImpl() }
    single { SessionService(get(), get()) }   // userService + repo
    single { StatsService(get(), get(), get()) }   // userService + sessionRepo + programService (ARCH #16)

    // Conquistas (ARCH #16). Reusa o StatsService em vez de recalcular sessoes/streak/nivel:
    // duas contas do mesmo numero acabariam divergindo.
    single<AchievementRepository> { AchievementRepositoryImpl() }
    single { AchievementService(get(), get(), get()) }   // userService + statsService + repo
}
