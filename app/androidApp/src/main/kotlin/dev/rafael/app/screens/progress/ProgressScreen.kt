package dev.rafael.app.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.data.session.SessaoLocal
import dev.rafael.app.ui.ShimmerList
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.sincronizar() }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Progresso", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        state.stats?.let { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metrica("Treinos", "${s.totalSessions}", Modifier.weight(1f))
                Metrica("Nesta semana", "${s.sessionsThisWeek}", Modifier.weight(1f))
                Metrica("Sequência", "${s.streakDays}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
        }

        Text(
            "HISTÓRICO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        when {
            state.carregandoInicial -> ShimmerList(rows = 5)
            state.historico.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                Text(
                    "Nenhum treino registrado ainda.\nSeu histórico aparece aqui depois do primeiro treino.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.historico) { sessao -> LinhaSessao(sessao) }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun Metrica(rotulo: String, valor: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Uma sessão do histórico. Pendente = feita offline, ainda não confirmada pelo servidor.
 * Usa o tom apagado (`tertiaryContainer` = VoltDim) — mesma gramática do "XP pendente" do
 * check-in de grupo: pendente é sempre apagado, confirmado é aceso.
 */
@Composable
private fun LinhaSessao(sessao: SessaoLocal) {
    val feitas = sessao.dto.sets.count { it.done }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(sessao.dto.workoutName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    "${dataCurta(sessao.dto.finishedAt)} · $feitas séries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sessao.pendente) {
                Icon(
                    Icons.Outlined.CloudQueue, contentDescription = "Aguardando sincronizar",
                    tint = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "pendente",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
        }
    }
}

/** "2026-08-13T18:40:12" -> "13/08 18:40". Sem parse: o formato é fixo (ISO local). */
private fun dataCurta(iso: String): String = runCatching {
    val (data, hora) = iso.split("T")
    val (_, mes, dia) = data.split("-")
    "$dia/$mes ${hora.take(5)}"
}.getOrDefault(iso.take(16))
