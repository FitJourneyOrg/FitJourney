package dev.rafael.app.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.rafael.core.result.AppError
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.app.ui.ShimmerLine
import dev.rafael.app.ui.shimmer
import dev.rafael.contract.stats.UserStatsDto
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenLibrary: () -> Unit,
    onOpenWorkouts: () -> Unit,
    onGenerateWithAI: () -> Unit,
    onStartWorkout: (String) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenProgress: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val loggedOut by viewModel.loggedOut.collectAsState()
    val state by viewModel.state.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(loggedOut) { if (loggedOut) onLoggedOut() }
    // recarrega ao voltar (o treino pode ter sido executado/alterado)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Sair da conta?") },
            text = { Text("Você precisará entrar de novo para acessar seus programas.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    viewModel.logout()
                }) { Text("Sair") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancelar") } },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Saudacao(onLogoutClick = { showLogoutConfirm = true })
        Spacer(Modifier.height(16.dp))

        // Faixa de recompensa (ARCH #16) — lime é EXCLUSIVO do perfil individual.
        // Vem do cache local: aparece SEMPRE, inclusive offline. Antes do 1º sync da vida
        // mostra zerada, o que é verdade (o usuário ainda não tem XP).
        FaixaDeProgresso(state.stats, state.sessoesPendentes)
        Spacer(Modifier.height(12.dp))

        when {
            state.isLoading -> CardEsqueleto()
            state.error != null -> CardErro(state.error!!) { viewModel.load() }   // erro de ação
            // Vazio POR FALTA DE SYNC (nunca sincronizou neste aparelho) ≠ vazio de verdade.
            // Sem esta distinção, quem já tem programas era convidado a criar tudo de novo.
            // "não baixei ainda" ≠ "você não tem". Sem o !jaSincronizou, quem sincronizou
            // ontem e abre offline hoje com zero programas via "Sem conexão" — mentira.
            state.semPrograma && state.erroSync != null && !state.jaSincronizou ->
                CardNaoSincronizado(state.erroSync!!, onTentarDeNovo = { viewModel.load() })
            state.semPrograma -> CardSemPrograma(
                onGerarComIa = onGenerateWithAI,
                onCriarManual = onOpenWorkouts,
            )
            // já treinou hoje (dado LOCAL): celebra e não oferece o mesmo treino de novo.
            // Funciona offline — o XP é o do servidor quando disponível, mas não é requisito.
            state.treinouHoje -> CardTreinoConcluido(
                xpHoje = state.stats?.xpToday ?: 0,
                streak = state.stats?.streakDays ?: 0,
                nomeDoTreino = state.today?.name,
                onVerProgresso = onOpenProgress,
            )
            state.today != null -> CardTreinoDeHoje(
                treino = state.today!!,
                onIniciar = { onStartWorkout(state.today!!.workoutId) },
                onVerPrograma = onOpenWorkouts,
            )
            else -> CardDiaDeDescanso(onTreinoAvulso = onOpenWorkouts, onProgresso = onOpenProgress)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "ATALHOS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Atalho("Criar treino", Icons.Filled.Add, Modifier.weight(1f), onOpenWorkouts)
            Atalho("Exercícios", Icons.Outlined.BarChart, Modifier.weight(1f), onOpenLibrary)
            Atalho("Grupos", Icons.Outlined.Group, Modifier.weight(1f), onOpenGroups)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Saudacao(onLogoutClick: () -> Unit) {
    val hoje = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val dias = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
    val meses = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    val saudacao = when (hoje.hour) {
        in 0..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(saudacao, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "${dias[hoje.date.dayOfWeek.ordinal]} · ${hoje.date.day} ${meses[hoje.date.month.ordinal]}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onLogoutClick) { Text("Sair") }
    }
}

/**
 * Streak + nível + barra de XP. Usa `tertiary` (lime) porque é RECOMPENSA do perfil
 * individual — [REGRA] ARCH #16: lime nunca em ação comum, navegação ou grupo.
 */
@Composable
private fun FaixaDeProgresso(stats: UserStatsDto?, pendentes: Int) {
    val lime = MaterialTheme.colorScheme.tertiary
    // sem sync ainda (1ª abertura / offline no primeiro uso): mostra zerado, não some
    val s = stats ?: UserStatsDto(
        xp = 0, level = 1, xpInLevel = 0, xpForNextLevel = 1000,
        streakDays = 0, totalSessions = 0, sessionsThisWeek = 0,
    )
    // XP entra "contando" e a barra cresce: quando o treino offline sincroniza, o salto
    // (0 -> 250) vira uma animação de recompensa em vez de um número que troca seco.
    val xpAnimado by animateIntAsState(
        targetValue = s.xpInLevel,
        animationSpec = tween(durationMillis = 900),
        label = "xp",
    )
    val barraAnimada by animateFloatAsState(
        targetValue = if (s.xpForNextLevel > 0) s.xpInLevel / s.xpForNextLevel.toFloat() else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "xp-barra",
    )
    val streakAnimado by animateIntAsState(
        targetValue = s.streakDays,
        animationSpec = tween(durationMillis = 600),
        label = "streak",
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocalFireDepartment, contentDescription = null,
                    tint = if (s.streakDays > 0) lime else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "$streakAnimado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (s.streakDays > 0) lime else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (s.streakDays == 1) "dia seguido" else "dias seguidos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Nível ${s.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "$xpAnimado / ${s.xpForNextLevel} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { barraAnimada },
                color = lime,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            // XP de treino feito offline só entra depois que a sessão sobe (autoridade do
            // servidor). Avisar evita o usuário achar que o treino não contou.
            if (pendentes > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (pendentes == 1) "1 treino aguardando sincronizar — o XP entra quando houver conexão"
                    else "$pendentes treinos aguardando sincronizar — o XP entra quando houver conexão",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CardTreinoDeHoje(
    treino: TodayWorkout,
    onIniciar: () -> Unit,
    onVerPrograma: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "TREINO DE HOJE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(treino.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val detalhe = buildString {
                append("${treino.exerciseCount} exercícios")
                if (treino.minutes > 0) append(" · ~${treino.minutes} min")
                append(" · semana ${treino.week}/${treino.totalWeeks}")
            }
            Text(detalhe, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(treino.programName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            if (treino.locked) {
                // ARCH #23: dia trancado p/ não-premium — a Home não fura o blur
                OutlinedButton(onClick = onVerPrograma, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Desbloquear treino")
                }
            } else {
                Button(onClick = onIniciar, modifier = Modifier.fillMaxWidth()) { Text("Iniciar treino") }
            }
        }
    }
}

/**
 * Treino do dia concluído. Substitui o card de "iniciar" até o dia seguinte — evita refazer
 * o mesmo treino em loop e fecha o ciclo do dia com a recompensa à vista (ARCH #16).
 */
@Composable
private fun CardTreinoConcluido(
    xpHoje: Int,
    streak: Int,
    nomeDoTreino: String?,
    onVerProgresso: () -> Unit,
) {
    val lime = MaterialTheme.colorScheme.tertiary
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.CheckCircle, contentDescription = null,
                tint = lime, modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Treino concluído", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            nomeDoTreino?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (xpHoje > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "+$xpHoje XP",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = lime,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (streak > 1) "Sequência de $streak dias mantida. Descanse — o próximo treino aparece amanhã."
                else "Sequência iniciada. Descanse — o próximo treino aparece amanhã.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onVerProgresso, modifier = Modifier.fillMaxWidth()) {
                Text("Ver progresso")
            }
        }
    }
}

@Composable
private fun CardDiaDeDescanso(onTreinoAvulso: () -> Unit, onProgresso: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.Bedtime, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Dia de descanso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Recuperar faz parte do progresso — é no descanso que o músculo se constrói.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTreinoAvulso, modifier = Modifier.weight(1f)) { Text("Treino avulso") }
                OutlinedButton(onClick = onProgresso, modifier = Modifier.weight(1f)) { Text("Progresso") }
            }
        }
    }
}

