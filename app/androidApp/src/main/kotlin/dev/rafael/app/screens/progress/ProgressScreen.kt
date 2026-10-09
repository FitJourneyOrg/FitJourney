package dev.rafael.app.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.R
import dev.rafael.app.ui.nomeDoPrograma
import dev.rafael.app.ui.rotulo
import dev.rafael.contract.stats.ExerciseSummaryDto
import dev.rafael.contract.stats.ExerciseTrendDto
import dev.rafael.contract.stats.JanelasDeProgresso
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
    onAbrirDetalheDeExercicio: (programId: String, exercicioId: String) -> Unit = { _, _ -> },
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
        SeletorDeJanela(
            estrutura = state.estrutura,
            selecionado = state.filtro,
            trancado = state.trancado,
            onEscolher = viewModel::selecionarJanela,
            onOpenPaywall = onOpenPaywall,
        )

        Column(Modifier.padding(horizontal = MARGEM)) {
            // ⚠️ `analise.totalSessions`, nunca o `stats` global: o `stats` conta TODOS os
            // treinos de sempre, e ao lado de uma tonelagem recortada isso lia
            // "40,2 t · 30 treinos" com os 30 incluindo o outro programa e os avulsos. Os dois
            // numeros do cartao tem de medir o mesmo recorte.
            CartaoDeTotais(analise, analise?.totalSessions ?: 0)
            analise?.lastVsPrevious?.let { c ->
                Spacer(Modifier.height(10.dp))
                CartaoDeComparacao(c)
            }

            Spacer(Modifier.height(10.dp))
            // ⚠️ A ordem e a hierarquia aqui mudaram na J.4.3, e e o ponto delicado da fatia.
            //
            // Antes `trancado` pulava o corpo inteiro: havia tres estados mutuamente exclusivos.
            // Com a carga por semana no gratis, "trancado" deixou de significar "nao ha nada a
            // mostrar" e passou a significar "falta o resto" — entao o grafico semanal e o
            // cartao de assinatura aparecem JUNTOS.
            //
            // `semCarga` continua tendo precedencia sobre os dois: quem so treina peso corporal
            // tem de ler a explicacao, nao um grafico de oito zeros com um paywall embaixo.
            when {
                state.semCarga -> CartaoSemCarga()
                analise != null -> {
                    // So existe "detalhe de exercicio" dentro de um PROGRAMA: e o programId que
                    // a rota nova exige. No eixo de calendario nao ha referente -- o botao some,
                    // nao aparece desabilitado (ver LinhaDeExercicio).
                    val programIdAtual = (state.filtro as? FiltroDeProgresso.DoPrograma)?.programId
                    BlocosDaAnalise(
                        analise = analise,
                        trancado = state.trancado,
                        onOpenPaywall = onOpenPaywall,
                        onAlternarExercicio = viewModel::alternarExercicio,
                        onAbrirDetalhe = programIdAtual?.let { pid ->
                            { exercicioId: String -> onAbrirDetalheDeExercicio(pid, exercicioId) }
                        },
                    )
                }
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
        // A janela escolhida SOBREVIVE a troca de recorte: quem esta vendo 52 semanas de tudo e
        // toca em "avulsos" quer 52 semanas de avulsos. Vindo de um programa (que nao tem
        // janela) cai no padrao. E o `!ativo` impede que tocar no chip ativo zere a janela.
        val janelaAtual = selecionado.semanas ?: JanelasDeProgresso.PADRAO
        val emTodos = selecionado is FiltroDeProgresso.Todos
        Chip(
            texto = stringResource(R.string.progresso_filtro_todos),
            ativo = emTodos,
            onClick = { if (!emTodos) onSelecionar(FiltroDeProgresso.Todos(janelaAtual)) },
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
            val emAvulsos = selecionado is FiltroDeProgresso.Avulsos
            Chip(
                texto = stringResource(R.string.progresso_filtro_avulsos),
                ativo = emAvulsos,
                onClick = { if (!emAvulsos) onSelecionar(FiltroDeProgresso.Avulsos(janelaAtual)) },
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
            // ⚠️ LINHA, nao barras. A amostra era um grafico de barras esmaecido — e desde a
            // J.4.1 o grafico de barras de VERDADE fica logo acima deste cartao. Barra falsa
            // debaixo de barra real lia "assine para ver isto que voce ja esta vendo". A linha
            // representa o que de fato esta trancado: a evolucao por exercicio.
            GraficoDeLinhas(
                linhas = listOf(
                    LinhaDoGrafico("", AMOSTRA_DO_PAYWALL, Chart2.copy(alpha = 0.35f)),
                ),
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
private fun BlocosDaAnalise(
    analise: ProgressDto,
    trancado: Boolean,
    onOpenPaywall: () -> Unit,
    onAlternarExercicio: (String, List<String>) -> Unit,
    /** Ver [LinhaDeExercicio] -- nulo fora do recorte de programa. */
    onAbrirDetalhe: ((String) -> Unit)?,
) {
    // Gratis desde a J.4.1 — e por isso vem ANTES do cartao de assinatura: o que se vende e a
    // profundidade, e mostrar a janela curta primeiro e o argumento.
    analise.weeklyLoad?.takeIf { it.isNotEmpty() }?.let {
        BlocoDeCargaSemanal(it)
        Spacer(Modifier.height(10.dp))
    }
    if (trancado) {
        CartaoTrancado(onOpenPaywall)
        return
    }
    analise.strengthTrend?.takeIf { it.isNotEmpty() }?.let {
        // A regua vem do `weeklyLoad`: ele E a lista de semanas da janela, em ordem. Sem ela a
        // linha se espalha pela largura do cartao independente do periodo (ver `posicaoNaRegua`).
        BlocoDeEvolucao(
            trend = it,
            regua = analise.weeklyLoad.orEmpty().map { s -> s.weekStart },
            resumo = analise.exerciseSummary.orEmpty(),
            onAlternar = onAlternarExercicio,
            onAbrirDetalhe = onAbrirDetalhe,
        )
        Spacer(Modifier.height(10.dp))
    }
    analise.setsByMuscle?.takeIf { it.byMuscle.isNotEmpty() }?.let {
        BlocoDeVolume(it)
    }
}

/**
 * Os chips de janela: 8 / 26 / 52 semanas. So no eixo de CALENDARIO.
 *
 * ## Travado e VISIVEL, nunca escondido
 *
 * Esconder 26 e 52 do free nao vende nada — ninguem quer o que nao sabe que existe, e era
 * exatamente o problema de quando o grafico inteiro era pago. O chip travado leva ao paywall em
 * vez de mandar a requisicao; o servidor encaixa igual, porque o cliente nao e autoridade (#16).
 *
 * ## O selecionado sai do `weeksWindow`, nao do que a tela pediu
 *
 * O servidor devolve a janela APLICADA. Marcar o que foi pedido deixaria o chip dizendo 52 com o
 * eixo desenhando 8 — a mesma mentira do rotulo "esta semana" numa faixa que terminou semanas
 * atras. O `?:` so cobre o instante antes da primeira resposta.
 */
@Composable
private fun SeletorDeJanela(
    estrutura: EstruturaDoFiltro,
    selecionado: FiltroDeProgresso,
    trancado: Boolean,
    onEscolher: (Int) -> Unit,
    onOpenPaywall: () -> Unit,
) {
    val pedida = selecionado.semanas ?: return   // recorte de programa: a faixa e a janela
    val atual = estrutura.janela ?: pedida

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = MARGEM),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        JanelasDeProgresso.OFERECIDAS.forEach { semanas ->
            val travado = trancado && semanas > JanelasDeProgresso.FREE
            val ativo = semanas == atual
            FilterChip(
                selected = ativo,
                onClick = {
                    when {
                        travado -> onOpenPaywall()
                        !ativo -> onEscolher(semanas)
                    }
                },
                label = { Text(stringResource(R.string.progresso_janela, semanas), maxLines = 1) },
                trailingIcon = if (!travado) null else {
                    { Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp)) }
                },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
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

    // Decidido pela CONTAGEM, nao pela largura medida: a decisao tambem governa os rotulos do
    // eixo, que ficam fora do `Row` do grafico. Oito barras cabem com folga; vinte e seis nao
    // cabem em celular nenhum, e barra de 11dp e alvo de toque ruim antes de ser grafico ruim.
    val rola = semanas.size > SEMANAS_QUE_CABEM

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
            val valores = semanas.map { (it.kg / maior).toFloat() }
            // Tocar a mesma barra DESMARCA: sem isso nao haveria como voltar ao estado de
            // resumo, e a tela ficaria presa na ultima semana que alguem encostou.
            val alternar = { i: Int -> selecionada = if (selecionada == i) null else i }

            if (!rola) {
                GraficoDeBarras(valores, selecionada ?: indiceDoPico, Chart1,
                    Modifier.weight(1f).height(120.dp), alternar)
            } else {
                // ⚠️ A CALHA DO Y FICA FORA do scroll (ela e a irma deste `else`, no mesmo Row):
                // escala que rola junto com as barras deixa de ser escala. So o desenho rola.
                val rolagem = rememberScrollState()
                // Comeca no FIM. A pergunta padrao e "como estou agora", nao "como eu estava em
                // janeiro" — abrir 52 semanas no comeco faria a pessoa arrastar a cada visita.
                // A chave e o `maxValue` porque antes da medida ele e zero: so depois do layout
                // existe fim para onde ir.
                LaunchedEffect(rolagem.maxValue) { rolagem.scrollTo(rolagem.maxValue) }
                Row(Modifier.weight(1f).horizontalScroll(rolagem)) {
                    GraficoDeBarras(valores, selecionada ?: indiceDoPico, Chart1,
                        Modifier.width(LARGURA_POR_SEMANA * semanas.size).height(120.dp), alternar)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Eixo so nas PONTAS, e so quando NAO rola.
        //
        // Parado, oito datas nao cabem na largura de um celular e rotulo que se corta nao e
        // rotulo: o que a pessoa precisa saber e onde a serie comeca e que a ultima barra e a
        // semana dela — o meio se interpola sozinho.
        //
        // Rolando, as pontas passam a MENTIR: a primeira e a ultima barra visiveis nao sao a
        // primeira e a ultima da serie, e rotulo que depende de onde o dedo parou nao e rotulo.
        // Ali o eixo da lugar a dica, e quem localiza a semana e o toque.
        if (rola) {
            Text(
                stringResource(R.string.progresso_arraste_semana),
                Modifier.padding(start = LARGURA_DA_CALHA),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else Row(Modifier.fillMaxWidth().padding(start = LARGURA_DA_CALHA)) {
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
 * Onde a semana cai no eixo X, de 0 a 1, dada a regua de semanas da janela.
 *
 * Pura e `internal` de proposito: e a conta que a J.4.3b veio consertar, e conta dentro de
 * `Composable` nao tem teste. Devolve nulo para semana fora da regua — ver a chamada.
 *
 * Regua de um elemento so devolve 0: com uma semana nao ha eixo, e dividir por zero ali daria
 * `NaN`, que o Canvas desenha como nada e vira "o grafico sumiu" sem erro nenhum.
 */
internal fun posicaoNaRegua(semana: String, regua: List<String>): Float? {
    val i = regua.indexOf(semana)
    if (i < 0) return null
    return if (regua.size > 1) i.toFloat() / (regua.size - 1) else 0f
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
internal fun diaMes(iso: String): String {
    val p = iso.split("-")
    return if (p.size == 3) stringResource(R.string.progresso_dia_mes, p[2], p[1]) else iso
}

@Composable
private fun BlocoDeEvolucao(
    trend: List<ExerciseTrendDto>,
    regua: List<String>,
    resumo: List<ExerciseSummaryDto>,
    onAlternar: (String, List<String>) -> Unit,
    onAbrirDetalhe: ((String) -> Unit)?,
) {
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

    // ⚠️ A regua do eixo X e a JANELA, nao os pontos que existem.
    //
    // Antes o x saia do indice do ponto entre os pontos presentes: oito pontos viravam
    // 0, 1/7 … 1 e a linha preenchia a largura INDEPENDENTE da janela. Com 8 semanas isso era
    // inofensivo, porque dado e janela coincidiam; com 52, a linha desenhava um ano de
    // progressao onde havia dois meses de dado — e grafico que mente continua convincente.
    //
    // Agora a linha comeca onde o dado comeca e os buracos aparecem como buracos. O `ifEmpty`
    // cobre o caso de a carga por semana nao ter vindo: sem regua, volta ao comportamento
    // antigo, que e ruim mas e melhor do que nao desenhar.
    val semanas = regua.ifEmpty {
        trend.flatMap { it.points.map { p -> p.weekStart } }.distinct().sorted()
    }
    val cores = listOf(Chart1, Chart2, Chart3)

    // Mesma decisao e mesmo limiar das barras, pelo mesmo motivo: numa janela de 26/52 semanas
    // com pouco historico real, a linha fica espremida num canto do cartao — area vazia lendo
    // como grafico quebrado, nao como "ainda nao ha mais dado". Largura fixa por semana + scroll
    // resolve sem tocar na regua: ela continua sendo a janela inteira, so o espaco que ela ocupa
    // na tela deixa de ser fixo. Rola de forma INDEPENDENTE do cartao de barras (nao compartilha
    // `ScrollState`) — e a dificuldade deliberadamente adiada: ver `debitos.md`.
    val rola = semanas.size > SEMANAS_QUE_CABEM

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
            val linhas = trend.mapIndexed { i, ex ->
                LinhaDoGrafico(
                    nome = ex.name,
                    cor = cores[i % cores.size],
                    // `mapNotNull`: ponto fora da regua e DESCARTADO, nao encaixado na
                    // ponta. Por construcao nao acontece (as duas listas saem do mesmo
                    // recorte, com a mesma conta de semana), e e justamente por isso que
                    // encaixar seria pior: esconderia a divergencia em vez de some-la.
                    pontos = ex.points.mapNotNull { p ->
                        val x = posicaoNaRegua(p.weekStart, semanas) ?: return@mapNotNull null
                        val y = if (semVariacao) 0.5 else (p.estimated1rm - minimo) / amplitude
                        x to y.toFloat()
                    },
                )
            }
            if (!rola) {
                GraficoDeLinhas(linhas, Modifier.weight(1f).height(140.dp))
            } else {
                // ⚠️ Mesma regra das barras: a CALHA DO Y fica fora do scroll (irma deste
                // `else`, no mesmo `Row`) — escala que rola junto com o desenho deixa de ser
                // escala.
                val rolagem = rememberScrollState()
                // Comeca no FIM, pelo mesmo motivo das barras: a pergunta padrao e "como estou
                // agora", nao "como eu estava ha um ano".
                LaunchedEffect(rolagem.maxValue) { rolagem.scrollTo(rolagem.maxValue) }
                Row(Modifier.weight(1f).horizontalScroll(rolagem)) {
                    GraficoDeLinhas(
                        linhas,
                        Modifier.width(LARGURA_POR_SEMANA * semanas.size).height(140.dp),
                    )
                }
            }
        }
        if (rola) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.progresso_arraste_semana),
                Modifier.padding(start = LARGURA_DA_CALHA),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        ListaDeExercicios(
            // Cache antigo ainda nao tem `exerciseSummary`. Em vez de um cartao sem legenda por
            // alguns segundos, a lista e SINTETIZADA do proprio trend — `sets = 0` apaga a linha
            // de baixo, entao o que falta simplesmente nao aparece.
            resumo = resumo.ifEmpty {
                trend.map {
                    ExerciseSummaryDto(
                        exerciseId = it.exerciseId,
                        name = it.name,
                        current1rm = it.points.lastOrNull()?.estimated1rm ?: 0.0,
                        changePercent = it.changePercent,
                        sets = 0,
                        weeks = it.points.size,
                    )
                }
            },
            desenhados = trend.map { it.exerciseId },
            cores = cores,
            onAlternar = onAlternar,
            onAbrirDetalhe = onAbrirDetalhe,
        )
    }
}

/**
 * A lista de exercicios do recorte — e o CONTROLE das linhas do grafico (J.4.4).
 *
 * ## Por que ela substituiu a legenda em vez de conviver com ela
 *
 * A legenda listava os tres desenhados com nome e variacao. Os tres primeiros itens desta lista
 * dizem exatamente isso, logo abaixo dela: dois controles para a mesma informacao. Aqui o
 * quadradinho de cor nao e so decoracao de legenda — ele responde "por que ESTES tres", e o
 * toque troca.
 *
 * ## Entra colapsada
 *
 * Trinta itens empurrariam o "series por grupo" para fora da tela. Colapsada mostra os
 * desenhados; "ver todos" abre o resto.
 *
 * ## O que a lista responde que o grafico nao responde
 *
 * "Como esta minha rosca direta." As tres linhas dividem a mesma escala — e e isso que quebra com
 * mais linhas: leg press a 200 kg e rosca a 20 kg no mesmo eixo achatam a rosca numa reta, e ela
 * pode ter subido 30%. A lista da o numero sem desenhar nada.
 */
@Composable
private fun ListaDeExercicios(
    resumo: List<ExerciseSummaryDto>,
    desenhados: List<String>,
    cores: List<Color>,
    onAlternar: (String, List<String>) -> Unit,
    onAbrirDetalhe: ((String) -> Unit)?,
) {
    if (resumo.isEmpty()) return
    // `remember(resumo.size)`: trocar de recorte recolhe a lista. Manter aberta uma lista de 27
    // ao mudar para um recorte de 4 deixaria a tela num estado que a pessoa nao pediu.
    var expandida by remember(resumo.size) { mutableStateOf(false) }
    val mostrados = if (expandida) resumo else resumo.filter { it.exerciseId in desenhados }

    mostrados.forEach { item ->
        // ⚠️ A cor vem da POSICAO NO GRAFICO, nao do exercicio: tirar uma linha reatribui as
        // cores das outras. E consciente, nao descuido.
        //
        // Cor fixa por exercicio seria melhor de ler, mas exigiria uma cor distinguivel por
        // exercicio — e a familia Chart1/2/3 tem TRES, escolhidas juntas e validadas para
        // daltonismo contra a superficie. Com treze exercicios e tres cores, dois selecionados
        // cairiam na mesma e virariam duas linhas indistinguiveis. Cor que muda e pior que cor
        // repetida so ate alguem tentar ler duas linhas da mesma cor.
        val i = desenhados.indexOf(item.exerciseId)
        LinhaDeExercicio(
            item = item,
            cor = if (i >= 0) cores[i % cores.size] else null,
            onClick = { onAlternar(item.exerciseId, desenhados) },
            onAbrirDetalhe = onAbrirDetalhe?.let { f -> { f(item.exerciseId) } },
        )
    }

    if (resumo.size > mostrados.size || expandida) {
        TextButton(onClick = { expandida = !expandida }, Modifier.padding(top = 2.dp)) {
            Text(
                if (expandida) stringResource(R.string.progresso_ver_menos)
                else stringResource(R.string.progresso_ver_todos, resumo.size),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (expandida) {
        Text(
            stringResource(R.string.progresso_toque_exercicio),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinhaDeExercicio(
    item: ExerciseSummaryDto,
    cor: Color?,
    onClick: () -> Unit,
    /**
     * Abre a tela cheia do exercicio DENTRO DESTE PROGRAMA (J.5) -- nulo fora do recorte de
     * programa, onde "ver detalhado" nao tem o que detalhar.
     *
     * Alvo de toque PROPRIO, separado do `onClick` da linha: o toque na linha alterna o
     * exercicio no grafico (J.4.4), e os dois gestos nao podem disputar a mesma area -- senao
     * tocar pra ver o detalhe tambem mexeria nas tres linhas do grafico por engano.
     */
    onAbrirDetalhe: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Quadrado APAGADO quando nao esta desenhado, nunca ausente: o espaco fica, entao a lista
        // nao salta quando um exercicio entra ou sai do grafico.
        Box(
            Modifier
                .size(10.dp)
                .background(cor ?: MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val kg = umaCasa(item.current1rm) + " " + stringResource(R.string.comum_kg)
            Text(
                if (item.sets > 0) {
                    kg + " · " + stringResource(R.string.progresso_series_contagem, item.sets)
                } else {
                    kg
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Com uma semana so nao ha variacao a declarar. "+0,0 %" ali parece estagnacao medida,
        // quando e falta de historico — mesma regra que o grafico ja usava.
        if (item.weeks >= 2) {
            Spacer(Modifier.width(8.dp))
            Text(
                comSinal(item.changePercent, "%"),
                style = MaterialTheme.typography.bodySmall,
                color = corDoDelta(item.changePercent),
            )
        }
        if (onAbrirDetalhe != null) {
            IconButton(onClick = onAbrirDetalhe, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.progresso_ver_detalhe),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
internal fun CalhaDoEixo(de_cima_para_baixo: List<String>, altura: androidx.compose.ui.unit.Dp) {
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
internal fun CartaoDeGrafico(titulo: String, conteudo: @Composable ColumnScope.() -> Unit) {
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
/** Subida com um deload no meio: a forma de um progresso real, sem numero nenhum. */
private val AMOSTRA_DO_PAYWALL = listOf(
    0f to 0.15f, 0.2f to 0.32f, 0.4f to 0.45f, 0.6f to 0.38f, 0.8f to 0.62f, 1f to 0.8f,
)

/**
 * Largura de cada semana quando um grafico rola (barras ou linha). Alvo de toque confortavel;
 * abaixo de ~16dp a barra vira alvo ruim antes de virar grafico ruim.
 */
private val LARGURA_POR_SEMANA = 22.dp

/** Acima disto o grafico rola. Oito cabem com folga, vinte e seis nao cabem em celular nenhum. */
private const val SEMANAS_QUE_CABEM = 12

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
