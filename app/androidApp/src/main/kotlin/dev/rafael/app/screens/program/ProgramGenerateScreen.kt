package dev.rafael.app.screens.program

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.features.program.presentation.state.GenerateError
import dev.rafael.features.program.presentation.state.ProgramGenerateEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramGenerateViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramGenerateScreen(
    onBack: () -> Unit,
    onGenerated: (String) -> Unit,
) {
    val viewModel: ProgramGenerateViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    // sucesso → navega pro detalhe do programa gerado
    LaunchedEffect(state.generatedId) {
        val id = state.generatedId ?: return@LaunchedEffect
        viewModel.consumeGeneratedId()
        onGenerated(id)
    }

    // reações aos 403 (placeholders)
    when (state.error) {
        GenerateError.Entitlement -> PremiumDialog(onDismiss = { viewModel.onEvent(ProgramGenerateEvent.DismissError) })
        GenerateError.HealthGate -> HealthGateDialog(onDismiss = { viewModel.onEvent(ProgramGenerateEvent.DismissError) })
        is GenerateError.Other -> {} // mostrado inline abaixo
        null -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Criar com IA") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Voltar") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "O assistente monta um programa completo pra você, com base no seu perfil e objetivo.",
                style = MaterialTheme.typography.bodyMedium,
            )

            (state.error as? GenerateError.Other)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.onEvent(ProgramGenerateEvent.Generate) },
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isGenerating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gerando seu programa...")
                    }
                } else {
                    Text("Gerar programa")
                }
            }
        }
    }
}

@Composable
private fun PremiumDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limite de programas gratuitos") },
        text = { Text("Você atingiu o limite de programas gerados no plano grátis. Assine o premium pra gerar mais.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi") } },
    )
}

@Composable
private fun HealthGateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avaliação de saúde") },
        text = { Text("Complete a avaliação de saúde do seu perfil antes de gerar treinos com IA.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi") } },
    )
}
