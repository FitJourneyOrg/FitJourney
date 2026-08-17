package dev.rafael.features.workout.data.di

import dev.rafael.features.workout.data.WorkoutDataSource
import dev.rafael.features.workout.data.WorkoutLocalDataSource
import dev.rafael.features.workout.data.WorkoutRepositoryImpl
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

val workoutDataModule: Module = module {
    single { WorkoutDataSource(client = get<HttpClient>()) }
    single { WorkoutLocalDataSource(get(), get()) }   // db + TokenProvider (cache por uid)
    // remote + local + SyncStamps + Outbox + AgendadorDeSync
    single<WorkoutRepository> { WorkoutRepositoryImpl(get(), get(), get(), get(), get()) }
}