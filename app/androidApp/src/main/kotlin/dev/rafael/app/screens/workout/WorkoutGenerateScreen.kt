package dev.rafael.app.screens.workout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.features.workout.presentation.state.GenerateError
import dev.rafael.features.workout.presentation.state.WorkoutGenerateEvent
import dev.rafael.features.workout.presentation.viewmodel.WorkoutGenerateViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutGenerateScreen(
    onBack: () -> Unit,
    onGenerated: (String) -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val viewModel: WorkoutGenerateViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    // sucesso → navega pro detalhe do treino gerado
    LaunchedEffect(state.generatedId) {
        state.generatedId?.let(onGenerated)
    }

    // reações aos 403 (placeholders)
    when (state.error) {
        GenerateError.Entitlement -> PremiumDialog(onDismiss = { viewModel.onEvent(WorkoutGenerateEvent.DismissError) })
        GenerateError.HealthGate -> HealthGateDialog(onDismiss = { viewModel.onEvent(WorkoutGenerateEvent.DismissError) })
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
                "Descreva seu objetivo e o assistente monta um treino pra você.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { viewModel.onEvent(WorkoutGenerateEvent.PromptChanged(it)) },
                label = { Text("Ex.: foco em superiores, 45 min") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                enabled = !state.isGenerating,
            )

            (state.error as? GenerateError.Other)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.onEvent(WorkoutGenerateEvent.Generate) },
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isGenerating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Gerando seu treino...")
                    }
                } else {
                    Text("Gerar treino")
                }
            }
        }
    }
}

@Composable
private fun PremiumDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recurso premium") },
        text = { Text("A geração por IA faz parte do plano premium. Assinatura em breve.") },
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