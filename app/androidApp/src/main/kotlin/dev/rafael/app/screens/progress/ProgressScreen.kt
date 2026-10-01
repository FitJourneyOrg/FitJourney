package dev.rafael.app.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.R
import org.koin.androidx.compose.koinViewModel

/**
 * Progresso.
 *
 * ## O que saiu daqui, e por quê (desmembramento de 2026-10-01)
 *
 * A tela acumulava três assuntos: métricas, a porta das conquistas e o histórico inteiro. Três
 * coisas numa tela não é uma tela de três coisas — é uma tela sem assunto.
 *
 * **Conquistas** e **Histórico** foram para o drawer, cada um com tela própria. Os dois são
 * CONSULTA: quem abre o app para treinar não passa por eles; quem quer vê-los vem de propósito.
 *
 * O que fica aqui é análise do treino: as métricas de hoje e, na fatia seguinte, carga acumulada,
 * recordes e a progressão de carga no tempo.
 *
 * ⚠️ Enquanto a fatia de análise não chega, esta tela é **magra de propósito** — três números. Não
 * há aviso de "em breve": espaço vazio é honesto, promessa na tela é dívida.
 */
@Composable
fun ProgressScreen(viewModel: ProgressViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.sincronizar() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            stringResource(R.string.comum_progresso),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        state.stats?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metrica(
                    stringResource(R.string.progresso_metrica_treinos),
                    "${s.totalSessions}",
                    Modifier.weight(1f),
                )
                Metrica(
                    stringResource(R.string.progresso_metrica_semana),
                    "${s.sessionsThisWeek}",
                    Modifier.weight(1f),
                )
                Metrica(
                    stringResource(R.string.progresso_metrica_sequencia),
                    "${s.streakDays}",
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Metrica(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                rotulo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
