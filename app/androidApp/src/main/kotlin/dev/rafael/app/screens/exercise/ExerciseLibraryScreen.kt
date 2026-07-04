package dev.rafael.app.screens.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.features.exercise.presentation.state.ExerciseListEvent
import dev.rafael.features.exercise.presentation.viewmodel.ExerciseListViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExerciseLibraryScreen(
    viewModel: ExerciseListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Exercícios", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // filtro por categoria — "Todas" + as 16
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { viewModel.onEvent(ExerciseListEvent.CategorySelected(null)) },
                    label = { Text("Todas") },
                )
            }
            items(ExerciseCategory.entries) { cat ->
                FilterChip(
                    selected = state.selectedCategory == cat,
                    onClick = { viewModel.onEvent(ExerciseListEvent.CategorySelected(cat)) },
                    label = { Text(cat.name) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.weight(1f)) {
            when {
                state.isRefreshing && state.exercises.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.exercises.isEmpty() ->
                    Text("Nenhum exercício.", Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.exercises) { ex ->
                            ListItem(
                                headlineContent = { Text(ex.name) },
                                supportingContent = { Text(ex.category.name) },
                            )
                        }
                    }
            }
        }
    }
}