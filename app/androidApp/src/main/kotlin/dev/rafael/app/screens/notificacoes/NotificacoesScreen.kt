package dev.rafael.app.screens.notificacoes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.ErroInline
import dev.rafael.contract.notificacao.NotificacaoDto
import org.koin.androidx.compose.koinViewModel

/** Os tipos que o servidor manda. Espelha `Aviso.TIPO_*`. */
private const val TIPO_PEDIDO_DE_AMIZADE = "PEDIDO_DE_AMIZADE"

/**
 * A central de notificações (F.1) — o que o ícone da barra abre.
 *
 * **Tudo que for notificação passa por aqui**, e é o ponto único de aviso do app.
 *
 * O texto vem PRONTO do servidor (`title`/`body`): a tela não monta frase. Assim a notificação de
 * ontem continua dizendo o que dizia, mesmo que a pessoa citada troque de nome depois.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificacoesScreen(
    onBack: () -> Unit,
    onAbrir: (NotificacaoDto) -> Unit,
    viewModel: NotificacoesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.carregar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notificações") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        if (state.itens.isEmpty() && !state.carregando) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nada por aqui ainda.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.erro?.let { item { ErroInline(it, Modifier.padding(bottom = 8.dp)) } }

            items(state.itens, key = { it.id }) { n -> Item(n) { onAbrir(n) } }
        }
    }
}

/**
 * Uma notificação na lista.
 *
 * O destaque de "não lida" usa o `readAt` do SNAPSHOT carregado, e não o estado atual do servidor
 * — a tela já marcou tudo como lido ao abrir. Sem isso a pessoa perderia justamente a informação
 * que a fez abrir a tela: o que é novo.
 */
@Composable
private fun Item(n: NotificacaoDto, onClick: () -> Unit) {
    val naoLida = n.readAt == null

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (naoLida) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            iconeDe(n.type),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                n.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (naoLida) FontWeight.Medium else FontWeight.Normal,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                n.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (naoLida) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * Ícone por tipo. Tipo desconhecido — de uma versão futura do servidor — cai no genérico em vez
 * de derrubar a tela. Mesma escolha do catálogo de conquistas com id removido.
 */
private fun iconeDe(tipo: String): ImageVector = when (tipo) {
    TIPO_PEDIDO_DE_AMIZADE -> Icons.Outlined.PersonAdd
    else -> Icons.Outlined.NotificationsNone
}
