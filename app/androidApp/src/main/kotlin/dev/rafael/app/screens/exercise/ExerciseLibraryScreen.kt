package dev.rafael.app.screens.exercise

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.rafael.app.ui.ErroEmSnackbar
import dev.rafael.app.ui.rotulo
import dev.rafael.app.ui.NetworkImage
import dev.rafael.app.ui.ShimmerLine
import dev.rafael.app.ui.shimmer
import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.MuscleGroup
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

    /*
     * ⭐ A busca MORA NA BARRA, e o estado de "aberta" mora AQUI — não no ViewModel.
     *
     * Abrir ou fechar não muda o que é consultado, só o que é desenhado: é estado de tela, e
     * estado de tela não sobe pro ViewModel. `rememberSaveable` porque girar o aparelho com a
     * busca aberta e ver a barra voltar ao título seria perder o que a pessoa estava fazendo.
     *
     * > **O ViewModel guarda o que foi perguntado; a tela guarda se o campo está à mostra.**
     */
    var buscaAberta by rememberSaveable { mutableStateOf(false) }
    val focoDoCampo = remember { FocusRequester() }

    /**
     * Fechar SEMPRE limpa. Fechar mantendo o termo deixaria a lista filtrada sem nada na tela
     * explicando por quê — e filtro invisível é o pior tipo de filtro: parece catálogo quebrado.
     */
    fun fecharBusca() {
        buscaAberta = false
        viewModel.onEvent(ExerciseListEvent.BuscaAlterada(""))
    }

    // O botão do sistema fecha a busca antes de sair da tela, que é o que a pessoa espera de um
    // campo aberto por cima de outra coisa. Só intercepta enquanto está aberta.
    BackHandler(enabled = buscaAberta) { fecharBusca() }

    // Falha de sync com a Biblioteca na tela é aviso passageiro (ARCH #31): snackbar, nunca card fixo.
    val snackbarHost = remember { SnackbarHostState() }
    ErroEmSnackbar(erro = state.error, host = snackbarHost, onConsumir = { viewModel.consumeError() })

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = buscaAberta,
                        // Entra deslizando de leve e aparecendo; sai só apagando, mais rápido.
                        // Movimento de saída chamando atenção é movimento competindo com o que
                        // está entrando. `clip = false` evita o corte durante a troca de largura.
                        // `ContentTransform` montado à mão em vez do infixo `togetherWith ...
                        // using ...`: o `using` não é importável nesta versão do Compose, e o
                        // construtor não depende de qual das duas formas a versão expõe.
                        transitionSpec = {
                            ContentTransform(
                                targetContentEnter = fadeIn(tween(220)) +
                                    slideInHorizontally(tween(220)) { it / 6 },
                                initialContentExit = fadeOut(tween(120)),
                                sizeTransform = SizeTransform(clip = false),
                            )
                        },
                        label = "titulo-ou-busca",
                    ) { aberta ->
                        if (aberta) {
                            /*
                             * O foco espera UM FRAME. `requestFocus()` disparado no mesmo quadro
                             * em que o campo entra na composição pode cair num nó que ainda não
                             * está anexado — e aí o teclado não sobe, de forma intermitente.
                             */
                            LaunchedEffect(Unit) {
                                withFrameNanos { }
                                focoDoCampo.requestFocus()
                            }
                            CampoDeBuscaNaBarra(
                                valor = state.busca,
                                onValorAlterado = { viewModel.onEvent(ExerciseListEvent.BuscaAlterada(it)) },
                                foco = focoDoCampo,
                            )
                        } else {
                            Text(stringResource(R.string.comum_exercicios))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (buscaAberta) fecharBusca() else onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                if (buscaAberta) R.string.comum_fechar else R.string.comum_voltar,
                            ),
                        )
                    }
                },
                actions = {
                    // UM botão que troca de papel, e não dois. Lupa abre; X fecha e limpa.
                    //
                    // O campo NÃO tem o seu próprio X de limpar quando está na barra: dois X lado
                    // a lado, num espaço de 48dp, viram adivinhação sobre qual apaga o texto e
                    // qual fecha. Como fechar já limpa, um só dá conta dos dois desejos.
                    IconButton(onClick = { if (buscaAberta) fecharBusca() else buscaAberta = true }) {
                        AnimatedContent(targetState = buscaAberta, label = "lupa-ou-fechar") { aberta ->
                            if (aberta) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.comum_fechar),
                                )
                            } else {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.exercicios_buscar),
                                )
                            }
                        }
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
                    // O campo desta tela vive na TopAppBar; o do seletor de exercícios é embutido,
                    // porque lá não existe barra nenhuma. Ver `mostrarBusca`.
                    mostrarBusca = false,
                    onBuscaAlterada = { viewModel.onEvent(ExerciseListEvent.BuscaAlterada(it)) },
                    onCategorySelected = { viewModel.onEvent(ExerciseListEvent.CategorySelected(it)) },
                    onMuscleGroupSelected = { viewModel.onEvent(ExerciseListEvent.MuscleGroupSelected(it)) },
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
    /**
     * Obrigatório de propósito, ao contrário de [onMuscleGroupSelected]: não existe tela que
     * mostre 923 exercícios e não queira busca. Tornar opcional seria abrir a porta para alguém
     * esquecer e a lista voltar a ser só rolagem.
     */
    onBuscaAlterada: (String) -> Unit,
    /**
     * Desenha o campo DENTRO do conteúdo. Falso para quem já tem o seu na barra (a Biblioteca),
     * verdadeiro para quem não tem barra (o seletor dentro da folha de exercícios).
     *
     * Note que `onBuscaAlterada` continua obrigatório mesmo com isto falso: quem não desenha o
     * campo ainda precisa de um jeito de limpar a busca ao fechar.
     */
    mostrarBusca: Boolean = true,
    onCategorySelected: (ExerciseCategory?) -> Unit,
    onMuscleGroupSelected: ((MuscleGroup?) -> Unit)? = null,
    selectedIds: Set<String>? = null,
    onToggle: ((String) -> Unit)? = null,
    onOpenDetail: ((String) -> Unit)? = null,
) {
    Column {
        if (mostrarBusca) {
            /*
             * A busca vem ANTES dos chips, e não depois.
             *
             * Com 923 itens, quem abre esta tela quase sempre já sabe o nome do que procura — os
             * chips servem a quem está passeando pelo acervo. O que resolve o caso mais comum fica
             * onde o polegar chega primeiro.
             */
            OutlinedTextField(
                value = state.busca,
                onValueChange = onBuscaAlterada,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.exercicios_buscar)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    // Só aparece com texto: botão de limpar num campo vazio é um botão que não faz nada.
                    if (state.busca.isNotEmpty()) {
                        IconButton(onClick = { onBuscaAlterada("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.comum_limpar),
                            )
                        }
                    }
                },
                // Busca: o teclado não tem "próximo campo" para onde ir, e a lista já filtrou sozinha.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(8.dp))
        }

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
        // Segundo filtro, dimensão diferente (anatomia, não estilo) — coexiste com o de cima
        // (decisão do Rafael). onMuscleGroupSelected é opcional pra este conteúdo continuar
        // reusável em qualquer tela futura que só precise do filtro por categoria.
        onMuscleGroupSelected?.let { onMuscle ->
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.selectedMuscleGroup == null,
                        onClick = { onMuscle(null) },
                        label = { Text(stringResource(R.string.exercicios_todos_musculos)) },
                    )
                }
                items(MuscleGroup.entries) { musculo ->
                    FilterChip(
                        selected = state.selectedMuscleGroup == musculo,
                        onClick = { onMuscle(musculo) },
                        label = { Text(stringResource(musculo.rotulo())) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

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
                // Duas frases diferentes para dois vazios diferentes: "nada encontrado" é
                // resultado de uma pergunta; "nenhum exercício" é catálogo que não baixou. Dizer
                // a segunda a quem digitou faria o app parecer quebrado.
                state.exercises.isEmpty() && state.busca.isNotBlank() ->
                    Text(
                        stringResource(R.string.exercicios_busca_vazia, state.busca),
                        Modifier.align(Alignment.Center),
                    )
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
/**
 * O campo que vive DENTRO da `TopAppBar`.
 *
 * ## Por que `BasicTextField` e não `TextField`
 *
 * O `TextField` do Material carrega **padding interno próprio** (~16dp horizontais), que o `Text`
 * do título não tem. Resultado observado na bateria: o título começava colado no botão voltar e o
 * placeholder começava 16dp mais à direita — a barra "pulava" para o lado ao abrir a busca.
 *
 * Esse padding não é configurável no `TextField`: a API não expõe `contentPadding`. Zerar exigiria
 * a decoração completa de qualquer jeito, então é mais honesto usar o `BasicTextField`, que não
 * desenha decoração nenhuma, e escrever só o que esta barra precisa — o placeholder.
 *
 * > **Componente que traz enfeite embutido não serve para encaixar em algo que já tem o seu.**
 *
 * A tipografia é a MESMA do título (`titleLarge`) pelo mesmo motivo: o texto tem de nascer onde o
 * título morreu, no mesmo tamanho e na mesma linha de base. `singleLine` faz o texto longo rolar
 * na horizontal em vez de truncar.
 */
@Composable
private fun CampoDeBuscaNaBarra(
    valor: String,
    onValorAlterado: (String) -> Unit,
    foco: FocusRequester,
) {
    BasicTextField(
        value = valor,
        onValueChange = onValorAlterado,
        modifier = Modifier.fillMaxWidth().focusRequester(foco),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { campo ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (valor.isEmpty()) {
                    Text(
                        stringResource(R.string.exercicios_buscar),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                campo()
            }
        },
    )
}
