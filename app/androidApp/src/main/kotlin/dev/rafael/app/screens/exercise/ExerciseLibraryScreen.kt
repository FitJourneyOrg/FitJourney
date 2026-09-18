package dev.rafael.app.screens.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.rotulo
import dev.rafael.app.ui.NetworkImage
import dev.rafael.app.ui.ShimmerLine
import dev.rafael.app.ui.shimmer
import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.network.MediaUrls
import dev.rafael.features.exercise.presentation.state.ExerciseListEvent
import dev.rafael.features.exercise.presentation.state.ExerciseListState
import dev.rafael.features.exercise.presentation.viewmodel.ExerciseListViewModel
import org.koin.androidx.compose.koinViewModel
import dev.rafael.app.R
import androidx.compose.ui.res.stringResource

/**
 * A biblioteca de exercícios, alcançada pela gaveta e pela Home.
 *
 * ## Por que ela ganhou `Scaffold` e `TopAppBar` (2026-09-15)
 *
 * Esta era a única tela navegável do app **sem `Scaffold`**: um `Column` com um `Text` de título.
 * Funcionava enquanto o app não desenhava por baixo das barras do sistema; com o edge-to-edge
 * padronizado, o título passou a dividir a faixa da status bar com o relógio e a câmera.
 *
 * O `Text` não era um título, era um texto que parecia um: não pintava a barra, não reservava a
 * própria altura e não tinha o botão voltar que toda tela empilhada tem.
 *
 * > **Um cabeçalho desenhado à mão acompanha o layout até o dia em que o sistema muda de opinião
 * > sobre onde a tela começa.**
 *
 * O `padding` horizontal saiu do container e foi para o conteúdo: a `TopAppBar` tem o recuo dela,
 * e mantê-los juntos deslocaria o título 16dp a mais que o das outras telas.
 *
 * ## ⭐ Puxar para atualizar (2026-09-18)
 *
 * O `ExerciseListEvent.Refresh` existia desde sempre, com o comentário *"o usuário pediu: fura o
 * TTL"* — e **nada no app o emitia**. O caminho estava inteiro do ViewModel ao repositório, e
 * faltava o primeiro passo.
 *
 * > **Evento que ninguém emite não é recurso: é a metade de um recurso que passa por pronta na
 * > leitura do ViewModel.**
 *
 * A consequência não era cosmética. O catálogo tem TTL de 24h (é semiestático, muda em deploy), e
 * sem este gesto **um deploy que corrige o catálogo leva até um dia para chegar**, sem nada que o
 * usuário possa fazer. Foi o que aconteceu com as mídias quebradas da fatia H: teria sido um
 * gesto, e virou investigação.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onOpenExercise: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ExerciseListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comum_exercicios)) },
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
        /*
         * O `PullToRefreshBox` envolve a TELA, e não só a lista.
         *
         * A `GruposScreen` faz o contrário — lá ele fica dentro do ramo `else`, depois de
         * `carregando` e `vazio`. As duas escolhas estão certas, e a diferença é o que cada tela
         * tem a oferecer quando está vazia:
         *
         * - em Grupos, vazio é um convite ("crie seu primeiro desafio"), com botões. Puxar ali não
         *   faria sentido: não há o que atualizar, há o que criar.
         * - aqui, vazio significa **catálogo não baixado** — exatamente a situação em que puxar é
         *   a única coisa útil. Deixá-lo fora do ramo vazio tiraria o gesto de quem mais precisa.
         *
         * > **Puxar para atualizar pertence à tela quando o vazio é falta de dado, e à lista
         * > quando o vazio é falta de conteúdo.**
         *
         * Os chips de categoria ficam dentro dele de propósito: com a lista vazia, eles são a
         * única área com altura, e sem isso não haveria onde iniciar o gesto.
         */
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(ExerciseListEvent.Refresh) },
            modifier = Modifier.padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                ExerciseListContent(
                    state = state,
                    onCategorySelected = { viewModel.onEvent(ExerciseListEvent.CategorySelected(it)) },
                    onOpenDetail = onOpenExercise,
                )
            }
        }
    }
}

/** Conteúdo reusável. selectedIds != null ⇒ modo seleção (picker). */
@Composable
fun ExerciseListContent(
    state: ExerciseListState,
    onCategorySelected: (ExerciseCategory?) -> Unit,
    selectedIds: Set<String>? = null,
    onToggle: ((String) -> Unit)? = null,
    onOpenDetail: ((String) -> Unit)? = null,
) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text(stringResource(R.string.exercicios_todas)) },
                )
            }
            items(ExerciseCategory.entries) { cat ->
                FilterChip(
                    selected = state.selectedCategory == cat,
                    onClick = { onCategorySelected(cat) },
                    label = { Text(stringResource(cat.rotulo())) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        state.error?.let {
            ErroInline(it)
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            when {
                state.isRefreshing && state.exercises.isEmpty() ->
                    Column(Modifier.fillMaxSize()) {
                        repeat(8) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(Modifier.size(56.dp).shimmer(RoundedCornerShape(8.dp)))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ShimmerLine(width = 180.dp, height = 16.dp)
                                    ShimmerLine(width = 90.dp, height = 12.dp)
                                }
                            }
                        }
                    }
                state.exercises.isEmpty() ->
                    Text(stringResource(R.string.exercicios_vazio), Modifier.align(Alignment.Center))
                else ->
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.exercises) { ex ->
                            val selected = selectedIds?.contains(ex.id) == true
                            ListItem(
                                leadingContent = {
                                    NetworkImage(
                                        url = MediaUrls.url(ex.thumbRef),
                                        contentDescription = ex.name,
                                        modifier = Modifier.size(56.dp),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                },
                                headlineContent = { Text(ex.name) },
                                supportingContent = { Text(stringResource(ex.category.rotulo())) },
                                trailingContent = if (onToggle != null) {
                                    { Checkbox(checked = selected, onCheckedChange = { onToggle(ex.id) }) }
                                } else null,
                                modifier = when {
                                    onToggle != null -> Modifier.clickable { onToggle(ex.id) }
                                    onOpenDetail != null -> Modifier.clickable { onOpenDetail(ex.id) }
                                    else -> Modifier
                                },
                            )
                        }
                    }
            }
        }
    }
}