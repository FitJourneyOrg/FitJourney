package dev.rafael.app.screens.grupos

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
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
import androidx.compose.ui.layout.ContentScale
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
import dev.rafael.contract.group.RankingEntryDto
import dev.rafael.core.network.HttpClientFactory
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import org.koin.androidx.compose.koinViewModel

/**
 * Detalhe do desafio: **cabeçalho fixo + três abas** (fatia C).
 *
 * A tela era um scroll único com tudo empilhado — banner, dados, participantes e feed. Com o
 * ranking entrando, isso deixou de caber: são três conteúdos de natureza diferente, e o único
 * jeito de ver o terceiro era rolar por cima dos outros dois.
 *
 * **A separação em abas resolve um débito de graça.** Cada aba ganha a própria `LazyColumn`, e o
 * feed deixa de compor todos os itens carregados para sempre — que era o motivo de a paginação
 * ter virado botão em vez de rolagem infinita.
 *
 * **O cabeçalho é fixo e enxuto de propósito.** Colapsá-lo com `nestedScroll` seria o ideal, mas
 * um cabeçalho alto e fixo comeria metade da tela do celular. O meio-termo: o essencial sempre
 * visível — banner, selo, ação — e as especificações (período, fuso, regras, código) num bloco
 * que se abre quando alguém quer. Fechado por padrão, porque é consulta rara.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrupoDetalheScreen(
    groupId: String,
    onBack: () -> Unit,
    onCheckIn: () -> Unit,
    /**
     * [REGRA] #35: tocar no nome abre o perfil, em QUALQUER superfície — ranking, posts e
     * membros. Um `onAbrirPerfil` só, passado às três abas, e não três parâmetros: se o gesto é
     * o mesmo em toda parte, o destino também tem que ser.
     */
    onAbrirPerfil: (String) -> Unit,
    viewModel: GrupoDetalheViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmarSaida by remember { mutableStateOf(false) }
    var alvo by remember { mutableStateOf<GroupMemberDto?>(null) }
    var alvoParaApagar by remember { mutableStateOf<CheckInDto?>(null) }

    LaunchedEffect(state.saiu) { if (state.saiu) onBack() }

    // ON_START e não `LaunchedEffect(groupId)`: voltando do check-in a composição é RESTAURADA da
    // pilha, e um efeito já executado não roda de novo — a tela ficaria com o botão "Fazer
    // check-in" depois de a pessoa ter acabado de fazer um.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.carregar(groupId)
        viewModel.iniciarEnquete(groupId)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pararEnquete() }

    DialogoDeExclusaoDeCheckIn(alvoParaApagar, aoFechar = { alvoParaApagar = null }) { item ->
        viewModel.apagarCheckIn(groupId, item.id)
    }
    DialogoDeSaida(
        aberto = confirmarSaida,
        souUltimo = state.souUltimo,
        aoFechar = { confirmarSaida = false },
        aoConfirmar = { viewModel.sair(groupId) },
    )
    DialogoDeMembro(alvo, aoFechar = { alvo = null },
        aoTornarAdmin = { viewModel.transferirAdmin(groupId, it.userId) },
        aoRemover = { viewModel.expulsar(groupId, it.userId) },
    )

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
        val grupo = state.grupo

        if (state.carregando && grupo == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        if (grupo == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                state.erro?.let { ErroInline(it, Modifier.padding(24.dp)) }
            }
            return@Scaffold
        }

        // Abre em SOBRE porque é onde moram as AÇÕES: check-in, convite e sair.
        //
        // A aba de abertura tem de ser a que oferece o que fazer, não a que informa. Em `AGENDADO`
        // isso é o convite — o gargalo do produto (2-B.0), grupo que nasce vazio nasce morto. Em
        // `ATIVO` é o check-in, que é o laço diário inteiro. Cair no ranking e ter de trocar de
        // aba para agir poria a consulta na frente da ação.
        val pager = rememberPagerState(initialPage = 0, pageCount = { ABAS.size })
        val escopo = rememberCoroutineScope()

        // A aba visível dita o que o polling atualiza. Sem isso, o laço de 10s buscaria feed E
        // ranking o tempo todo — o dobro de requisições, uma delas para ninguém.
        LaunchedEffect(pager.currentPage) {
            viewModel.aoTrocarDeAba(ABAS[pager.currentPage].aba, groupId)
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pager.currentPage) {
                ABAS.forEachIndexed { indice, aba ->
                    Tab(
                        selected = pager.currentPage == indice,
                        onClick = { escopo.launch { pager.animateScrollToPage(indice) } },
                        text = { Text(aba.titulo) },
                    )
                }
            }

            // `weight(1f)` e NÃO `fillMaxSize()`: dentro de uma `Column`, preencher o tamanho todo
            // faria o pager pedir a altura inteira do pai e empurrar o cabeçalho e as abas para
            // fora da tela. O peso é o que diz "fique com o que sobrou".
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { pagina ->
                when (ABAS[pagina].aba) {
                    GrupoDetalheViewModel.Aba.SOBRE ->
                        AbaSobre(
                            grupo = grupo,
                            souAdmin = state.souAdmin,
                            souUltimo = state.souUltimo,
                            ocupado = state.ocupado,
                            erro = state.erro,
                            onCheckIn = onCheckIn,
                            onSair = { confirmarSaida = true },
                        )

                    GrupoDetalheViewModel.Aba.RANKING ->
                        AbaDoRanking(state.ranking, state.carregandoRanking, onAbrirPerfil)

                    GrupoDetalheViewModel.Aba.POSTS ->
                        AbaDePosts(
                            onAbrirPerfil = onAbrirPerfil,
                            fusoDoGrupo = grupo.timezone,
                            itens = state.feed,
                            carregando = state.carregandoFeed,
                            temMais = state.temMais,
                            carregandoMais = state.carregandoMais,
                            onApagar = { alvoParaApagar = it },
                            onCarregarMais = { viewModel.carregarMais(groupId) },
                        )

                    GrupoDetalheViewModel.Aba.MEMBROS ->
                        AbaDeMembros(
                            onAbrirPerfil = onAbrirPerfil,
                            membros = state.membros,
                            souAdmin = state.souAdmin,
                            onAgir = { alvo = it },
                        )
                }
            }
        }
    }
}

