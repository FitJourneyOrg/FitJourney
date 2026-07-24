package dev.rafael.features.program.data.di

import dev.rafael.features.program.data.ProgramDataSource
import dev.rafael.features.program.data.ProgramRepositoryImpl
import dev.rafael.features.program.domain.repository.ProgramRepository
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

val programDataModule: Module = module {
    single { ProgramDataSource(client = get<HttpClient>()) }
    single<ProgramRepository> { ProgramRepositoryImpl(get()) }
}
