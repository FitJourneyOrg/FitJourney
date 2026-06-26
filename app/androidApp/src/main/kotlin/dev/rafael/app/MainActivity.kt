package dev.rafael.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.rafael.app.screens.authentication.LoginScreen
import dev.rafael.app.screens.onboarding.QuizScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@Composable
fun App() {
    MaterialTheme {
        var screen by remember { mutableStateOf(AppScreen.LOGIN) }
        when (screen) {
            AppScreen.LOGIN -> LoginScreen(onLoggedIn = { screen = AppScreen.QUIZ })
            AppScreen.QUIZ -> QuizScreen(onCompleted = { screen = AppScreen.DONE })
            AppScreen.DONE -> OnboardingDoneScreen()
        }
    }
}

private enum class AppScreen { LOGIN, QUIZ, DONE }

@Composable
private fun OnboardingDoneScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Onboarding completo! 🎉", style = MaterialTheme.typography.headlineSmall)
    }
}