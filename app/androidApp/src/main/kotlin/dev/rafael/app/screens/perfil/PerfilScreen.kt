package dev.rafael.app.screens.perfil

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.AvatarInicial
import dev.rafael.contract.stats.AchievementDto
import dev.rafael.contract.stats.UserStatsDto
import org.koin.androidx.compose.koinViewModel

/**
 * Perfil (ARCH #34). Na fatia A.0 só existe o PRÓPRIO perfil — `userId` continua na rota para
 * que a A.1 abra o de outra pessoa sem reescrever a tela.
 *
 * O lápis de editar e qualquer seção privada aparecem apenas quando `souEu`. Note que isso é
 * uma decisão de APRESENTAÇÃO: a garantia de verdade é o `PublicProfileDto` não carregar dado
 * privado (A.1). Esconder na UI nunca foi, e não é, a fronteira.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilScreen(
    onBack: () -> Unit,
    onEditar: () -> Unit,
    onVerConquistas: () -> Unit,
    souEu: Boolean = true,
    viewModel: PerfilViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (souEu) {
                        IconButton(onClick = onEditar) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar perfil")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarInicial(nome = state.nome, id = state.id, tamanho = 58.dp)
                Text(
                    state.nome.ifBlank { "Você" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            CartaoDeProgresso(state.stats)

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "CONQUISTAS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.conquistas.isNotEmpty()) {
                    Text(
                        "${state.desbloqueadas} de ${state.conquistas.size}",
                        style = MaterialTheme.typography.labelMedium,
                        // lime: recompensa do perfil individual ([REGRA] ARCH #16).
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FileiraDeConquistas(state.conquistas, onVerConquistas)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CartaoDeProgresso(stats: UserStatsDto?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
    ) {
        val lime = MaterialTheme.colorScheme.tertiary
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Nível ${stats?.level ?: 1}",
                style = MaterialTheme.typography.titleSmall,
                color = lime,
            )
            if (stats != null) {
                Text(
                    "${stats.xpInLevel} / ${stats.xpForNextLevel} XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        LinearProgressIndicator(
            progress = {
                // `coerceAtLeast(1)` no denominador: nível sem custo definido não pode virar
                // divisão por zero e derrubar a tela inteira do perfil.
                (stats?.xpInLevel ?: 0).toFloat() / (stats?.xpForNextLevel ?: 1).coerceAtLeast(1)
            },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = lime,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        )
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Numero(stats?.totalSessions ?: 0, "treinos")
            Numero(stats?.sessionsThisWeek ?: 0, "nesta semana")
        }
    }
}

@Composable
private fun Numero(valor: Int, rotulo: String) {
    Column {
        Text("$valor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(
            rotulo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Cinco medalhas, desbloqueadas primeiro (o servidor já manda nessa ordem, ARCH #32). Toque
 * leva à grade completa — o perfil é vitrine, não catálogo.
 */
@Composable
private fun FileiraDeConquistas(conquistas: List<AchievementDto>, onVerTodas: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val lime = MaterialTheme.colorScheme.tertiary
        val apagado = MaterialTheme.colorScheme.onSurfaceVariant
        val vitrine = conquistas.take(5)
        // Antes do 1º sync a lista é vazia. Cinco caixas cinzas seguram o layout e não mentem:
        // o usuário não tem conquista nenhuma para mostrar mesmo.
        val casas = if (vitrine.isEmpty()) List(5) { null } else vitrine
        casas.forEach { conquista ->
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (conquista?.unlocked == true) lime.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (conquista?.unlocked == true) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock,
                    contentDescription = conquista?.title,
                    tint = if (conquista?.unlocked == true) lime else apagado,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onVerTodas) {
                Text("···", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
