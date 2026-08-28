package dev.rafael.app.di

import dev.rafael.app.data.session.SessionApi
import dev.rafael.app.data.stats.StatsApi
import dev.rafael.app.data.stats.Stats
import dev.rafael.app.data.achievements.Achievements
import dev.rafael.app.data.achievements.AchievementsApi
import dev.rafael.app.data.achievements.AchievementsRepository
import dev.rafael.app.data.me.Me
import dev.rafael.app.data.me.MeApi
import dev.rafael.app.data.me.MeRepository
import dev.rafael.app.data.groups.Groups
import dev.rafael.app.data.groups.GroupsApi
import dev.rafael.app.data.groups.GroupsRepository
import dev.rafael.app.data.sessao.SairDaConta
import dev.rafael.app.screens.conta.ContaViewModel
import dev.rafael.app.screens.grupos.EntrarViewModel
import dev.rafael.app.screens.grupos.GrupoDetalheViewModel
import dev.rafael.app.screens.grupos.GrupoFormViewModel
import dev.rafael.app.screens.grupos.GruposViewModel
import dev.rafael.app.screens.onboarding.NomeViewModel
import dev.rafael.app.screens.menu.MenuViewModel
import dev.rafael.app.screens.amigos.AmigosViewModel
import dev.rafael.app.screens.amigos.BloqueadosViewModel
import dev.rafael.app.screens.perfil.PerfilPublicoViewModel
import dev.rafael.app.screens.perfil.PerfilViewModel
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
import dev.rafael.app.screens.achievements.AchievementsViewModel
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

    // Conquistas (ARCH #16) — mesmo desenho do Stats: cache local + sync de fundo.
    single { AchievementsApi(get()) }
    single<Achievements> { AchievementsRepository(get(), get(), get(), get()) }

    // Usuário: nome e plano (V35, ARCH #33/#34). Reusa o MeDataSource de auth:data em vez de
    // repetir as rotas de /me — quem é dono delas continua sendo ele.
    single { MeApi(get()) }
    single<Me> { MeRepository(get(), get(), get(), get()) }

    // Grupos (Fase 6, ARCH #33). Leitura cache-first; escrita online-only, porque o código e o
    // estado do grupo são do servidor — um grupo otimista local não teria nem um nem outro.
    // Sair da conta: UM dono da sequência (limpar onboarding ANTES do signOut), duas portas —
    // o rodapé do menu lateral e a tela de conta.
    single { SairDaConta(get(), get()) }

    single { GroupsApi(get()) }
    single<Groups> { GroupsRepository(get(), get(), get(), get()) }

    // Check-in (fatia B). Sem SQLDelight e sem outbox: online-only (10.1) — ver `CheckIns`.
    single { dev.rafael.app.data.checkin.CheckInsApi(get()) }
    single<dev.rafael.app.data.checkin.CheckIns> { dev.rafael.app.data.checkin.CheckInsRepository(get()) }

    // Perfil público de terceiro (C.1): online-only, sem repositório de cache. Ver KDoc.
    single<dev.rafael.app.data.perfil.PerfisPublicos> { dev.rafael.app.data.perfil.PerfisPublicosApi(get()) }

    // Amizades (#35): online-only, sem repositório de cache. Ver KDoc de `Amizades`.
    single<dev.rafael.app.data.amizades.Amizades> { dev.rafael.app.data.amizades.AmizadesApi(get()) }
    single { dev.rafael.app.data.checkin.Localizador(androidContext()) }
    viewModel { dev.rafael.app.screens.checkin.CheckInViewModel(get(), get(), get()) }
    single { SyncScheduler(androidContext()) }   // WorkManager: flush da outbox em background
    single<HistoricoDeSessoes> { SessionSync(get(), get(), get(), get(), get()) }   // + SyncStamps

    viewModelOf(::SplashViewModel)   // injeta AuthRepository + ProfileRepository + ExerciseRepository + CoroutineScope
    viewModelOf(::HomeViewModel)     // treino de hoje (não conhece mais sessão — ARCH #34)
    viewModelOf(::MenuViewModel)     // cabeçalho do menu lateral: nome + nível, do cache
    viewModelOf(::PerfilViewModel)   // perfil: nome, nível, conquistas
    viewModelOf(::PerfilPublicoViewModel)   // perfil de OUTRA pessoa (C.1)
    viewModelOf(::AmigosViewModel)          // amigos e pedidos (#35)
    viewModelOf(::BloqueadosViewModel)      // Conta > Bloqueados (#35)
    viewModelOf(::ContaViewModel)    // conta: renomear (PATCH /me) e sair
    viewModelOf(::NomeViewModel)     // 1º passo do onboarding: confirmar o nome (1-A.2)
    viewModelOf(::GruposViewModel)   // aba Grupos: lista cache-first
    viewModelOf(::GrupoFormViewModel)   // criar desafio
    viewModelOf(::EntrarViewModel)      // entrar por código ou link, com preview
    // Detalhe do grupo: gerência de membros (A.4) + FEED de check-ins (B.5). O `viewModelOf`
    // resolveu o `CheckIns` novo sem tocar aqui — é o que essa forma compra.
    viewModelOf(::GrupoDetalheViewModel)
    viewModelOf(::ProgressViewModel) // histórico offline-first + stats
    viewModelOf(::AchievementsViewModel)   // conquistas offline-first
    viewModelOf(::ProgramRevealViewModel)   // injeta ProgramRepository (revelação)
    viewModelOf(::PaywallViewModel)          // injeta Billing (página de assinatura)
    viewModel { (workoutId: String) -> WorkoutSessionViewModel(workoutId, get(), get(), get()) }   // execução
}
