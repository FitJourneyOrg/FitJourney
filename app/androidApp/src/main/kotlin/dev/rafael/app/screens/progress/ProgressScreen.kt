package dev.rafael.app.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import dev.rafael.app.ui.nomeDoPrograma
import dev.rafael.app.ui.rotulo
import dev.rafael.contract.stats.ExerciseTrendDto
import dev.rafael.contract.stats.MuscleVolumeDto
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.contract.stats.WeeklyLoadDto
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.stats.domain.FiltroDeProgresso
import dev.rafael.core.designsystem.Chart1
import dev.rafael.core.designsystem.Chart2
import dev.rafael.core.designsystem.Chart3
import org.koin.androidx.compose.koinViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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

    // ⚠️ A margem NAO vive na coluna de fora. A fileira de chips rola na horizontal e precisa
    // SANGRAR ate a borda da tela: com a margem no pai, o chip e cortado 20dp antes do fim e a
    // lista parece ter acabado. A margem desce para cada bloco, e a fileira a aplica por dentro
    // do scroll, onde ela vira padding de CONTEUDO.
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.comum_progresso),
            Modifier.padding(horizontal = MARGEM),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        val analise = state.analise

        FiltroDeProgramas(
            estrutura = state.estrutura,
            programas = state.programas,
            selecionado = state.filtro,
            onSelecionar = viewModel::selecionar,
        )
        FaixaDeSemanas(
            estrutura = state.estrutura,
            selecionado = state.filtro,
            onEscolher = viewModel::selecionarFaixa,
        )

        Column(Modifier.padding(horizontal = MARGEM)) {
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
        }
        Spacer(Modifier.height(24.dp))
    }
}

/* -------------------------------------------------------------------------- */
/*  Filtro (J.3)                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Os chips de recorte: Todos, um por programa com sessao, e Avulsos.
 *
 * ## Some quando nao ha o que escolher
 *
 * Com um unico programa e nenhuma sessao avulsa, "Todos" e o programa mostram **o mesmo grafico**
 * — um filtro que nao filtra e ruido que ensina a pessoa a ignorar a fileira. Por isso a regra e
 * duas opcoes de verdade, nao "existe programa".
 *
 * ## O nome sai do cache LOCAL de programas, nunca do servidor
 *
 * O `availablePrograms` traz so ids, porque o nome e derivado de `daysPerWeek` + `split` no
 * idioma da tela (V48/ARCH #37). Id que o cache local ainda nao conhece e PULADO em vez de virar
 * um chip generico: dois chips escritos "Programa" sao piores que um chip a menos, e a ausencia
 * se resolve sozinha no proximo sync.
 */
