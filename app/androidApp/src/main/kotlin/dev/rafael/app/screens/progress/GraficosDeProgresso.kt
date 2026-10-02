package dev.rafael.app.screens.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rafael.core.designsystem.ChartGrid

/**
 * Os tres graficos da tela de Progresso, em Canvas puro.
 *
 * ## Por que sem biblioteca
 *
 * Sao tres formas simples — barras, linha, barras horizontais. Uma lib de chart traria tema
 * proprio, dependencia transitiva e uma gramatica visual que nao e a do app, para desenhar o que
 * cabe em 150 linhas de `Canvas`. Em compensacao, cada decisao visual daqui e nossa e esta escrita.
 *
 * ## O Canvas desenha MARCA, nunca texto
 *
 * Todo rotulo e um `Text` de verdade, posicionado por `Row`/`Column`. Texto dentro de Canvas
 * ignora tamanho de fonte do sistema e fica fora do alcance do leitor de tela — numa tela cujo
 * assunto E o numero, isso nao e detalhe.
 *
 * > **Grafico que desenha o proprio rotulo e grafico que nao se deixa ler.**
 *
 * ## Escala vem pronta de fora
 *
 * Estes composables nao normalizam nada: recebem valores ja em 0..1. Quem desenha nao decide a
 * escala — se decidisse, duas series do mesmo grafico poderiam acabar em escalas diferentes, que
 * e a forma mais eficiente de mentir num grafico.
 */

/**
 * Barras verticais. [valores] ja normalizados em 0..1; [destaque] e o indice que recebe a cor
 * cheia (os demais ficam esmaecidos).
 *
 * ⚠️ Valor zero vira um toco de 2dp em vez de nada. Semana sem treino E informacao — foi a semana
 * que a pessoa faltou —, e uma barra ausente se confunde com o fim do grafico.
 */
