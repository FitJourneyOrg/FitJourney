package dev.rafael.app.screens.grupos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.rafael.app.ui.AvatarInicial
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.NetworkImage
import dev.rafael.app.ui.shimmer
import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupMemberDto
import dev.rafael.contract.group.GroupState
import dev.rafael.contract.group.MemberRole
import dev.rafael.core.network.HttpClientFactory
import org.koin.androidx.compose.koinViewModel

/**
 * Detalhe do grupo (fatia A.4): quem participa, o código, o convite e as ações do admin.
 *
 * As ações de admin não aparecem para membro comum — mas quem decide é o servidor, que recusa
 * com `Forbidden`. Esconder o botão é conveniência, nunca a garantia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrupoDetalheScreen(
    groupId: String,
    onBack: () -> Unit,
    onCheckIn: () -> Unit,
    viewModel: GrupoDetalheViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val area = LocalClipboardManager.current
    val contexto = LocalContext.current
    var confirmarSaida by remember { mutableStateOf(false) }
    var alvo by remember { mutableStateOf<GroupMemberDto?>(null) }
    var alvoParaApagar by remember { mutableStateOf<CheckInDto?>(null) }

    LaunchedEffect(state.saiu) { if (state.saiu) onBack() }

    // ON_START e não `LaunchedEffect(groupId)`: voltando do check-in a composição é RESTAURADA da
    // pilha, e um `LaunchedEffect` já executado não roda de novo — a tela ficaria com o botão
    // "Fazer check-in" depois de a pessoa ter acabado de fazer um.
    //
    // O polling (8.3) vive no mesmo par ON_START/ON_STOP: em segundo plano, um laço de 10s é
    // bateria e requisição que ninguém pediu, para uma tela que ninguém está vendo.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.carregar(groupId)
        viewModel.iniciarEnquete(groupId)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pararEnquete() }

    alvoParaApagar?.let { item ->
        AlertDialog(
            onDismissRequest = { alvoParaApagar = null },
            title = { Text("Apagar este check-in?") },
            text = {
                Text(
                    // A 4.11 dita ANTES: apagar libera o slot do dia, então dá para refazer hoje.
                    // Sem isso a pessoa hesita achando que perde o dia.
                    "O dia fica livre de novo — você pode fazer outro check-in hoje. Depois da meia-noite não dá mais para apagar.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    alvoParaApagar = null
                    viewModel.apagarCheckIn(groupId, item.id)
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { alvoParaApagar = null }) { Text("Cancelar") } },
        )
    }

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            // Duas saídas MUITO diferentes atrás do mesmo botão (2.5-A): o membro sai de um
            // desafio que continua existindo; o admin sozinho leva o desafio junto. Um diálogo
            // que dissesse a mesma coisa nos dois casos estaria mentindo em um deles — e no que
            // é irreversível.
            title = { Text(if (state.souUltimo) "Excluir o desafio?" else "Sair do desafio?") },
            text = {
                Text(
                    if (state.souUltimo) {
                        "Você é a única pessoa aqui. Sair apaga este desafio — o código deixa de valer e não dá para desfazer."
                    } else {
                        // Diz a verdade sobre o que fica: os check-ins permanecem no histórico do
                        // grupo (2.6). Omitir isso faria a pessoa sair achando que apagou tudo.
                        "Você deixa de aparecer no ranking. Os seus check-ins continuam no histórico do grupo."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmarSaida = false; viewModel.sair(groupId) }) {
                    Text(if (state.souUltimo) "Excluir" else "Sair")
                }
            },
            dismissButton = { TextButton(onClick = { confirmarSaida = false }) { Text("Cancelar") } },
        )
    }

    alvo?.let { m ->
        AlertDialog(
            onDismissRequest = { alvo = null },
            title = { Text(m.displayName) },
            text = { Text("O que você quer fazer com esta pessoa?") },
            confirmButton = {
                TextButton(onClick = { alvo = null; viewModel.transferirAdmin(groupId, m.userId) }) {
                    Text("Tornar admin")
                }
            },
            dismissButton = {
                TextButton(onClick = { alvo = null; viewModel.expulsar(groupId, m.userId) }) {
                    Text("Remover do grupo", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.grupo?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            val grupo = state.grupo
            if (state.carregando && grupo == null) {
                Spacer(Modifier.height(40.dp))
                CircularProgressIndicator(Modifier.padding(24.dp))
                return@Column
            }
            if (grupo == null) {
                state.erro?.let { ErroInline(it, Modifier.padding(24.dp)) }
                return@Column
            }

            grupo.bannerUrl?.let { url ->
                AsyncImage(
                    model = HttpClientFactory.BASE_URL.removeSuffix("/") + url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(BANNER_RATIO)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            Column(Modifier.padding(16.dp)) {
                Selo(grupo.state)
                Spacer(Modifier.height(6.dp))
                grupo.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                }
                Linha("Período", "${grupo.startDate} a ${grupo.endDate}")
                Linha("Fuso", grupo.timezone)
                if (grupo.rules.isNotEmpty()) {
                    Linha("Check-in exige", grupo.rules.joinToString(", ") { it.name.lowercase().replace('_', ' ') })
                }

                // CÓDIGO e CONVIDAR só existem em `AGENDADO` — [REGRA] tabela 2-B: "única janela
                // de ENTRADA. Ver regras, convidar, sair". Em `ATIVO`/`ENCERRADO` a entrada está
                // fechada, e o código não abre porta nenhuma.
                //
                // A primeira versão mostrava o botão sempre e avisava por baixo que já não
                // adiantava. Um botão primário, azul, de largura inteira, com um rodapé cinza
                // dizendo que não funciona: a hierarquia visual dizia "clique" e o texto dizia
                // "não adianta". Quando os dois discordam, quem ganha é o botão. Oferecer só o
                // que é possível é mais honesto do que oferecer e desmentir.
                //
                // O código é visível para TODO membro, não só para o admin: ele é identidade do
                // grupo, e a 2-B não reserva "convidar" a ninguém em especial.
                if (grupo.state == GroupState.AGENDADO) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "CÓDIGO DO DESAFIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(grupo.code, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                        }
                        TextButton(onClick = { area.setText(AnnotatedString(grupo.code)) }) { Text("Copiar") }
                    }

                    Spacer(Modifier.height(10.dp))
                    // CONVIDAR compartilha o CÓDIGO, não um link.
                    //
                    // O token de convite existe no servidor desde a A.2, mas link só funciona com
                    // deep link — e deep link só funciona de verdade com um domínio https e App
                    // Links, porque as mensagerias não transformam `fitjourney://` em algo
                    // clicável. Sem domínio definido, um botão de "copiar link" entregaria um UUID
                    // que ninguém consegue usar. O código funciona hoje, em qualquer aplicativo.
                    Button(
                        onClick = {
                            contexto.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Entra no meu desafio no FitJourney: \"${grupo.title}\".\n" +
                                                "Use o código ${grupo.code} para entrar.",
                                        )
                                    },
                                    "Convidar para o desafio",
                                ),
                            )
                        },
                        enabled = !state.ocupado,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Convidar") }
                }

                state.erro?.let {
                    Spacer(Modifier.height(10.dp))
                    ErroInline(it)
                }

                // CHECK-IN só com o desafio ATIVO ([INV]) e só se AINDA NÃO FIZ hoje (4.3).
                //
                // A segunda condição custou uma tela inteira de trabalho jogado fora no teste:
                // sem ela a pessoa tira a foto, espera o GPS, edita o texto, envia — e só então
                // lê "você já fez check-in hoje". É o mesmo defeito do convite num desafio já
                // começado: oferecer e desmentir. Quem decide é o servidor (`myCheckInToday`),
                // porque "hoje" depende do fuso do grupo e do relógio dele.
                if (grupo.state == GroupState.ATIVO) {
                    Spacer(Modifier.height(16.dp))
                    if (grupo.myCheckInToday == null) {
                        Button(onClick = onCheckIn, modifier = Modifier.fillMaxWidth()) {
                            Text("Fazer check-in")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                // Diz o que dá para fazer, e não só o que não dá: apagar libera o
                                // dia (4.11), e sem essa frase a pessoa não descobre sozinha.
                                "Você já treinou hoje. Para refazer, apague o check-in abaixo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Text(
                    "PARTICIPANTES · ${grupo.memberCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                state.membros.forEach { m ->
                    Membro(
                        membro = m,
                        // Admin não age sobre si mesmo: para sair existe o botão de sair.
                        podeAgir = state.souAdmin && m.role != MemberRole.ADMIN,
                        onAgir = { alvo = m },
                    )
                }

                // O FEED (8.0). Fica DEPOIS dos participantes de propósito: quem abre o grupo
                // quer ver o que aconteceu, e o que aconteceu está aqui embaixo — mas a
                // identidade do desafio (quem está, quais as regras) vem primeiro.
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                Text(
                    "CHECK-INS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Feed(
                    itens = state.feed,
                    carregando = state.carregandoFeed,
                    temMais = state.temMais,
                    carregandoMais = state.carregandoMais,
                    onApagar = { alvoParaApagar = it },
                    onCarregarMais = { viewModel.carregarMais(groupId) },
                )

                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { confirmarSaida = true }, enabled = !state.ocupado) {
                    Text("Sair do desafio", color = MaterialTheme.colorScheme.error)
                }
                // A regra dita ANTES do erro — mas só quando ela vale. Enquanto o admin era o
                // único membro, este aviso mandava transferir o cargo para alguém que não
                // existia: instrução impossível embaixo de um botão que sempre falhava.
                if (state.souAdmin && !state.souUltimo) {
                    Text(
                        "Como admin, você precisa passar o cargo para alguém antes de sair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Membro(membro: GroupMemberDto, podeAgir: Boolean, onAgir: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarInicial(nome = membro.displayName, id = membro.userId, tamanho = 34.dp)
        Text(membro.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (membro.role == MemberRole.ADMIN) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = "Admin",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (podeAgir) {
            IconButton(onClick = onAgir) {
                Icon(Icons.Outlined.Close, contentDescription = "Gerenciar ${membro.displayName}")
            }
        }
    }
}

/**
 * O FEED do grupo (8.0).
 *
 * **O que cada item mostra está fechado pela 8.0.2/8.0.3**: quem, foto, lugar. Nada de XP, nível
 * ou histórico individual (9.3) e nada de e-mail (#33) — e a garantia não é este código, é o
 * `CheckInDto`, que não tem esses campos. A tela não teria como vazá-los nem se quisesse.
 */
