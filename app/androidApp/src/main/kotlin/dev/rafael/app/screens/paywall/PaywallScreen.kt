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
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rafael.app.R
import dev.rafael.app.ui.ErroInline
import org.koin.androidx.compose.koinViewModel

/**
 * O que uma célula da tabela comparativa DIZ (fatia G.4, ARCH #37).
 *
 * ## ⭐ O sentinela virou tipo
 *
 * Até 2026-09-11 isto era um `String?`, e o código fazia `value == "Sim"` para escolher entre ✓, ✗
 * e texto. A `String` estava fazendo o trabalho de um tipo, e `"Sim"` era **um sentinela
 * disfarçado de texto de tela**: mandá-lo ao catálogo teria quebrado a comparação EM SILÊNCIO no
 * dia em que alguém traduzisse a linha, e a tabela passaria a mostrar ✗ onde deveria ✓ sem erro
 * nenhum.
 *
 * > **Sentinela escrito em português é código ramificando em idioma, não texto de tela.**
 *
 * Com o tipo fechado, as três possibilidades passam a ser TRÊS COISAS DIFERENTES para o
 * compilador, e o `when` sobre elas é exaustivo: acrescentar um quarto caso quebra o build em vez
 * de cair no `else`. O texto que sobrou em [Texto] é texto de verdade, e por isso é `@StringRes`.
 *
 * É a terceira vez que esta base ramificou numa palavra em português, e a última a ser fechada: o
 * `"Não encontrado"` do `AppError` virou sentinela DERIVADA (`AppError.NotFound().message`), e o
 * `SplitType.label` ficou porque é chave interna do motor, não texto.
 *
 * > **A `String` é o tipo que aceita qualquer coisa, inclusive a coisa errada. Quando o código**
 * > **ramifica no valor dela, o tipo certo já existe e só não foi escrito.**
 */
private sealed interface Celula {
    /** Tem o recurso. Renderiza ✓, e a palavra que a pessoa ouve vem de `paywall_celula_sim`. */
    data object Sim : Celula

    /** Não tem. Renderiza ✗. Era o `null`, que não dizia se era "não tem" ou "esqueci de preencher". */
    data object Nao : Celula

    /** Tem, com uma qualificação: *1 programa*, *Até 2*, *Só o Dia 1*. Isto sim é texto de tela. */
    data class Texto(@StringRes val id: Int) : Celula
}

/**
 * Uma linha do comparativo. Os três campos são do catálogo ou do tipo fechado: **não sobrou
 * nenhuma palavra solta nesta tela.**
 */
private data class Feature(@StringRes val label: Int, val free: Celula, val premium: Celula)
private data class Group(@StringRes val title: Int, val features: List<Feature>)

private val GROUPS = listOf(
    Group(
        R.string.paywall_grupo_ia,
        listOf(
            Feature(
                R.string.paywall_recurso_gerar_ia,
                free = Celula.Texto(R.string.paywall_valor_um_programa),
                premium = Celula.Texto(R.string.paywall_valor_varios),
            ),
            Feature(
                R.string.paywall_recurso_ver_dias,
                free = Celula.Texto(R.string.paywall_valor_so_dia_1),
                premium = Celula.Texto(R.string.paywall_valor_todos),
            ),
            Feature(R.string.paywall_recurso_trocar_exercicio, Celula.Nao, Celula.Sim),
            Feature(R.string.paywall_recurso_add_remover, Celula.Nao, Celula.Sim),
            Feature(R.string.paywall_recurso_editar_series, Celula.Nao, Celula.Sim),
            Feature(R.string.paywall_recurso_reagendar, Celula.Nao, Celula.Sim),
        ),
    ),
    Group(
        R.string.paywall_grupo_manuais,
        listOf(
            Feature(
                R.string.paywall_recurso_criar_manual,
                free = Celula.Texto(R.string.paywall_valor_ate_2),
                premium = Celula.Texto(R.string.paywall_valor_varios),
            ),
            Feature(R.string.paywall_recurso_add_editar_treinos, Celula.Sim, Celula.Sim),
            Feature(R.string.paywall_recurso_escolher_dia, Celula.Sim, Celula.Sim),
            // A MESMA chave da linha do grupo de IA: é a mesma capacidade, e duas chaves para
            // ela divergiriam na tradução.
            Feature(R.string.paywall_recurso_reagendar, Celula.Sim, Celula.Sim),
        ),
    ),
    Group(
        R.string.paywall_grupo_geral,
        listOf(
            Feature(R.string.paywall_recurso_onboarding, Celula.Sim, Celula.Sim),
            Feature(R.string.paywall_recurso_descanso_ia, Celula.Sim, Celula.Sim),
            Feature(R.string.paywall_recurso_biblioteca, Celula.Sim, Celula.Sim),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    onClose: (assinou: Boolean) -> Unit,
    viewModel: PaywallViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // sucesso → fecha e volta pra origem (que recarrega e enxerga o premium)
    LaunchedEffect(state.subscribed) { if (state.subscribed) onClose(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comum_premium)) },
                navigationIcon = {
                    IconButton(onClick = { onClose(false) }, enabled = !state.isSubscribing) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.comum_fechar))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.error?.let { ErroInline(it) }
                    Button(
                        onClick = viewModel::subscribe,
                        enabled = !state.isSubscribing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSubscribing) CircularProgressIndicator(Modifier.size(20.dp))
                        else Text(stringResource(R.string.paywall_assinar))
                    }
                    TextButton(
                        onClick = { onClose(false) },
                        enabled = !state.isSubscribing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.paywall_agora_nao)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.paywall_chamada),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.paywall_subchamada),
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
        Text(
            stringResource(R.string.paywall_coluna_recurso),
            Modifier.weight(2f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        // ⚠️ "Free", em inglês, num app em português. Divergência herdada; ver `comum_gratis`.
        Text(
            stringResource(R.string.paywall_coluna_free),
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            stringResource(R.string.comum_premium),
            Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GroupTitle(@StringRes title: Int) {
    Text(
        stringResource(title),
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FeatureRow(f: Feature) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(f.label), Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
        Cell(Modifier.weight(1f), valor = f.free, premium = false)
        Cell(Modifier.weight(1f), valor = f.premium, premium = true)
    }
}

/**
 * Uma célula da tabela.
 *
 * O `when` é sobre uma [Celula] e é **exaustivo**: não há `else`. Um quarto estado de célula
 * quebra o build aqui, em vez de cair silenciosamente no ramo de texto — que era exatamente o que
 * acontecia quando o parâmetro era `String?`.
 */
@Composable
private fun Cell(modifier: Modifier, valor: Celula, premium: Boolean) {
    val accent = if (premium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        when (valor) {
            Celula.Nao ->
                Icon(
                    Icons.Default.Close,
                    // A descrição vai ao catálogo porque é lida em voz alta. Antes havia um
                    // sentinela ao lado dela que não podia ir; hoje não há mais sentinela nenhum.
                    contentDescription = stringResource(R.string.paywall_celula_nao),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )

            Celula.Sim ->
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.paywall_celula_sim),
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )

            is Celula.Texto ->
                Text(
                    stringResource(valor.id),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (premium) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (premium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
        }
    }
}
