package dev.rafael.app.screens.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.features.program.presentation.state.ProgramDetailEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramDetailViewModel
import dev.rafael.app.ui.ShimmerContent
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(
    programId: String,
    onBack: () -> Unit,
    onOpenWorkout: (String, Boolean) -> Unit,   // (workoutId, editLocked)
    onAddWorkout: (String, String) -> Unit,     // (programId, diasOcupadosCSV)
    onOpenPaywall: () -> Unit,                  // programa trancado → página de assinatura
    onGenerateNew: () -> Unit,                  // programa concluído → gerar novo com IA
    onCreateManual: () -> Unit,                 // programa concluído → criar um manual
    viewModel: ProgramDetailViewModel = koinViewModel { parametersOf(programId) },
) {
    val state by viewModel.state.collectAsState()
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // ARCH #25: programa IA de usuário free vem trancado — edição é premium.
    // 'locked' já é setado pelo ProgramBlur só quando (origin=AI && !premium).
    val readOnly = state.program?.locked == true
    // Ao voltar do Paywall, o ON_RESUME abaixo já refaz o Retry → server desblurra vendo o premium.

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
        bottomBar = {
            // CTA da revelação (value-first): programa IA trancado → assinar desbloqueia tudo.
            if (readOnly && state.program != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = onOpenPaywall,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Desbloquear todos os treinos")
                    }
                }
            }
        },
        floatingActionButton = {
            if (!readOnly) {
                FloatingActionButton(onClick = {
                    val taken = state.program?.schedule.orEmpty()
                        .map { it.dayOfWeek }.sorted().joinToString(",")
                    onAddWorkout(programId, taken)
                }) { Text("+") }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && state.program == null ->
                    ShimmerContent()
                // com programa carregado, falha vira ruído: a tela funciona (ARCH #30, nível 1)
                state.error != null && state.program == null ->
                    ErroDeTela(
                        erro = state.error!!,
                        modifier = Modifier.align(Alignment.Center),
                        onAcao = { viewModel.onEvent(ProgramDetailEvent.Retry) },
                    )
                state.program?.workouts?.isEmpty() == true ->
                    Text("Nenhum treino neste programa ainda.", Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Cronograma: "Semana Y de X" + banner de conclusão (só p/ programa estruturado).
                        state.program?.takeIf { it.daysPerWeek > 0 }?.let { p ->
                            item {
                                WeekProgress(
                                    currentWeek = p.currentWeek,
                                    durationWeeks = p.durationWeeks,
                                    onGenerateNew = onGenerateNew,
                                    onCreateManual = onCreateManual,
                                )
                            }
                        }
                        state.program?.rationale?.takeIf { it.isNotBlank() }?.let { rationale ->
                            item {
                                Text(rationale, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        val workouts = state.program?.workouts.orEmpty()
                        // Agendar por dia só em programa que você edita (não trancado).
                        val canSchedule = !readOnly
                        val dayByWorkout = state.program?.schedule.orEmpty().associate { it.workoutId to it.dayOfWeek }
                        // Visão da SEMANA: 7 dias; dia sem treino = "Descanso" (descanso é implícito).
                        val workoutByDay = workouts
                            .mapNotNull { w -> w.id?.let { id -> dayByWorkout[id]?.let { d -> d to w } } }
                            .toMap()
                        items((1..7).toList()) { day ->
                            val w = workoutByDay[day]
                            when {
                                w == null -> ListItem(
                                    overlineContent = { Text(weekdayLabel(day)) },
                                    headlineContent = {
                                        Text("Descanso", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    },
                                )
                                w.locked -> ListItem(
                                    overlineContent = { Text(weekdayLabel(day)) },
                                    headlineContent = { Text(w.name) },
                                    supportingContent = { Text("${w.exerciseCount} exercícios · Assine para desbloquear") },
                                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = "Bloqueado") },
                                    modifier = Modifier.clickable { onOpenPaywall() },
                                )
                                else -> ListItem(
                                    overlineContent = { Text(weekdayLabel(day)) },
                                    headlineContent = { Text(w.name) },
                                    supportingContent = { Text("${w.exerciseCount} exercícios") },
                                    trailingContent = if (!canSchedule) null else {
                                        {
                                            WeekdayPicker(
                                                day = day,
                                                enabled = !state.isReordering,
                                                onPick = { d -> w.id?.let { viewModel.onEvent(ProgramDetailEvent.SetWorkoutDay(it, d)) } },
                                            )
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

private val WEEKDAYS = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
private fun weekdayLabel(day: Int): String = WEEKDAYS.getOrElse(day - 1) { "?" }

/** "Semana Y de X" + barra de progresso; ao concluir a janela, banner com as 2 ações de novo programa. */
@Composable
private fun WeekProgress(
    currentWeek: Int,
    durationWeeks: Int,
    onGenerateNew: () -> Unit,
    onCreateManual: () -> Unit,
) {
    val done = currentWeek >= durationWeeks
    Column(Modifier.fillMaxWidth()) {
        Text("Semana $currentWeek de $durationWeeks", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { (currentWeek.toFloat() / durationWeeks).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        if (done) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Programa concluído", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Hora de trocar. Gere um novo com IA ou crie um manual.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onGenerateNew, modifier = Modifier.weight(1f)) { Text("Gerar com IA") }
                        OutlinedButton(onClick = onCreateManual, modifier = Modifier.weight(1f)) { Text("Criar manual") }
                    }
                }
            }
        }
    }
}

/** Botão com o dia atual do treino; abre um menu p/ escolher outro (1=Seg..7=Dom). */
@Composable
private fun WeekdayPicker(day: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = enabled) { Text(weekdayLabel(day)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..7).forEach { d ->
                DropdownMenuItem(
                    text = { Text(weekdayLabel(d) + if (d == day) "  ✓" else "") },
                    onClick = { open = false; if (d != day) onPick(d) },
                )
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
