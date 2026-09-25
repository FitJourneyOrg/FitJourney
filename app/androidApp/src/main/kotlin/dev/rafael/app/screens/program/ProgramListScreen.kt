package dev.rafael.app.screens.program

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.ui.erroDoCampo
import dev.rafael.app.R
import dev.rafael.contract.error.ErrorFields
import dev.rafael.core.result.AppError
import dev.rafael.features.program.domain.model.PendenciaDeSync
import dev.rafael.features.program.domain.model.ProgramWorkout
import dev.rafael.features.program.presentation.state.ProgramListEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramListViewModel
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.app.ui.ErroEmSnackbar
import dev.rafael.app.ui.ShimmerList
import org.koin.androidx.compose.koinViewModel

/**
 * "Meus treinos" (V59) — deck achatado por TREINO, não por programa. Substitui a antiga lista
 * por programa na aba Treino: o conceito de programa continua existindo por baixo (é dele que
 * os treinos vêm), mas quem o usuário escolhe no dia a dia é "qual treino está ativo agora",
 * não "qual programa".
 *
 * `onOpenProgram` sobrevive só pro fluxo de criar programa MANUAL vazio (que nasce sem
 * nenhum treino — não haveria o que mostrar no deck ainda): a pessoa precisa ir ao detalhe do
 * programa pra adicionar o primeiro treino. Fora desse caso, a tela inteira opera em treino.
 */
@Composable
fun ProgramListScreen(
    onOpenProgram: (String) -> Unit,
    onOpenWorkout: (id: String, editLocked: Boolean) -> Unit,
    onStartWorkout: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onGenerateWithAI: () -> Unit,
    viewModel: ProgramListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var confirmarAtivacao by remember { mutableStateOf<ProgramWorkout?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(ProgramListEvent.Load)
    }

    LaunchedEffect(state.createdId) {
        val id = state.createdId ?: return@LaunchedEffect
        showCreateDialog = false
        viewModel.consumeCreatedId()
        onOpenProgram(id)
    }

    if (showCreateDialog) {
        CreateProgramDialog(
            erro = state.error,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name -> viewModel.onEvent(ProgramListEvent.CreateManual(name)) },
        )
    }

    confirmarAtivacao?.let { treino ->
        ConfirmarAtivacaoDialog(
            nome = treino.name,
            onConfirmar = {
                confirmarAtivacao = null
                treino.id?.let { viewModel.onEvent(ProgramListEvent.Activate(it)) }
            },
            onDismiss = { confirmarAtivacao = null },
        )
    }

    val snackbarHost = remember { SnackbarHostState() }

    ErroEmSnackbar(
        erro = state.error,
        host = snackbarHost,
        onConsumir = viewModel::consumeError,
        onAcao = { viewModel.onEvent(ProgramListEvent.Retry) },
    )

    val treinos = remember(state.programs) { state.programs.flatMap { it.workouts } }
    val ativo = treinos.firstOrNull { it.isActive }
    val outros = if (ativo != null) treinos.filterNot { it.isActive } else treinos

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onGenerateWithAI,
                    text = { Text(stringResource(R.string.comum_criar_com_ia)) },
                    icon = { Text("✨") },
                )
                FloatingActionButton(onClick = { showCreateDialog = true }) { Text("+") }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading && treinos.isEmpty() ->
                    ShimmerList(modifier = Modifier.padding(16.dp))
                // NÍVEL 2: vazio POR FALTA DE SYNC ≠ vazio de verdade (ARCH #30).
                state.vazioPorFaltaDeSync -> ErroDeTela(
                    erro = state.erroSync!!,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    onAcao = { viewModel.onEvent(ProgramListEvent.Retry) },
                )
                treinos.isEmpty() ->
                    Text(
                        stringResource(R.string.programa_lista_vazio),
                        Modifier.align(Alignment.Center).padding(16.dp),
                    )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(stringResource(R.string.programa_lista_titulo), style = MaterialTheme.typography.headlineSmall)
                    }
                    item {
                        if (ativo != null) {
                            TreinoAtivoCard(
                                treino = ativo,
                                onIniciar = { ativo.id?.let(onStartWorkout) },
                            )
                        } else {
                            TreinoAtivoVazio()
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(
                                    if (ativo != null) R.string.treino_ativo_secao_outros
                                    else R.string.treino_ativo_secao_seus,
                                ) + " · ${outros.size}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            TextButton(onClick = onOpenLibrary) {
                                Text(stringResource(R.string.treino_ativo_ver_biblioteca))
                            }
                        }
                    }
                    items(outros) { treino ->
                        TreinoRow(
                            treino = treino,
                            pendencia = state.pendenciaDe(treino.id),
                            ativando = state.activating == treino.id,
                            onClick = { treino.id?.let { onOpenWorkout(it, treino.locked) } },
                            onAtivar = { confirmarAtivacao = treino },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TreinoAtivoCard(treino: ProgramWorkout, onIniciar: () -> Unit) {
    OutlinedCard(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Badge simples (Surface+Text, não AssistChip) -- evita depender de um parametro de
            // cor "disabled" da API do AssistChip que eu nao tinha como confirmar sem rodar o
            // Gradle (device_bash nao roda o build neste projeto).
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    stringResource(R.string.treino_ativo_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(treino.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.treino_card_exercicios, treino.exerciseCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onIniciar, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.treino_ativo_iniciar))
            }
        }
    }
}