@Composable
private fun Feed(
    itens: List<CheckInDto>,
    carregando: Boolean,
    temMais: Boolean,
    carregandoMais: Boolean,
    onApagar: (CheckInDto) -> Unit,
    onCarregarMais: () -> Unit,
) {
    if (carregando && itens.isEmpty()) {
        repeat(2) {
            Box(Modifier.fillMaxWidth().height(220.dp).shimmer(RoundedCornerShape(12.dp)))
            Spacer(Modifier.height(10.dp))
        }
        return
    }
    if (itens.isEmpty()) {
        Text(
            "Ninguém treinou ainda hoje. Seja o primeiro.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    itens.forEach { item ->
        ItemDoFeed(item, onApagar)
        Spacer(Modifier.height(12.dp))
    }

    // Botão, e não rolagem infinita. O detalhe do grupo é uma `Column` com `verticalScroll`, e
    // não uma `LazyColumn`: cada item carregado fica COMPOSTO para sempre. Com o botão, quem
    // decide o quanto cresce é a pessoa, e o normal é parar em uma ou duas páginas.
    //
    // A conversão para `LazyColumn` é o caminho certo, mas mexe na tela inteira — banner, dados,
    // participantes e feed hoje rolam juntos. Ver DEBITOS.
    if (temMais) {
        OutlinedButton(
            onClick = onCarregarMais,
            enabled = !carregandoMais,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (carregandoMais) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text("Ver check-ins mais antigos")
        }
    }
}

@Composable
private fun ItemDoFeed(item: CheckInDto, onApagar: (CheckInDto) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        item.photoUrl?.let { caminho ->
            NetworkImage(
                // A rota é AUTENTICADA e mora na base da API, não na base de mídia dos exercícios.
                url = HttpClientFactory.BASE_URL + caminho,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
            )
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarInicial(nome = item.displayName, id = item.userId, tamanho = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                item.placeName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // O botão de apagar só existe quando o SERVIDOR disse que dá (`canDelete`): é meu e é
            // hoje. A tela não recalcula a data — ela não tem o fuso do grupo nem o relógio certo.
            if (item.canDelete) {
                IconButton(onClick = { onApagar(item) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Apagar meu check-in",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Selo(estado: GroupState) {
    val (texto, cor) = when (estado) {
        GroupState.AGENDADO -> "Começa em breve · entrada aberta" to MaterialTheme.colorScheme.primary
        GroupState.ATIVO -> "Em andamento · entrada fechada" to MaterialTheme.colorScheme.onSurface
        GroupState.ENCERRADO -> "Encerrado" to MaterialTheme.colorScheme.outline
    }
    Text(texto, style = MaterialTheme.typography.labelMedium, color = cor)
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            rotulo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(valor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}
