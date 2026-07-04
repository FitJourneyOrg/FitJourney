package dev.rafael.features.exercise.data.repository.di

import dev.rafael.features.exercise.data.repository.ExerciseRepositoryImpl
import dev.rafael.features.exercise.data.repository.local.ExerciseLocalDataSource
import dev.rafael.features.exercise.data.repository.remote.ExerciseRemoteDataSource
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import org.koin.dsl.module

val exerciseDataModule = module {
    single { ExerciseRemoteDataSource(get()) }               // HttpClient do networkModule
    single { ExerciseLocalDataSource(get()) }                // FitJourneyDatabase do databaseModule
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
}