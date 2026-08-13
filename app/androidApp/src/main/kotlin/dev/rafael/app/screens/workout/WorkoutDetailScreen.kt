package dev.rafael.app.screens.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import dev.rafael.features.workout.presentation.state.ResolvedExercise
import dev.rafael.features.workout.presentation.state.WorkoutDetailEvent
import dev.rafael.features.workout.presentation.viewmodel.WorkoutDetailViewModel
import kotlinx.coroutines.launch
import dev.rafael.app.ui.ShimmerContent
import dev.rafael.app.ui.ShimmerList
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartSession: () -> Unit = {},   // Fase 5: iniciar a execução do treino
    editLocked: Boolean = false,   // ARCH #25: programa IA + free → editar barra na hora
) {
    val viewModel: WorkoutDetailViewModel = koinViewModel { parametersOf(workoutId) }
    val exerciseRepo: ExerciseRepository = koinInject()
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    var showConfirm by remember { mutableStateOf(false) }
    var actionFor by remember { mutableStateOf<ResolvedExercise?>(null) }        // menu (long-press)
    var swapFor by remember { mutableStateOf<ResolvedExercise?>(null) }          // sheet de alternativas
    var alternatives by remember { mutableStateOf<List<Exercise>?>(null) }        // null = carregando
    var altError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!state.isDeleted) viewModel.onEvent(WorkoutDetailEvent.Retry)
    }

    // --- diálogos ---
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Excluir treino?") },
            text = { Text("Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; viewModel.onEvent(WorkoutDetailEvent.Delete) }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancelar") } },
        )
    }

    actionFor?.let { ex ->
        AlertDialog(
            onDismissRequest = { actionFor = null },
            title = { Text(ex.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        actionFor = null
                        swapFor = ex
                        alternatives = null; altError = null
                        scope.launch {
                            when (val r = exerciseRepo.alternatives(ex.exerciseId)) {
                                is AppResult.Success -> alternatives = r.value
                                is AppResult.Failure -> altError = r.error.message
                            }
                        }
                    }) { Text("🔁  Trocar exercício") }
                    TextButton(onClick = {
                        actionFor = null
                        viewModel.onEvent(WorkoutDetailEvent.RemoveExercise(ex.orderIndex))
                    }) { Text("🗑  Excluir exercício") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { actionFor = null }) { Text("Cancelar") } },
        )
    }

    swapFor?.let { ex ->
        AlertDialog(
            onDismissRequest = { swapFor = null },
            title = { Text("Trocar por") },
            text = {
                when {
                    altError != null -> Text(altError!!, color = MaterialTheme.colorScheme.error)
                    alternatives == null -> ShimmerList(rows = 4)
                    alternatives!!.isEmpty() -> Text("Nenhuma alternativa do mesmo tipo disponível.")
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(alternatives!!) { alt ->
                            Text(
                                alt.name,
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        swapFor = null
                                        viewModel.onEvent(WorkoutDetailEvent.SwapExercise(ex.orderIndex, alt.id))
                                    }
                                    .padding(vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { swapFor = null }) { Text("Fechar") } },
        )
    }

    if (state.showPaywall) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(WorkoutDetailEvent.DismissPaywall) },
            title = { Text("Recurso premium") },
            text = { Text("Editar um programa gerado por IA faz parte do plano premium. Assinatura em breve.") },
            confirmButton = { TextButton(onClick = { viewModel.onEvent(WorkoutDetailEvent.DismissPaywall) }) { Text("Entendi") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { "Treino" }) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = {
                        // programa IA trancado (free): não entra na edição, mostra paywall
                        if (editLocked) viewModel.onEvent(WorkoutDetailEvent.ShowPaywall) else onEdit()
                    }) { Icon(Icons.Default.Edit, "Editar") }
                    IconButton(onClick = { showConfirm = true }) { Icon(Icons.Default.Delete, "Excluir") }
                },
            )
        },
        bottomBar = {
            // Fase 5: começar a execução (só quando há exercícios carregados).
            if (state.exercises.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = onStartSession,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text("Iniciar treino") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.exercises.isEmpty() ->
                    ShimmerContent()
                state.error != null ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.onEvent(WorkoutDetailEvent.Retry) }) { Text("Tentar de novo") }
                    }
                state.exercises.isEmpty() ->
                    Text("Nenhum exercício neste treino.", Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(Modifier.padding(16.dp)) {
                        items(state.exercises) { ex ->
                            ListItem(
                                headlineContent = { Text(ex.name) },
                                supportingContent = { Text(ex.setsSummary) },
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = { actionFor = ex },
                                ),
                            )
                            HorizontalDivider()
                        }
                    }
            }
        }
    }
}