private data class AbaDaTela(val aba: GrupoDetalheViewModel.Aba, val titulo: String)

/**
 * A ordem lê como uma pergunta de cada vez: **o que é isto → quem está ganhando → o que aconteceu
 * → quem está aqui.**
 *
 * "Sobre" é a primeira posicionalmente, mas não é a de abertura no caso comum: com o desafio
 * `ATIVO`, a tela abre em Ranking. Ver o `initialPage`.
 */
private val ABAS = listOf(
    AbaDaTela(GrupoDetalheViewModel.Aba.SOBRE, "Sobre"),
    AbaDaTela(GrupoDetalheViewModel.Aba.RANKING, "Ranking"),
    AbaDaTela(GrupoDetalheViewModel.Aba.POSTS, "Posts"),
    AbaDaTela(GrupoDetalheViewModel.Aba.MEMBROS, "Membros"),
)

// ---------------------------------------------------------------------------
// ABA 1 — SOBRE (as especificações do desafio)
// ---------------------------------------------------------------------------

/**
 * O que este desafio É **e o que dá para fazer nele**: banner, período, regras, convite, check-in
 * e sair.
 *
 * É a aba de ABERTURA, e por isso: ela reúne as ações. Uma aba de abertura que só informa poria a
 * consulta na frente da ação — e a ação aqui é o laço diário inteiro do produto.
 *
 * A ordem dentro dela vai do agora para o raro: **primeiro o que fazer hoje** (check-in ou
 * convite), depois o que o desafio é, e por último sair — que é decisão, não gesto.
 */
