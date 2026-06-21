package dev.rafael.feature.auth.data.di

import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.TokenProvider
import dev.rafael.feature.auth.data.FirebaseAuthRepository
import dev.rafael.feature.auth.data.FirebaseTokenProvider
import dev.rafael.feature.auth.data.MeDataSource
import dev.rafael.feature.auth.domain.AuthRepository
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

val authDataModule: Module = module {
    // engine HTTP por plataforma (expect/actual) — declarada no módulo de plataforma
    // o TokenProvider vai direto no Firebase (sem ciclo)
    single<TokenProvider> { FirebaseTokenProvider() }

    // HttpClient autenticado: usa a engine (get) + o TokenProvider (get)
    single<HttpClient> { HttpClientFactory.create(engine = get(), tokenProvider = get()) }

    // datasource do /me
    single { MeDataSource(client = get()) }

    // repositório (implementa a interface do domain)
    single<AuthRepository> { FirebaseAuthRepository(meDataSource = get()) }
}