@Composable
private fun FiltroDeProgramas(
    estrutura: EstruturaDoFiltro,
    programas: List<Program>,
    selecionado: FiltroDeProgresso,
    onSelecionar: (FiltroDeProgresso) -> Unit,
) {
    val porId = programas.mapNotNull { p -> p.id?.let { it to p } }.toMap()
    val comNome = estrutura.programas.mapNotNull { id -> porId[id]?.let { id to it } }
    val temAvulsos = estrutura.temAvulsos
    if (comNome.size + (if (temAvulsos) 1 else 0) < 2) return

    Row(
        // A ordem importa: `padding` DEPOIS do `horizontalScroll` fica DENTRO da area rolavel,
        // entao e padding de CONTEUDO e nao recorte da area visivel.
        //
        // ⚠️ So no `start`. O comeco alinha com o titulo e os cartoes, porque e dali que o olho
        // parte; o fim NAO ganha margem de proposito — chip encostando na borda direita e o que
        // diz "tem mais, role". Fechar dos dois lados faria a fileira parecer completa mesmo
        // quando nao esta.
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = MARGEM),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip(
            texto = stringResource(R.string.progresso_filtro_todos),
            ativo = selecionado is FiltroDeProgresso.Todos,
            onClick = { onSelecionar(FiltroDeProgresso.Todos) },
        )
        comNome.forEach { (id, p) ->
            val ativo = selecionado is FiltroDeProgresso.DoPrograma && selecionado.programId == id
            Chip(
                texto = nomeDoPrograma(p.name, p.daysPerWeek, p.split),
                ativo = ativo,
                // O guarda `!ativo` nao e economia de requisicao — e o que impede tocar no chip
                // ativo de ZERAR a faixa escolhida, porque `DoPrograma(id)` sem faixa e um
                // filtro diferente de `DoPrograma(id, 10, 14)`. Trocar DE programa reseta a
                // faixa de proposito; reafirmar o mesmo programa nao.
                onClick = { if (!ativo) onSelecionar(FiltroDeProgresso.DoPrograma(id)) },
            )
        }
        if (temAvulsos) {
            Chip(
                texto = stringResource(R.string.progresso_filtro_avulsos),
                ativo = selecionado is FiltroDeProgresso.Avulsos,
                onClick = { onSelecionar(FiltroDeProgresso.Avulsos) },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Chip(texto: String, ativo: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = ativo,
        onClick = onClick,
        label = { Text(texto, maxLines = 1) },
    )
}

/**
 * A faixa de semanas DO PROGRAMA (J.3.3b) — "quero ver da semana 10 a 14".
 *
 * ## Fica junto dos chips, nao embaixo do grafico
 *
 * A faixa recorta o DTO inteiro: tonelagem, comparacao, series por grupo, os tres graficos. Posto
 * abaixo do grafico semanal, o controle pareceria mexer so naquele grafico enquanto o cartao de
 * totais acima dele ja estaria obedecendo — o controle mentiria sobre o proprio alcance. Chips e
 * faixa definem a mesma coisa (o recorte), entao moram no mesmo lugar.
 *
 * ## O arraste e LOCAL; so o fim do gesto vai a rede
 *
 * Um `RangeSlider` emite a cada pixel, e cada valor e um recorte com chave de cache propria.
 * Aplicar no `onValueChange` viraria dezenas de requisicoes e dezenas de entradas de cache por
 * gesto — e o grafico piscaria sob o dedo. O estado do arraste e `remember` local; o filtro muda
 * no `onValueChangeFinished`.
 *
 * ## Some quando nao ha o que escolher
 *
 * Sem programa escolhido nao existe "semana do programa" (ARCH #27: programas coexistem, entao a
 * semana 3 de X e a 11 de Y acontecem no mesmo dia). Com uma semana so, nao ha faixa. Em ambos o
 * controle nao aparece, em vez de aparecer desabilitado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FaixaDeSemanas(
    estrutura: EstruturaDoFiltro,
    selecionado: FiltroDeProgresso,
    onEscolher: (Int, Int) -> Unit,
) {
    val programa = selecionado as? FiltroDeProgresso.DoPrograma ?: return
    // A medida tem de ser DESTE programa: durante a troca, a estrutura ainda fala do anterior.
    if (estrutura.programaMedido != programa.programId) return
    val total = estrutura.semanas ?: return
    if (total < 2) return

    // O filtro vem primeiro: logo depois do gesto ele ja carrega a faixa, entao o rotulo nao
    // espera a resposta do servidor para mostrar o que a pessoa acabou de escolher.
    val de = (programa.de ?: estrutura.de ?: 1).coerceIn(1, total)
    val ate = (programa.ate ?: estrutura.ate ?: total).coerceIn(de, total)

    // Chave com o programa E o teto: programa novo, ou programa que cresceu uma semana, recomeca
    // do que o servidor aplicou — nao do que o dedo deixou no slider anterior.
    var arraste by remember(programa.programId, total, de, ate) {
        mutableStateOf(de.toFloat()..ate.toFloat())
    }
    val inicio = arraste.start.roundToInt()
    val fim = arraste.endInclusive.roundToInt()

    Column(Modifier.padding(horizontal = MARGEM)) {
        Text(
            stringResource(R.string.progresso_faixa, inicio, fim),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RangeSlider(
            value = arraste,
            onValueChange = { arraste = it },
            valueRange = 1f..total.toFloat(),
            // `steps` conta os pontos ENTRE as pontas: 8 semanas sao 8 valores, 6 no meio.
            // Sem isso o slider e continuo e devolveria 7,3 — semana nao tem fracao.
            steps = (total - 2).coerceAtLeast(0),
            onValueChangeFinished = { onEscolher(inicio, fim) },
        )
    }
    Spacer(Modifier.height(4.dp))
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
                    rotuloDaSemana(semana),
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
                rotuloDaSemana(semanas.first()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                // ⚠️ "esta semana" SO quando a ultima barra e mesmo a desta semana. Com faixa do
                // programa (J.3.3b) ela pode ser a semana 14 de quem esta na 16 — a frase fixa
                // viraria rotulo errado, e rotulo errado num grafico e pior que rotulo nenhum,
                // porque o desenho continua convincente.
                rotuloDaSemana(semanas.last(), atual = true),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Como a semana se chama no eixo.
 *
 * Num recorte de programa a data nao e a referencia que a pessoa usa — ela pensa "semana 10 do
 * programa", nao "12/08" (ARCH #27: programas coexistem, entao a mesma data e semana 3 de um e 11
 * de outro). O `weekNumber` vem do servidor exatamente quando existe essa referencia; nulo
 * significa recorte por calendario, e ai a data e que localiza.
 *
 * @param atual permite "esta semana" no fim do eixo — valido so sem faixa, porque com faixa a
 *   ultima barra pode estar semanas atras.
 */
@Composable
private fun rotuloDaSemana(semana: WeeklyLoadDto, atual: Boolean = false): String {
    val n = semana.weekNumber
    return when {
        n != null -> stringResource(R.string.progresso_semana_n, n)
        atual -> stringResource(R.string.progresso_semana_atual)
        else -> diaMes(semana.weekStart)
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

/** Margem lateral da tela. Vive aqui porque cada bloco a aplica por conta: ver o KDoc do topo. */
private val MARGEM = 20.dp

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
