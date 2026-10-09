package dev.rafael.app.screens.wiki

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.rafael.app.R
import dev.rafael.app.ui.ErroEmSnackbar
import dev.rafael.app.ui.ShimmerLine
import dev.rafael.app.ui.rotulo
import dev.rafael.app.ui.shimmer
import dev.rafael.features.wiki.domain.model.WikiArticle
import dev.rafael.features.wiki.presentation.leitura.minutosDeLeitura
import dev.rafael.features.wiki.presentation.state.WikiListEvent
import dev.rafael.features.wiki.presentation.viewmodel.WikiViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * "Aprender" — o acervo (Fase 8).
 *
 * Copia o esqueleto do `ExerciseLibraryScreen` de propósito (`Scaffold` + `TopAppBar` + `LazyRow`
 * de chips + `LazyColumn`): é o padrão de catálogo já estabelecido no app, e quem usou a Biblioteca
 * não precisa reaprender nada aqui.
 *
 * ## O que esta tela NÃO tem, e é decisão
 *
 * **Busca.** A Biblioteca, com 923 exercícios, não tem — é só chip. Com ~12 artigos, uma busca
 * falharia na maioria das tentativas: seria uma máquina de decepção numa tela cujo trabalho é
 * acolher quem está começando.
 *
 * **Foto no card.** Seriam de 10 a 14 imagens a produzir ou licenciar, e imagem é o segundo gargalo
 * de conteúdo depois do texto. O selo de categoria carrega a informação que a miniatura carregaria.
 *
 * Os chips vêm de `state.categorias`, que o ViewModel deriva do que EXISTE — categoria sem artigo
 * não vira chip que leva a uma tela vazia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiScreen(
    onAbrirArtigo: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: WikiViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Falha de sync com o acervo na tela é aviso passageiro (ARCH #31): snackbar, nunca card fixo.
    val snackbarHost = remember { SnackbarHostState() }
    ErroEmSnackbar(erro = state.error, host = snackbarHost, onConsumir = viewModel::consumeError)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_wiki)) },
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
        // Envolve a TELA e não só a lista, pela mesma razão da Biblioteca: aqui o vazio significa
        // "conteúdo não baixado", que é exatamente a situação em que puxar é a única coisa útil.
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(WikiListEvent.Refresh) },
            modifier = Modifier.padding(padding),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.categoriaSelecionada == null,
                            onClick = { viewModel.onEvent(WikiListEvent.CategoriaSelecionada(null)) },
                            label = { Text(stringResource(R.string.aprender_tudo)) },
                        )
                    }
                    items(state.categorias) { cat ->
                        FilterChip(
                            selected = state.categoriaSelecionada == cat,
                            onClick = { viewModel.onEvent(WikiListEvent.CategoriaSelecionada(cat)) },
                            label = { Text(stringResource(cat.rotulo())) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    when {
                        state.carregando -> EsqueletoDoAcervo()
                        state.destaque == null && state.artigos.isEmpty() ->
                            Text(
                                stringResource(R.string.aprender_vazio),
                                Modifier.align(Alignment.Center).padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            state.destaque?.let { destaque ->
                                item { CardDeDestaque(destaque, onAbrirArtigo) }
                            }
                            items(state.artigos) { artigo -> CardDeArtigo(artigo, onAbrirArtigo) }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

/** O "COMECE AQUI": resolve o problema de quem não sabe por onde começar melhor que chip ou busca. */
@Composable
private fun CardDeDestaque(artigo: WikiArticle, onAbrir: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onAbrir(artigo.slug) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(20.dp)) {
            // `.uppercase()` aqui e não no `strings.xml`: caixa alta é decisão de DESENHO, e o
            // `CatalogoDeStringsTest` recusa texto que já venha gritando. Mesmo tratamento do selo
            // de categoria no `WikiArticleScreen`.
            Text(
                stringResource(R.string.aprender_comece_aqui).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                artigo.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.aprender_min_leitura, minutosDeLeitura(artigo.body)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardDeArtigo(artigo: WikiArticle, onAbrir: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onAbrir(artigo.slug) }) {
        Column(Modifier.padding(16.dp)) {
            Text(artigo.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.aprender_min_leitura, minutosDeLeitura(artigo.body)) +
                    " · " + stringResource(artigo.category.rotulo()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Reusa o shimmer do app — o esqueleto não é tela nova, é a mesma lista antes de ter texto. */
@Composable
private fun EsqueletoDoAcervo() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(110.dp).shimmer(RoundedCornerShape(12.dp)))
        repeat(4) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ShimmerLine(width = 220.dp, height = 16.dp)
                ShimmerLine(width = 120.dp, height = 12.dp)
            }
        }
    }
}