@Composable
fun GraficoDeBarras(
    valores: List<Float>,
    destaque: Int,
    cor: Color,
    modifier: Modifier = Modifier,
    onSelecionar: ((Int) -> Unit)? = null,
) {
    val quantas = valores.size
    val entrada = animacaoDeEntrada(quantas)
    Canvas(
        modifier
            .clipToBounds()
            .then(
                if (onSelecionar == null || quantas == 0) {
                    Modifier
                } else {
                    Modifier.pointerInput(quantas) {
                        detectTapGestures { toque ->
                            // A zona de toque e a FATIA inteira (barra + vao), nao a barra.
                            // Barra de 8 semanas tem ~30dp de largura, abaixo do alvo minimo de
                            // 48dp: exigir acerto na marca faria o toque falhar na barra baixa,
                            // que e justamente a semana que a pessoa quer entender.
                            val passo = size.width.toFloat() / quantas
                            onSelecionar((toque.x / passo).toInt().coerceIn(0, quantas - 1))
                        }
                    }
                },
            ),
    ) {
        if (valores.isEmpty()) return@Canvas
        val vao = 6.dp.toPx()
        val raio = 4.dp.toPx()
        val toco = 2.dp.toPx()
        val largura = ((size.width - vao * (valores.size - 1)) / valores.size).coerceAtLeast(1f)

        // Grade atras das barras, recessiva: so da referencia de altura. As linhas batem com os
        // rotulos que a TELA desenha na calha a esquerda — por isso sao tres, e nao quatro.
        repeat(3) { i ->
            val y = size.height * (i / 2f)
            drawLine(ChartGrid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        valores.forEachIndexed { i, v ->
            val altura = (v.coerceIn(0f, 1f) * entrada * size.height).coerceAtLeast(toco)
            val tom = if (i == destaque) cor else cor.copy(alpha = 0.5f)
            drawRoundRect(
                // Gradiente vertical sutil: a barra ganha volume sem virar outra cor. O topo e a
                // cor cheia porque e o topo que a pessoa compara entre as barras.
                brush = Brush.verticalGradient(
                    colors = listOf(tom, tom.copy(alpha = tom.alpha * 0.55f)),
                    startY = size.height - altura,
                    endY = size.height,
                ),
                topLeft = Offset(i * (largura + vao), size.height - altura),
                // +raio para o canto de BAIXO sair pela borda: barra encostada na linha de base
                // tem topo arredondado e pe reto, nao uma pastilha flutuando.
                size = Size(largura, altura + raio),
                cornerRadius = CornerRadius(raio, raio),
            )
        }
    }
}

/** Uma linha do grafico de evolucao. [pontos] sao pares (x, y) ja normalizados em 0..1. */
data class LinhaDoGrafico(
    val nome: String,
    val pontos: List<Pair<Float, Float>>,
    val cor: Color,
)

/**
 * Linhas sobre a mesma escala, com marcador no ultimo ponto de cada uma.
 *
 * A identidade NUNCA depende so da cor: a legenda abaixo repete nome e variacao. E a regra de
 * acessibilidade e tambem o que faz [dev.rafael.core.designsystem.Chart3] poder conviver com o
 * roxo da IA.
 */
@Composable
fun GraficoDeLinhas(linhas: List<LinhaDoGrafico>, modifier: Modifier = Modifier) {
    val entrada = animacaoDeEntrada(linhas.size)
    Canvas(modifier) {
        repeat(3) { i ->
            val y = size.height * (i / 2f)
            drawLine(ChartGrid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        // Desenha de tras para a frente: a PRIMEIRA linha e a mais relevante (a de maior
        // tonelagem) e tem de ficar por cima quando duas se cruzam.
        linhas.asReversed().forEach { linha ->
            if (linha.pontos.isEmpty()) return@forEach

            fun ponto(x: Float, y: Float) = Offset(
                x.coerceIn(0f, 1f) * size.width,
                // A entrada cresce a partir da BASE: a linha sobe ate o valor, em vez de aparecer
                // pronta. Sem isso o grafico "pisca" e nao se le a ordem das series.
                size.height - (y.coerceIn(0f, 1f) * entrada * size.height),
            )

            val caminho = Path()
            linha.pontos.forEachIndexed { i, (x, y) ->
                val p = ponto(x, y)
                if (i == 0) caminho.moveTo(p.x, p.y) else caminho.lineTo(p.x, p.y)
            }

            // Area sob a curva, em degrade ate transparente. E o que separa "duas retas num
            // retangulo" de um grafico: da peso visual a linha sem engrossa-la.
            if (linha.pontos.size > 1) {
                val area = Path()
                area.addPath(caminho)
                val fim = ponto(linha.pontos.last().first, linha.pontos.last().second)
                val ini = ponto(linha.pontos.first().first, linha.pontos.first().second)
                area.lineTo(fim.x, size.height)
                area.lineTo(ini.x, size.height)
                area.close()
                drawPath(
                    area,
                    Brush.verticalGradient(
                        colors = listOf(linha.cor.copy(alpha = 0.28f), Color.Transparent),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }

            drawPath(caminho, linha.cor, style = Stroke(width = 2.dp.toPx()))

            val ultimo = ponto(linha.pontos.last().first, linha.pontos.last().second)
            // Anel na cor do cartao: o marcador nao se funde com a area de outra serie atras.
            drawCircle(linha.cor.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = ultimo)
            drawCircle(linha.cor, radius = 4.dp.toPx(), center = ultimo)
        }
    }
}

/**
 * A entrada do grafico: 0..1 em 550ms, uma vez, quando o bloco aparece.
 *
 * `Animatable` e nao `animateFloatAsState` porque o valor nao reage a estado nenhum — ele toca
 * uma vez e fica em 1. A chave e o TAMANHO da serie: dado novo com a mesma forma nao reanima, e
 * reanimar a cada sync faria o grafico tremer a cada dois minutos.
 */
@Composable
private fun animacaoDeEntrada(chave: Int): Float {
    val progresso = remember(chave) { Animatable(0f) }
    LaunchedEffect(chave) {
        progresso.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing))
    }
    return progresso.value
}

/**
 * Barras horizontais com rotulo a esquerda e valor a direita — direto na marca, sem legenda.
 *
 * [itens] vem ordenado por quem chama; o valor ja normalizado em 0..1 e o texto do valor ja
 * formatado, porque formatar numero e decisao de idioma e nao de desenho.
 */
@Composable
fun BarrasHorizontais(
    itens: List<Triple<String, Float, String>>,
    cor: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itens.forEach { (rotulo, fracao, valor) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rotulo,
                    Modifier.width(78.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.weight(1f).height(14.dp).clipToBounds()) {
                    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                        val raio = 4.dp.toPx()
                        val largura = (fracao.coerceIn(0f, 1f) * size.width).coerceAtLeast(2.dp.toPx())
                        // Ponta ESQUERDA reta (encostada no eixo), ponta direita arredondada:
                        // o canto de tras sai pela borda e o `clipToBounds` do Box o apara.
                        drawRoundRect(
                            color = cor,
                            topLeft = Offset(-raio, 0f),
                            size = Size(largura + raio, size.height),
                            cornerRadius = CornerRadius(raio, raio),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    valor,
                    Modifier.width(40.dp).padding(start = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
