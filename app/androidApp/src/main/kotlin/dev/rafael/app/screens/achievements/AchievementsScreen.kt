package dev.rafael.app.screens.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.app.ui.ShimmerList
import dev.rafael.contract.stats.AchievementDto
import org.koin.androidx.compose.koinViewModel

/**
 * Conquistas (ARCH #16).
 *
 * A grade mostra o catálogo INTEIRO, bloqueadas incluídas e em cinza. Uma tela só com o que
 * já foi ganho não diz o que fazer a seguir — e o valor da gamificação está justamente no
 * próximo marco visível.
 *
 * [REGRA] `lime` (= `colorScheme.tertiary`) é EXCLUSIVA das recompensas do perfil individual.
 * Aqui ela está no lugar certo; em ação comum, navegação ou grupo, nunca.
 */
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Voltar de um treino muda o progresso; sem `forcar` o TTL de 2 min seguraria justamente
    // a medalha que o usuário acabou de merecer.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.sincronizar(forcar = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conquistas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp)) {
            when {
                state.carregandoInicial && state.conquistas.isEmpty() ->
                    ShimmerList(modifier = Modifier.padding(vertical = 8.dp))

                else -> {
                    Text(
                        "${state.desbloqueadas.size} de ${state.total}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,   // lime: é recompensa
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(16.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.conquistas, key = { it.id }) { c -> CartaoDeConquista(c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoDeConquista(conquista: AchievementDto) {
    // O estado vem de `unlockedAt`, NUNCA de comparar current >= target: streak quebra e a
    // medalha continua ganha. Quem decide desbloqueio é o servidor ([REGRA] ARCH #16).
    val desbloqueada = conquista.unlocked
    val lime = MaterialTheme.colorScheme.tertiary
    val apagado = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (desbloqueada) {
                lime.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (desbloqueada) lime.copy(alpha = 0.2f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (desbloqueada) Icons.Default.Star else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (desbloqueada) lime else apagado,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                conquista.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (desbloqueada) MaterialTheme.colorScheme.onSurface else apagado,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                conquista.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = apagado,
            )

            // Progresso SÓ na bloqueada. Na desbloqueada a barra seria ruído — e pior, poderia
            // aparecer incompleta (streak que quebrou depois), sugerindo que a medalha está
            // em risco. Ela não está: conquista concedida nunca é retirada.
            if (!desbloqueada) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { conquista.current.toFloat() / conquista.target.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                    color = apagado,
                    // `trackColor` EXPLÍCITO. O default do Material3 é `secondaryContainer`, que
                    // neste tema é roxo vivo — mais chamativo que o próprio progresso, cinza.
                    // O resultado lido de relance era o inverso do real: 11 de 30 parecia 90%.
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${conquista.current} de ${conquista.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = apagado,
                )
            }
        }
    }
}
