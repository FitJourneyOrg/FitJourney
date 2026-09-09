package dev.rafael.app.screens.comentarios

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.AvatarInicial
import dev.rafael.app.ui.DialogoDeDenuncia
import dev.rafael.app.ui.ErroInline
import dev.rafael.contract.checkin.CommentDto
import org.koin.androidx.compose.koinViewModel

/**
 * A conversa de um check-in (8.1, fatia E.1).
 *
 * ## Por que tela própria
 *
 * A alternativa era expandir no card. O feed viraria uma lista de alturas imprevisíveis que salta
 * a cada polling de 10s, e o teclado taparia metade do que a pessoa está lendo. Aqui a conversa
 * tem a tela inteira, o campo fica fixo embaixo com `imePadding`, e o back stack funciona.
 *
 * ## Sem edição
 *
 * Não há botão de editar, e a ausência é a regra (8.1): o que as pessoas leram continua sendo o
 * que está escrito. Apagar existe — para o autor e para o admin —, e quem decide é o servidor,
 * pelo `canDelete` de cada item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComentariosScreen(
    groupId: String,
    checkInId: String,
    onBack: () -> Unit,
    onAbrirPerfil: (String) -> Unit,
    viewModel: ComentariosViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lista = rememberLazyListState()

    // ON_START: voltar do perfil de alguém precisa refletir comentários que chegaram enquanto isso.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.carregar(groupId, checkInId) }

    DialogoDeDenuncia(
        alvo = state.denunciando?.let { "o comentário de ${it.displayName}" },
        ocupado = state.enviando,
        erro = state.erro,
        aoFechar = viewModel::cancelarDenuncia,
        aoEnviar = { motivo -> viewModel.denunciar(groupId, motivo) },
    )

    // Rola para o fim quando a conversa cresce — o comentário novo é o último, e ficar olhando o
    // topo depois de escrever seria não ver o próprio texto aparecer.
    LaunchedEffect(state.itens.size) {
        if (state.itens.isNotEmpty()) lista.animateScrollToItem(state.itens.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comentários") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // `imePadding` no CONTAINER, não no campo: é o que empurra a lista junto e mantém
                // o último comentário visível com o teclado aberto.
                .imePadding(),
        ) {
            Box(Modifier.weight(1f)) {
                when {
                    state.carregando && state.itens.isEmpty() ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                    state.itens.isEmpty() ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                "Ninguém comentou ainda.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                    else -> LazyColumn(
                        state = lista,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.itens, key = { it.id }) { c ->
                            Comentario(
                                comentario = c,
                                onAbrirPerfil = { onAbrirPerfil(c.userId) },
                                onApagar = { viewModel.apagar(groupId, checkInId, c.id) },
                                onDenunciar = { viewModel.pedirDenuncia(c) },
                            )
                        }
                    }
                }
            }

            // Só quando o diálogo NÃO está aberto: com ele por cima, a mesma frase apareceria duas
            // vezes — uma no diálogo e outra atrás dele.
            if (state.denunciando == null) {
                state.erro?.let {
                    ErroInline(it, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                }
            }

            // O aviso de sucesso é INLINE e some sozinho: um `AlertDialog` de "pronto" exigiria
            // mais um toque para fechar algo que a pessoa já sabe que fez.
            if (state.denunciaEnviada) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(4000)
                    viewModel.avisoVisto()
                }
                Text(
                    "Denúncia enviada. O admin do desafio vai avaliar.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            HorizontalDivider()
            CampoDeComentario(
                texto = state.rascunho,
                restantes = state.restantes,
                podeEnviar = state.podeEnviar,
                enviando = state.enviando,
                onDigitar = viewModel::aoDigitar,
                onEnviar = { viewModel.enviar(groupId, checkInId) },
            )
        }
    }
}

@Composable
private fun Comentario(
    comentario: CommentDto,
    onAbrirPerfil: () -> Unit,
    onApagar: () -> Unit,
    onDenunciar: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // O avatar e o nome levam ao perfil — em qualquer superfície, tocar no nome abre o perfil
        // ([REGRA] #35). O texto do comentário NÃO é clicável: quem quer ler não quer navegar.
        AvatarInicial(
            nome = comentario.displayName,
            id = comentario.userId,
            tamanho = 36.dp,
            modifier = Modifier.clickable(onClick = onAbrirPerfil),
        )

        Column(Modifier.weight(1f)) {
            Text(
                comentario.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onAbrirPerfil),
            )
            Spacer(Modifier.height(2.dp))
            Text(comentario.body, style = MaterialTheme.typography.bodyMedium)
        }

        // Menu, e não dois ícones soltos (fatia E.2). Apagar e denunciar podem aparecer no MESMO
        // comentário sem contradição — quem pode apagar resolve na hora, quem não pode encaminha
        // ao admin — e dois ícones ao lado de um texto de duas linhas dominariam a linha.
        //
        // Quais itens aparecem é decisão do SERVIDOR (`canDelete`/`canReport`): a tela não compara
        // ids, não consulta papel e não calcula prazo.
        if (comentario.canDelete || comentario.canReport) {
            var aberto by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { aberto = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Ações deste comentário",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
                    if (comentario.canDelete) {
                        DropdownMenuItem(
                            text = { Text("Apagar") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { aberto = false; onApagar() },
                        )
                    }
                    if (comentario.canReport) {
                        DropdownMenuItem(
                            text = { Text("Denunciar") },
                            leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                            onClick = { aberto = false; onDenunciar() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoDeComentario(
    texto: String,
    restantes: Int,
    podeEnviar: Boolean,
    enviando: Boolean,
    onDigitar: (String) -> Unit,
    onEnviar: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = onDigitar,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Escreva um comentário") },
            // Sem `singleLine`: comentário de 500 caracteres numa linha só seria ilegível enquanto
            // se escreve. Teto de 4 linhas para o campo não engolir a conversa.
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = {
                // O contador só aparece perto do limite. Mostrar "restam 487" desde a primeira
                // letra é ruído — o limite só interessa a quem está chegando nele.
                if (restantes <= 50) {
                    Text(
                        "$restantes",
                        color = if (restantes == 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
        )

        IconButton(onClick = onEnviar, enabled = podeEnviar) {
            if (enviando) {
                CircularProgressIndicator(Modifier.width(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
            }
        }
    }
}
