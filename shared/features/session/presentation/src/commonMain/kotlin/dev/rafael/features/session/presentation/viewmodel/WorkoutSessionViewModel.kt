package dev.rafael.features.session.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.contract.session.SetLogDto
import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.catalog.ExerciseLookup
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.session.domain.HistoricoDeSessoes
import dev.rafael.features.session.presentation.state.SessionEvent
import dev.rafael.features.session.presentation.state.SetEntry
import dev.rafael.features.session.presentation.state.WorkoutSessionState
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

class WorkoutSessionViewModel(
    private val workoutId: String,
    private val workouts: WorkoutRepository,
    private val lookup: ExerciseLookup,
    private val sync: HistoricoDeSessoes,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutSessionState())
    val state: StateFlow<WorkoutSessionState> = _state.asStateFlow()

    private val startedAt = nowIso()   // início = abertura da tela
    private val inicioMs = Clock.System.now().toEpochMilliseconds()
    private var programId: String? = null

    init {
        load()
        contarOTempoDaSessao()
    }

    /**
     * O cronômetro do topo. Deriva do INSTANTE de abertura, não de um contador que soma 1 por
     * segundo: um contador atrasa quando o app vai pro fundo, e treino é justamente quando o
     * celular fica no bolso.
     */
    private fun contarOTempoDaSessao() {
        viewModelScope.launch {
            while (true) {
                _state.update { it.copy(segundosDaSessao = ((nowMs() - inicioMs) / 1000).toInt()) }
                delay(1_000)
            }
        }
    }

    fun onEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.RepsChanged -> updateEntry(event.index) { it.copy(repsDone = event.value.filter { c -> c.isDigit() }) }
            is SessionEvent.WeightChanged -> updateEntry(event.index) { it.copy(weight = event.value.filter { c -> c.isDigit() || c == '.' }) }
            is SessionEvent.ToggleDone -> {
                val marcando = _state.value.entries.getOrNull(event.index)?.done == false
                updateEntry(event.index) { it.copy(done = !it.done) }
                // só ao MARCAR (desmarcar é correção, não gera descanso)
                if (marcando) _state.value.entries.getOrNull(event.index)?.let { startRest(it.restSeconds) }
            }
            SessionEvent.Finish -> finish()
            SessionEvent.SkipRest -> stopRest()
            is SessionEvent.AddRest -> addRest(event.seconds)

            SessionEvent.ExercicioAnterior -> irParaExercicio(_state.value.exercicioAtual - 1)
            SessionEvent.ExercicioProximo -> irParaExercicio(_state.value.exercicioAtual + 1)
            is SessionEvent.SerieSelecionada -> _state.update { it.copy(serieAtual = event.setIndex) }
            is SessionEvent.AjustarPeso -> ajustarPeso(event.delta)
            is SessionEvent.AjustarReps -> ajustarReps(event.delta)
            SessionEvent.AlternarDescanso -> alternarDescanso()
        }
    }

    // ---- navegação entre exercícios -----------------------------------------

    /**
     * Trocar de exercício **zera a série selecionada**, e não a mantém.
     *
     * Manter o índice levaria a "estou na série 3" num exercício que tem duas: o chip ativo
     * apontaria para o vazio. A primeira série é sempre uma resposta válida.
     */
    private fun irParaExercicio(indice: Int) {
        val total = _state.value.exercicios.size
        if (indice !in 0 until total) return
        _state.update { it.copy(exercicioAtual = indice, serieAtual = 0) }
    }

    // ---- passos de peso e repetição ------------------------------------------

    /**
     * O `-`/`+` anda em passo fixo; o teclado continua existindo pra quem precisa do número exato
     * (a tela abre o teclado ao TOCAR no número).
     *
     * **2,5 kg não é um número redondo escolhido no olho:** é o par de anilhas de 1,25 kg, o menor
     * incremento real de uma barra. Halter e máquina variam por fabricante, e por isso o passo
     * nunca vai servir a todos -- quem precisa de outro valor digita.
     *
     * Campo vazio conta como zero: é o estado inicial do peso, e `+` ali tem de dar 2,5, não nada.
     */
    private fun ajustarPeso(delta: Double) {
        val i = _state.value.serie?.indice ?: return
        updateEntry(i) { e ->
            val atual = e.weight.toDoubleOrNull() ?: 0.0
            e.copy(weight = formatarPeso((atual + delta).coerceAtLeast(0.0)))
        }
    }

    private fun ajustarReps(delta: Int) {
        val i = _state.value.serie?.indice ?: return
        updateEntry(i) { e ->
            val atual = e.repsDone.toIntOrNull() ?: 0
            e.copy(repsDone = (atual + delta).coerceAtLeast(0).toString())
        }
    }

    // ---- descanso: ocioso / correndo / pausado -------------------------------

    /**
     * O ▶/⏸ do card, com os três estados que o desenho pede:
     *
     * | estado | o que o botão faz |
     * |---|---|
     * | ocioso (nada correndo) | inicia o descanso prescrito do exercício atual |
     * | correndo | pausa |
     * | pausado | retoma de onde parou |
     *
     * Marcar a série como feita continua disparando o descanso sozinho -- o botão existe para
     * quem quer descansar FORA dessa hora (entre séries de aquecimento, por exemplo).
     */
    private fun alternarDescanso() {
        val s = _state.value
        when {
            s.restRemaining == null -> startRest(s.exercicio?.restSeconds ?: 0)
            s.restPausado -> retomarDescanso()
            else -> pausarDescanso()
        }
    }

    private fun pausarDescanso() {
        restJob?.cancel()
        restEndsAt = null                       // o que vale enquanto pausado é `restRemaining`
        _state.update { it.copy(restPausado = true) }
    }

    private fun retomarDescanso() {
        val restante = _state.value.restRemaining ?: return
        restEndsAt = nowMs() + restante * 1000L
        _state.update { it.copy(restPausado = false) }
        tiquetaquear()
    }

    // ---- descanso ----------------------------------------------------------
    // Guarda o INSTANTE DO FIM e recalcula o restante a cada tick. Um contador puro
    // atrasaria se o app fosse pro fundo; assim o tempo continua correto ao voltar.

    private var restEndsAt: Long? = null
    private var restJob: Job? = null

    private fun startRest(seconds: Int) {
        if (seconds <= 0) return
        restEndsAt = nowMs() + seconds * 1000L
        _state.update { it.copy(restRemaining = seconds, restTotal = seconds, restPausado = false) }
        tiquetaquear()
    }

    /** O laço do cronômetro, extraído porque `startRest` e `retomarDescanso` precisam do mesmo. */
    private fun tiquetaquear() {
        restJob?.cancel()
        restJob = viewModelScope.launch {
            while (true) {
                delay(250)
                val fim = restEndsAt ?: break
                val restante = ((fim - nowMs() + 999) / 1000).toInt()   // arredonda p/ cima
                if (restante <= 0) {
                    restEndsAt = null
                    _state.update {
                        it.copy(restRemaining = null, restPausado = false, restDoneTick = it.restDoneTick + 1)
                    }
                    break
                }
                if (restante != _state.value.restRemaining) {
                    _state.update { it.copy(restRemaining = restante) }
                }
            }
        }
    }

    private fun addRest(seconds: Int) {
        val fim = restEndsAt ?: return
        restEndsAt = fim + seconds * 1000L
        _state.update { it.copy(restTotal = it.restTotal + seconds) }
    }

    private fun stopRest() {
        restJob?.cancel()
        restEndsAt = null
        _state.update { it.copy(restRemaining = null, restPausado = false) }
    }

    override fun onCleared() {
        restJob?.cancel()
        super.onCleared()
    }

    private fun nowMs() = Clock.System.now().toEpochMilliseconds()

    private fun load() {
        viewModelScope.launch {
            when (val r = workouts.get(workoutId)) {
                is AppResult.Success -> {
                    val w = r.value
                    programId = w.programId
                    val refs = lookup.byIds(w.exercises.map { it.exerciseId })
                    val entries = w.exercises.sortedBy { it.orderIndex }.flatMap { ex ->
                        ex.sets.sortedBy { it.orderIndex }.map { set ->
                            SetEntry(
                                exerciseId = ex.exerciseId,
                                // Sem fallback aqui: `null` significa "o catálogo não conhece
                                // este id", e quem escreve a palavra é a TELA, do catálogo pt-BR.
                                exerciseName = refs[ex.exerciseId]?.name,
                                orderIndex = ex.orderIndex,
                                setIndex = set.orderIndex,
                                targetReps = set.reps,
                                repsDone = set.reps.toString(),   // começa no alvo
                                weight = "",
                                done = false,
                                restSeconds = ex.restSeconds,     // prescrição do motor
                                thumbRef = refs[ex.exerciseId]?.thumbRef,
                                videoRef = refs[ex.exerciseId]?.videoRef,
                            )
                        }
                    }
                    _state.update { it.copy(isLoading = false, workoutName = w.name, entries = entries) }
                }
                is AppResult.Failure -> _state.update { it.copy(isLoading = false, error = r.error) }
            }
        }
    }

    private fun finish() {
        val s = _state.value
        if (!s.canFinish) return
        _state.update { it.copy(isSaving = true, error = null) }
        val dto = WorkoutSessionDto(
            id = Uuid.random().toString(),           // idempotência do sync
            programId = programId,
            workoutId = workoutId,
            workoutName = s.workoutName,
            startedAt = startedAt,
            finishedAt = nowIso(),
            sets = s.entries.map { e ->
                SetLogDto(
                    exerciseId = e.exerciseId,
                    orderIndex = e.orderIndex,
                    setIndex = e.setIndex,
                    targetReps = e.targetReps,
                    repsDone = e.repsDone.toIntOrNull() ?: 0,
                    weightKg = e.weight.toDoubleOrNull(),
                    done = e.done,
                )
            },
        )
        viewModelScope.launch {
            runCatching { sync.record(dto) }.fold(
                onSuccess = { _state.update { it.copy(isSaving = false, saved = true) } },   // salvo (local ao menos)
                // ⚠️ SEM mensagem: `AppError.Unexpected` não carrega código, e a política de
                // apresentação (ErrorUi) escreve o texto da família. A frase que estava aqui
                // NUNCA chegava à tela — era texto que ninguém lia.
                onFailure = { _state.update { it.copy(isSaving = false, error = AppError.Unexpected()) } },
            )
        }
    }

    private fun updateEntry(index: Int, transform: (SetEntry) -> SetEntry) {
        _state.update { st ->
            st.copy(entries = st.entries.mapIndexed { i, e -> if (i == index) transform(e) else e })
        }
    }

    companion object {
        /** Ver o KDoc de `ajustarPeso`: o par de anilhas de 1,25 kg. A TELA lê daqui. */
        const val PASSO_DO_PESO_KG = 2.5
    }

    private fun nowIso() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
}

/**
 * O peso como TEXTO, do jeito que o campo mostra: `42.5`, mas `45` e não `45.0`.
 *
 * Escrito à mão porque `String.format` não existe em `commonMain`. Pura, então o arredondamento
 * tem teste próprio -- é a parte que erra em silêncio (somar 2,5 dez vezes em `Double` não dá
 * exatamente 25).
 */
internal fun formatarPeso(kg: Double): String {
    val arredondado = kotlin.math.round(kg * 10) / 10.0
    val inteiro = arredondado.toLong()
    val decimo = kotlin.math.round((arredondado - inteiro) * 10).toInt()
    return if (decimo == 0) inteiro.toString() else "$inteiro.$decimo"
}
