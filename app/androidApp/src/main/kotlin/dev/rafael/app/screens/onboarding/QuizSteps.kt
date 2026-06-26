package dev.rafael.app.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.MuscleGroup

@Composable
fun GoalStep(selected: Goal?, onSelect: (Goal) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Qual seu objetivo principal?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Goal.entries.forEach { g ->
            val label = when (g) {
                Goal.GAIN_MUSCLE -> "Ganhar massa"
                Goal.LOSE_FAT -> "Perder gordura"
                Goal.MAINTAIN -> "Manter a forma"
                Goal.GENERAL_HEALTH -> "Saúde geral"
            }
            FilterChip(selected = selected == g, onClick = { onSelect(g) }, label = { Text(label) })
        }
    }
}

@Composable
fun LevelStep(selected: Level?, onSelect: (Level) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Qual seu nível hoje?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Level.entries.forEach { l ->
            val label = when (l) {
                Level.BEGINNER -> "Iniciante"
                Level.INTERMEDIATE -> "Intermediário"
                Level.ADVANCED -> "Avançado"
            }
            FilterChip(selected = selected == l, onClick = { onSelect(l) }, label = { Text(label) })
        }
    }
}

@Composable
fun DaysStep(selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quantos dias por semana?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..6).forEach { d ->
                FilterChip(selected = selected == d, onClick = { onSelect(d) }, label = { Text("$d") })
            }
        }
    }
}

@Composable
fun FocusStep(selected: List<MuscleGroup>, onToggle: (MuscleGroup) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quer focar em algum músculo?", style = MaterialTheme.typography.headlineSmall)
        Text("Escolha até 2 grupos (ou deixe vazio = equilibrado).", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        MuscleGroup.entries.forEach { m ->
            val label = when (m) {
                MuscleGroup.CHEST -> "Peito"
                MuscleGroup.BACK -> "Costas"
                MuscleGroup.ARMS -> "Braços"
                MuscleGroup.SHOULDERS -> "Ombros"
                MuscleGroup.LEGS -> "Pernas"
                MuscleGroup.GLUTES -> "Glúteos"
                MuscleGroup.CORE -> "Core"
            }
            FilterChip(selected = m in selected, onClick = { onToggle(m) }, label = { Text(label) })
        }
        Text("${selected.size}/2 selecionados", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun BodyStep(
    weight: Double?,
    height: Double?,
    onWeight: (Double?) -> Unit,
    onHeight: (Double?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Peso e altura (opcional)", style = MaterialTheme.typography.headlineSmall)
        Text("Dado privado, usado só pro seu plano. Você pode pular.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = weight?.toString() ?: "",
            onValueChange = { onWeight(it.toDoubleOrNull()) },
            label = { Text("Peso (kg)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = height?.toString() ?: "",
            onValueChange = { onHeight(it.toDoubleOrNull()) },
            label = { Text("Altura (cm)") },
            singleLine = true,
        )
    }
}