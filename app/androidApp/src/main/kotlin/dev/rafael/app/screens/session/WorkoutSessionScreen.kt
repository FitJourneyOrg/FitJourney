package dev.rafael.app.screens.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(
    workoutId: String,
    onDone: () -> Unit,
    viewModel: WorkoutSessionViewModel = koinViewModel { parametersOf(workoutId) },
) {
    val state by viewModel.state.collectAsState()

    // salvou (localmente ao menos) → volta
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.workoutName.ifBlank { "Treino" }) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            )
        },
        bottomBar = {
            if (!state.isLoading && state.error == null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.onEvent(SessionEvent.Finish) },
                        enabled = state.canFinish,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp))
                        else Text("Finalizar treino")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(state.error!!, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    itemsIndexed(state.entries) { i, e ->
                        val firstOfExercise = i == 0 || state.entries[i - 1].orderIndex != e.orderIndex
                        if (firstOfExercise) {
                            Spacer(Modifier.height(12.dp))
                            Text(e.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        SetRow(
                            entry = e,
                            onReps = { viewModel.onEvent(SessionEvent.RepsChanged(i, it)) },
                            onWeight = { viewModel.onEvent(SessionEvent.WeightChanged(i, it)) },
                            onToggle = { viewModel.onEvent(SessionEvent.ToggleDone(i)) },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    entry: SetEntry,
    onReps: (String) -> Unit,
    onWeight: (String) -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Série ${entry.setIndex + 1}", Modifier.width(64.dp), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = entry.repsDone,
            onValueChange = onReps,
            label = { Text("reps") },
            supportingText = { Text("alvo ${entry.targetReps}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = entry.weight,
            onValueChange = onWeight,
            label = { Text("kg") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = entry.done, onCheckedChange = { onToggle() })
    }
}
