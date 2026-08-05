package dev.rafael.app.screens.paywall

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

/** Uma linha do comparativo. free/premium: "Sim" → ✓, null → ✗, texto → mostra o texto. */
private data class Feature(val label: String, val free: String?, val premium: String?)
private data class Group(val title: String, val features: List<Feature>)

private val GROUPS = listOf(
    Group(
        "Treinos com IA",
        listOf(
            Feature("Gerar programa com IA", free = "1 programa", premium = "Vários"),
            Feature("Ver todos os dias do programa", free = "Só o Dia 1", premium = "Todos"),
            Feature("Trocar exercício por alternativa", free = null, premium = "Sim"),
            Feature("Adicionar / remover exercício", free = null, premium = "Sim"),
            Feature("Editar séries e repetições", free = null, premium = "Sim"),
            Feature("Reagendar os dias da semana", free = null, premium = "Sim"),
        ),
    ),
    Group(
        "Treinos manuais",
        listOf(
            Feature("Criar programa manual", free = "Até 2", premium = "Vários"),
            Feature("Adicionar e editar treinos", free = "Sim", premium = "Sim"),
            Feature("Escolher o dia ao criar", free = "Sim", premium = "Sim"),
            Feature("Reagendar os dias da semana", free = "Sim", premium = "Sim"),
        ),
    ),
    Group(
        "Geral",
        listOf(
            Feature("Onboarding guiado (split, descanso)", free = "Sim", premium = "Sim"),
            Feature("Descanso distribuído pela IA", free = "Sim", premium = "Sim"),
            Feature("Biblioteca de exercícios", free = "Sim", premium = "Sim"),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    viewModel: PaywallViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // sucesso → fecha e volta pra origem (que recarrega e enxerga o premium)
    LaunchedEffect(state.subscribed) { if (state.subscribed) onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premium") },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !state.isSubscribing) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = viewModel::subscribe,
                        enabled = !state.isSubscribing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSubscribing) CircularProgressIndicator(Modifier.size(20.dp))
                        else Text("Assinar premium")
                    }
                    TextButton(
                        onClick = onClose,
                        enabled = !state.isSubscribing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Agora não") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Destrave tudo com o Premium", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Compare o que você tem no grátis e o que o premium libera.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            ComparisonHeader()
            HorizontalDivider()
            GROUPS.forEach { group ->
                GroupTitle(group.title)
                group.features.forEach { f ->
                    FeatureRow(f)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Recurso", Modifier.weight(2f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Text("Free", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
        Text(
            "Premium",
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GroupTitle(title: String) {
    Text(
        title,
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FeatureRow(f: Feature) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(f.label, Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
        Cell(Modifier.weight(1f), value = f.free, premium = false)
        Cell(Modifier.weight(1f), value = f.premium, premium = true)
    }
}

@Composable
private fun Cell(modifier: Modifier, value: String?, premium: Boolean) {
    val accent = if (premium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            value == null ->
                Icon(Icons.Default.Close, contentDescription = "Não", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            value == "Sim" ->
                Icon(Icons.Default.Check, contentDescription = "Sim", tint = accent, modifier = Modifier.size(18.dp))
            else ->
                Text(
                    value,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (premium) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (premium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
        }
    }
}
