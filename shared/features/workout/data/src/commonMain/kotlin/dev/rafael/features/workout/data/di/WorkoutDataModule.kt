package dev.rafael.features.workout.data.di

import dev.rafael.features.workout.data.WorkoutDataSource
import dev.rafael.features.workout.data.WorkoutRepositoryImpl
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

val workoutDataModule: Module = module {
    single { WorkoutDataSource(client = get<HttpClient>()) }
    single<WorkoutRepository> { WorkoutRepositoryImpl(get()) }
}