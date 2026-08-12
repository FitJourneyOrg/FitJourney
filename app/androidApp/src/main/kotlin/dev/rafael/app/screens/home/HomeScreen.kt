package dev.rafael.app.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Group
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
import dev.rafael.app.ui.ShimmerLine
import dev.rafael.app.ui.shimmer
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenLibrary: () -> Unit,
    onOpenWorkouts: () -> Unit,
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
        Spacer(Modifier.height(20.dp))

        when {
            state.isLoading -> CardEsqueleto()
            state.error != null -> CardErro(state.error!!) { viewModel.load() }
            state.semPrograma -> CardSemPrograma(onCriar = onOpenWorkouts)
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

@Composable
private fun CardSemPrograma(onCriar: () -> Unit) {
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
            Button(onClick = onCriar, modifier = Modifier.fillMaxWidth()) { Text("Criar programa") }
        }
    }
}

@Composable
private fun CardErro(mensagem: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Não foi possível carregar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                mensagem, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Tentar de novo") }
        }
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
