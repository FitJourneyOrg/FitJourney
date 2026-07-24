package dev.rafael.app.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenLibrary: () -> Unit,
    onOpenWorkouts: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val loggedOut by viewModel.loggedOut.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(loggedOut) { if (loggedOut) onLoggedOut() }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Sair da conta?") },
            text = { Text("Você precisará entrar de novo para acessar seus programas.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout()
                }) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancelar") } },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Início", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenWorkouts) { Text("Meus programas") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenLibrary) { Text("Biblioteca de exercícios") }
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = { showLogoutConfirm = true }) { Text("Sair da conta") }
    }
}
