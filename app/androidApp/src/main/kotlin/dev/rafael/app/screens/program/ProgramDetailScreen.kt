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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.app.ui.Frase
import dev.rafael.app.ui.nomeDoPrograma
import dev.rafael.app.ui.resolver
import dev.rafael.app.ui.rotuloDoDiaDaSemana
import dev.rafael.app.R
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
            title = { Text(stringResource(R.string.programa_detalhe_excluir_pergunta)) },
            text = { Text(stringResource(R.string.programa_detalhe_excluir_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.onEvent(ProgramDetailEvent.Delete)
                }) { Text(stringResource(R.string.comum_excluir)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.comum_cancelar))
                }
            },
        )
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // G.5: `name` vazio significa "programa gerado, derive o rótulo" — o `?:`
                    // sozinho não bastava, porque ele só cobria o programa ainda não carregado.
                    Text(
                        nomeDoPrograma(
                            name = state.program?.name,
                            daysPerWeek = state.program?.daysPerWeek ?: 0,
                            split = state.program?.split.orEmpty(),
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.comum_voltar))
                    }
                },
                actions = {
                    if (!readOnly) {
                        IconButton(onClick = { showRename = true }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.programa_detalhe_renomear))
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.comum_excluir))
                    }
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
                        Text(stringResource(R.string.comum_desbloquear_treinos))
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
                    Text(stringResource(R.string.programa_detalhe_vazio), Modifier.align(Alignment.Center))
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
                                // ⚠️ TEXTO DO SERVIDOR, não traduzido. Ver `TextoDoServidorTest`.
                                Text(
                                    Frase.DoServidor(rationale).resolver(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
                                    overlineContent = { Text(stringResource(rotuloDoDiaDaSemana(day))) },
                                    headlineContent = {
                                        Text(
                                            stringResource(R.string.programa_detalhe_descanso),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                )
                                w.locked -> ListItem(
                                    overlineContent = { Text(stringResource(rotuloDoDiaDaSemana(day))) },
                                    headlineContent = { Text(w.name) },
                                    supportingContent = {
                                        Text(
                                            pluralStringResource(
                                                R.plurals.treino_exercicios,
                                                w.exerciseCount,
                                                w.exerciseCount,
                                            ) + " · " + stringResource(R.string.programa_detalhe_assine),
                                        )
                                    },
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.programa_detalhe_bloqueado),
                                        )
                                    },
                                    modifier = Modifier.clickable { onOpenPaywall() },
                                )
                                else -> ListItem(
                                    overlineContent = { Text(stringResource(rotuloDoDiaDaSemana(day))) },
                                    headlineContent = { Text(w.name) },
                                    supportingContent = {
                                        Text(
                                            pluralStringResource(
                                                R.plurals.treino_exercicios,
                                                w.exerciseCount,
                                                w.exerciseCount,
                                            ),
                                        )
                                    },
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
        Text(
            stringResource(R.string.programa_detalhe_semana, currentWeek, durationWeeks),
            style = MaterialTheme.typography.titleMedium,
        )
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
                    Text(
                        stringResource(R.string.programa_detalhe_concluido_titulo),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.programa_detalhe_concluido_texto),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onGenerateNew, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.programa_detalhe_gerar_ia))
                        }
                        OutlinedButton(onClick = onCreateManual, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.programa_detalhe_criar_manual))
                        }
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
        TextButton(onClick = { open = true }, enabled = enabled) {
            Text(stringResource(rotuloDoDiaDaSemana(day)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..7).forEach { d ->
                val rotulo = stringResource(rotuloDoDiaDaSemana(d))
                DropdownMenuItem(
                    text = {
                        Text(
                            if (d == day) stringResource(R.string.programa_detalhe_dia_atual, rotulo)
                            else rotulo,
                        )
                    },
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
        title = { Text(stringResource(R.string.programa_detalhe_renomear_titulo)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.comum_nome)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.comum_salvar)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.comum_cancelar)) }
        },
    )
}
