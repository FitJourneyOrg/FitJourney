package dev.rafael.app.screens.reveal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.features.program.domain.model.ProgramWorkout
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProgramRevealScreen(
    onDone: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: ProgramRevealViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // ao voltar do Paywall (ou do fundo), re-busca o programa — já desbloqueado se assinou.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isGenerating -> GeneratingView()
            state.program == null -> ErrorView(state.error, onRetry = viewModel::retry)
            else -> RevealContent(
                name = state.program!!.name,
                daysPerWeek = state.program!!.daysPerWeek,
                split = state.program!!.split,
                rationale = state.program!!.rationale,
                workouts = state.program!!.workouts,
                locked = state.locked,
                onOpenPaywall = onOpenPaywall,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun GeneratingView() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text("Montando seu programa…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Estamos calibrando os treinos pro seu objetivo e nível.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorView(error: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Não deu pra montar seu programa.", style = MaterialTheme.typography.titleMedium)
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Tentar de novo") }
    }
}

@Composable
private fun RevealContent(
    name: String,
    daysPerWeek: Int,
    split: String,
    rationale: String,
    workouts: List<ProgramWorkout>,
    locked: Boolean,
    onOpenPaywall: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (locked) "Seu plano está pronto 🎉" else "Tudo liberado! 🎉",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${daysPerWeek}x por semana · $split",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rationale.isNotBlank()) {
                Text(rationale, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))
            Text("Sua semana", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            workouts.forEachIndexed { i, w -> WorkoutRow(day = i + 1, w = w) }

            if (locked) {
                Spacer(Modifier.height(8.dp))
                BenefitsCard(daysPerWeek)
            }
        }

        // Rodapé fixo com o CTA — o ponto de conversão.
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (locked) {
                    Button(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) {
                        Text("Desbloquear todos os treinos")
                    }
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Começar com o Dia 1 grátis")
                    }
                } else {
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text("Começar a treinar")
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(day: Int, w: ProgramWorkout) {
    ListItem(
        overlineContent = { Text("Dia $day") },
        headlineContent = { Text(w.name) },
        supportingContent = {
            Text(
                if (w.locked) "${w.exerciseCount} exercícios · Premium"
                else "${w.exerciseCount} exercícios · Grátis",
            )
        },
        leadingContent = {
            if (w.locked) Icon(Icons.Default.Lock, contentDescription = "Trancado")
            else Icon(Icons.Default.Check, contentDescription = "Liberado", tint = MaterialTheme.colorScheme.primary)
        },
    )
    HorizontalDivider()
}

@Composable
private fun BenefitsCard(daysPerWeek: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Com o premium você tem:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            listOf(
                "Todos os $daysPerWeek treinos da semana, não só o Dia 1",
                "Trocar exercícios e ajustar do seu jeito",
                "Reagendar os dias como quiser",
            ).forEach { benefit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(benefit, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
