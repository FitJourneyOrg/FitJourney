package dev.rafael.app.screens.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.features.exercise.presentation.state.ExerciseListEvent
import dev.rafael.features.exercise.presentation.viewmodel.ExerciseListViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    viewModel: ExerciseListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selected by remember { mutableStateOf(emptySet<String>()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxHeight(0.9f).padding(horizontal = 16.dp)) {
            Text("Adicionar exercícios", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            Box(Modifier.weight(1f)) {
                ExerciseListContent(
                    state = state,
                    onCategorySelected = { viewModel.onEvent(ExerciseListEvent.CategorySelected(it)) },
                    selectedIds = selected,
                    onToggle = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                )
            }

            Button(
                onClick = { onConfirm(selected.toList()); onDismiss() },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) {
                Text("Adicionar (${selected.size})")
            }
        }
    }
}