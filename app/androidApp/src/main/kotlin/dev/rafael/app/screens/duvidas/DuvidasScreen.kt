package dev.rafael.app.screens.duvidas

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// ⚠️ Import explicito do R: os curingas do Compose acima trazem R de outras libs para o escopo
// (ver o mesmo aviso em ExerciseDetailScreen.kt).
import dev.rafael.app.R

/**
 * Dúvidas frequentes.
 *
 * ## Lista ESTÁTICA, sem servidor nem banco
 *
 * Ao contrário do Aprender (Fase 8), que levou banco + migration repetível porque o acervo é
 * grande e cresce, aqui são 8 perguntas que mudam raramente. Banco aqui seria infraestrutura sem
 * uso -- decisão tomada com o Rafael em 2026-10-03. Pergunta e resposta vivem em [R.string],
 * iguais nas duas línguas pelo mesmo padrão do resto do catálogo bilíngue.
 *
 * ## Accordion, não tela por pergunta
 *
 * A alternativa (cada pergunta abrindo uma tela própria, como o artigo da Wiki) pesaria
 * navegação demais pra uma resposta de duas frases. Aqui a resposta abre embaixo da própria
 * pergunta, sem sair da tela.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuvidasScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_duvidas)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.comum_voltar),
                        )
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DUVIDAS.forEach { ItemDeDuvida(it) }
        }
    }
}

private data class Duvida(@StringRes val pergunta: Int, @StringRes val resposta: Int)

/** As 8 perguntas, na ordem fechada com o Rafael em 2026-10-03. */
private val DUVIDAS = listOf(
    Duvida(R.string.duvidas_checkin_pergunta, R.string.duvidas_checkin_resposta),
    Duvida(R.string.duvidas_contestar_pergunta, R.string.duvidas_contestar_resposta),
    Duvida(R.string.duvidas_localizacao_pergunta, R.string.duvidas_localizacao_resposta),
    Duvida(R.string.duvidas_premium_pergunta, R.string.duvidas_premium_resposta),
    Duvida(R.string.duvidas_xp_pergunta, R.string.duvidas_xp_resposta),
    Duvida(R.string.duvidas_ranking_pergunta, R.string.duvidas_ranking_resposta),
    Duvida(R.string.duvidas_multiplos_programas_pergunta, R.string.duvidas_multiplos_programas_resposta),
    Duvida(R.string.duvidas_relogio_pergunta, R.string.duvidas_relogio_resposta),
)

@Composable
private fun ItemDeDuvida(duvida: Duvida) {
    var aberta by remember { mutableStateOf(false) }
    // Gira em vez de trocar de ícone: a MESMA seta vira a resposta, em vez de duas formas
    // diferentes piscando uma pra outra -- é o que deixa claro que é a mesma pergunta abrindo e
    // fechando, não um estado novo surgindo do nada.
    val rotacao by animateFloatAsState(if (aberta) 180f else 0f, label = "duvida_seta")

    Card(
        onClick = { aberta = !aberta },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (aberta) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(duvida.pergunta),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = rotacao },
                )
            }
            AnimatedVisibility(visible = aberta) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(duvida.resposta),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
