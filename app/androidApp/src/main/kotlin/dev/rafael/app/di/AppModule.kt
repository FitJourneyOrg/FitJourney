package dev.rafael.app.di

import dev.rafael.app.data.session.SessionApi
import dev.rafael.app.data.stats.StatsApi
import dev.rafael.app.data.stats.Stats
import dev.rafael.app.data.stats.StatsRepository
import dev.rafael.app.data.sync.SyncScheduler
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.database.outbox.AgendadorDeSync
import dev.rafael.core.database.outbox.ProcessadorDeOutbox
import dev.rafael.features.program.data.ExecutorDePrograma
import dev.rafael.features.workout.data.ExecutorDeTreino
import dev.rafael.core.database.outbox.Outbox
import kotlin.time.Clock
import dev.rafael.core.network.TokenProvider
import org.koin.android.ext.koin.androidContext
import dev.rafael.app.data.session.HistoricoDeSessoes
import dev.rafael.app.data.session.SessionSync
import dev.rafael.app.screens.home.HomeViewModel
import dev.rafael.app.screens.progress.ProgressViewModel
import dev.rafael.app.screens.paywall.PaywallViewModel
import dev.rafael.app.screens.reveal.ProgramRevealViewModel
import dev.rafael.app.screens.session.WorkoutSessionViewModel
import dev.rafael.app.screens.splash.SplashViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    // Escopo de app (vive enquanto o processo vive). Usado p/ trabalho fire-and-forget
    // que NÃO pode morrer junto com a tela — ex.: pré-aquecer o catálogo na Splash, que
    // é destruída assim que decide a rota (popUpTo inclusive) e cancelaria o viewModelScope.
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Relógio do app. Registrado porque o `viewModelOf` resolve TODOS os parâmetros do
    // construtor por reflexão e IGNORA valores default — sem esta definição o boot crasha
    // com NoDefinitionFoundException, mesmo o HomeViewModel tendo `clock = Clock.System`.
    // Injetável de propósito: é o que torna "achou o treino de hoje" testável sem depender
    // do dia em que a suíte roda.
    single<Clock> { Clock.System }

    // CARIMBOS DE SYNC persistidos (ARCH #30). Registrado aqui — na raiz de composição —
    // porque é o único ponto que vê ao mesmo tempo o banco (core:database) e o TokenProvider
    // (core:network). Passar o uid como lambda evita core:database depender de core:network,
    // o que seria persistência dependendo de rede.
    single { SyncStamps(db = get(), uidAtual = { get<TokenProvider>().currentUid() }) }

    // FILA DE ESCRITAS pendentes (ARCH #30, fatia B.2). Mesmo motivo do SyncStamps para
    // morar aqui: é o único ponto que vê o banco e o TokenProvider ao mesmo tempo.
    single { Outbox(db = get(), uidAtual = { get<TokenProvider>().currentUid() }) }

    // Gatilho de envio (B.3/B.4): o repositório enfileira e chama agendar(); quem acorda o
    // processo quando a rede volta é o WorkManager, que só existe no Android — por isso os
    // repositórios (KMP) enxergam apenas a interface.
    single<AgendadorDeSync> { AgendadorDeSync { get<SyncScheduler>().agendarAgora() } }

    // Executores: um por feature, porque são eles que conhecem os DataSources. O processador
    // recebe a lista e roteia por tipo — adicionar operação nova não toca em core:database.
    //
    // A lista é montada AQUI, e não registrada como `single<List<ExecutorDeOperacao>>`: Koin
    // resolvendo tipo genérico de coleção é o tipo de wiring que falha em runtime, dentro de um
    // worker, sem stack visível. Explícito custa uma linha e falha na cara.
    single { ExecutorDeTreino(get(), get()) }
    single { ExecutorDePrograma(get()) }
    single {
        ProcessadorDeOutbox(
            outbox = get<Outbox>(),
            executores = listOf(get<ExecutorDeTreino>(), get<ExecutorDePrograma>()),
        )
    }

    // Sessão de treino (Fase 5): remote + sync offline-first (outbox local).
    single { SessionApi(get()) }
    single { StatsApi(get()) }              // XP/nível/streak (ARCH #16)
    single<Stats> { StatsRepository(get(), get(), get(), get()) }   // api + db + TokenProvider + SyncStamps
    single { SyncScheduler(androidContext()) }   // WorkManager: flush da outbox em background
    single<HistoricoDeSessoes> { SessionSync(get(), get(), get(), get(), get()) }   // + SyncStamps

    viewModelOf(::SplashViewModel)   // injeta AuthRepository + ProfileRepository + ExerciseRepository + CoroutineScope
    viewModelOf(::HomeViewModel)     // injeta AuthRepository (logout)
    viewModelOf(::ProgressViewModel) // histórico offline-first + stats
    viewModelOf(::ProgramRevealViewModel)   // injeta ProgramRepository (revelação)
    viewModelOf(::PaywallViewModel)          // injeta Billing (página de assinatura)
    viewModel { (workoutId: String) -> WorkoutSessionViewModel(workoutId, get(), get(), get()) }   // execução
}
