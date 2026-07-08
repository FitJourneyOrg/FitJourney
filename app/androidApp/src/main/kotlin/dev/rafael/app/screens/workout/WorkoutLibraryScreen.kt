package dev.rafael.app.screens.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.features.workout.presentation.viewmodel.WorkoutListViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun WorkoutLibraryScreen(
    viewModel: WorkoutListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Meus treinos", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                state.isLoading && state.workouts.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.workouts.isEmpty() ->
                    Text("Nenhum treino ainda.", Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.workouts) { w ->
                            ListItem(
                                headlineContent = { Text(w.name) },
                                supportingContent = { Text("${w.exerciseCount} exercícios") },
                            )
                        }
                    }
            }
        }
    }
}