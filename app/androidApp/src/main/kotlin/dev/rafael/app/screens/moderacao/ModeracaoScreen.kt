package dev.rafael.app.screens.moderacao

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.AvatarInicial
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.NetworkImage
import dev.rafael.contract.checkin.ReportItemDto
import dev.rafael.contract.checkin.ReportTarget
import dev.rafael.core.network.HttpClientFactory
import org.koin.androidx.compose.koinViewModel

/**
 * A FILA DE MODERAÇÃO do admin (fatia E.2, seção 6).
 *
 * ## O que esta tela NÃO mostra, e por quê
 *
 * **Quem denunciou.** O servidor guarda, o DTO não carrega. Num grupo de conhecidos, denúncia
 * identificada é denúncia que ninguém faz — e uma fila vazia com o problema intacto é pior que
 * nenhuma fila. O admin julga **o conteúdo**, com o contador e os motivos escritos.
 *
 * ## Duas ações, e nenhuma delas é "adiar"
 *
 * Acatar ou manter. Não há "decidir depois" porque 6.12 já diz que o admin não tem prazo: deixar
 * na fila É adiar, e um botão para isso só faria a fila parecer menor sem ela ser.
 *
 * ## Cada decisão é irreversível
 *
 * Não há recurso (6.7) e o registro é append-only (6.6). Por isso as duas ações passam por
 * confirmação — inclusive "manter", que fecha o caso e some da fila.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeracaoScreen(
    groupId: String,
    onBack: () -> Unit,
    onAbrirPerfil: (String) -> Unit,
    viewModel: ModeracaoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.carregar(groupId) }

    DialogoDeJulgamento(
        decisao = state.confirmando,
        aoFechar = viewModel::cancelarConfirmacao,
        aoConfirmar = { viewModel.julgar(groupId) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Denúncias") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.erro?.let {
                ErroInline(it, modifier = Modifier.padding(16.dp))
            }

            when {
                state.carregando && state.itens.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                state.itens.isEmpty() -> FilaVazia()

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.itens, key = { it.targetId }) { item ->
                        Caso(
                            item = item,
                            ocupado = state.julgando == item.targetId,
                            onAbrirPerfil = onAbrirPerfil,
                            onJulgar = { acatar -> viewModel.pedirConfirmacao(item, acatar) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fila vazia é BOA NOTÍCIA, e o texto diz isso.
 *
 * "Nenhuma denúncia" sozinho soaria a erro de carregamento. A tela em branco de uma lista de
 * problemas precisa ser lida como "está tudo em ordem", não como "não consegui buscar".
 */
@Composable
private fun FilaVazia() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text("Nada para avaliar.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Quando alguém denunciar um check-in ou comentário, ele aparece aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Um caso: o conteúdo denunciado, o contador e os motivos.
 *
 * O CONTEÚDO vem primeiro e os motivos depois, de propósito. Ler as acusações antes de ver a foto
 * enquadraria o julgamento — o admin olharia a imagem procurando o que lhe disseram para procurar.
 */
@Composable
private fun Caso(
    item: ReportItemDto,
    ocupado: Boolean,
    onAbrirPerfil: (String) -> Unit,
    onJulgar: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when (item.target) {
            ReportTarget.CHECK_IN -> item.checkIn?.let { c ->
                c.photoUrl?.let { caminho ->
                    NetworkImage(
                        url = HttpClientFactory.BASE_URL + caminho,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                    )
                }
                Autor(c.displayName, c.userId, onAbrirPerfil)
                Text(
                    listOfNotNull(c.emoji, c.placeName, c.localDate).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            ReportTarget.COMMENT -> item.comment?.let { c ->
                Autor(c.displayName, c.userId, onAbrirPerfil)
                Text(
                    c.body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()

        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    // O contador é a informação da 6.11: uma pessoa reclamando é diferente de
                    // cinco, e é o que o admin tem no lugar dos nomes.
                    if (item.count == 1) "1 denúncia" else "${item.count} denúncias",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(6.dp))
            // Uma linha por motivo, e não um parágrafo só: três pessoas dizendo a mesma coisa é
            // informação diferente de uma dizendo três, e só a lista preserva a distinção.
            item.reasons.forEach { motivo ->
                Text(
                    "• $motivo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // "Manter" primeiro e neutro; "acatar" depois e em vermelho. A ordem e a cor
                // colocam o peso do gesto do lado do que é irreversível.
                OutlinedButton(
                    onClick = { onJulgar(false) },
                    enabled = !ocupado,
                    modifier = Modifier.weight(1f),
                ) { Text("Manter") }

                OutlinedButton(
                    onClick = { onJulgar(true) },
                    enabled = !ocupado,
                    modifier = Modifier.weight(1f),
                ) {
                    if (ocupado) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (item.target == ReportTarget.CHECK_IN) "Invalidar" else "Remover",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Autor(nome: String, userId: String, onAbrirPerfil: (String) -> Unit) {
    Row(
        Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // [REGRA] #35: tocar no nome abre o perfil, em qualquer superfície — inclusive aqui.
        AvatarInicial(nome = nome, id = userId, tamanho = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(nome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * A confirmação. **Também para "manter"** — e essa é a parte que se esquece.
 *
 * Manter parece inofensivo e não é: fecha o caso, tira da fila e grava uma decisão append-only que
 * ninguém pode desfazer (6.6 + 6.7). Confirmar só o destrutivo ensinaria que o outro botão é
 * reversível.
 */
@Composable
private fun DialogoDeJulgamento(
    decisao: Julgamento?,
    aoFechar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    val atual = decisao ?: return
    val ehCheckIn = atual.item.target == ReportTarget.CHECK_IN

    val titulo = when {
        !atual.acatar -> "Manter e encerrar o caso?"
        ehCheckIn -> "Invalidar este check-in?"
        else -> "Remover este comentário?"
    }
    val texto = when {
        !atual.acatar ->
            "O conteúdo continua como está e a denúncia sai da fila. A decisão não pode ser desfeita."
        ehCheckIn ->
            "A pessoa perde o ponto no ranking. O check-in continua visível, marcado como invalidado, " +
                "e a decisão não pode ser desfeita."
        else -> "O comentário some para todo mundo. A decisão não pode ser desfeita."
    }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = aoConfirmar) {
                Text(
                    if (atual.acatar) "Confirmar" else "Manter",
                    color = if (atual.acatar) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
