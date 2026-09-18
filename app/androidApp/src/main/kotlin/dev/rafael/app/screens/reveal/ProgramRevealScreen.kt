package dev.rafael.app.screens.reveal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.Frase
import dev.rafael.app.ui.nomeDoPrograma
import dev.rafael.app.ui.resolver
import dev.rafael.app.R
import dev.rafael.core.result.AppError
import dev.rafael.features.program.domain.model.ProgramWorkout
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProgramRevealScreen(
    onDone: () -> Unit,
    onOpenPaywall: () -> Unit,
    viewModel: ProgramRevealViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // ao voltar do Paywall (ou do fundo), re-busca o programa — já desbloqueado se assinou.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }

    Box(Modifier.fillMaxSize()) {
        when {
            state.isGenerating -> GeneratingView()
            state.program == null -> ErrorView(state.error, onRetry = viewModel::retry)
            else -> RevealContent(
                name = state.program!!.name,
                daysPerWeek = state.program!!.daysPerWeek,
                split = state.program!!.split,
                rationale = state.program!!.rationale,
                workouts = state.program!!.workouts,
                locked = state.locked,
                onOpenPaywall = onOpenPaywall,
                onDone = onDone,
            )
        }
    }
}

@Composable
private fun GeneratingView() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.programa_reveal_montando), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.programa_reveal_montando_texto),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorView(error: AppError?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.programa_reveal_erro), style = MaterialTheme.typography.titleMedium)
        error?.let {
            Spacer(Modifier.height(6.dp))
            ErroInline(it)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.programa_reveal_tentar_de_novo)) }
    }
}

@Composable
private fun RevealContent(
    name: String,
    daysPerWeek: Int,
    split: String,
    rationale: String,
    workouts: List<ProgramWorkout>,
    locked: Boolean,
    onOpenPaywall: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (locked) R.string.programa_reveal_pronto_trancado
                    else R.string.programa_reveal_pronto_liberado,
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            // G.5: o servidor deixa o nome em branco para o programa gerado; quem monta o rótulo
            // é o cliente, no idioma da tela. Ver `NomeDePrograma.kt`.
            Text(nomeDoPrograma(name, daysPerWeek, split), style = MaterialTheme.typography.titleMedium)
            // `split` é o nome do modelo vindo do servidor ("Push/Pull/Legs"): jargão de
            // academia, igual em qualquer idioma. Ver `SplitType.rotulo()`.
            Text(
                stringResource(R.string.programa_reveal_resumo, daysPerWeek, split),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // ⚠️ TEXTO DO SERVIDOR, não traduzido. O `rationale` é uma frase em português
            // montada pelo `StructureEngine`. Enumerado no `TextoDoServidorTest`; a saída é o
            // servidor mandar código + parâmetros, como a G.2 fez com os erros.
            if (rationale.isNotBlank()) {
                Text(Frase.DoServidor(rationale).resolver(), style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.programa_reveal_semana),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            workouts.forEachIndexed { i, w -> WorkoutRow(day = i + 1, w = w) }

            if (locked) {
                Spacer(Modifier.height(8.dp))
                BenefitsCard(daysPerWeek)
            }
        }

        // Rodapé fixo com o CTA — o ponto de conversão.
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (locked) {
                    Button(onClick = onOpenPaywall, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.comum_desbloquear_treinos))
                    }
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.programa_reveal_comecar_gratis))
                    }
                } else {
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.programa_reveal_comecar))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(day: Int, w: ProgramWorkout) {
    ListItem(
        overlineContent = { Text(stringResource(R.string.programa_reveal_dia, day)) },
        headlineContent = { Text(w.name) },
        supportingContent = {
            Text(
                pluralStringResource(R.plurals.treino_exercicios, w.exerciseCount, w.exerciseCount) +
                    " · " + stringResource(
                        if (w.locked) R.string.comum_premium
                        else R.string.comum_gratis,
                    ),
            )
        },
        leadingContent = {
            if (w.locked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringResource(R.string.programa_reveal_trancado),
                )
            } else {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.programa_reveal_liberado),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
    HorizontalDivider()
}

@Composable
private fun BenefitsCard(daysPerWeek: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.programa_reveal_beneficios_titulo),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // ⚠️ `map` antes do `forEach` não seria necessário aqui (forEach é inline), mas os
            // `stringResource` ficam na lista, resolvidos em composição.
            listOf(
                stringResource(R.string.programa_reveal_beneficio_treinos, daysPerWeek),
                stringResource(R.string.programa_reveal_beneficio_trocar),
                stringResource(R.string.programa_reveal_beneficio_reagendar),
            ).forEach { benefit ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(benefit, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
