package dev.rafael.app.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.R
import dev.rafael.app.ui.rotulo
import dev.rafael.contract.stats.ExerciseTrendDto
import dev.rafael.contract.stats.MuscleVolumeDto
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.contract.stats.WeeklyLoadDto
import dev.rafael.core.designsystem.Chart1
import dev.rafael.core.designsystem.Chart2
import dev.rafael.core.designsystem.Chart3
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlin.math.abs

/**
 * Progresso — a analise do treino (J.2).
 *
 * ## O que saiu daqui, e por que (desmembramento de 2026-10-01)
 *
 * A tela acumulava tres assuntos: metricas, a porta das conquistas e o historico inteiro. Tres
 * coisas numa tela nao e uma tela de tres coisas — e uma tela sem assunto. **Conquistas** e
 * **Historico** foram para o drawer, cada um com tela propria, porque os dois sao CONSULTA.
 *
 * ## Tres estados, e confundi-los seria o defeito caro
 *
 * | estado | quando | o que aparece |
 * |---|---|---|
 * | trancado | `analysisLocked` do servidor | cartao de assinatura |
 * | sem carga | `sinceDate` nulo | explicacao, nao paywall |
 * | com dado | o resto | os tres graficos |
 *
 * ⚠️ **Nulo nao decide nada aqui.** Os blocos pagos vem nulos nos dois primeiros casos, e usar o
 * nulo como criterio mostraria paywall a quem treina so peso corporal — cobrando por algo que ela
 * nao conseguiria usar nem pagando. Quem responde "e pago" e o servidor, no `analysisLocked`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onOpenPaywall: () -> Unit = {},
    viewModel: ProgressViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.sincronizar() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.comum_progresso),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        val analise = state.analise

        CartaoDeTotais(analise, state.stats?.totalSessions ?: 0)
        analise?.lastVsPrevious?.let { c ->
            Spacer(Modifier.height(10.dp))
            CartaoDeComparacao(c)
        }

        Spacer(Modifier.height(10.dp))
        when {
            state.trancado -> CartaoTrancado(onOpenPaywall)
            state.semCarga -> CartaoSemCarga()
            analise != null -> BlocosPagos(analise)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* -------------------------------------------------------------------------- */
/*  Gratis                                                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun CartaoDeTotais(analise: ProgressDto?, treinos: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            analise?.sinceDate?.let { iso ->
                val partes = iso.split("-")
                if (partes.size == 3) {
                    Text(
                        stringResource(R.string.progresso_desde, partes[2], partes[1]),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Numerao(
                    valor = emToneladas(analise?.totalKg ?: 0.0),
                    unidade = stringResource(R.string.progresso_unidade_tonelada),
                    rotulo = stringResource(R.string.progresso_total_rotulo),
                )
                Numerao(
                    valor = "$treinos",
                    unidade = null,
                    rotulo = stringResource(R.string.progresso_metrica_treinos),
                )
            }
        }
    }
}

@Composable
private fun Numerao(valor: String, unidade: String?, rotulo: String) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            unidade?.let {
                Text(
                    it,
                    Modifier.padding(start = 2.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CartaoDeComparacao(c: dev.rafael.contract.stats.WorkoutComparisonDto) {
    val delta = c.currentKg - c.previousKg
    val percentual = if (c.previousKg > 0) delta / c.previousKg * 100.0 else 0.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.progresso_comparacao_titulo, c.workoutName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                comSinal(delta, stringResource(R.string.comum_kg)),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = corDoDelta(delta),
            )
            Text(
                stringResource(R.string.progresso_comparacao_variacao, comSinal(percentual, "%")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            c.exercises.take(LINHAS_DA_COMPARACAO).forEach { e ->
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                    )
                    Text(
                        comSinal(e.deltaKg, stringResource(R.string.comum_kg)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = corDoDelta(e.deltaKg),
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Portao e estado vazio                                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun CartaoTrancado(onOpenPaywall: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Uma amostra do grafico, esmaecida: mostra O QUE se destrava, sem entregar numero.
            GraficoDeBarras(
                valores = AMOSTRA_DO_PAYWALL,
                destaque = -1,
                cor = Chart1.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(60.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.progresso_bloqueado_titulo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.progresso_bloqueado_descricao),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onOpenPaywall) { Text(stringResource(R.string.paywall_assinar)) }
        }
    }
}

@Composable
private fun CartaoSemCarga() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.progresso_sem_carga_titulo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.progresso_sem_carga_descricao),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Premium                                                                    */
/* -------------------------------------------------------------------------- */

