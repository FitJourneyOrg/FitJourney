package dev.rafael.app.screens.grupos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.shimmer
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupState
import org.koin.androidx.compose.koinViewModel

/**
 * A aba Grupos (ARCH #33, fatia A.3). Cache-first: a lista pinta no primeiro frame, offline
 * inclusive; o sync de fundo só atualiza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GruposScreen(
    onCriar: () -> Unit,
    onEntrarPorCodigo: () -> Unit,
    viewModel: GruposViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Recarrega ao voltar: o grupo pode ter mudado de estado (a data virou) enquanto a tela
    // estava em segundo plano — e estado é derivado do relógio do SERVIDOR.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.carregar() }

    Scaffold(
        floatingActionButton = {
            if (!state.vazio) {
                ExtendedFloatingActionButton(
                    onClick = onCriar,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Criar desafio") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.carregando && state.vazio -> Esqueleto()
                state.vazio -> Vazio(state.jaSincronizou, onCriar, onEntrarPorCodigo)
                else -> PullToRefreshBox(
                    isRefreshing = state.atualizando,
                    onRefresh = viewModel::atualizar,
                ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        OutlinedButton(onClick = onEntrarPorCodigo, modifier = Modifier.fillMaxWidth()) {
                            Text("Entrar com um código")
                        }
                    }
                    // O cartão ainda não abre nada: a tela de detalhe e a gerência de membros
                    // são a fatia A.4. Preferi um cartão que informa a um cartão que promete.
                    items(state.grupos, key = { it.id }) { grupo -> CartaoDeGrupo(grupo) }
                }
                }
            }
        }
    }
}

@Composable
private fun CartaoDeGrupo(grupo: GroupDto) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                grupo.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Selo(grupo.state)
        }
        Spacer(Modifier.height(6.dp))
        // Sem `lime` em lugar nenhum deste cartão: [REGRA] ARCH #16, a cor é exclusiva do perfil
        // individual e não pode aparecer em contexto de grupo.
        Text(
            "${grupo.memberCount} ${if (grupo.memberCount == 1) "pessoa" else "pessoas"} · " +
                "${grupo.startDate} a ${grupo.endDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** O estado vem RESOLVIDO do servidor — o cliente não recalcula com o próprio relógio. */
@Composable
private fun Selo(estado: GroupState) {
    val (texto, cor) = when (estado) {
        GroupState.AGENDADO -> "Começa em breve" to MaterialTheme.colorScheme.onSurfaceVariant
        GroupState.ATIVO -> "Em andamento" to MaterialTheme.colorScheme.primary
        GroupState.ENCERRADO -> "Encerrado" to MaterialTheme.colorScheme.outline
    }
    Text(texto, style = MaterialTheme.typography.labelSmall, color = cor)
}

@Composable
private fun Esqueleto() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .shimmer(RoundedCornerShape(12.dp)),
            )
        }
    }
}

/**
 * Vazio de VERDADE ≠ vazio por falta de sync.
 *
 * Mesma distinção da Home: dizer "você não participa de nenhum grupo" a quem só não baixou
 * ainda convida a pessoa a criar um grupo que talvez já exista.
 */
@Composable
private fun Vazio(jaSincronizou: Boolean, onCriar: () -> Unit, onEntrar: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Group,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (jaSincronizou) "Nenhum desafio ainda" else "Carregando seus desafios",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (jaSincronizou) {
                "Crie um desafio e convide quem treina com você — ou entre em um com o código."
            } else {
                "Se você já participa de algum, ele aparece assim que a lista chegar."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (jaSincronizou) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onCriar) { Text("Criar desafio") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEntrar) { Text("Entrar com um código") }
        }
    }
}
