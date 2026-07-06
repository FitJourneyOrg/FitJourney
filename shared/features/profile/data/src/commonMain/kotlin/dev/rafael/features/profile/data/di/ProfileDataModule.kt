package dev.rafael.features.profile.data.di

import dev.rafael.features.profile.data.ProfileDataSource
import dev.rafael.features.profile.data.ProfileLocalDataSource
import dev.rafael.features.profile.data.ProfileRepositoryImpl
import dev.rafael.features.profile.domain.repository.ProfileRepository
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

val profileDataModule: Module = module {
    // reusa o HttpClient autenticado já provido pelo authDataModule
    single { ProfileDataSource(client = get<HttpClient>()) }
    single { ProfileLocalDataSource(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get(), get()) }
}