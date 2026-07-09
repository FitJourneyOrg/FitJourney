package dev.rafael.app.screens.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.features.workout.presentation.state.WorkoutDetailEvent
import dev.rafael.features.workout.presentation.viewmodel.WorkoutDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: String,
    onBack: () -> Unit,
    viewModel: WorkoutDetailViewModel = koinViewModel { parametersOf(workoutId) },
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { "Treino" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(WorkoutDetailEvent.Delete) }) {
                        Text("Excluir")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
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
                            )
                            HorizontalDivider()
                        }
                    }
            }
        }
    }
}