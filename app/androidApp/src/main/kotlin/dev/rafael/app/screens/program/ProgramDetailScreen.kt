package dev.rafael.app.screens.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.features.program.presentation.state.ProgramDetailEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramDetailViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit,
    onOpenWorkout: (String, Boolean) -> Unit,   // (workoutId, editLocked)
    onAddWorkout: (String) -> Unit,
    viewModel: ProgramDetailViewModel = koinViewModel { parametersOf(programId) },
) {
    val state by viewModel.state.collectAsState()
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }   // ARCH #23: toque em dia trancado
    // ARCH #25: programa IA de usuário free vem trancado — edição é premium.
    // 'locked' já é setado pelo ProgramBlur só quando (origin=AI && !premium).
    val readOnly = state.program?.locked == true

    // delete bem-sucedido → volta pra lista de programas
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!state.isDeleted) viewModel.onEvent(ProgramDetailEvent.Retry)
    }

    if (showRename) {
        RenameProgramDialog(
            initialName = state.program?.name.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = { name ->
                showRename = false
                viewModel.onEvent(ProgramDetailEvent.Rename(name))
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Excluir programa?") },
            text = { Text("Isso apaga o programa e todos os treinos dentro dele. Não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.onEvent(ProgramDetailEvent.Delete)
                }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") } },
        )
    }

    if (showPaywall) {
        AlertDialog(
            onDismissRequest = { showPaywall = false },
            title = { Text("Recurso premium") },
            text = { Text("O Dia 1 é seu de graça. Assine o premium para desbloquear o programa completo.") },
            confirmButton = { TextButton(onClick = { showPaywall = false }) { Text("Entendi") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.program?.name ?: "Programa") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (!readOnly) {
                        IconButton(onClick = { showRename = true }) { Icon(Icons.Default.Edit, "Renomear") }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, "Excluir") }
                },
            )
        },
        floatingActionButton = {
            if (!readOnly) {
                FloatingActionButton(onClick = { onAddWorkout(programId) }) { Text("+") }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.program == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null && state.program == null ->
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.onEvent(ProgramDetailEvent.Retry) }) { Text("Tentar de novo") }
                    }
                state.program?.workouts?.isEmpty() == true ->
                    Text("Nenhum treino neste programa ainda.", Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.program?.rationale?.takeIf { it.isNotBlank() }?.let { rationale ->
                            item {
                                Text(rationale, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        val workouts = state.program?.workouts.orEmpty()
                        // Reordenar só faz sentido em programa que você edita (não trancado) e com 2+ treinos.
                        val canReorder = !readOnly && workouts.size > 1
                        itemsIndexed(workouts) { index, w ->
                            if (w.locked) {
                                ListItem(
                                    headlineContent = { Text(w.name) },
                                    supportingContent = { Text("${w.exerciseCount} exercícios · Assine para desbloquear") },
                                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = "Bloqueado") },
                                    modifier = Modifier.clickable { showPaywall = true },
                                )
                            } else {
                                ListItem(
                                    headlineContent = { Text("Dia ${index + 1} · ${w.name}") },
                                    supportingContent = { Text("${w.exerciseCount} exercícios") },
                                    trailingContent = if (!canReorder) null else {
                                        {
                                            Row {
                                                IconButton(
                                                    onClick = { w.id?.let { viewModel.onEvent(ProgramDetailEvent.MoveWorkout(it, up = true)) } },
                                                    enabled = index > 0 && !state.isReordering,
                                                ) { Icon(Icons.Default.KeyboardArrowUp, "Subir") }
                                                IconButton(
                                                    onClick = { w.id?.let { viewModel.onEvent(ProgramDetailEvent.MoveWorkout(it, up = false)) } },
                                                    enabled = index < workouts.lastIndex && !state.isReordering,
                                                ) { Icon(Icons.Default.KeyboardArrowDown, "Descer") }
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable { w.id?.let { onOpenWorkout(it, readOnly) } },
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun RenameProgramDialog(initialName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear programa") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true)
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
