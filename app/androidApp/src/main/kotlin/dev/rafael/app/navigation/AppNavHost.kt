package dev.rafael.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.rafael.app.screens.placeholder.EmBreveScreen
import dev.rafael.app.screens.achievements.AchievementsScreen
import dev.rafael.app.screens.progress.ProgressScreen
import dev.rafael.core.network.SessionExpiryBus
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.program.domain.repository.ProgramRepository
import org.koin.compose.koinInject
import dev.rafael.app.screens.authentication.LoginScreen
import dev.rafael.app.screens.conta.ContaScreen
import dev.rafael.app.screens.exercise.ExerciseDetailScreen
import dev.rafael.app.screens.exercise.ExerciseLibraryScreen
import dev.rafael.app.screens.grupos.EntrarScreen
import dev.rafael.app.screens.grupos.GrupoFormScreen
import dev.rafael.app.screens.grupos.GruposScreen
import dev.rafael.app.screens.home.HomeScreen
import dev.rafael.app.screens.menu.MenuLateral
import dev.rafael.app.screens.onboarding.NomeScreen
import dev.rafael.app.screens.onboarding.QuizScreen
import dev.rafael.app.screens.perfil.PerfilScreen
import dev.rafael.app.screens.program.ProgramDetailScreen
import dev.rafael.app.screens.program.ProgramGenerateScreen
import dev.rafael.app.screens.program.ProgramListScreen
import dev.rafael.app.screens.paywall.PaywallScreen
import dev.rafael.app.screens.reveal.ProgramOfferScreen
import dev.rafael.app.screens.reveal.ProgramRevealScreen
import dev.rafael.app.screens.session.WorkoutSessionScreen
import dev.rafael.app.screens.splash.SplashScreen
import dev.rafael.app.screens.workout.WorkoutDetailScreen
import dev.rafael.app.screens.workout.WorkoutFormScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    // A barra de abas só aparece nas telas-raiz. Detalhe, execução, quiz e paywall
    // ocupam a tela inteira (o usuário está numa tarefa, não navegando).
    val entry by nav.currentBackStackEntryAsState()
    val mostrarAbas = BottomTab.entries.any { tab ->
        entry?.destination?.hasRoute(tab.routeClass) == true
    }

    // A lista de programas é cache-first. Mudanças feitas FORA da feature de programa
    // (criar/editar/excluir treino, virar premium) precisam sujar esse cache — quem faz a
    // ponte é a camada do app, porque feature nunca depende de feature (Konsist).
    val programas: ProgramRepository = koinInject()

    // SESSÃO EXPIRADA (401 que sobreviveu à renovação do token). Fica aqui, e não numa tela,
    // porque o 401 pode vir de qualquer request — inclusive do SyncWorker em background. Sem
    // isto o usuário ficava preso: "Sessão expirada" + um "Tentar de novo" que só repetia o 401.
    val sessionExpiry: SessionExpiryBus = koinInject()
    val auth: AuthRepository = koinInject()
    LaunchedEffect(Unit) {
        sessionExpiry.eventos.collect {
            auth.signOut()   // limpa a sessão local e o token cacheado do Ktor
            nav.navigate(AppRoute.Login) {
                popUpTo(0) { inclusive = true }   // não dá pra "voltar" pra uma sessão morta
                launchSingleTop = true
            }
        }
    }

    // MENU LATERAL (ARCH #34). Fica AQUI, e não dentro de uma tela, por dois motivos:
    // é global — abre em qualquer tela-raiz, sem obrigar a voltar para a Home — e precisa do
    // NavController, que é desta camada.
    //
    // `gesturesEnabled` acompanha `mostrarAbas`: em execução de treino ou formulário o usuário
    // está numa TAREFA, e um arrasto lateral que abre menu no meio dela é acidente.
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val escopo = rememberCoroutineScope()
    fun navegarDoMenu(rota: AppRoute) {
        escopo.launch { drawer.close() }
        nav.navigate(rota) { launchSingleTop = true }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = mostrarAbas,
        drawerContent = {
            MenuLateral(
                aberto = drawer.isOpen,
                onSaiu = {
                    escopo.launch { drawer.close() }
                    nav.navigate(AppRoute.Login) {
                        popUpTo(0) { inclusive = true }   // sessão encerrada não tem "voltar"
                        launchSingleTop = true
                    }
                },
                onPerfil = { navegarDoMenu(AppRoute.Perfil()) },
                onExercicios = { navegarDoMenu(AppRoute.Library) },
                onWiki = { navegarDoMenu(AppRoute.Wiki) },
                onDuvidas = { navegarDoMenu(AppRoute.Duvidas) },
                onConta = { navegarDoMenu(AppRoute.Conta) },
            )
        },
    ) {
    Scaffold(
        topBar = {
            if (mostrarAbas) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = { escopo.launch { drawer.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Abrir menu")
                        }
                    },
                )
            }
        },
        bottomBar = { if (mostrarAbas) FitJourneyBottomBar(nav) },
    ) { padding ->
    NavHost(
        navController = nav,
        startDestination = AppRoute.Splash,
        modifier = Modifier.padding(padding),
    ) {

        composable<AppRoute.Splash> {
            SplashScreen(
                onDecided = { dest ->
                    nav.navigate(dest) {
                        popUpTo(AppRoute.Splash) { inclusive = true }  // splash sai do back stack
                    }
                }
            )
        }

        composable<AppRoute.Login> {
            LoginScreen(onLoggedIn = {
                nav.navigate(AppRoute.Splash) {
                    popUpTo(AppRoute.Login) { inclusive = true }
                }
            })
        }

        composable<AppRoute.Nome> {
            NomeScreen(onPronto = {
                nav.navigate(AppRoute.Quiz) {
                    popUpTo(AppRoute.Nome) { inclusive = true }   // não dá pra "voltar" pro nome
                }
            })
        }

        composable<AppRoute.Quiz> {
            QuizScreen(onCompleted = {
                // Home vira a raiz e, por cima, abre a OFERTA do 1º programa (Fase 7).
                // Qualquer saída dali (gerar ou pular) desemboca na Home.
                nav.navigate(AppRoute.Home) {
                    popUpTo(AppRoute.Quiz) { inclusive = true }
                }
                nav.navigate(AppRoute.ProgramOffer)
            })
        }

        composable<AppRoute.ProgramOffer> {
            ProgramOfferScreen(
                // Sai da pilha ao gerar: o Reveal não deve poder voltar pra oferta (o
                // programa já foi criado — reperguntar "quer um programa?" não faz sentido).
                onGerar = {
                    nav.navigate(AppRoute.ProgramReveal) {
                        popUpTo(AppRoute.ProgramOffer) { inclusive = true }
                    }
                },
                onPular = { nav.popBackStack() },   // → Home, sem nada criado no servidor
            )
        }

        composable<AppRoute.ProgramReveal> {
            ProgramRevealScreen(
                onDone = { nav.popBackStack() },                     // conclui → Home (raiz do back stack)
                // voltarParaHome: recusar o premium aqui não pode devolver pro Reveal, que é
                // a própria tela de oferta — seria recusar e cair de volta na oferta.
                onOpenPaywall = { nav.navigate(AppRoute.Paywall(voltarParaHome = true)) },
            )
        }

        composable<AppRoute.Paywall> { entry ->
            val rota: AppRoute.Paywall = entry.toRoute()
            // virar premium muda o blur dos programas (#23) → cache de programas fica sujo
            PaywallScreen(onClose = { assinou ->
                // Só invalida se ASSINOU: virar premium destrava o blur (#23) e muda a lista.
                // Antes invalidava em todo fechamento, então até o "Agora não" custava um refetch.
                if (assinou) programas.invalidate()
                if (rota.voltarParaHome) {
                    nav.navigate(AppRoute.Home) { popUpTo(AppRoute.Home) { inclusive = true } }
                } else {
                    nav.popBackStack()
                }
            })
        }

        composable<AppRoute.Home> {
            HomeScreen(
                onOpenLibrary = { nav.navigate(AppRoute.Library) },
                onOpenWorkouts = { nav.navigate(AppRoute.Programs) },
                onGenerateWithAI = { nav.navigate(AppRoute.ProgramGenerate) },
                onStartWorkout = { id -> nav.navigate(AppRoute.WorkoutSession(id)) },
                onOpenGroups = { nav.navigate(AppRoute.Grupos) },
                onOpenProgress = { nav.navigate(AppRoute.Progresso) },
            )
        }

        composable<AppRoute.Library> {
            ExerciseLibraryScreen(
                onOpenExercise = { id -> nav.navigate(AppRoute.ExerciseDetail(id)) },
            )
        }
        composable<AppRoute.ExerciseDetail> { entry ->
            val route: AppRoute.ExerciseDetail = entry.toRoute()
            ExerciseDetailScreen(exerciseId = route.id, onBack = { nav.popBackStack() })
        }

        // ---- Programas (ARCH #27 — substitui a antiga AppRoute.Workout flat) ----

        composable<AppRoute.Programs> {
            ProgramListScreen(
                onOpenProgram = { id -> nav.navigate(AppRoute.ProgramDetail(id)) },
                onGenerateWithAI = { nav.navigate(AppRoute.ProgramGenerate) },
            )
        }
        composable<AppRoute.ProgramDetail> { entry ->
            val route: AppRoute.ProgramDetail = entry.toRoute()
            ProgramDetailScreen(
                programId = route.id,
                onBack = { nav.popBackStack() },
                onOpenWorkout = { id, editLocked -> nav.navigate(AppRoute.WorkoutDetail(id, editLocked)) },
                onAddWorkout = { programId, taken -> nav.navigate(AppRoute.WorkoutCreate(programId, taken)) },
                onOpenPaywall = { nav.navigate(AppRoute.Paywall()) },
                onGenerateNew = { nav.navigate(AppRoute.ProgramGenerate) },
                onCreateManual = { nav.popBackStack() },   // volta à lista, onde o "+" cria manual
            )
        }
        composable<AppRoute.ProgramGenerate> {
            ProgramGenerateScreen(
                onBack = { nav.popBackStack() },
                onGenerated = { id ->
                    // volta e abre o detalhe do programa gerado
                    nav.popBackStack()
                    nav.navigate(AppRoute.ProgramDetail(id))
                },
            )
        }

        // ---- Treino individual (dentro de um programa) ----

        composable<AppRoute.WorkoutDetail> { entry ->
            val route: AppRoute.WorkoutDetail = entry.toRoute()
            WorkoutDetailScreen(
                workoutId = route.id,
                editLocked = route.editLocked,
                // Invalida só se ALGO mudou de fato (trocou/removeu exercício, excluiu o
                // treino). Antes invalidava em toda volta, então só entrar e sair de um treino
                // já gerava um GET /programs — era o ruído que sobrava no log do servidor.
                onBack = { alterou ->
                    if (alterou) programas.invalidate()
                    nav.popBackStack()
                },
                onEdit = { nav.navigate(AppRoute.WorkoutEdit(route.id)) },
                onStartSession = { nav.navigate(AppRoute.WorkoutSession(route.id)) },
            )
        }
        composable<AppRoute.WorkoutCreate> { entry ->
            val route: AppRoute.WorkoutCreate = entry.toRoute()
            WorkoutFormScreen(
                workoutId = null,
                programId = route.programId,
                takenDays = route.takenDays,
                onBack = { nav.popBackStack() },
                // treino novo muda a contagem/agenda do programa → invalida o cache
                onSaved = { programas.invalidate(); nav.popBackStack() },
            )
        }
        composable<AppRoute.WorkoutEdit> { entry ->
            val route: AppRoute.WorkoutEdit = entry.toRoute()
            WorkoutFormScreen(
                workoutId = route.id,
                programId = null,
                onBack = { nav.popBackStack() },
                onSaved = { programas.invalidate(); nav.popBackStack() },
            )
        }
        composable<AppRoute.WorkoutSession> { entry ->
            val route: AppRoute.WorkoutSession = entry.toRoute()
            WorkoutSessionScreen(workoutId = route.id, onDone = { nav.popBackStack() })
        }

        // ---- Grupos (Fase 6, ARCH #33) ----

        composable<AppRoute.Grupos> {
            GruposScreen(
                onCriar = { nav.navigate(AppRoute.GrupoNovo) },
                onEntrarPorCodigo = { nav.navigate(AppRoute.GrupoEntrar()) },
            )
        }
        composable<AppRoute.GrupoNovo> {
            GrupoFormScreen(
                onBack = { nav.popBackStack() },
                // Sai da pilha ao criar: voltar para o formulário depois do grupo criado
                // convidaria a criar o mesmo desafio duas vezes.
                onCriado = { nav.popBackStack() },
            )
        }
        composable<AppRoute.GrupoEntrar> { entry ->
            val rota: AppRoute.GrupoEntrar = entry.toRoute()
            EntrarScreen(
                inviteToken = rota.inviteToken,
                onBack = { nav.popBackStack() },
                onEntrou = { nav.popBackStack() },
            )
        }

        // ---- Abas ainda não implementadas ----
        composable<AppRoute.Progresso> {
            ProgressScreen(onOpenConquistas = { nav.navigate(AppRoute.Conquistas) })
        }
        composable<AppRoute.Conquistas> { AchievementsScreen(onBack = { nav.popBackStack() }) }

        // ---- Perfil e conta (ARCH #34) ----

        composable<AppRoute.Perfil> { entry ->
            val rota: AppRoute.Perfil = entry.toRoute()
            PerfilScreen(
                onBack = { nav.popBackStack() },
                onEditar = { nav.navigate(AppRoute.Conta) },
                onVerConquistas = { nav.navigate(AppRoute.Conquistas) },
                // Na A.0 só existe o próprio perfil; a A.1 compara com o uid da sessão.
                souEu = rota.userId == null,
            )
        }
        composable<AppRoute.Conta> {
            ContaScreen(
                onBack = { nav.popBackStack() },
                onSaiu = {
                    // Fecha o menu junto: sair com o drawer aberto deixava um painel sem dono
                    // por cima da tela de login.
                    escopo.launch { drawer.close() }
                    nav.navigate(AppRoute.Login) {
                        popUpTo(0) { inclusive = true }   // não dá pra "voltar" pra sessão encerrada
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<AppRoute.Wiki> {
            EmBreveScreen("Wiki fitness", "Conteúdo sobre treino, técnica e recuperação. Chega na Fase 8.")
        }
        composable<AppRoute.Duvidas> {
            EmBreveScreen("Dúvidas frequentes", "As perguntas mais comuns sobre o app e os treinos.")
        }
    }
    }
    }
}
