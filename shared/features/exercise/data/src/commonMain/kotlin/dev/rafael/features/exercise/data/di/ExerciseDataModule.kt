package dev.rafael.features.exercise.data.di

import dev.rafael.features.exercise.data.ExerciseRepositoryImpl
import dev.rafael.features.exercise.data.ExerciseLocalDataSource
import dev.rafael.features.exercise.data.ExerciseRemoteDataSource
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import org.koin.dsl.module

val exerciseDataModule = module {
    single { ExerciseRemoteDataSource(get()) }               // HttpClient do networkModule
    single { ExerciseLocalDataSource(get()) }                // FitJourneyDatabase do databaseModule
    // + SyncStamps: carimbo de sync persistido (TTL sobrevive ao fechar o app)
    // O 4º `get()` é a porta estreita do idioma: uma `() -> Idioma` que o módulo `app` fornece a
    // partir do `IdiomaDoAparelho`. Aqui em commonMain não há `Context` — e não deve haver.
    single<ExerciseRepository> { ExerciseRepositoryImpl(get(), get(), get(), get()) }
}