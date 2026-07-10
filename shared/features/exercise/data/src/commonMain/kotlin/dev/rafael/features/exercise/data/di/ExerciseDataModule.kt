package dev.rafael.features.exercise.data.di

import dev.rafael.features.exercise.data.ExerciseRepositoryImpl
import dev.rafael.features.exercise.data.ExerciseLocalDataSource
import dev.rafael.features.exercise.data.ExerciseRemoteDataSource
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import org.koin.dsl.module

val exerciseDataModule = module {
    single { ExerciseRemoteDataSource(get()) }               // HttpClient do networkModule
    single { ExerciseLocalDataSource(get()) }                // FitJourneyDatabase do databaseModule
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get()) }
}