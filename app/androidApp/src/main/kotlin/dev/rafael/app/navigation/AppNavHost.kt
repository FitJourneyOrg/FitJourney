package dev.rafael.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavController
import androidx.navigation.toRoute
import dev.rafael.app.screens.authentication.LoginScreen
import dev.rafael.app.screens.exercise.ExerciseLibraryScreen
import dev.rafael.app.screens.home.HomeScreen
import dev.rafael.app.screens.onboarding.QuizScreen
import dev.rafael.app.screens.splash.SplashScreen
import dev.rafael.app.screens.workout.WorkoutDetailScreen
import dev.rafael.app.screens.workout.WorkoutFormScreen
import dev.rafael.app.screens.workout.WorkoutGenerateScreen
import dev.rafael.app.screens.workout.WorkoutLibraryScreen
import dev.rafael.features.workout.presentation.state.WorkoutDetailEvent
import dev.rafael.features.workout.presentation.viewmodel.WorkoutDetailViewModel
import org.koin.compose.koinInject

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
                onOpenWorkouts = { nav.navigate(AppRoute.Workout) },
            )
        }

        composable<AppRoute.Library> {
            ExerciseLibraryScreen()
        }
        composable<AppRoute.Workout> {
            WorkoutLibraryScreen(
                onOpenWorkout = { id -> nav.navigate(AppRoute.WorkoutDetail(id)) },
                onCreateWorkout = { nav.navigate(AppRoute.WorkoutCreate) },
                onGenerateWithAI = { nav.navigate(AppRoute.WorkoutGenerate) },
            )
        }
        composable<AppRoute.WorkoutDetail> { entry ->
            val route: AppRoute.WorkoutDetail = entry.toRoute()
            WorkoutDetailScreen(
                workoutId = route.id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(AppRoute.WorkoutEdit(route.id)) },
            )
        }
        composable<AppRoute.WorkoutCreate> {
            WorkoutFormScreen(workoutId = null, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
        }
        composable<AppRoute.WorkoutEdit> { entry ->
            val route: AppRoute.WorkoutEdit = entry.toRoute()
            WorkoutFormScreen(workoutId = route.id, onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack() })
        }
        composable<AppRoute.WorkoutGenerate> {
            WorkoutGenerateScreen(
                onBack = { nav.popBackStack() },
                onGenerated = { id ->
                    // volta e abre o detalhe do treino gerado
                    nav.popBackStack()
                    nav.navigate(AppRoute.WorkoutDetail(id))
                },
                onOpenPaywall = { /* placeholder: dialog já trata dentro da tela */ },
            )
        }
    }
}