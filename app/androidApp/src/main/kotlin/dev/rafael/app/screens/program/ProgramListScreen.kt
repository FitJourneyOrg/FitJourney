package dev.rafael.app.screens.program

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import dev.rafael.app.ui.nomeDoPrograma
import dev.rafael.app.R
import dev.rafael.contract.error.ErrorFields
import dev.rafael.core.result.AppError
import dev.rafael.features.program.domain.model.PendenciaDeSync
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.presentation.state.ProgramListEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramListViewModel
import dev.rafael.app.ui.DescartarPendenciaDialog
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.app.ui.ErroEmSnackbar
import dev.rafael.app.ui.SeloDeSync
import dev.rafael.app.ui.ShimmerList
import org.koin.androidx.compose.koinViewModel

/**
 * "Meus treinos" — lista de PROGRAMAS (restaurado 2026-09-26; era a versão anterior ao V59, que
 * tinha achatado isto num deck por treino). O programa continua sendo a unidade de navegação:
 * tocar num card abre [ProgramDetailScreen], que mostra a semana (7 dias) e é onde "ativar" um
 * treino específico agora mora — ver aquele arquivo pro selo "· Ativo" e o botão "Ativar".
 *
 * O único fluxo que fura essa regra é a criação de programa manual: nasce sem nenhum treino, daí
 * precisa navegar direto pro detalhe pra o usuário adicionar o primeiro.
 */
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

    val snackbarHost = remember { SnackbarHostState() }

    ErroEmSnackbar(
        erro = state.error,
        host = snackbarHost,
        onConsumir = viewModel::consumeError,
        onAcao = { viewModel.onEvent(ProgramListEvent.Retry) },
    )

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
                state.isLoading && state.programs.isEmpty() ->
                    ShimmerList(modifier = Modifier.padding(16.dp))
                // NÍVEL 2: vazio POR FALTA DE SYNC ≠ vazio de verdade (ARCH #30).
                state.vazioPorFaltaDeSync -> ErroDeTela(
                    erro = state.erroSync!!,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    onAcao = { viewModel.onEvent(ProgramListEvent.Retry) },
                )
                state.programs.isEmpty() ->
                    Text(
                        stringResource(R.string.programa_lista_vazio),
                        Modifier.align(Alignment.Center).padding(16.dp),
                    )
                else -> {
                    // Programa dono do treino ativo sempre no topo -- é o que o usuário quer
                    // ver primeiro ao abrir a tela. sortedByDescending é estável: quem não tem
                    // ativo mantém a ordem que já vinha do servidor.
                    val programasOrdenados = remember(state.programs) {
                        state.programs.sortedByDescending { p -> p.workouts.any { it.isActive } }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(stringResource(R.string.programa_lista_titulo), style = MaterialTheme.typography.headlineSmall)
                        }
                        items(programasOrdenados) { programa ->
                            ProgramCard(
                                programa = programa,
                                pendencia = state.pendenciaDe(programa.id),
                                onClick = { programa.id?.let(onOpenProgram) },
                                onDescartarPendencia = { programa.id?.let { viewModel.onEvent(ProgramListEvent.Descartar(it)) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(
    programa: Program,
    pendencia: PendenciaDeSync?,
    onClick: () -> Unit,
    onDescartarPendencia: () -> Unit,
) {
    val temTreinoAtivo = programa.workouts.any { it.isActive }
    var confirmarDescarte by remember { mutableStateOf(false) }

    if (confirmarDescarte) {
        DescartarPendenciaDialog(
            mensagem = pendencia?.erroPermanente,
            onConfirmar = { confirmarDescarte = false; onDescartarPendencia() },
            onDismiss = { confirmarDescarte = false },
        )
    }

    OutlinedCard(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    nomeDoPrograma(name = programa.name, daysPerWeek = programa.daysPerWeek, split = programa.split),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (programa.locked) {
                    Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.programa_detalhe_bloqueado))
                }
                pendencia?.let {
                    SeloDeSync(it, onDescartar = if (!it.aguardando) ({ confirmarDescarte = true }) else null)
                }
            }
            if (temTreinoAtivo) {
                Spacer(Modifier.height(8.dp))
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
            }
            if (programa.daysPerWeek > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.programa_detalhe_semana, programa.currentWeek, programa.durationWeeks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
