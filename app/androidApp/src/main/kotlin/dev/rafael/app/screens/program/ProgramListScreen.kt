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
import dev.rafael.app.ui.erroDoCampo
import dev.rafael.contract.error.ErrorFields
import dev.rafael.core.result.AppError
import dev.rafael.features.program.domain.model.PendenciaDeSync
import dev.rafael.features.program.presentation.state.ProgramListEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramListViewModel
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.app.ui.ErroEmSnackbar
import dev.rafael.app.ui.ShimmerList
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
        showCreateDialog = false       // só fecha quando DEU CERTO
        viewModel.consumeCreatedId()   // limpa antes de navegar (evento one-shot, não re-dispara ao voltar)
        onOpenProgram(id)
    }

    if (showCreateDialog) {
        CreateProgramDialog(
            erro = state.error,
            onDismiss = { showCreateDialog = false },
            // NÃO fecha aqui: se o servidor recusar o nome, o diálogo precisa continuar
            // aberto pra mostrar o erro NO CAMPO. Fecha no sucesso (LaunchedEffect acima).
            onConfirm = { name -> viewModel.onEvent(ProgramListEvent.CreateManual(name)) },
        )
    }

    val snackbarHost = remember { SnackbarHostState() }

    // NÍVEL 3: falhou algo que o usuário pediu (criar programa). Efêmero — o banner vermelho
    // fixo de antes continuava na tela muito depois de ter deixado de importar.
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

            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading && state.programs.isEmpty() ->
                        ShimmerList(modifier = Modifier.padding(vertical = 8.dp))
                    // NÍVEL 2: vazio POR FALTA DE SYNC ≠ vazio de verdade. Com dado local a
                    // falha de sync é nível 1 (silêncio) e este ramo nem é alcançado.
                    state.vazioPorFaltaDeSync -> ErroDeTela(
                        erro = state.erroSync!!,
                        modifier = Modifier.align(Alignment.Center),
                        onAcao = { viewModel.onEvent(ProgramListEvent.Retry) },
                    )
                    state.programs.isEmpty() ->
                        Text("Nenhum programa ainda.", Modifier.align(Alignment.Center))
                    else ->
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.programs) { p ->
                                val pendencia = state.pendenciaDe(p.id)
                                ListItem(
                                    headlineContent = { Text(p.name) },
                                    supportingContent = {
                                        val base = "${p.workouts.size} treinos"
                                        Text(if (p.daysPerWeek > 0) "$base · Semana ${p.currentWeek}/${p.durationWeeks}" else base)
                                    },
                                    // ARCH #30/B.4: escrita otimista sem selo é desonesta — o
                                    // usuário não teria como saber que o dado só existe aqui.
                                    trailingContent = pendencia?.let { { SeloDeSync(it) } },
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
private fun CreateProgramDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    erro: AppError? = null,
) {
    var name by remember { mutableStateOf("") }
    val erroNome = erro.erroDoCampo(ErrorFields.NAME)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo programa") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome") },
                singleLine = true,
                isError = erroNome != null,
                supportingText = erroNome?.let { { Text(it) } },
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
            label = { Text("Pendente", style = MaterialTheme.typography.labelSmall) },
        )
    } else {
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text("Não sincronizou", style = MaterialTheme.typography.labelSmall) },
            colors = AssistChipDefaults.assistChipColors(
                disabledLabelColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}
