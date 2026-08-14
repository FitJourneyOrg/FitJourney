package dev.rafael.app.screens.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.features.program.presentation.state.ProgramListEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramListViewModel
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

            // Só erro de AÇÃO em vermelho (ex.: falhou ao criar). Falha de SYNC não é erro de
            // tela: com dado local o usuário nem percebe; sem dado, vira o estado abaixo.
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            Box(Modifier.weight(1f)) {
                when {
                    state.isLoading && state.programs.isEmpty() ->
                        ShimmerList(modifier = Modifier.padding(vertical = 8.dp))
                    // vazio POR FALTA DE SYNC ≠ vazio de verdade
                    state.vazioPorFaltaDeSync -> SemConexao(
                        modifier = Modifier.align(Alignment.Center),
                        onTentarDeNovo = { viewModel.onEvent(ProgramListEvent.Retry) },
                    )
                    state.programs.isEmpty() ->
                        Text("Nenhum programa ainda.", Modifier.align(Alignment.Center))
                    else ->
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.programs) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name) },
                                    supportingContent = {
                                        val base = "${p.workouts.size} treinos"
                                        Text(if (p.daysPerWeek > 0) "$base · Semana ${p.currentWeek}/${p.durationWeeks}" else base)
                                    },
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

/**
 * Sem dado local E sync falhou. Diferente de "você não tem programas": pode ser alguém com
 * programas abrindo o app num aparelho novo, sem rede. Não sugere criar nada.
 */
@Composable
private fun SemConexao(modifier: Modifier = Modifier, onTentarDeNovo: () -> Unit) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CloudOff, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Sem conexão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ainda não sincronizamos seus programas neste aparelho. " +
                "Conecte-se para baixá-los — depois disso funcionam offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onTentarDeNovo) { Text("Tentar de novo") }
    }
}
