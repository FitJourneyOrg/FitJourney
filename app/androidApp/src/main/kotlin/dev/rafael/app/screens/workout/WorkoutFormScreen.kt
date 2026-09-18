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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rafael.app.screens.exercise.ExercisePickerSheet
import dev.rafael.app.R
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.rotuloDoDiaDaSemana
import dev.rafael.app.ui.erroDoCampo
import dev.rafael.contract.error.ErrorFields
import dev.rafael.features.workout.presentation.state.WorkoutFormEvent
import dev.rafael.features.workout.presentation.viewmodel.WorkoutFormViewModel
import dev.rafael.app.ui.ShimmerContent
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
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.treino_form_titulo_editar
                            else R.string.treino_form_titulo_novo,
                        ),
                    )
                },
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
                else Text(stringResource(R.string.treino_form_salvar))
            }
        },
    ) { padding ->
        if (state.isLoading) {
            ShimmerContent(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // O servidor diz QUAL campo recusou (fieldErrors): o erro fica no campo,
                // não numa frase solta no rodapé que o usuário tem que interpretar.
                val erroNome = state.error.erroDoCampo(ErrorFields.NAME)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onEvent(WorkoutFormEvent.NameChanged(it)) },
                    label = { Text(stringResource(R.string.treino_form_nome)) },
                    singleLine = true,
                    isError = erroNome != null,
                    supportingText = erroNome?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Dia da semana — só na criação (na edição o dia é gerido pela agenda do programa).
            if (!state.isEditing) {
                item {
                    Text(stringResource(R.string.treino_form_dia_da_semana), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Terceira cópia dos nomes dos dias no app, agora eliminada: o índice
                        // ISO é a fonte e o rótulo vem de `rotuloDoDiaDaSemana`.
                        (1..7).forEach { d ->
                            val taken = d in state.takenDays
                            FilterChip(
                                selected = state.selectedDay == d,
                                enabled = !taken,   // dia já ocupado por outro treino
                                onClick = { viewModel.onEvent(WorkoutFormEvent.DaySelected(d)) },
                                label = { Text(stringResource(rotuloDoDiaDaSemana(d))) },
                            )
                        }
                    }
                }
            }

            state.error?.let { erro ->
                item { ErroInline(erro) }
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
                                Text(stringResource(R.string.comum_serie, j + 1), Modifier.width(72.dp))
                                OutlinedTextField(
                                    value = reps,
                                    onValueChange = { viewModel.onEvent(WorkoutFormEvent.SetRepsChanged(i, j, it)) },
                                    label = { Text(stringResource(R.string.comum_reps)) },
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
                            Text(stringResource(R.string.treino_form_add_serie))
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.treino_form_add_exercicio)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}