package dev.rafael.app.screens.session

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rafael.app.R
import dev.rafael.app.screens.exercise.ExerciseVideoLoop
import dev.rafael.app.ui.ErroDeTela
import dev.rafael.app.ui.NetworkImage
import dev.rafael.app.ui.ShimmerContent
import dev.rafael.core.network.MediaUrls
import dev.rafael.features.session.presentation.state.ExercicioEmExecucao
import dev.rafael.features.session.presentation.state.SerieEmExecucao
import dev.rafael.features.session.presentation.state.SessionEvent
import dev.rafael.features.session.presentation.state.WorkoutSessionState
import dev.rafael.features.session.presentation.viewmodel.WorkoutSessionViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A execução do treino, redesenhada em 2026-10-01: **um exercício por vez**.
 *
 * ## O que mudou, e por quê
 *
 * A versão anterior era a planilha inteira numa `LazyColumn`: todas as séries de todos os
 * exercícios, com campo de texto e checkbox. Dava pra conferir o treino todo e era péssima pra
 * EXECUTAR — na academia, entre uma série e outra, com o celular na mão suada, a pessoa precisa de
 * um número grande e um botão grande, não de uma tabela.
 *
 * A ordem vertical é a ordem da ação: **o que você vai fazer** (exercício) → **com quanto**
 * (números) → **onde você está** (séries) → **registrar** → **o que vem depois** (descanso).
 *
 * > **O gesto que grava o dado fica ao lado do dado, não do que acontece depois dele.**
 *
 * O agrupamento por exercício é projeção do `WorkoutSessionState`, não um modelo novo: a lista
 * plana de séries continua sendo o que vai pro servidor.
 *
 * ## O que esta tela NÃO tem, de propósito
 *
 * **"Anotar"** está no desenho e ficou de fora: não existe campo de anotação em lugar nenhum do
 * sistema (nem coluna, nem DTO, nem rota). Botão que não faz nada é pior que botão ausente, e é
 * exatamente a crítica que esta mesma sessão fez ao `EmBreveScreen`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSessionScreen(
    workoutId: String,
    onDone: () -> Unit,
    viewModel: WorkoutSessionViewModel = koinViewModel { parametersOf(workoutId) },
) {
    val state by viewModel.state.collectAsState()

    // salvou (localmente ao menos) → volta
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    // descanso acabou → vibra (o usuário não fica olhando a tela entre séries)
    val context = LocalContext.current
    LaunchedEffect(state.restDoneTick) { if (state.restDoneTick > 0) vibrar(context) }

    /*
     * ⭐ SAIR NO MEIO SÓ PERGUNTA QUANDO HÁ O QUE PERDER.
     *
     * A sessão só vira registro no `Finish` -- até lá, as séries marcadas vivem só no state.
     * Sair com três séries marcadas apagava as três, calado.
     *
     * Mas perguntar SEMPRE é atrito sem contrapartida: quem abriu a tela por engano e não marcou
     * nada não tem nada a perder, e um diálogo ali só ensina a pessoa a tocar "sim" sem ler.
     *
     * > **Confirmação que aparece quando não há o que perder treina o usuário a ignorá-la.**
     *
     * Vale para o X e para o botão do sistema, pelo mesmo motivo: os dois saem da tela.
     */
    val temAlgoAPerder = state.entries.any { it.done } && !state.saved
    var pedindoSaida by rememberSaveable { mutableStateOf(false) }

    fun tentarSair() {
        if (temAlgoAPerder) pedindoSaida = true else onDone()
    }

    BackHandler(enabled = temAlgoAPerder) { pedindoSaida = true }

    if (pedindoSaida) {
        AlertDialog(
            onDismissRequest = { pedindoSaida = false },
            title = { Text(stringResource(R.string.sessao_sair_titulo)) },
            text = { Text(stringResource(R.string.sessao_sair_texto)) },
            confirmButton = {
                TextButton(onClick = { pedindoSaida = false; onDone() }) {
                    Text(stringResource(R.string.sessao_sair_confirmar))
                }
            },
            // Ficar é a opção SEGURA, e por isso é a que tem o verbo do que se está fazendo
            // ("Continuar treino"), não um "Cancelar" que não diz o que cancela.
            dismissButton = {
                TextButton(onClick = { pedindoSaida = false }) {
                    Text(stringResource(R.string.sessao_sair_ficar))
                }
            },
        )
    }

    Scaffold(
        topBar = { CabecalhoDaSessao(state, onSair = { tentarSair() }) },
        bottomBar = {
            if (!state.isLoading && state.error == null) {
                BarraDeNavegacao(
                    state = state,
                    onAnterior = { viewModel.onEvent(SessionEvent.ExercicioAnterior) },
                    onProximo = { viewModel.onEvent(SessionEvent.ExercicioProximo) },
                    onEncerrar = { viewModel.onEvent(SessionEvent.Finish) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val exercicio = state.exercicio
            when {
                state.isLoading -> ShimmerContent()
                state.error != null ->
                    ErroDeTela(erro = state.error!!, modifier = Modifier.align(Alignment.Center))
                exercicio == null ->
                    Text(
                        stringResource(R.string.sessao_vazia),
                        Modifier.align(Alignment.Center).padding(32.dp),
                        textAlign = TextAlign.Center,
                    )
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    /*
                     * ⭐ A TÉCNICA ABRE DENTRO DA SESSÃO, não numa tela nova.
                     *
                     * Navegar pro detalhe tirava a pessoa da execução: o cronômetro sumia de
                     * vista, o descanso corria fora do campo de visão, e voltar era um gesto a
                     * mais com o celular na mão suada. Quem está no meio da série quer CONFERIR o
                     * movimento, não estudar o exercício.
                     *
                     * > **Sair da tela para consultar uma coisa interrompe a coisa que se estava
                     * > fazendo. Expandir, não.**
                     *
                     * `rememberSaveable` por EXERCÍCIO: trocar de exercício fecha o painel (o
                     * texto é de outro movimento), mas girar o aparelho com ele aberto mantém.
                     */
                    var tecnicaAberta by rememberSaveable(exercicio.exerciseId) { mutableStateOf(false) }

                    // Sem vídeo não há chip: oferecer "ver técnica" e abrir um painel vazio é
                    // prometer o que não se entrega. Os dois nulos (`videoRef` e a URL montada)
                    // se resolvem aqui, de uma vez.
                    val urlDoVideo = exercicio.videoRef?.let { MediaUrls.url(it) }

                    CartaoDoExercicio(
                        exercicio = exercicio,
                        serieAtual = state.serieAtual,
                        tecnicaAberta = tecnicaAberta,
                        onAlternarTecnica = if (urlDoVideo != null) {
                            { tecnicaAberta = !tecnicaAberta }
                        } else null,
                    )
                    if (urlDoVideo != null) {
                        PainelDeTecnica(urlDoVideo = urlDoVideo, aberto = tecnicaAberta)
                    }
                    state.serie?.let { serie ->
                        CartaoDeNumeros(
                            serie = serie,
                            onPeso = { viewModel.onEvent(SessionEvent.AjustarPeso(it)) },
                            onReps = { viewModel.onEvent(SessionEvent.AjustarReps(it)) },
                        )
                        FileiraDeSeries(
                            exercicio = exercicio,
                            serieAtual = state.serieAtual,
                            onSelecionar = { viewModel.onEvent(SessionEvent.SerieSelecionada(it)) },
                        )
                        BotaoSerieFeita(
                            feita = serie.entrada.done,
                            onToggle = { viewModel.onEvent(SessionEvent.ToggleDone(serie.indice)) },
                        )
                    }
                    CartaoDeDescanso(
                        state = state,
                        restSeconds = exercicio.restSeconds,
                        onAlternar = { viewModel.onEvent(SessionEvent.AlternarDescanso) },
                        onMais15 = { viewModel.onEvent(SessionEvent.AddRest(15)) },
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** Nome do treino, posição no treino, cronômetro da sessão e a barra de progresso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CabecalhoDaSessao(state: WorkoutSessionState, onSair: () -> Unit) {
    Column {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.workoutName.ifBlank { stringResource(R.string.treino_detalhe_titulo_padrao) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    if (state.totalDeExercicios > 0) {
                        Text(
                            "  ·  " + stringResource(
                                R.string.comum_fracao,
                                state.exercicioAtual + 1,
                                state.totalDeExercicios,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            actions = {
                CronometroDaSessao(state.segundosDaSessao)
                IconButton(onClick = onSair) {
                    Icon(Icons.Default.Close, stringResource(R.string.comum_fechar))
                }
            },
        )
        // Progresso por SÉRIE feita, e não por exercício: é o que mede trabalho realizado.
        val feitas = state.entries.count { it.done }
        val total = state.entries.size.coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { feitas / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(3.dp),
        )
    }
}

/** O ponto verde é a convenção de "gravando": a sessão está correndo. */
@Composable
private fun CronometroDaSessao(segundos: Int) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.sessao_tempo, segundos / 60, segundos % 60),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CartaoDoExercicio(
    exercicio: ExercicioEmExecucao,
    serieAtual: Int,
    tecnicaAberta: Boolean,
    /** `null` quando o exercício não tem vídeo: nada de chip, nada de toque na miniatura. */
    onAlternarTecnica: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(if (onAlternarTecnica != null) Modifier.clickable(onClick = onAlternarTecnica) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            NetworkImage(
                url = exercicio.thumbRef?.let { MediaUrls.url(it) },
                contentDescription = exercicio.nome,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(14.dp),
            )
            // Badge de play sobre a miniatura: só com vídeo, e gira pra virar a seta que fecha.
            if (onAlternarTecnica != null) Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    // Gira 90 graus quando abre: o mesmo selo vira a seta que fecha.
                    modifier = Modifier.size(20.dp).graphicsLayer {
                        rotationZ = if (tecnicaAberta) 90f else 0f
                    },
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                exercicio.nome ?: stringResource(R.string.comum_exercicio),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.sessao_serie_de, serieAtual + 1, exercicio.series.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (onAlternarTecnica != null) AssistChip(
                onClick = onAlternarTecnica,
                label = {
                    Text(
                        stringResource(
                            if (tecnicaAberta) R.string.sessao_ocultar_tecnica
                            else R.string.sessao_ver_tecnica,
                        ),
                    )
                },
            )
        }
    }
}

/**
 * O movimento, grande, abrindo e fechando dentro da sessão.
 *
 * ## Só o vídeo, e isso foi uma correção
 *
 * Nasceu com o vídeo MAIS a descrição do catálogo. O Rafael cortou o texto na bateria manual, e
 * tinha razão: quem está entre duas séries quer CONFERIR o movimento em dois segundos, não ler
 * oito linhas. Com o texto fora, o vídeo ganhou o espaço inteiro.
 *
 * > **Consulta no meio de uma tarefa tem que caber num olhar. O que exige leitura já é outra
 * > tarefa.**
 *
 * ## A animação, e por que a mola em vez da curva fixa
 *
 * A primeira versão abria com `tween`, e ficava mecânica: a altura crescia em velocidade
 * constante e o conteúdo aparecia junto, espremido num quadro de altura zero.
 *
 * Agora a ALTURA abre com mola (`spring`), que desacelera sozinha no fim como algo com massa, e
 * o conteúdo só começa a aparecer **60ms depois** que a abertura começou. Esse atraso é o que
 * faz a diferença: você vê o espaço abrir e DEPOIS o vídeo ocupá-lo, em vez dos dois brigando
 * pelo mesmo instante.
 *
 * Fechar é o contrário e mais rápido: o conteúdo some primeiro (90ms), a altura fecha em
 * seguida. Entrar pode chamar atenção; sair não deve.
 *
 * ## O player só existe enquanto o painel está aberto
 *
 * `ExerciseVideoLoop` levanta um `ExoPlayer`. Mantê-lo vivo durante o treino inteiro seria pagar
 * bateria por um vídeo que ninguém está olhando. Dentro do `AnimatedVisibility` ele nasce ao
 * abrir e é descartado ao fechar.
 */
@Composable
private fun PainelDeTecnica(urlDoVideo: String, aberto: Boolean) {
    AnimatedVisibility(
        visible = aberto,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(tween(durationMillis = 180, delayMillis = 60)),
        exit = fadeOut(tween(90)) + shrinkVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
        ),
    ) {
        ExerciseVideoLoop(
            url = urlDoVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

/**
 * Peso e repetições, grandes, com `-`/`+`.
 *
 * O número é o protagonista da tela: é o que se lê de relance no meio da série.
 */
@Composable
private fun CartaoDeNumeros(
    serie: SerieEmExecucao,
    onPeso: (Double) -> Unit,
    onReps: (Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Numero(
                valor = serie.entrada.weight.ifBlank { "0" },
                rotulo = stringResource(R.string.sessao_peso_rotulo),
                descricaoMenos = stringResource(R.string.sessao_diminuir_peso),
                descricaoMais = stringResource(R.string.sessao_aumentar_peso),
                onMenos = { onPeso(-WorkoutSessionViewModel.PASSO_DO_PESO_KG) },
                onMais = { onPeso(WorkoutSessionViewModel.PASSO_DO_PESO_KG) },
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(Modifier.height(96.dp))
            Numero(
                valor = serie.entrada.repsDone.ifBlank { "0" },
                rotulo = stringResource(R.string.comum_reps),
                descricaoMenos = stringResource(R.string.sessao_diminuir_reps),
                descricaoMais = stringResource(R.string.sessao_aumentar_reps),
                onMenos = { onReps(-1) },
                onMais = { onReps(1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Numero(
    valor: String,
    rotulo: String,
    descricaoMenos: String,
    descricaoMais: String,
    onMenos: () -> Unit,
    onMais: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontSize = 44.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            rotulo.uppercase(),   // caixa alta é DESENHO: mora aqui, não no catálogo de textos
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onMenos) {
                Icon(Icons.Default.Remove, descricaoMenos)
            }
            Spacer(Modifier.width(10.dp))
            FilledTonalIconButton(onClick = onMais) {
                Icon(Icons.Default.Add, descricaoMais)
            }
        }
    }
}

/**
 * `S1 S2 S3 S4` — onde você está dentro do exercício, e como pular pra outra série.
 *
 * `LazyRow` em vez de `Row`: o motor prescreve de 2 a 5 séries e um dia pode prescrever mais.
 * Fileira fixa quebraria calada no dia em que isso acontecesse.
 */
@Composable
private fun FileiraDeSeries(
    exercicio: ExercicioEmExecucao,
    serieAtual: Int,
    onSelecionar: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(exercicio.series) { serie ->
            val indice = serie.entrada.setIndex
            FilterChip(
                selected = indice == serieAtual,
                onClick = { onSelecionar(indice) },
                label = { Text(stringResource(R.string.sessao_serie_curta, indice + 1)) },
                leadingIcon = if (serie.entrada.done) {
                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

/** O gesto que registra a série. É o único botão de destaque da tela, e isso é intencional. */
@Composable
private fun BotaoSerieFeita(feita: Boolean, onToggle: () -> Unit) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = if (feita) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Icon(
            if (feita) Icons.Default.Check else Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.sessao_serie_feita), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Os três estados do descanso, no mesmo lugar da tela:
 *
 * | | rótulo | botão |
 * |---|---|---|
 * | ocioso | "Descanso · após a série" + o tempo PRESCRITO | ▶ inicia agora |
 * | correndo | "Descanso" + o tempo restante | ⏸ pausa |
 * | pausado | idem | ▶ retoma |
 *
 * O rótulo "após a série" é o que impede o card de MENTIR quando está parado: ele não finge
 * contagem, anuncia o que vem.
 */
@Composable
private fun CartaoDeDescanso(
    state: WorkoutSessionState,
    restSeconds: Int,
    onAlternar: () -> Unit,
    onMais15: () -> Unit,
) {
    val correndo = state.restRemaining != null
    val segundos = state.restRemaining ?: restSeconds
    val progresso by animateFloatAsState(
        targetValue = if (correndo && state.restTotal > 0) {
            (state.restRemaining ?: 0) / state.restTotal.toFloat()
        } else 1f,
        label = "descanso",
    )

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (correndo) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (correndo) stringResource(R.string.sessao_descanso)
                        else stringResource(R.string.sessao_descanso_apos)).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        stringResource(R.string.sessao_tempo, segundos / 60, segundos % 60),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (correndo) {
                    TextButton(onClick = onMais15) { Text(stringResource(R.string.sessao_mais_15s)) }
                    Spacer(Modifier.width(8.dp))
                }
                FilledIconButton(onClick = onAlternar) {
                    Icon(
                        if (correndo && !state.restPausado) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = when {
                            !correndo -> stringResource(R.string.sessao_iniciar_descanso)
                            state.restPausado -> stringResource(R.string.sessao_retomar_descanso)
                            else -> stringResource(R.string.sessao_pausar_descanso)
                        },
                    )
                }
            }
            if (correndo) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progresso }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * `Anterior` · `Próximo` · `Encerrar`.
 *
 * "Encerrar" é **discreto de propósito**: é a ação que menos se quer no meio do treino, e estava
 * com a cor mais chamativa da tela no desenho original. O destaque é do "Série feita".
 */
@Composable
private fun BarraDeNavegacao(
    state: WorkoutSessionState,
    onAnterior: () -> Unit,
    onProximo: () -> Unit,
    onEncerrar: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAnterior, enabled = state.temAnterior) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                Text(stringResource(R.string.sessao_anterior))
            }
            TextButton(onClick = onProximo, enabled = state.temProximo) {
                Text(stringResource(R.string.sessao_proximo))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEncerrar, enabled = state.canFinish) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp))
                else Text(stringResource(R.string.sessao_finalizar))
            }
        }
    }
}

/**
 * Vibração curta ao fim do descanso. No-op se o aparelho não tiver vibrador.
 *
 * `minSdk` é 24 — `VibrationEffect` só existe a partir do 26 (O). Guarda em RUNTIME
 * (`SDK_INT`), não `@RequiresApi`: essa anotação é só sinal pro lint, não gera nenhum
 * `if` no bytecode. Num aparelho real em API 24/25 ela não impede a chamada, só cala o
 * aviso — o crash (`NoClassDefFoundError`) continua.
 */
private fun vibrar(context: Context) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(400)
        }
    }
}
