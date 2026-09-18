package dev.rafael.app.screens.authentication

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rafael.app.R
import dev.rafael.app.ui.ErroContexto
import dev.rafael.app.ui.ErroInline
import dev.rafael.features.auth.presentation.state.LoginEvent
import dev.rafael.features.auth.presentation.viewmodel.LoginViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // login concluído -> avisa o host (vai pro quiz)
    LaunchedEffect(state.loggedInUserId) {
        if (state.loggedInUserId != null) onLoggedIn()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.login_titulo), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = state.email,
            onValueChange = { viewModel.onEvent(LoginEvent.EmailChanged(it)) },
            label = { Text(stringResource(R.string.login_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = { viewModel.onEvent(LoginEvent.PasswordChanged(it)) },
            label = { Text(stringResource(R.string.login_senha)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.onEvent(LoginEvent.SubmitLogin) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.login_entrar)) }
                OutlinedButton(
                    onClick = { viewModel.onEvent(LoginEvent.SubmitSignUp) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.login_cadastrar)) }
            }
        }
        state.error?.let {
            Spacer(Modifier.height(16.dp))
            // AUTENTICANDO: aqui 401 é "senha errada", não "sessão expirada" (ver ErrorUi).
            ErroInline(it, contexto = ErroContexto.AUTENTICANDO)
        }
    }
}