/**
 * Sem dado local E o sync falhou. Não é "usuário novo": pode ser alguém com programas que
 * abriu o app num aparelho novo, sem rede. Convidar a criar programa aqui seria enganoso.
 *
 * O conteúdo vem do [ErroDeTela] — o texto muda conforme a causa real (wifi do usuário vs
 * servidor fora do ar), coisa que este card não tinha como saber quando era hardcoded.
 */
@Composable
private fun CardNaoSincronizado(erro: AppError, onTentarDeNovo: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        ErroDeTela(
            erro = erro,
            modifier = Modifier.fillMaxWidth(),
            onAcao = { onTentarDeNovo() },
        )
    }
}

@Composable
private fun CardSemPrograma(onGerarComIa: () -> Unit, onCriarManual: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Comece seu primeiro programa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Monte um programa e o treino do dia aparece aqui, pronto pra começar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // DUAS saídas: quem recusou a oferta no onboarding não pode cair num card que
            // só oferece a mesma coisa que ele acabou de recusar.
            Button(onClick = onGerarComIa, modifier = Modifier.fillMaxWidth()) {
                Text("Montar com IA")
            }
            TextButton(onClick = onCriarManual, modifier = Modifier.fillMaxWidth()) {
                Text("Criar treino manualmente")
            }
        }
    }
}

@Composable
private fun CardErro(erro: AppError, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        ErroDeTela(erro = erro, modifier = Modifier.fillMaxWidth(), onAcao = { onRetry() })
    }
}

@Composable
private fun CardEsqueleto() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            ShimmerLine(width = 110.dp, height = 12.dp)
            Spacer(Modifier.height(10.dp))
            ShimmerLine(width = 200.dp, height = 26.dp)
            Spacer(Modifier.height(8.dp))
            ShimmerLine(width = 160.dp, height = 14.dp)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(44.dp).shimmer(RoundedCornerShape(20.dp)))
        }
    }
}

@Composable
private fun Atalho(rotulo: String, icone: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(rotulo, style = MaterialTheme.typography.labelMedium)
        }
    }
}
