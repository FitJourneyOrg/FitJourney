package dev.rafael.app.screens.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
// ⚠️ Import explicito do R: os curingas do Compose acima trazem R de outras libs para o escopo
// (ver o mesmo aviso em ExerciseDetailScreen.kt).
import dev.rafael.app.R
import dev.rafael.app.ui.ErroAcao
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.contract.stats.PontoDeSessaoDto
import dev.rafael.core.designsystem.Chart1
import dev.rafael.core.designsystem.Chart2
import org.koin.androidx.compose.koinViewModel

/**
 * Detalhe de UM exercicio dentro de UM programa (J.5).
 *
 * Reaproveita [CalhaDoEixo], [CartaoDeGrafico], [GraficoDeLinhas] e [GraficoDeBarras] da tela de
 * Progresso (mesmo pacote) -- a decisao de visual (calha de eixo Y fora do scroll, escala
 * compartilhada, largura fixa por item com scroll acima de um limiar) ja foi tomada la e
 * validada visualmente; duplicar aqui so trocaria "semana" por "sessao" em codigo identico.
 *
 * Duas metricas, nunca uma so: 1RM estimado (intensidade) e volume (quantidade de trabalho) PODEM
 * divergir -- ver o teste `volumeKg e estimated1rm viajam juntos, e podem discordar` no server.
 * Mostrar so uma esconderia a outra historia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercicioDetalheScreen(
    programId: String,
    exercicioId: String,
    onBack: () -> Unit,
    viewModel: ExercicioDetalheViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(programId, exercicioId) { viewModel.carregar(programId, exercicioId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detalhe?.name ?: stringResource(R.string.comum_exercicio)) },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            val detalhe = state.detalhe
            when {
                state.carregando && detalhe == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.erro != null ->
                    ErroDeTela(
                        erro = state.erro!!,
                        modifier = Modifier.align(Alignment.Center),
                        onAcao = { acao ->
                            if (acao == ErroAcao.TENTAR_DE_NOVO) viewModel.carregar(programId, exercicioId)
                        },
                    )

                detalhe != null && detalhe.points.isEmpty() ->
                    Text(
                        stringResource(R.string.progresso_detalhe_vazio),
                        Modifier.align(Alignment.Center).padding(32.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                detalhe != null ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BlocoDe1Rm(detalhe.points)
                        BlocoDeVolumePorSessao(detalhe.points)
                    }
            }
        }
    }
}

/** Largura de cada sessao quando o grafico rola. Mesmo valor/motivo de `LARGURA_POR_SEMANA`. */
private val LARGURA_POR_SESSAO = 22.dp

/** Acima disto o grafico rola -- mesmo limiar das barras/linha semanais. */
private const val SESSOES_QUE_CABEM = 12

/** Largura da calha do eixo Y. Mesmo valor de `CalhaDoEixo` (ver aquele arquivo). */
private val LARGURA_DA_CALHA = 34.dp

@Composable
private fun BlocoDe1Rm(pontos: List<PontoDeSessaoDto>) {
    val minimo = pontos.minOf { it.estimated1rm }
    val maximo = pontos.maxOf { it.estimated1rm }
    val amplitude = maximo - minimo
    // Mesma guarda de `BlocoDeEvolucao`: serie plana nao tem amplitude, e dividir por zero
    // afundaria o ponto na base como se a carga fosse zero.
    val semVariacao = amplitude <= 0.0
    val pr = pontos.maxByOrNull { it.estimated1rm }
    val rola = pontos.size > SESSOES_QUE_CABEM

    CartaoDeGrafico(stringResource(R.string.progresso_detalhe_1rm_titulo)) {
        if (pr != null) {
            Text(
                stringResource(
                    R.string.progresso_detalhe_pr,
                    umaCasa(pr.estimated1rm) + " " + stringResource(R.string.comum_kg),
                    diaMes(pr.date),
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Chart1,
            )
            Spacer(Modifier.height(6.dp))
        }
        Row {
            CalhaDoEixo(
                de_cima_para_baixo = listOf(
                    umaCasa(maximo),
                    umaCasa((maximo + minimo) / 2),
                    umaCasa(minimo),
                ),
                altura = 140.dp,
            )
            val linha = LinhaDoGrafico(
                nome = stringResource(R.string.progresso_detalhe_1rm_titulo),
                cor = Chart1,
                // Um ponto por SESSAO, igualmente espacado -- nao ha regua de semanas aqui: o
                // escopo e "so dentro do programa" (J.5), entao toda sessao do historico entra,
                // uma apos a outra, sem buraco de calendario para representar.
                pontos = pontos.mapIndexed { i, p ->
                    val x = if (pontos.size > 1) i.toFloat() / (pontos.size - 1) else 0.5f
                    val y = if (semVariacao) 0.5 else (p.estimated1rm - minimo) / amplitude
                    x to y.toFloat()
                },
            )
            if (!rola) {
                GraficoDeLinhas(listOf(linha), Modifier.weight(1f).height(140.dp))
            } else {
                val rolagem = rememberScrollState()
                LaunchedEffect(rolagem.maxValue) { rolagem.scrollTo(rolagem.maxValue) }
                Row(Modifier.weight(1f).horizontalScroll(rolagem)) {
                    GraficoDeLinhas(
                        listOf(linha),
                        Modifier.width(LARGURA_POR_SESSAO * pontos.size).height(140.dp),
                    )
                }
            }
        }
        if (rola) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.progresso_detalhe_arraste_sessao),
                Modifier.padding(start = LARGURA_DA_CALHA),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlocoDeVolumePorSessao(pontos: List<PontoDeSessaoDto>) {
    var selecionada by remember { mutableStateOf<Int?>(null) }
    val pico = pontos.maxOf { it.volumeKg }
    val maior = pico.takeIf { it > 0.0 } ?: 1.0
    val indiceDoPico = pontos.indexOfFirst { it.volumeKg == pico }
    val sessao = selecionada?.let { pontos.getOrNull(it) }
    val rola = pontos.size > SESSOES_QUE_CABEM

    CartaoDeGrafico(stringResource(R.string.progresso_detalhe_volume_titulo)) {
        Row(Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sessao == null) {
                Text(
                    stringResource(R.string.progresso_detalhe_toque_sessao),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    diaMes(sessao.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    emToneladas(sessao.volumeKg) + " " + stringResource(R.string.progresso_unidade_tonelada),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Chart2,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            CalhaDoEixo(
                de_cima_para_baixo = listOf(emToneladas(pico), emToneladas(pico / 2), "0"),
                altura = 120.dp,
            )
            val valores = pontos.map { (it.volumeKg / maior).toFloat() }
            val alternar = { i: Int -> selecionada = if (selecionada == i) null else i }
            if (!rola) {
                GraficoDeBarras(
                    valores, selecionada ?: indiceDoPico, Chart2,
                    Modifier.weight(1f).height(120.dp), alternar,
                )
            } else {
                val rolagem = rememberScrollState()
                LaunchedEffect(rolagem.maxValue) { rolagem.scrollTo(rolagem.maxValue) }
                Row(Modifier.weight(1f).horizontalScroll(rolagem)) {
                    GraficoDeBarras(
                        valores, selecionada ?: indiceDoPico, Chart2,
                        Modifier.width(LARGURA_POR_SESSAO * pontos.size).height(120.dp), alternar,
                    )
                }
            }
        }
        if (rola) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.progresso_detalhe_arraste_sessao),
                Modifier.padding(start = LARGURA_DA_CALHA),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