@Composable
private fun AbaSobre(
    grupo: GroupDto,
    souAdmin: Boolean,
    souUltimo: Boolean,
    ocupado: Boolean,
    erro: dev.rafael.core.result.AppError?,
    onCheckIn: () -> Unit,
    onSair: () -> Unit,
) {
    val area = LocalClipboardManager.current
    val contexto = LocalContext.current

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            grupo.bannerUrl?.let { url ->
                AsyncImage(
                    model = HttpClientFactory.BASE_URL.removeSuffix("/") + url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(BANNER_RATIO)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                Selo(grupo.state)

                // A AÇÃO DE HOJE vem antes da ficha do desafio: quem abre um desafio ativo vem
                // para fazer check-in, não para reler o período.
                Spacer(Modifier.height(12.dp))
                AcaoDeCheckIn(grupo, onCheckIn)

                grupo.description?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(14.dp))
                Linha("Período", "${grupo.startDate} a ${grupo.endDate}")
                Linha("Fuso", grupo.timezone)
                if (grupo.rules.isNotEmpty()) {
                    Linha(
                        "Check-in exige",
                        grupo.rules.joinToString(", ") { it.name.lowercase().replace('_', ' ') },
                    )
                }

                ConviteDoDesafio(grupo, area, contexto)

                erro?.let {
                    Spacer(Modifier.height(14.dp))
                    ErroInline(it)
                }

                // SAIR por último, e depois de um divisor: é decisão, não gesto. Perto do botão de
                // check-in, um toque errado custaria o desafio inteiro.
                Spacer(Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onSair, enabled = !ocupado) {
                    Text(
                        if (souUltimo) "Excluir o desafio" else "Sair do desafio",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                // A regra dita ANTES do erro — mas só quando ela vale. Enquanto o admin era o
                // único membro, este aviso mandava transferir o cargo para alguém que não existia.
                if (souAdmin && !souUltimo) {
                    Text(
                        "Como admin, você precisa passar o cargo para alguém antes de sair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * CÓDIGO e CONVIDAR só existem em `AGENDADO` — [REGRA] tabela 2-B: "única janela de ENTRADA".
 *
 * Em `ATIVO`/`ENCERRADO` a entrada está fechada e o código não abre porta nenhuma. A primeira
 * versão mostrava o botão sempre e avisava por baixo que já não adiantava: um botão primário, de
 * largura inteira, com um rodapé cinza dizendo que não funciona. Quando os dois discordam, ganha
 * o botão.
 *
 * O código é visível para TODO membro: ele é identidade do grupo, e a 2-B não reserva "convidar"
 * a ninguém em especial.
 */
@Composable
private fun ConviteDoDesafio(
    grupo: GroupDto,
    area: androidx.compose.ui.platform.ClipboardManager,
    contexto: android.content.Context,
) {
    if (grupo.state != GroupState.AGENDADO) return

    Spacer(Modifier.height(14.dp))
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

    Spacer(Modifier.height(8.dp))
    // CONVIDAR compartilha o CÓDIGO, não um link: deep link só funciona de verdade com domínio
    // https e App Links, porque as mensagerias não transformam `fitjourney://` em algo clicável.
    OutlinedButton(
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
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Convidar") }
}

// ---------------------------------------------------------------------------
// ABA 1 — RANKING
// ---------------------------------------------------------------------------

/**
 * O ranking (7.2). **A posição vem do servidor** — a tela não numera a lista.
 *
 * Numerar aqui abriria espaço para a tela discordar do desempate: duas fontes para a mesma
 * verdade acabam divergindo, e a que o usuário vê seria a errada.
 */
@Composable
private fun AbaDoRanking(
    itens: List<RankingEntryDto>,
    carregando: Boolean,
    onAbrirPerfil: (String) -> Unit,
) {
    if (carregando && itens.isEmpty()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(5) { Box(Modifier.fillMaxWidth().height(56.dp).shimmer(RoundedCornerShape(12.dp))) }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        items(itens, key = { it.userId }) { linha -> LinhaDoRanking(linha, onAbrirPerfil) }
    }
}

@Composable
private fun LinhaDoRanking(linha: RankingEntryDto, onAbrirPerfil: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // A LINHA inteira é o alvo, não só o nome: um texto de 14sp é alvo de toque ruim, e
            // aqui a linha não faz mais nada — não há segundo gesto para disputar com este.
            .clickable { onAbrirPerfil(linha.userId) }
            // Destaque para a própria linha: quem abre o ranking procura a si mesmo antes de tudo.
            // `mine` vem resolvido do servidor — a tela não compara ids.
            .background(
                if (linha.mine) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), Alignment.Center) {
            Text(
                "${linha.position}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (linha.position <= 3) FontWeight.Bold else FontWeight.Normal,
                // Sem `lime` em lugar nenhum: [REGRA] ARCH #16, a cor é exclusiva do perfil
                // individual e não pode aparecer em contexto de grupo.
                color = if (linha.position <= 3) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        AvatarInicial(nome = linha.displayName, id = linha.userId, tamanho = 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            linha.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (linha.mine) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            // Contagem de check-ins, nunca XP ([REGRA] #18).
            "${linha.checkIns}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// ABA 2 — POSTS (o feed, 8.0)
// ---------------------------------------------------------------------------

/**
 * O feed do grupo (8.0).
 *
 * **`LazyColumn` agora que a aba é só dele.** Enquanto o feed dividia um `verticalScroll` com o
 * resto da tela, cada item carregado ficava composto para sempre — foi por isso que a paginação
 * virou botão em vez de rolagem infinita. Com a lista preguiçosa, o botão poderia virar rolagem;
 * fica como está por ora, porque carregar sob demanda explícita ainda é mais previsível.
 *
 * O que cada item mostra está fechado pela 8.0.2/8.0.3, e a garantia não é este código: é o
 * `CheckInDto`, que não tem campo de XP nem de e-mail. A tela não teria como vazá-los.
 */
@Composable
private fun AbaDePosts(
    fusoDoGrupo: String,
    itens: List<CheckInDto>,
    carregando: Boolean,
    temMais: Boolean,
    carregandoMais: Boolean,
    onApagar: (CheckInDto) -> Unit,
    onCarregarMais: () -> Unit,
    onAbrirPerfil: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (carregando && itens.isEmpty()) {
            items(2) { Box(Modifier.fillMaxWidth().height(220.dp).shimmer(RoundedCornerShape(12.dp))) }
            return@LazyColumn
        }
        if (itens.isEmpty()) {
            item {
                Text(
                    "Ninguém treinou ainda. Seja o primeiro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
            return@LazyColumn
        }

        items(itens, key = { it.id }) { item -> ItemDoFeed(item, fusoDoGrupo, onApagar, onAbrirPerfil) }

        if (temMais) {
            item {
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
    }
}

/**
 * O botão de check-in, ou a explicação de por que ele não está aí.
 *
 * Só existe com o desafio `ATIVO` ([INV]) e se AINDA NÃO FIZ hoje (4.3). **A ausência do botão é o
 * sinal** — oferecer e depois recusar é o defeito que já corrigimos três vezes nesta fase.
 *
 * Quando já treinei, a frase fica exatamente onde a solução está: o item com a lixeira é o próximo
 * da lista. Enquanto isto morava noutra aba, o texto precisava dizer "apague em Posts"; aqui ele
 * pode apontar para baixo.
 */
@Composable
private fun AcaoDeCheckIn(grupo: GroupDto, onCheckIn: () -> Unit) {
    if (grupo.state != GroupState.ATIVO) return

    if (grupo.myCheckInToday == null) {
        Button(
            onClick = onCheckIn,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Fazer check-in")
        }
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                // Diz o que dá para fazer, e não só o que não dá: apagar libera o dia (4.11), e
                // sem essa frase a pessoa não descobre sozinha.
                //
                // O texto aponta a ABA, e não uma direção. Ele já disse "abaixo" quando o botão
                // morava no topo do feed e o item vinha logo em seguida; agora que a ação está em
                // "Sobre" e o item em "Posts", "abaixo" mandaria a pessoa rolar uma tela onde não
                // há nada. Texto que descreve posição envelhece a cada mudança de layout.
                "Você já treinou hoje. Para refazer, apague o seu check-in na aba Posts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Quando o check-in foi feito, **no fuso do GRUPO**.
 *
 * Não no fuso do aparelho — e a diferença não é teórica. O desafio conta dias no fuso do grupo
 * (4.6): um treino às 22h em São Paulo pertence ao dia 26 mesmo já sendo dia 27 em UTC. Formatar
 * no relógio de quem olha faria alguém em outro fuso ver uma data diferente do dia em que aquele
 * check-in de fato contou — e aí a data na tela discordaria da contagem do ranking.
 *
 * O DIA vem do `localDate`, que já é o dia civil do grupo, resolvido pelo servidor. Só a HORA
 * precisa de conversão.
 */
private fun quandoFoi(item: CheckInDto, fusoDoGrupo: String): String {
    val fuso = runCatching { TimeZone.of(fusoDoGrupo) }.getOrDefault(TimeZone.UTC)
    val instante = runCatching { Instant.parse(item.createdAt) }.getOrNull()
        ?: return item.localDate
    val quando = instante.toLocalDateTime(fuso)
    val hora = "%02d:%02d".format(quando.hour, quando.minute)

    val hoje = Clock.System.now().toLocalDateTime(fuso).date
    val dia = runCatching { LocalDate.parse(item.localDate) }.getOrNull()

    return when {
        dia == null -> hora
        dia == hoje -> "Hoje às $hora"
        dia == hoje.minus(DatePeriod(days = 1)) -> "Ontem às $hora"
        // Data absoluta para o que já saiu da memória recente. "há 12 dias" obriga a pessoa a
        // fazer a conta de cabeça para saber de que dia se trata.
        else -> "%02d/%02d às %s".format(dia.dayOfMonth, dia.monthNumber, hora)
    }
}

@Composable
private fun ItemDoFeed(
    item: CheckInDto,
    fusoDoGrupo: String,
    onApagar: (CheckInDto) -> Unit,
    onAbrirPerfil: (String) -> Unit,
) {
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
            // Aqui o alvo é só o BLOCO DO AUTOR, e não o cartão inteiro como no ranking: o cartão
            // já tem a foto e o botão de apagar. Um toque no cartão que abrisse perfil roubaria o
            // gesto de quem só quis ver a imagem — e a foto é o conteúdo do post.
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .clickable { onAbrirPerfil(item.userId) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            AvatarInicial(nome = item.displayName, id = item.userId, tamanho = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    // Lugar e horário na mesma linha: são as duas circunstâncias do treino, e
                    // separá-los em duas linhas daria a cada um peso que nenhum dos dois tem.
                    // O lugar pode não existir — o grupo só o exige se a regra estiver ligada.
                    listOfNotNull(item.placeName, quandoFoi(item, fusoDoGrupo)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

// ---------------------------------------------------------------------------
// ABA 3 — MEMBROS
// ---------------------------------------------------------------------------

@Composable
private fun AbaDeMembros(
    membros: List<GroupMemberDto>,
    souAdmin: Boolean,
    onAgir: (GroupMemberDto) -> Unit,
    onAbrirPerfil: (String) -> Unit,
) {
    /**
     * O ADMIN sempre em primeiro.
     *
     * Ordenação de APRESENTAÇÃO, e por isso mora aqui e não no servidor: lá a lista vem por
     * `joined_at` crescente, que é a ordem da fila de reivindicação do cargo (2.12). Reordenar no
     * servidor perderia esse significado para ganhar um detalhe de tela.
     *
     * `sortedByDescending` é ESTÁVEL: dentro de cada grupo, a ordem de entrada se mantém.
     */
    val ordenados = remember(membros) {
        membros.sortedByDescending { it.role == MemberRole.ADMIN }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "PARTICIPANTES · ${membros.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        items(ordenados, key = { it.userId }) { m ->
            Membro(
                membro = m,
                onAbrir = { onAbrirPerfil(m.userId) },
                // Admin não age sobre si mesmo: para sair existe o botão em "Sobre".
                podeAgir = souAdmin && m.role != MemberRole.ADMIN,
                onAgir = { onAgir(m) },
            )
        }
    }
}

@Composable
private fun Membro(
    membro: GroupMemberDto,
    podeAgir: Boolean,
    onAgir: () -> Unit,
    onAbrir: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onAbrir)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarInicial(nome = membro.displayName, id = membro.userId, tamanho = 40.dp)
        Spacer(Modifier.width(12.dp))
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
                Icon(Icons.Outlined.Close, contentDescription = "Gerenciar", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DIÁLOGOS
// ---------------------------------------------------------------------------

@Composable
private fun DialogoDeExclusaoDeCheckIn(
    alvo: CheckInDto?,
    aoFechar: () -> Unit,
    aoConfirmar: (CheckInDto) -> Unit,
) {
    val item = alvo ?: return
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Apagar este check-in?") },
        text = {
            Text(
                // A 4.11 dita ANTES: apagar libera o slot do dia, então dá para refazer hoje. Sem
                // isso a pessoa hesita achando que perde o dia.
                "O dia fica livre de novo — você pode fazer outro check-in hoje. Depois da meia-noite não dá mais para apagar.",
            )
        },
        confirmButton = {
            TextButton(onClick = { aoFechar(); aoConfirmar(item) }) {
                Text("Apagar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/**
 * Duas saídas MUITO diferentes atrás do mesmo botão (2.5-A): o membro sai de um desafio que
 * continua existindo; o admin sozinho leva o desafio junto. Um diálogo que dissesse a mesma coisa
 * nos dois casos estaria mentindo em um deles — e no que é irreversível.
 */
@Composable
private fun DialogoDeSaida(
    aberto: Boolean,
    souUltimo: Boolean,
    aoFechar: () -> Unit,
    aoConfirmar: () -> Unit,
) {
    if (!aberto) return
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (souUltimo) "Excluir o desafio?" else "Sair do desafio?") },
        text = {
            Text(
                if (souUltimo) {
                    "Você é a única pessoa aqui. Sair apaga este desafio — o código deixa de valer e não dá para desfazer."
                } else {
                    // Diz a verdade sobre o que fica: os check-ins permanecem no histórico do
                    // grupo (2.6). Omitir isso faria a pessoa sair achando que apagou tudo.
                    "Você deixa de aparecer no ranking. Os seus check-ins continuam no histórico do grupo."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { aoFechar(); aoConfirmar() }) {
                Text(if (souUltimo) "Excluir" else "Sair")
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoDeMembro(
    alvo: GroupMemberDto?,
    aoFechar: () -> Unit,
    aoTornarAdmin: (GroupMemberDto) -> Unit,
    aoRemover: (GroupMemberDto) -> Unit,
) {
    val m = alvo ?: return
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(m.displayName) },
        text = { Text("O que você quer fazer com esta pessoa?") },
        confirmButton = {
            TextButton(onClick = { aoFechar(); aoTornarAdmin(m) }) { Text("Tornar admin") }
        },
        dismissButton = {
            TextButton(onClick = { aoFechar(); aoRemover(m) }) {
                Text("Remover do grupo", color = MaterialTheme.colorScheme.error)
            }
        },
    )
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
            modifier = Modifier.width(120.dp),
        )
        Text(valor, style = MaterialTheme.typography.bodySmall)
    }
}
