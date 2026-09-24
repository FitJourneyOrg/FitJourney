package dev.rafael.features.session.data.di

import dev.rafael.features.session.data.SessionApi
import dev.rafael.features.session.data.SessionSync
import dev.rafael.features.session.domain.HistoricoDeSessoes
import org.koin.dsl.module

val sessionDataModule = module {
    single { SessionApi(get()) }
    // + AgendadorDeSync (do appModule) + TokenProvider + SyncStamps
    single<HistoricoDeSessoes> { SessionSync(get(), get(), get(), get(), get()) }
}
