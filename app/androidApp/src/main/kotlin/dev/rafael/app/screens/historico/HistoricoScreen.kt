package dev.rafael.app.screens.historico

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rafael.app.R
import dev.rafael.app.ui.ShimmerList
import dev.rafael.features.session.domain.SessaoLocal
import org.koin.androidx.compose.koinViewModel

/**
 * O histórico de treinos, em tela própria desde o desmembramento do Progresso (2026-10-01).
 *
 * Alcançada pelo drawer, não por aba: é consulta, não rotina. Quem abre o app para treinar não
 * passa por aqui; quem quer conferir o que fez na semana passada vem de propósito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoricoScreen(
    onBack: () -> Unit,
    viewModel: HistoricoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_historico)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.comum_voltar),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            when {
                state.carregandoInicial -> ShimmerList(rows = 5)
                state.sessoes.isEmpty() -> Text(
                    stringResource(R.string.progresso_vazio),
                    Modifier.align(Alignment.TopCenter).padding(top = 40.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // Puxar pertence à LISTA, não à tela: vazio aqui é falta de CONTEÚDO ("nenhum
                // treino ainda"), não falta de dado por sync -- mesma régua do ExerciseLibrary.
                else -> PullToRefreshBox(
                    isRefreshing = state.sincronizando,
                    onRefresh = { viewModel.sincronizar() },
                ) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.sessoes) { sessao -> LinhaSessao(sessao) }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
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
                Text(
                    sessao.dto.workoutName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
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
                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(18.dp),
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
 * É `@Composable` desde a G.3: a ordem dia/mês é do pt-BR e precisa sair do Kotlin para o
 * catálogo, onde en-US pode invertê-la.
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
