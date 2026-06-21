package dev.rafael.core.network.di

import io.ktor.client.engine.HttpClientEngine
import dev.rafael.core.network.httpEngine
import org.koin.core.module.Module
import org.koin.dsl.module

val networkModule: Module = module {
    single<HttpClientEngine> { httpEngine() }
}