package dev.rafael.app.screens.exercise

import androidx.compose.foundation.clickable
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
import dev.rafael.features.exercise.presentation.state.ExerciseListState
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
        ExerciseListContent(
            state = state,
            onCategorySelected = { viewModel.onEvent(ExerciseListEvent.CategorySelected(it)) },
        )
    }
}

/** Conteúdo reusável. selectedIds != null ⇒ modo seleção (picker). */
@Composable
fun ExerciseListContent(
    state: ExerciseListState,
    onCategorySelected: (ExerciseCategory?) -> Unit,
    selectedIds: Set<String>? = null,
    onToggle: ((String) -> Unit)? = null,
) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("Todas") },
                )
            }
            items(ExerciseCategory.entries) { cat ->
                FilterChip(
                    selected = state.selectedCategory == cat,
                    onClick = { onCategorySelected(cat) },
                    label = { Text(cat.name) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            when {
                state.isRefreshing && state.exercises.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.exercises.isEmpty() ->
                    Text("Nenhum exercício.", Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.exercises) { ex ->
                            val selected = selectedIds?.contains(ex.id) == true
                            ListItem(
                                headlineContent = { Text(ex.name) },
                                supportingContent = { Text(ex.category.name) },
                                trailingContent = if (onToggle != null) {
                                    { Checkbox(checked = selected, onCheckedChange = { onToggle(ex.id) }) }
                                } else null,
                                modifier = if (onToggle != null) {
                                    Modifier.clickable { onToggle(ex.id) }
                                } else Modifier,
                            )
                        }
                    }
            }
        }
    }
}