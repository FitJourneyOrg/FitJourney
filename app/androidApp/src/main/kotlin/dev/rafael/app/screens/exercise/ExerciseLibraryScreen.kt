package dev.rafael.app.screens.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.app.ui.NetworkImage
import dev.rafael.app.ui.ShimmerLine
import dev.rafael.app.ui.shimmer
import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.network.MediaUrls
import dev.rafael.features.exercise.presentation.state.ExerciseListEvent
import dev.rafael.features.exercise.presentation.state.ExerciseListState
import dev.rafael.features.exercise.presentation.viewmodel.ExerciseListViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ExerciseLibraryScreen(
    onOpenExercise: (String) -> Unit,
    viewModel: ExerciseListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Exercícios", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        ExerciseListContent(
            state = state,
            onCategorySelected = { viewModel.onEvent(ExerciseListEvent.CategorySelected(it)) },
            onOpenDetail = onOpenExercise,
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
    onOpenDetail: ((String) -> Unit)? = null,
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
                    Column(Modifier.fillMaxSize()) {
                        repeat(8) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(56.dp).shimmer(RoundedCornerShape(8.dp)))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ShimmerLine(width = 180.dp, height = 16.dp)
                                    ShimmerLine(width = 90.dp, height = 12.dp)
                                }
                            }
                        }
                    }
                state.exercises.isEmpty() ->
                    Text("Nenhum exercício.", Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.exercises) { ex ->
                            val selected = selectedIds?.contains(ex.id) == true
                            ListItem(
                                leadingContent = {
                                    NetworkImage(
                                        url = MediaUrls.url(ex.thumbRef),
                                        contentDescription = ex.name,
                                        modifier = Modifier.size(56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                },
                                headlineContent = { Text(ex.name) },
                                supportingContent = { Text(ex.category.name) },
                                trailingContent = if (onToggle != null) {
                                    { Checkbox(checked = selected, onCheckedChange = { onToggle(ex.id) }) }
                                } else null,
                                modifier = when {
                                    onToggle != null -> Modifier.clickable { onToggle(ex.id) }
                                    onOpenDetail != null -> Modifier.clickable { onOpenDetail(ex.id) }
                                    else -> Modifier
                                },
                            )
                        }
                    }
            }
        }
    }
}