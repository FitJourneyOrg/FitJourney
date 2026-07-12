package dev.rafael.app.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.features.profile.presentation.state.QuizEvent
import dev.rafael.features.profile.presentation.state.QuizStep
import dev.rafael.features.profile.presentation.viewmodel.QuizViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun QuizScreen(
    onCompleted: () -> Unit,
    viewModel: QuizViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // quando o onboarding conclui, avisa o host do app (decisão B: sem Navigation)
    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    val steps = QuizStep.entries
    val stepIndex = steps.indexOf(state.step)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        // progresso (passo atual / total)
        LinearProgressIndicator(
            progress = { (stepIndex + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text("${stepIndex + 1}/${steps.size}", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(24.dp))

        // miolo: a pergunta do passo atual
        Box(modifier = Modifier.weight(1f)) {
            when (state.step) {
                QuizStep.GOAL -> GoalStep(state.goal) { viewModel.onEvent(QuizEvent.GoalSelected(it)) }
                QuizStep.LEVEL -> LevelStep(state.level) { viewModel.onEvent(QuizEvent.LevelSelected(it)) }
                QuizStep.DAYS -> DaysStep(state.daysPerWeek) { viewModel.onEvent(QuizEvent.DaysSelected(it)) }
                QuizStep.FOCUS -> FocusStep(state.focusAreas) { viewModel.onEvent(QuizEvent.FocusToggled(it)) }
                QuizStep.ENVIRONMENT -> EnvironmentStep(state.environment) { viewModel.onEvent(QuizEvent.EnvironmentSelected(it)) }
                QuizStep.HEALTH -> HealthStep(state.health, onToggle = { viewModel.onEvent(QuizEvent.HealthToggled(it)) }, onAck = { viewModel.onEvent(QuizEvent.AcknowledgedRiskToggled) })
                QuizStep.BODY -> BodyStep(
                    weight = state.weightKg,
                    height = state.heightCm,
                    onWeight = { viewModel.onEvent(QuizEvent.WeightChanged(it)) },
                    onHeight = { viewModel.onEvent(QuizEvent.HeightChanged(it)) },
                )
            }
        }

        // erro do servidor, se houver
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        // navegação
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stepIndex > 0) {
                OutlinedButton(
                    onClick = { viewModel.onEvent(QuizEvent.Back) },
                    enabled = !state.isSubmitting,
                ) { Text("Voltar") }
            }
            Button(
                onClick = { viewModel.onEvent(QuizEvent.Next) },
                enabled = state.canAdvance && !state.isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(if (stepIndex == steps.lastIndex) "Concluir" else "Continuar")
                }
            }
        }
    }
}