@Composable
private fun TreinoAtivoVazio() {
    OutlinedCard {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.treino_ativo_nenhum_titulo), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.treino_ativo_nenhum_corpo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TreinoRow(
    treino: ProgramWorkout,
    pendencia: PendenciaDeSync?,
    ativando: Boolean,
    onClick: () -> Unit,
    onAtivar: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(treino.name) },
        supportingContent = { Text(stringResource(R.string.treino_card_exercicios, treino.exerciseCount)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pendencia?.let { SeloDeSync(it) }
                OutlinedButton(onClick = onAtivar, enabled = !ativando) {
                    Text(stringResource(R.string.treino_ativo_ativar))
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ConfirmarAtivacaoDialog(nome: String, onConfirmar: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.treino_ativo_confirmar_titulo)) },
        text = { Text(stringResource(R.string.treino_ativo_confirmar_corpo, nome)) },
        confirmButton = {
            TextButton(onClick = onConfirmar) { Text(stringResource(R.string.treino_ativo_ativar)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.comum_cancelar)) }
        },
    )
}

@Composable
private fun CreateProgramDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    erro: AppError? = null,
) {
    var name by remember { mutableStateOf("") }
    val erroNome = erro.erroDoCampo(ErrorFields.NAME)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.programa_lista_novo)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.comum_nome)) },
                singleLine = true,
                isError = erroNome != null,
                supportingText = erroNome?.let { { Text(it) } },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.programa_lista_criar)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.comum_cancelar)) }
        },
    )
}

/**
 * Selo de sincronização (ARCH #30, B.4).
 *
 * Dois estados, com pesos diferentes de propósito:
 *  - AGUARDANDO: discreto. É o caso normal do offline-first — some sozinho quando a rede
 *    volta, e alarmar o usuário sobre algo que se resolve sem ele seria ruído.
 *  - FALHA PERMANENTE: em `error`. O servidor recusou, ninguém vai tentar de novo, e sem
 *    destaque o usuário seguiria acreditando que salvou.
 *
 * [REGRA] Nada de `lime` aqui: a cor é exclusiva das recompensas do perfil individual (#16).
 */
@Composable
private fun SeloDeSync(pendencia: PendenciaDeSync) {
    if (pendencia.aguardando) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(stringResource(R.string.programa_lista_pendente), style = MaterialTheme.typography.labelSmall)
            },
        )
    } else {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    stringResource(R.string.programa_lista_nao_sincronizou),
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}
