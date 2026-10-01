package dev.rafael.features.wiki.data.di

import dev.rafael.features.wiki.data.WikiLocalDataSource
import dev.rafael.features.wiki.data.WikiLocalDataSourceSqlDelight
import dev.rafael.features.wiki.data.WikiRemoteDataSource
import dev.rafael.features.wiki.data.WikiRemoteDataSourceKtor
import dev.rafael.features.wiki.data.WikiRepositoryImpl
import dev.rafael.features.wiki.domain.repository.WikiRepository
import org.koin.dsl.module

val wikiDataModule = module {
    single<WikiRemoteDataSource> { WikiRemoteDataSourceKtor(get()) }   // HttpClient do networkModule
    single<WikiLocalDataSource> { WikiLocalDataSourceSqlDelight(get()) }  // FitJourneyDatabase do databaseModule
    // 3º get(): SyncStamps (carimbo persistido). 4º: a porta estreita do idioma, uma `() -> Idioma`
    // que o módulo `app` fornece a partir do IdiomaDoAparelho — aqui em commonMain não há Context.
    single<WikiRepository> { WikiRepositoryImpl(get(), get(), get(), get()) }
}
