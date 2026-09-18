package dev.rafael.app.screens.progress

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
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
import dev.rafael.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

@Composable
fun ProgressScreen(
    onOpenConquistas: () -> Unit,
    viewModel: ProgressViewModel = koinViewModel(),
) {
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
                Metrica(stringResource(R.string.progresso_metrica_treinos), "${s.totalSessions}", Modifier.weight(1f))
                Metrica(stringResource(R.string.progresso_metrica_semana), "${s.sessionsThisWeek}", Modifier.weight(1f))
                Metrica(stringResource(R.string.progresso_metrica_sequencia), "${s.streakDays}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }

        // Porta para as conquistas (ARCH #16). Fica aqui, e não como aba, porque conquista é
        // consequência do progresso — quem abre esta tela já está olhando a própria evolução.
        // `tertiary` (lime) é permitido: recompensa do perfil individual.
        Card(
            onClick = onOpenConquistas,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.comum_conquistas), fontWeight = FontWeight.Bold)
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.progresso_secao_historico),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        when {
            state.carregandoInicial -> ShimmerList(rows = 5)
            state.historico.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), Alignment.Center) {
                Text(
                    stringResource(R.string.progresso_vazio),
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
                    dataCurta(sessao.dto.finishedAt) + " · " +
                        pluralStringResource(R.plurals.progresso_series, feitas, feitas),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (sessao.pendente) {
                Icon(
                    Icons.Outlined.CloudQueue,
                    contentDescription = stringResource(R.string.progresso_aguardando_sync),
                    tint = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.progresso_pendente),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
        }
    }
}

/**
 * "2026-08-13T18:40:12" -> "13/08 18:40". Sem parse: o formato é fixo (ISO local).
 *
 * Virou `@Composable` na G.3: a ordem dia/mês é do pt-BR e precisa sair do Kotlin para o catálogo,
 * onde en-US pode invertê-la.
 *
 * ⚠️ O `stringResource` fica FORA do `runCatching`. Chamada de composable dentro de `try/catch` é
 * terreno em que o compilador do Compose já foi restritivo; separar o que pode falhar (a quebra da
 * string) do que compõe (a formatação) custa três linhas e não depende dessa garantia.
 */
@Composable
private fun dataCurta(iso: String): String {
    val partes = runCatching {
        val (data, hora) = iso.split("T")
        val (_, mes, dia) = data.split("-")
        Triple(dia, mes, hora.take(5))
    }.getOrNull() ?: return iso.take(16)

    return stringResource(R.string.progresso_data, partes.first, partes.second, partes.third)
}
