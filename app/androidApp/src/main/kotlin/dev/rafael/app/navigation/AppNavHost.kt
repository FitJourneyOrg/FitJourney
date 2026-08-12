package dev.rafael.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.rafael.app.screens.placeholder.EmBreveScreen
import dev.rafael.app.screens.authentication.LoginScreen
import dev.rafael.app.screens.exercise.ExerciseDetailScreen
import dev.rafael.app.screens.exercise.ExerciseLibraryScreen
import dev.rafael.app.screens.home.HomeScreen
import dev.rafael.app.screens.onboarding.QuizScreen
import dev.rafael.app.screens.program.ProgramDetailScreen
import dev.rafael.app.screens.program.ProgramGenerateScreen
import dev.rafael.app.screens.program.ProgramListScreen
import dev.rafael.app.screens.paywall.PaywallScreen
import dev.rafael.app.screens.reveal.ProgramRevealScreen
import dev.rafael.app.screens.session.WorkoutSessionScreen
import dev.rafael.app.screens.splash.SplashScreen
import dev.rafael.app.screens.workout.WorkoutDetailScreen
import dev.rafael.app.screens.workout.WorkoutFormScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()

    // A barra de abas só aparece nas telas-raiz. Detalhe, execução, quiz e paywall
    // ocupam a tela inteira (o usuário está numa tarefa, não navegando).
    val entry by nav.currentBackStackEntryAsState()
    val mostrarAbas = BottomTab.entries.any { tab ->
        entry?.destination?.hasRoute(tab.routeClass) == true
    }

    Scaffold(
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

        composable<AppRoute.Quiz> {
            QuizScreen(onCompleted = {
                // Revelação (Fase 7): Home vira a raiz e, por cima, abre a tela dedicada de
                // revelação (gera o 1º programa + CTA de assinar). Voltar/concluir cai no Home.
                nav.navigate(AppRoute.Home) {
                    popUpTo(AppRoute.Quiz) { inclusive = true }
                }
                nav.navigate(AppRoute.ProgramReveal)
            })
        }

        composable<AppRoute.ProgramReveal> {
            ProgramRevealScreen(
                onDone = { nav.popBackStack() },                     // conclui → Home (raiz do back stack)
                onOpenPaywall = { nav.navigate(AppRoute.Paywall) },  // "desbloquear" → página de assinatura
            )
        }

        composable<AppRoute.Paywall> {
            PaywallScreen(onClose = { nav.popBackStack() })   // assina/fecha → volta pra origem (que recarrega)
        }

        composable<AppRoute.Home> {
            HomeScreen(
                onOpenLibrary = { nav.navigate(AppRoute.Library) },
                onOpenWorkouts = { nav.navigate(AppRoute.Programs) },
                onStartWorkout = { id -> nav.navigate(AppRoute.WorkoutSession(id)) },
                onOpenGroups = { nav.navigate(AppRoute.Grupos) },
                onOpenProgress = { nav.navigate(AppRoute.Progresso) },
                onLoggedOut = {
                    nav.navigate(AppRoute.Login) {
                        popUpTo(AppRoute.Home) { inclusive = true }  // limpa o back stack
                        launchSingleTop = true
                    }
                },
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
                onOpenPaywall = { nav.navigate(AppRoute.Paywall) },
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
                onBack = { nav.popBackStack() },
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
                onSaved = { nav.popBackStack() },
            )
        }
        composable<AppRoute.WorkoutEdit> { entry ->
            val route: AppRoute.WorkoutEdit = entry.toRoute()
            WorkoutFormScreen(
                workoutId = route.id,
                programId = null,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
            )
        }
        composable<AppRoute.WorkoutSession> { entry ->
            val route: AppRoute.WorkoutSession = entry.toRoute()
            WorkoutSessionScreen(workoutId = route.id, onDone = { nav.popBackStack() })
        }

        // ---- Abas ainda não implementadas ----
        composable<AppRoute.Grupos> {
            EmBreveScreen("Grupos", "Treine com amigos, registre check-ins e dispute o ranking.")
        }
        composable<AppRoute.Progresso> {
            EmBreveScreen("Progresso", "Seu histórico de treinos, XP e conquistas.")
        }
        composable<AppRoute.Perfil> {
            EmBreveScreen("Perfil", "Seus dados, plano e configurações.")
        }
    }
    }
}
