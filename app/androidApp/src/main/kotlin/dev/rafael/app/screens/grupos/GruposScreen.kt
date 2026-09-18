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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.shimmer
import dev.rafael.app.R
import dev.rafael.app.ui.ErroAcao
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.app.ui.rotulo
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
    onAbrirGrupo: (String) -> Unit,
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
                    text = { Text(stringResource(R.string.comum_criar_desafio)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.carregando && state.vazio -> Esqueleto()

                /*
                 * ⭐ SEM DADO LOCAL E COM FALHA: a tela precisa DIZER, não girar.
                 *
                 * Este ramo não existia até 2026-09-11, e a ausência dele é o defeito que o
                 * `MenuLateral.kt` já descreve num outro lugar: **carregar e falhar eram o mesmo
                 * pixel**. Com o servidor inalcançável e o cache vazio, a tela ficava em
                 * "Carregando seus desafios" para sempre — sem erro, sem retentativa, e com um
                 * `erroSync` no estado que ninguém preenchia e ninguém lia.
                 *
                 * A ordem importa: vem ANTES de `vazio`, porque "falhou" é mais específico que
                 * "está vazio", e depois de `carregando`, porque erro de uma tentativa anterior
                 * não deve cobrir a tentativa em curso.
                 *
                 * `ErroDeTela` é o nível 2 do ARCH #31 e o KDoc dele já dizia a regra que faltava
                 * aplicar: *"use SÓ quando não há dado local"*. Com lista em cache, falha de sync
                 * continua sendo silêncio.
                 */
                state.vazio && state.erroSync != null ->
                    ErroDeTela(
                        erro = state.erroSync!!,
                        onAcao = { acao ->
                            if (acao == ErroAcao.TENTAR_DE_NOVO) viewModel.atualizar()
                        },
                    )

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
                            Text(stringResource(R.string.comum_entrar_com_codigo))
                        }
                    }
                    items(state.grupos, key = { it.id }) { grupo ->
                        CartaoDeGrupo(grupo) { onAbrirGrupo(grupo.id) }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun CartaoDeGrupo(grupo: GroupDto, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
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
            pluralStringResource(R.plurals.grupo_pessoas, grupo.memberCount, grupo.memberCount) +
                " · " + stringResource(R.string.grupo_periodo, grupo.startDate, grupo.endDate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** O estado vem RESOLVIDO do servidor — o cliente não recalcula com o próprio relógio. */
@Composable
private fun Selo(estado: GroupState) {
    val cor = when (estado) {
        GroupState.AGENDADO -> MaterialTheme.colorScheme.onSurfaceVariant
        GroupState.ATIVO -> MaterialTheme.colorScheme.primary
        GroupState.ENCERRADO -> MaterialTheme.colorScheme.outline
    }
    Text(stringResource(estado.rotulo()), style = MaterialTheme.typography.labelSmall, color = cor)
}

/**
 * Esqueleto com a FORMA do cartão real — título largo em cima, linha menor embaixo — e não
 * blocos genéricos. Assim a lista não "salta" quando o conteúdo chega: o espaço já está
 * reservado no lugar certo.
 */
@Composable
private fun Esqueleto() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(40.dp).shimmer(RoundedCornerShape(20.dp)))
        Spacer(Modifier.height(2.dp))
        repeat(3) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(14.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.55f).height(16.dp).shimmer(RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.75f).height(12.dp).shimmer(RoundedCornerShape(4.dp)))
            }
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
            stringResource(
                if (jaSincronizou) R.string.grupos_vazio_titulo else R.string.grupos_carregando_titulo,
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                if (jaSincronizou) R.string.grupos_vazio_texto else R.string.grupos_carregando_texto,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (jaSincronizou) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onCriar) { Text(stringResource(R.string.comum_criar_desafio)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onEntrar) {
                Text(stringResource(R.string.comum_entrar_com_codigo))
            }
        }
    }
}
