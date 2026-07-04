package dev.rafael.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.app.screens.authentication.LoginScreen
import dev.rafael.app.screens.exercise.ExerciseLibraryScreen
import dev.rafael.app.screens.onboarding.QuizScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    MaterialTheme {
        var screen by remember { mutableStateOf(AppScreen.LIBRARY) }
        when (screen) {
            AppScreen.LOGIN -> LoginScreen(onLoggedIn = { screen = AppScreen.QUIZ })
            AppScreen.QUIZ -> QuizScreen(onCompleted = { screen = AppScreen.DONE })
            AppScreen.DONE -> OnboardingDoneScreen(onOpenLibrary = { screen = AppScreen.LIBRARY })
            AppScreen.LIBRARY -> ExerciseLibraryScreen()
        }
    }
}

private enum class AppScreen { LOGIN, QUIZ, DONE, LIBRARY }

@Composable
private fun OnboardingDoneScreen(onOpenLibrary: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Onboarding completo!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenLibrary) { Text("Ver biblioteca de exercícios") }
    }
}