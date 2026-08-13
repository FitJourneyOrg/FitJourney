package dev.rafael.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * Shimmer (gradiente animado varrendo o componente) — placeholder PADRÃO de carregamento
 * pra texto e imagens no app. Use em qualquer estado de loading em vez de deixar vazio/preto.
 */
fun Modifier.shimmer(shape: Shape = RectangleShape): Modifier = composed {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer-progress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface
    val w = size.width.toFloat().coerceAtLeast(1f)
    val x = (progress * 2f - 1f) * w   // varre de -w a +w
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + w, 0f),
    )
    clip(shape).onGloballyPositioned { size = it.size }.background(brush)
}

/** Linha de texto "esqueleto" com shimmer (enquanto o texto carrega). */
@Composable
fun ShimmerLine(width: Dp, height: Dp = 16.dp) {
    Box(Modifier.size(width, height).shimmer(RoundedCornerShape(4.dp)))
}

/** Esqueleto de lista (linhas shimmer) — pra telas de lista carregando. */
@Composable
fun ShimmerList(rows: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(rows) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShimmerLine(width = 200.dp, height = 16.dp)
                ShimmerLine(width = 120.dp, height = 12.dp)
            }
        }
    }
}

/** Esqueleto genérico de conteúdo (bloco + linhas) — pra telas de detalhe/form carregando. */
@Composable
fun ShimmerContent(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp).shimmer(RoundedCornerShape(12.dp)))
        ShimmerLine(width = 220.dp, height = 22.dp)
        ShimmerLine(width = 160.dp, height = 16.dp)
        ShimmerLine(width = 260.dp, height = 16.dp)
    }
}

/**
 * Imagem de rede padrão do app: shimmer enquanto carrega, placeholder neutro em erro/ref vazio.
 * Use SEMPRE isto pra imagens remotas (thumbs, etc.) no lugar de AsyncImage cru.
 */
@Composable
fun NetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val ph = MaterialTheme.colorScheme.surfaceVariant
    if (url == null) {
        Box(modifier.clip(shape).background(ph))   // ref vazio → placeholder neutro
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.clip(shape),
        loading = { Box(Modifier.matchParentSize().shimmer()) },
        error = { Box(Modifier.matchParentSize().background(ph)) },
    )
}
