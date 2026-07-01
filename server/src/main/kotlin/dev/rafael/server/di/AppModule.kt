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
import dev.rafael.server.features.exercise.services.ExerciseService
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
    single { ExerciseService(get()) }
}