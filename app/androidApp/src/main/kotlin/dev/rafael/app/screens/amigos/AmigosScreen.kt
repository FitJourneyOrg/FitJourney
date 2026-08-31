package dev.rafael.app.screens.amigos

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.push.AvisosDePush
import dev.rafael.app.ui.AvatarInicial
import dev.rafael.app.ui.ErroInline
import dev.rafael.contract.friendship.FriendRequestDto
import dev.rafael.contract.friendship.PersonDto
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class Aba(val titulo: String) { AMIGOS("Amigos"), PEDIDOS("Pedidos") }

/**
 * Amigos e pedidos (ARCH #35), alcançada por **Perfil → Amigos**.
 *
 * Duas abas com `TabRow` + `HorizontalPager`, o mesmo componente do detalhe do grupo — não por
 * economia, mas porque o gesto de trocar de aba deve ser idêntico em todo o app.
 *
 * O contador fica no `Badge` da aba **Pedidos** e é o `size` da lista, não uma rota de contagem:
 * duas fontes da mesma verdade divergem no dia em que uma ganhar cache e a outra não.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmigosScreen(
    onBack: () -> Unit,
    onAbrirPerfil: (String) -> Unit,
    viewModel: AmigosViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pager = rememberPagerState { Aba.entries.size }
    val escopo = rememberCoroutineScope()
    var confirmarRegenerar by remember { mutableStateOf(false) }

    // ON_START: voltar do perfil de alguém que acabei de aceitar precisa refletir a mudança.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.carregar() }

    // ...e o push, que muda a lista SEM a pessoa fazer nada. O `ON_START` cobre quem sai e volta;
    // este cobre quem está justamente olhando a tela quando o pedido chega — que é o caso em que
    // o badge desatualizado é mais evidente.
    //
    // Fica na TELA e não no ViewModel pelo mesmo motivo do `LifecycleEventEffect` acima: os dois
    // são eventos de plataforma dizendo "agora", e o ViewModel só precisa saber recarregar.
    val avisos: AvisosDePush = koinInject()
    LaunchedEffect(Unit) { avisos.eventos.collect { viewModel.carregar() } }

    // A busca por código abre o PERFIL ([REGRA] #35) — nunca manda pedido direto.
    LaunchedEffect(state.achado) {
        state.achado?.let {
            onAbrirPerfil(it)
            viewModel.buscaConsumida()
        }
    }

    if (confirmarRegenerar) {
        AlertDialog(
            onDismissRequest = { confirmarRegenerar = false },
            title = { Text("Gerar um código novo?") },
            text = {
                Text(
                    // O aviso é o ponto do diálogo: regenerar é a defesa contra importunação, mas
                    // tem custo — quem já recebeu seu código não te encontra mais.
                    "O código atual para de funcionar na hora. Quem já tem o antigo não vai " +
                        "conseguir te encontrar.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarRegenerar = false; viewModel.regenerarCodigo() }) {
                    Text("Gerar novo")
                }
            },
            dismissButton = { TextButton(onClick = { confirmarRegenerar = false }) { Text("Cancelar") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Amigos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pager.currentPage) {
                Aba.entries.forEachIndexed { i, aba ->
                    Tab(
                        selected = pager.currentPage == i,
                        onClick = { escopo.launch { pager.animateScrollToPage(i) } },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(aba.titulo)
                                if (aba == Aba.PEDIDOS && state.pendentes > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Badge { Text("${state.pendentes}") }
                                }
                            }
                        },
                    )
                }
            }

            // `weight(1f)` e não `fillMaxSize()`: dentro de uma Column, preencher tudo empurraria
            // as abas para fora da tela. Mesma armadilha do detalhe do grupo.
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { pagina ->
                when (Aba.entries[pagina]) {
                    Aba.AMIGOS -> AbaDeAmigos(
                        state = state,
                        onAbrirPerfil = onAbrirPerfil,
                        onBuscar = viewModel::buscarPorCodigo,
                        onLimparBusca = viewModel::limparErroDaBusca,
                        onRegenerar = { confirmarRegenerar = true },
                    )

                    Aba.PEDIDOS -> AbaDePedidos(
                        pedidos = state.pedidos,
                        carregando = state.carregando,
                        ocupado = state.ocupado,
                        onAbrirPerfil = onAbrirPerfil,
                        onAceitar = viewModel::aceitar,
                        onRecusar = viewModel::recusar,
                    )
                }
            }
        }
    }
}

@Composable
private fun AbaDeAmigos(
    state: AmigosState,
    onAbrirPerfil: (String) -> Unit,
    onBuscar: (String) -> Unit,
    onLimparBusca: () -> Unit,
    onRegenerar: () -> Unit,
) {
    var codigo by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            MeuCodigo(state.meuCodigo, onRegenerar)
            Spacer(Modifier.height(20.dp))

            /*
             * O campo de adicionar fica JUNTO do meu código, e não numa tela separada.
             *
             * Adicionar alguém exige que a outra pessoa saiba o código dela — o par natural do
             * gesto é "aqui está o meu, cole o dele". Separar as duas coisas obrigaria a pessoa a
             * navegar entre duas telas no meio de uma conversa.
             */
            Text(
                "ADICIONAR POR CÓDIGO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it.uppercase(); onLimparBusca() },
                    modifier = Modifier.weight(1f),
                    label = { Text("Código de 8 caracteres") },
                    singleLine = true,
                    // Maiúsculas no teclado: o código é sempre maiúsculo, e obrigar a pessoa a
                    // trocar o shift oito vezes seria hostil. Quem normaliza de fato é o servidor.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    isError = state.erroDaBusca != null,
                )
                Button(
                    onClick = { onBuscar(codigo) },
                    enabled = codigo.isNotBlank() && !state.buscando,
                ) { Text("Buscar") }
            }
            state.erroDaBusca?.let {
                Spacer(Modifier.height(6.dp))
                ErroInline(it)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "MEUS AMIGOS · ${state.amigos.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.amigos.isEmpty() && !state.carregando) {
            item {
                Text(
                    "Você ainda não tem amigos aqui. Passe seu código para alguém que treina " +
                        "com você.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        items(state.amigos, key = { it.userId }) { p ->
            LinhaDePessoa(p, onClick = { onAbrirPerfil(p.userId) })
        }
    }
}

