package dev.rafael.features.stats.data.di

import dev.rafael.features.stats.data.DetalheDeExercicioApi
import dev.rafael.features.stats.data.DetalheDeExercicioRepository
import dev.rafael.features.stats.data.ProgressApi
import dev.rafael.features.stats.data.ProgressRepository
import dev.rafael.features.stats.data.StatsApi
import dev.rafael.features.stats.data.StatsRepository
import dev.rafael.features.stats.domain.DetalheDeExercicio
import dev.rafael.features.stats.domain.Progresso
import dev.rafael.features.stats.domain.Stats
import org.koin.dsl.module

val statsDataModule = module {
    single { StatsApi(get()) }
    single { ProgressApi(get()) }
    single<Stats> { StatsRepository(get(), get(), get(), get()) }

    // A analise (J.2) e porta propria: so a tela de Progresso a le, e ela varre o historico
    // inteiro. Ultimo get(): o `() -> Idioma` do modulo do app — o nome do exercicio volta
    // traduzido, entao o idioma entra na chave do cache.
    single<Progresso> { ProgressRepository(get(), get(), get(), get(), get()) }

    // Detalhe de exercicio (J.5): porta separada, ONLINE-ONLY, sem cache — ver KDoc da
    // interface. Ultimo get() e o mesmo `() -> Idioma` usado acima.
    single { DetalheDeExercicioApi(get()) }
    single<DetalheDeExercicio> { DetalheDeExercicioRepository(get(), get()) }
}
