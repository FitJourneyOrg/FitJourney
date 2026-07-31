package dev.rafael.app.screens.workout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.rafael.app.screens.exercise.ExercisePickerSheet
import dev.rafael.features.workout.presentation.state.WorkoutFormEvent
import dev.rafael.features.workout.presentation.viewmodel.WorkoutFormViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutFormScreen(
    workoutId: String?,
    programId: String?,   // obrigatório ao criar (ARCH #27); null ao editar
    onBack: () -> Unit,
    onSaved: () -> Unit,
    takenDays: String = "",   // CSV dos dias já ocupados no programa
    viewModel: WorkoutFormViewModel = koinViewModel { parametersOf(workoutId, programId, takenDays) },
) {
    val state by viewModel.state.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedId) { if (state.savedId != null) onSaved() }

    if (showPicker) {
        ExercisePickerSheet(
            onDismiss = { showPicker = false },
            onConfirm = { ids -> viewModel.onEvent(WorkoutFormEvent.ExercisesAdded(ids)) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Editar treino" else "Novo treino") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            )
        },
        bottomBar = {
            Button(
                onClick = { viewModel.onEvent(WorkoutFormEvent.Save) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp))
                else Text("Salvar treino")
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onEvent(WorkoutFormEvent.NameChanged(it)) },
                    label = { Text("Nome do treino") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Dia da semana — só na criação (na edição o dia é gerido pela agenda do programa).
            if (!state.isEditing) {
                item {
                    Text("Dia da semana", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom").forEachIndexed { idx, lbl ->
                            val d = idx + 1
                            val taken = d in state.takenDays
                            FilterChip(
                                selected = state.selectedDay == d,
                                enabled = !taken,   // dia já ocupado por outro treino
                                onClick = { viewModel.onEvent(WorkoutFormEvent.DaySelected(d)) },
                                label = { Text(lbl) },
                            )
                        }
                    }
                }
            }

            state.error?.let { msg ->
                item { Text(msg, color = MaterialTheme.colorScheme.error) }
            }

            itemsIndexed(state.exercises) { i, ex ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ex.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(
                                onClick = { viewModel.onEvent(WorkoutFormEvent.ExerciseMovedUp(i)) },
                                enabled = i > 0,
                            ) { Text("↑") }
                            IconButton(
                                onClick = { viewModel.onEvent(WorkoutFormEvent.ExerciseMovedDown(i)) },
                                enabled = i < state.exercises.lastIndex,
                            ) { Text("↓") }
                            IconButton(onClick = { viewModel.onEvent(WorkoutFormEvent.ExerciseRemoved(i)) }) { Text("✕") }
                        }

                        ex.sets.forEachIndexed { j, reps ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Série ${j + 1}", Modifier.width(72.dp))
                                OutlinedTextField(
                                    value = reps,
                                    onValueChange = { viewModel.onEvent(WorkoutFormEvent.SetRepsChanged(i, j, it)) },
                                    label = { Text("reps") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(110.dp),
                                )
                                IconButton(
                                    onClick = { viewModel.onEvent(WorkoutFormEvent.SetRemoved(i, j)) },
                                    enabled = ex.sets.size > 1,
                                ) { Text("−") }
                            }
                        }

                        TextButton(onClick = { viewModel.onEvent(WorkoutFormEvent.SetAdded(i)) }) {
                            Text("+ série")
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("+ adicionar exercício") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}