@Composable
private fun BlocosPagos(analise: ProgressDto) {
    analise.weeklyLoad?.takeIf { it.isNotEmpty() }?.let {
        BlocoDeCargaSemanal(it)
        Spacer(Modifier.height(10.dp))
    }
    analise.strengthTrend?.takeIf { it.isNotEmpty() }?.let {
        BlocoDeEvolucao(it)
        Spacer(Modifier.height(10.dp))
    }
    analise.setsByMuscle?.takeIf { it.byMuscle.isNotEmpty() }?.let {
        BlocoDeVolume(it)
    }
}

@Composable
private fun BlocoDeCargaSemanal(semanas: List<WeeklyLoadDto>) {
    val pico = semanas.maxOf { it.kg }
    val maior = pico.takeIf { it > 0.0 } ?: 1.0
    val indiceDoPico = semanas.indexOfFirst { it.kg == pico }

    // Tocar a barra troca o que a linha de cima mostra. ⚠️ A alternativa seria um balao sobre a
    // marca, e balao significa TEXTO DENTRO DO CANVAS — que ignora o tamanho de fonte do sistema
    // e some para o leitor de tela. A linha de cima ja e um `Text` de verdade; reaproveita-la
    // custa um `remember` e nao quebra nada disso.
    var selecionada by remember { mutableStateOf<Int?>(null) }
    val semana = selecionada?.let { semanas.getOrNull(it) }

    CartaoDeGrafico(stringResource(R.string.progresso_carga_semanal)) {
        // Altura FIXA: sem isso o cartao pula de tamanho ao selecionar a primeira barra.
        Row(Modifier.height(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (semana == null) {
                Text(
                    stringResource(R.string.progresso_toque_semana),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    diaMes(semana.weekStart),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    emToneladas(semana.kg) + " " + stringResource(R.string.progresso_unidade_tonelada),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Chart1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            // Calha do eixo Y. Os tres rotulos batem com as tres linhas de grade que o Canvas
            // desenha — e por isso que a grade tem tres linhas e nao quatro.
            CalhaDoEixo(
                de_cima_para_baixo = listOf(emToneladas(pico), emToneladas(pico / 2), "0"),
                altura = 120.dp,
            )
            GraficoDeBarras(
                valores = semanas.map { (it.kg / maior).toFloat() },
                destaque = selecionada ?: indiceDoPico,
                cor = Chart1,
                modifier = Modifier.weight(1f).height(120.dp),
                // Tocar a mesma barra DESMARCA: sem isso nao haveria como voltar ao estado de
                // resumo, e a tela ficaria presa na ultima semana que alguem encostou.
                onSelecionar = { i -> selecionada = if (selecionada == i) null else i },
            )
        }
        // Eixo so nas PONTAS. Oito datas nao cabem na largura de um celular, e rotulo que se
        // corta nao e rotulo; o que a pessoa precisa saber e onde a serie comeca e que a ultima
        // barra e a semana dela — o meio se interpola sozinho.
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(start = LARGURA_DA_CALHA)) {
            Text(
                diaMes(semanas.first().weekStart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.progresso_semana_atual),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "dd/MM" a partir de uma data ISO, sem API de data: a ordem dos campos e do idioma. */
@Composable
private fun diaMes(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) stringResource(R.string.progresso_dia_mes, p[2], p[1]) else iso
}

@Composable
private fun BlocoDeEvolucao(trend: List<ExerciseTrendDto>) {
    // Escala COMPARTILHADA pelas tres linhas: cada uma na sua escala faria subidas de tamanhos
    // diferentes parecerem iguais — a mentira mais comum em grafico de linha.
    val todos = trend.flatMap { it.points.map { p -> p.estimated1rm } }
    val minimo = todos.minOrNull() ?: 0.0
    val maximo = todos.maxOrNull() ?: 1.0
    val amplitude = maximo - minimo
    // ⚠️ Serie PLANA (um ponto so, ou todos iguais) nao tem amplitude: normalizar dividiria por
    // zero, e o fallback ingenuo de "amplitude = 1" joga tudo em y=0, afundando o ponto na linha
    // de base como se a carga fosse zero. Linha sem variacao mora no MEIO do grafico.
    val semVariacao = amplitude <= 0.0

    val semanas = trend.flatMap { it.points.map { p -> p.weekStart } }.distinct().sorted()
    val cores = listOf(Chart1, Chart2, Chart3)

    CartaoDeGrafico(stringResource(R.string.progresso_evolucao)) {
        Row {
            // Em quilos, nao em toneladas: aqui o numero e carga estimada de UMA serie.
            CalhaDoEixo(
                de_cima_para_baixo = listOf(
                    umaCasa(maximo),
                    umaCasa((maximo + minimo) / 2),
                    umaCasa(minimo),
                ),
                altura = 140.dp,
            )
            GraficoDeLinhas(
                linhas = trend.mapIndexed { i, ex ->
                    LinhaDoGrafico(
                        nome = ex.name,
                        cor = cores[i % cores.size],
                        pontos = ex.points.map { p ->
                            val x = if (semanas.size > 1) {
                                semanas.indexOf(p.weekStart).toFloat() / (semanas.size - 1)
                            } else {
                                0f
                            }
                            val y = if (semVariacao) 0.5 else (p.estimated1rm - minimo) / amplitude
                            x to y.toFloat()
                        },
                    )
                },
                modifier = Modifier.weight(1f).height(140.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        trend.forEachIndexed { i, ex ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(cores[i % cores.size], MaterialTheme.shapes.extraSmall))
                Spacer(Modifier.width(8.dp))
                Text(ex.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                // Com um ponto so nao ha variacao a declarar. "+0,0 %" ali parece estagnacao
                // medida, quando e so falta de historico.
                if (ex.points.size >= 2) {
                    Text(
                        comSinal(ex.changePercent, "%"),
                        style = MaterialTheme.typography.bodySmall,
                        color = corDoDelta(ex.changePercent),
                    )
                }
            }
        }
    }
}

/** O rotulo do grupo vem do `strings.xml` pelo enum (ARCH #37), nunca do servidor. */
@Composable
private fun rotuloDe(grupo: dev.rafael.contract.profile.MuscleGroup): String =
    stringResource(grupo.rotulo())

@Composable
private fun BlocoDeVolume(volume: MuscleVolumeDto) {
    val naoClassificado = stringResource(R.string.progresso_nao_classificado)
    val linhas = buildList {
        volume.byMuscle.forEach { (grupo, series) -> add(rotuloDe(grupo) to series) }
        if (volume.unclassified > 0.0) add(naoClassificado to volume.unclassified)
    }.sortedByDescending { it.second }

    val maior = linhas.maxOfOrNull { it.second }?.takeIf { it > 0.0 } ?: 1.0

    CartaoDeGrafico(stringResource(R.string.progresso_volume)) {
        BarrasHorizontais(
            itens = linhas.map { (nome, series) ->
                Triple(nome, (series / maior).toFloat(), umaCasa(series))
            },
            cor = Chart1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.progresso_volume_nota),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Largura da calha de rotulos do eixo Y. Fixa para os dois graficos ficarem alinhados. */
private val LARGURA_DA_CALHA = 34.dp

/**
 * Os rotulos do eixo Y, de cima para baixo, distribuidos na mesma altura do grafico.
 *
 * Mora aqui, e nao no Canvas, pela regra do arquivo de graficos: texto desenhado em Canvas ignora
 * o tamanho de fonte do sistema e nao chega ao leitor de tela.
 */
@Composable
private fun CalhaDoEixo(de_cima_para_baixo: List<String>, altura: androidx.compose.ui.unit.Dp) {
    Column(
        Modifier.width(LARGURA_DA_CALHA).height(altura),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        de_cima_para_baixo.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CartaoDeGrafico(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            conteudo()
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Formatacao                                                                 */
/* -------------------------------------------------------------------------- */

private const val LINHAS_DA_COMPARACAO = 4

/** Silhueta generica no cartao trancado — nao sao dados de ninguem. */
private val AMOSTRA_DO_PAYWALL = listOf(0.45f, 0.62f, 0.5f, 0.3f, 0.78f, 0.55f, 0.6f, 0.7f)

/** Quilos viram toneladas com uma casa: "175,8" se le, "175876" nao. */
internal fun emToneladas(kg: Double): String = umaCasa(kg / 1000.0)

internal fun umaCasa(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)

/**
 * O sinal e parte do numero, nao enfeite: "+335 kg" e "335 kg" dizem coisas diferentes quando o
 * assunto e variacao, e o `+` e o que diz que subiu sem precisar da cor.
 * ⚠️ [unidade] vem de FORA, do `strings.xml`: o quilo e `R.string.comum_kg`. Escrever o literal
 * aqui era texto de usuario escondido em Kotlin, a classe de defeito que o inventario da G.3
 * existe para pegar. A chave ja existia e estava ORFA justamente por isso.
 *
 * O porcento e a UNICA excecao, e e deliberada: esse valor sozinho no catalogo tropecaria no
 * invariante `todo placeholder e posicional` do `CatalogoDeStringsTest`, que leria o sinal solto
 * como placeholder sem indice. Simbolo matematico fica no codigo; palavra, nao.
 */
internal fun comSinal(v: Double, unidade: String): String {
    val sinal = if (v > 0) "+" else if (v < 0) "-" else ""
    val numero = if (unidade == "%") umaCasa(abs(v)) else abs(v).toInt().toString()
    return "$sinal$numero $unidade".trim()
}

@Composable
private fun corDoDelta(v: Double): Color = when {
    v > 0 -> MaterialTheme.colorScheme.primary
    v < 0 -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
