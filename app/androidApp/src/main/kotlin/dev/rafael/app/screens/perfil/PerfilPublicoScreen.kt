package dev.rafael.app.screens.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.AvatarInicial
import dev.rafael.app.ui.ErroAcao
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.contract.user.PublicAchievementDto
import org.koin.androidx.compose.koinViewModel

/**
 * Perfil de OUTRA pessoa (C.1, #34 + emenda 9.3-A).
 *
 * ## Por que uma tela separada da [PerfilScreen]
 *
 * A tela do dono mostra "12 treinos · 3 nesta semana", que a 9.3-A manteve **privado**. Uma tela
 * só, com `if (souEu)` em volta desses números, faria o isolamento depender de alguém lembrar do
 * `if` — e o KDoc da própria [PerfilScreen] já dizia que esconder na UI nunca foi a fronteira.
 *
 * Duas telas, dois tipos: uma consome `UserStatsDto` (privado), a outra `PublicProfileDto`
 * (público). **O compilador passa a garantir o que antes dependia de disciplina.** O custo é
 * repetir layout; o layout é a parte barata de consertar quando erra.
 *
 * Os componentes visuais (`AvatarInicial`, a fileira de medalhas) são compartilhados — o que não
 * se compartilha é a FONTE DE DADOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilPublicoScreen(
    userId: String,
    onBack: () -> Unit,
    onVerMeuPerfil: () -> Unit,
    viewModel: PerfilPublicoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // ON_START e não LaunchedEffect: voltando da pilha o efeito já executado não roda de novo, e
    // o `carregar` é idempotente por id — quem decide se refaz a rede é o ViewModel, não a tela.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.carregar(userId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        val perfil = state.perfil
        when {
            perfil == null && state.carregando ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            perfil == null ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    ErroDeTela(
                        // Não há perfil na tela, então o erro OCUPA a tela (nível 1 do #31).
                        erro = state.erro ?: return@Box,
                        onAcao = { acao -> if (acao == ErroAcao.TENTAR_DE_NOVO) viewModel.recarregar() },
                    )
                }

            else ->
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
                        AvatarInicial(nome = perfil.displayName, id = perfil.userId, tamanho = 58.dp)
                        Text(
                            perfil.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    /**
                     * Tocar no PRÓPRIO nome no ranking cai aqui, e não é acidente que a gente
                     * deva desviar em silêncio: o que a pessoa está vendo é o próprio perfil
                     * **como os outros veem** — que é a única forma honesta de conferir o que a
                     * 9.3-A publicou. O aviso diz isso, e o toque leva ao perfil completo.
                     *
                     * `me` vem resolvido do servidor — a tela não compara ids.
                     */
                    if (perfil.me) {
                        Text(
                            "Este é o seu perfil, como os outros veem. Toque para abrir o seu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onVerMeuPerfil)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    CartaoDeNivel(nivel = perfil.level, xp = perfil.xp)

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "CONQUISTAS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Conquistas(perfil.achievements)

                    Spacer(Modifier.height(24.dp))
                }
        }
    }
}

/**
 * Nível e XP, sem barra de progresso.
 *
 * A barra do dono precisa de `xpInLevel`/`xpForNextLevel` — quanto falta para o próximo nível.
 * "Faltam 300 XP" é derivável em quantos treinos a pessoa ainda vai fazer, e isso é progresso.
 * Aqui o número é o marco alcançado, não o caminho.
 */
@Composable
private fun CartaoDeNivel(nivel: Int, xp: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // lime: recompensa do perfil individual ([REGRA] ARCH #16).
        Text("Nível $nivel", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
        Text(
            "$xp XP",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Só as conquistadas — a lista já vem assim do servidor.
 *
 * **Sem as caixas cinzas de bloqueado**, que a tela do dono tem. Lá elas informam o próximo
 * passo; aqui diriam quantas medalhas a pessoa NÃO tem, que é uma forma de progresso e não
 * interessa a ninguém além dela.
 */
@Composable
private fun Conquistas(medalhas: List<PublicAchievementDto>) {
    if (medalhas.isEmpty()) {
        Text(
            "Nenhuma conquista ainda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val lime = MaterialTheme.colorScheme.tertiary
        medalhas.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(lime.copy(alpha = 0.10f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = lime,
                    modifier = Modifier.size(22.dp),
                )
                Column {
                    Text(m.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        m.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
