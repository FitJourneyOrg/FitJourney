package dev.rafael.app.screens.wiki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.rafael.app.R
import dev.rafael.app.ui.ShimmerLine
import dev.rafael.app.ui.rotulo
import dev.rafael.features.wiki.presentation.leitura.minutosDeLeitura
import dev.rafael.features.wiki.presentation.leitura.partesDaData
import dev.rafael.features.wiki.presentation.markdown.Bloco
import dev.rafael.features.wiki.presentation.markdown.Trecho
import dev.rafael.features.wiki.presentation.markdown.blocosDoArtigo
import dev.rafael.features.wiki.presentation.viewmodel.WikiArticleViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A leitura de um artigo (Fase 8).
 *
 * Recebe o SLUG e relê do banco local: a tela reabre igual depois de morte de processo, e um deep
 * link futuro de notificação (#36) chega com slug, não com objeto.
 *
 * O corpo é markdown MÍNIMO, e quem o interpreta é a função pura `blocosDoArtigo` — esta tela só
 * desenha o que ela devolve. Mesmo arranjo do `paragrafosDaDescricao` da fatia H: a regra fica
 * testável em milissegundos e a tela não sabe o que é asterisco.
 *
 * Sem botão de compartilhar na v1: compartilhar artigo pede link, e link esbarra no débito aberto
 * de deep link ponta a ponta (domínio + `assetlinks.json`). Botão que gera link que não abre é pior
 * que botão ausente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiArticleScreen(
    slug: String,
    onBack: () -> Unit,
    viewModel: WikiArticleViewModel = koinViewModel { parametersOf(slug) },
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                .padding(horizontal = 20.dp),
        ) {
            val artigo = state.artigo
            when {
                state.carregando -> {
                    // Antes da primeira emissão do banco, `artigo == null` é "ainda não sei" --
                    // mostrar "não encontrado" aqui piscaria em toda abertura.
                    Spacer(Modifier.height(8.dp))
                    ShimmerLine(width = 120.dp, height = 12.dp)
                    Spacer(Modifier.height(12.dp))
                    ShimmerLine(width = 260.dp, height = 28.dp)
                    Spacer(Modifier.height(20.dp))
                    repeat(6) {
                        ShimmerLine(width = 300.dp, height = 14.dp)
                        Spacer(Modifier.height(10.dp))
                    }
                }

                artigo == null ->
                    Text(
                        stringResource(R.string.aprender_artigo_sumiu),
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                else -> {
                    Text(
                        stringResource(artigo.category.rotulo()).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        artigo.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        linhaDeMetadados(artigo.body, artigo.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))

                    blocosDoArtigo(artigo.body).forEach { bloco ->
                        // `when` exaustivo: bloco novo no parser quebra o BUILD em vez de sumir da tela.
                        when (bloco) {
                            is Bloco.Subtitulo -> {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    bloco.texto,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            is Bloco.Paragrafo -> {
                                Text(comNegrito(bloco.trechos), style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.height(14.dp))
                            }
                            is Bloco.Item -> {
                                Row(Modifier.padding(bottom = 8.dp)) {
                                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                                    Text(comNegrito(bloco.trechos), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * "4 min de leitura · atualizado em 15/06/2026".
 *
 * A data vem em três números e a FRASE os ordena (`aprender_atualizado` inverte dia e mês em
 * inglês) — mesmo mecanismo do `checkin_data_as`. Data ilegível some da linha sem derrubar nada.
 */
@Composable
private fun linhaDeMetadados(corpo: String, atualizadoEm: String): String {
    val leitura = stringResource(R.string.aprender_min_leitura, minutosDeLeitura(corpo))
    val partes = partesDaData(atualizadoEm) ?: return leitura
    val data = stringResource(R.string.aprender_atualizado, partes.dia, partes.mes, partes.ano)
    return "$leitura · $data"
}

/** Os trechos já vêm decididos pelo parser; aqui é só pintura. */
private fun comNegrito(trechos: List<Trecho>): AnnotatedString = buildAnnotatedString {
    trechos.forEach { t ->
        if (t.negrito) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t.texto) }
        else append(t.texto)
    }
}
