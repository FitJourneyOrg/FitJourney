package dev.rafael.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.rafael.app.screens.authentication.LoginScreen
import dev.rafael.app.screens.exercise.ExerciseLibraryScreen
import dev.rafael.app.screens.home.HomeScreen
import dev.rafael.app.screens.onboarding.QuizScreen
import dev.rafael.app.screens.program.ProgramDetailScreen
import dev.rafael.app.screens.program.ProgramGenerateScreen
import dev.rafael.app.screens.program.ProgramListScreen
import dev.rafael.app.screens.splash.SplashScreen
import dev.rafael.app.screens.workout.WorkoutDetailScreen
import dev.rafael.app.screens.workout.WorkoutFormScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = AppRoute.Splash) {

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
                nav.navigate(AppRoute.Home) {
                    popUpTo(AppRoute.Quiz) { inclusive = true }
                }
            })
        }

        composable<AppRoute.Home> {
            HomeScreen(
                onOpenLibrary = { nav.navigate(AppRoute.Library) },
                onOpenWorkouts = { nav.navigate(AppRoute.Programs) },
                onLoggedOut = {
                    nav.navigate(AppRoute.Login) {
                        popUpTo(AppRoute.Home) { inclusive = true }  // limpa o back stack
                        launchSingleTop = true
                    }
                },
            )
        }

        composable<AppRoute.Library> {
            ExerciseLibraryScreen()
        }

        // ---- Programas (ARCH #26 — substitui a antiga AppRoute.Workout flat) ----

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
                onOpenWorkout = { id -> nav.navigate(AppRoute.WorkoutDetail(id)) },
                onAddWorkout = { programId -> nav.navigate(AppRoute.WorkoutCreate(programId)) },
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
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(AppRoute.WorkoutEdit(route.id)) },
            )
        }
        composable<AppRoute.WorkoutCreate> { entry ->
            val route: AppRoute.WorkoutCreate = entry.toRoute()
            WorkoutFormScreen(
                workoutId = null,
                programId = route.programId,
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
    }
}
