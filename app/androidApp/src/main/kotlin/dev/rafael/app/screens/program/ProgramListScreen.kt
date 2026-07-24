package dev.rafael.app.screens.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.features.program.presentation.state.ProgramListEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramListViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProgramListScreen(
    onOpenProgram: (String) -> Unit,
    onGenerateWithAI: () -> Unit,
    viewModel: ProgramListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(ProgramListEvent.Load)
    }

    LaunchedEffect(state.createdId) {
        val id = state.createdId ?: return@LaunchedEffect
        viewModel.consumeCreatedId()   // limpa antes de navegar (evento one-shot, não re-dispara ao voltar)
        onOpenProgram(id)
    }

    if (showCreateDialog) {
        CreateProgramDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                viewModel.onEvent(ProgramListEvent.CreateManual(name))
            },
        )
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onGenerateWithAI,
                    text = { Text("Criar com IA") },
                    icon = { Text("✨") },
                )
                FloatingActionButton(onClick = { showCreateDialog = true }) { Text("+") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Meus programas", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading && state.programs.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.programs.isEmpty() ->
                        Text("Nenhum programa ainda.", Modifier.align(Alignment.Center))
                    else ->
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.programs) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name) },
                                    supportingContent = { Text("${p.workouts.size} treinos") },
                                    modifier = Modifier.clickable { p.id?.let(onOpenProgram) },
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun CreateProgramDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo programa") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
