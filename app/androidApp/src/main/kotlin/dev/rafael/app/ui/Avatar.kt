package dev.rafael.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AVATAR = inicial sobre cor derivada do id (ARCH #33, decisão 1-A.6).
 *
 * Foto de perfil fica para a v2: exigiria upload, moderação e storage por usuário. A inicial
 * colorida entrega quase todo o reconhecimento visual sem nenhuma dessas peças — e, como a cor
 * sai do id, a mesma pessoa tem sempre a mesma cor em qualquer aparelho, sem guardar nada.
 *
 * [REGRA] ARCH #16: `lime` (`tertiary`) NÃO entra nesta paleta. A cor é exclusiva de recompensa
 * do perfil individual — usá-la como cor de avatar a transformaria em decoração e apagaria o
 * significado que ela carrega no XP, no nível e nas conquistas.
 */
@Composable
fun AvatarInicial(
    nome: String,
    id: String,
    tamanho: Dp = 44.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(corDoAvatar(id)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            inicialDe(nome),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            // Proporcional ao círculo: o mesmo composable serve o cabeçalho do menu (42dp) e o
            // topo do perfil (58dp) sem uma constante de fonte para cada tamanho.
            fontSize = (tamanho.value * 0.4f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Primeira letra visível, em maiúscula. String vazia não acontece (o nome é NOT NULL), mas custa nada. */
fun inicialDe(nome: String): String =
    nome.trim().firstOrNull()?.uppercase() ?: "?"

/**
 * Cor estável a partir do id. Determinística de propósito: se sorteasse, a mesma pessoa mudaria
 * de cor entre duas aberturas do app, e cor de avatar é exatamente o tipo de pista que o olho
 * usa para reconhecer alguém numa lista de 50 nomes.
 */
fun corDoAvatar(id: String): Color =
    PALETA[(id.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % PALETA.size]

/** Sem lime, sem vermelho (erro) e sem amarelo (aviso): cor de avatar não pode significar estado. */
private val PALETA = listOf(
    Color(0xFF2E6BE6),   // azul
    Color(0xFF8B5CF6),   // roxo
    Color(0xFF0E9F6E),   // verde-mar
    Color(0xFFDB5AA0),   // rosa
    Color(0xFF0EA5B7),   // ciano
    Color(0xFF7C6AF0),   // índigo
)
