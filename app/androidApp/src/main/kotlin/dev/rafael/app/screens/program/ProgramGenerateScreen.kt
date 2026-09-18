package dev.rafael.app.screens.program

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.rafael.app.R
import dev.rafael.app.ui.Frase
import dev.rafael.app.ui.resolver
import dev.rafael.features.program.presentation.state.GenerateError
import dev.rafael.features.program.presentation.state.ProgramGenerateEvent
import dev.rafael.features.program.presentation.viewmodel.ProgramGenerateViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramGenerateScreen(
    onBack: () -> Unit,
    onGenerated: (String) -> Unit,
) {
    val viewModel: ProgramGenerateViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    // sucesso → navega pro detalhe do programa gerado
    LaunchedEffect(state.generatedId) {
        val id = state.generatedId ?: return@LaunchedEffect
        viewModel.consumeGeneratedId()
        onGenerated(id)
    }

    // reações aos 403 (placeholders)
    when (state.error) {
        GenerateError.Entitlement -> PremiumDialog(onDismiss = { viewModel.onEvent(ProgramGenerateEvent.DismissError) })
        GenerateError.HealthGate -> HealthGateDialog(onDismiss = { viewModel.onEvent(ProgramGenerateEvent.DismissError) })
        is GenerateError.Other -> {} // mostrado inline abaixo
        null -> {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comum_criar_com_ia)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.comum_voltar)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.programa_gerar_explicacao),
                style = MaterialTheme.typography.bodyMedium,
            )

            // ⚠️ TEXTO DO SERVIDOR, não traduzido. Enumerado no `TextoDoServidorTest`.
            (state.error as? GenerateError.Other)?.let {
                Text(Frase.DoServidor(it.message).resolver(), color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.onEvent(ProgramGenerateEvent.Generate) },
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isGenerating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.programa_gerar_gerando))
                    }
                } else {
                    Text(stringResource(R.string.programa_gerar_acao))
                }
            }
        }
    }
}

@Composable
private fun PremiumDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.programa_gerar_limite_titulo)) },
        text = { Text(stringResource(R.string.programa_gerar_limite_texto)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.comum_entendi)) }
        },
    )
}

@Composable
private fun HealthGateDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.programa_gerar_saude_titulo)) },
        text = { Text(stringResource(R.string.programa_gerar_saude_texto)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.comum_entendi)) }
        },
    )
}
