package dev.rafael.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavController
import dev.rafael.app.screens.authentication.LoginScreen
import dev.rafael.app.screens.exercise.ExerciseLibraryScreen
import dev.rafael.app.screens.home.HomeScreen
import dev.rafael.app.screens.onboarding.QuizScreen
import dev.rafael.app.screens.splash.SplashScreen

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
            HomeScreen(onOpenLibrary = { nav.navigate(AppRoute.Library) })
        }

        composable<AppRoute.Library> {
            ExerciseLibraryScreen()
        }
    }
}