/**
 * O meu código, com copiar e regenerar.
 *
 * Mostrado por extenso e não escondido atrás de um botão: ele existe para ser LIDO em voz alta ou
 * copiado, e um código que precisa de um toque para aparecer atrapalha os dois usos.
 */
@Composable
private fun MeuCodigo(codigo: String, onRegenerar: () -> Unit) {
    val clipboard = LocalClipboard.current
    val escopo = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        Text(
            "SEU CÓDIGO",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                codigo.ifBlank { "········" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    escopo.launch {
                        clipboard.setClipEntry(
                            androidx.compose.ui.platform.ClipEntry(
                                android.content.ClipData.newPlainText("código", codigo),
                            ),
                        )
                    }
                },
                enabled = codigo.isNotBlank(),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar código")
            }
            IconButton(onClick = onRegenerar, enabled = codigo.isNotBlank()) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Gerar código novo")
            }
        }
    }
}

@Composable
private fun AbaDePedidos(
    pedidos: List<FriendRequestDto>,
    carregando: Boolean,
    ocupado: Boolean,
    onAbrirPerfil: (String) -> Unit,
    onAceitar: (String) -> Unit,
    onRecusar: (String) -> Unit,
) {
    if (pedidos.isEmpty() && !carregando) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nenhum pedido no momento.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(pedidos, key = { it.from.userId }) { pedido ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            ) {
                LinhaDePessoa(pedido.from, onClick = { onAbrirPerfil(pedido.from.userId) })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onAceitar(pedido.from.userId) },
                        enabled = !ocupado,
                        modifier = Modifier.weight(1f),
                    ) { Text("Aceitar") }
                    OutlinedButton(
                        onClick = { onRecusar(pedido.from.userId) },
                        enabled = !ocupado,
                        modifier = Modifier.weight(1f),
                    ) { Text("Recusar") }
                }
            }
        }
    }
}

@Composable
private fun LinhaDePessoa(pessoa: PersonDto, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarInicial(nome = pessoa.displayName, id = pessoa.userId, tamanho = 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            pessoa.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
