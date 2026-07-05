package dev.rafael.app.screens.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.rafael.app.navigation.AppRoute
import org.koin.androidx.compose.koinViewModel

@Composable
fun SplashScreen(
    onDecided: (AppRoute) -> Unit,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        (state as? SplashState.Decided)?.let { onDecided(it.destination) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()   // marca da splash (wavy) fica pra polish; MVP: indicador simples
    }
}