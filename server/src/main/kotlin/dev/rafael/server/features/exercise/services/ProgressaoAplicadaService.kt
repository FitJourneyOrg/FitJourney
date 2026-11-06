package dev.rafael.server.features.exercise.services

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.workout.Papel
import dev.rafael.core.result.AppResult
import dev.rafael.server.error.Falhas
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.ExercicioParaProgressao
import dev.rafael.server.features.exercise.db.MesocicloRepository
import dev.rafael.server.features.exercise.db.ProgressaoRepository
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante
import dev.rafael.server.features.exercise.engine.v3.CicloDoMesociclo
import dev.rafael.server.features.exercise.engine.v3.PrescricaoV3
import dev.rafael.server.features.exercise.engine.v3.Progressao
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorRepeticao
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.session.models.SetLog
import dev.rafael.server.features.session.models.WorkoutSession
import dev.rafael.server.features.workout.db.WorkoutRepository
import dev.rafael.server.features.workout.models.Workout
import kotlin.uuid.Uuid

/**
 * Liga a sessão salva ao motor de progressão (fatia "rir-chega-e-registra", 2026-11-05;
 * `docs/trabalho/rir-plano.md`). Chamado por `SessionService.record()` via porta estreita —
 * mesmo padrão do `AchievementService.avaliarAposSessao`: falha nunca derruba o `record()`.
 *
 * Por exercício ÚNICO da sessão (bi-set/vaga duplicada: só a 1ª vaga — menor `orderIndex` —
 * conta, decisão 2026-11-05, sem mudar a PK de `registrar()`): resolve `Papel` (do
 * `WorkoutExercise` de origem, via `WorkoutRepository`), `incremento`/`barraLivre`/
 * `cargaInvertida` (catálogo, `ExerciseRepository.paraProgressao`) e o `Estado` anterior
 * (`ProgressaoRepository.buscar`); chama `Progressao.calibrar()` (1ª exposição: sem `Estado` ou
 * `Estado.carga == null`, ex.: pós-recalibração de `RetornoAposPausa`) ou `Progressao.decidir()`
 * (demais), e grava com `ProgressaoRepository.registrar()`.
 *
 * **Calibração não produz um `Decisao` (SOBE/MANTEM/DESCE) — decisão do Rafael (2026-11-05):
 * registra como `Evento.Carga(Decisao.MANTEM)`** (reaproveita o enum existente; a 1ª exposição
 * não "subiu", então conta pro streak de estagnação do `AncoraDoIniciante` como uma sessão que
 * manteve — não há `Decisao` própria pra "acabei de calibrar").
 *
 * Qualquer dado que falte pra UM exercício (papel null — treino manual/sem prescrição; sem
 * `incremento` no catálogo — sem carga externa em kg; sem `weightKg` logado pra calibrar) pula
 * SÓ aquele exercício, mesmo piso do `incremento == null` no núcleo ("o chamador não invoca o
 * motor"). Sem `Profile` (perfil incompleto, sem `Goal`) pula a sessão inteira — decisão do
 * Rafael (2026-11-05): sem `Goal` não dá pra escolher a faixa de prescrição pra NENHUM exercício.
 */
class ProgressaoAplicadaService(
    private val exerciseRepository: ExerciseRepository,
    private val progressaoRepository: ProgressaoRepository,
    private val mesocicloRepository: MesocicloRepository,
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: ProfileRepository,
    private val falhas: Falhas,
) {

    suspend fun aplicar(session: WorkoutSession) {
        runCatching { aplicarOuFalha(session) }.onFailure { e ->
            falhas.registrar("Progressão após sessão lançou", e)
        }
    }

    private suspend fun aplicarOuFalha(session: WorkoutSession) {
        val goal = profileRepository.findByUserId(session.userId)
            .comLogDeFalha("Não resolvi o perfil pra progressão da sessão ${session.id}")
            ?.goal ?: return

        val vagas = primeiraVagaPorExercicio(session.sets)
        if (vagas.isEmpty()) return

        val workout = session.workoutId?.let {
            workoutRepository.findById(session.userId, it)
                .comLogDeFalha("Não resolvi o treino de origem da sessão ${session.id}")
        }
        val catalogo = exerciseRepository.paraProgressao(vagas.keys.toList())
            .comLogDeFalha("Não resolvi o catálogo pra progressão da sessão ${session.id}")
            ?: return
        val mesociclo = CicloDoMesociclo.paraPerfil(
            mesocicloRepository.buscar(session.userId)
                .comLogDeFalha("Não resolvi o mesociclo pra progressão da sessão ${session.id}"),
        )

        for ((exerciseId, vaga) in vagas) {
            aplicarExercicio(session.userId, session.id, exerciseId, vaga, workout, catalogo, goal, mesociclo)
        }
    }

    /** `(exerciseId -> 1ª vaga)`: menor `orderIndex` do exercício + suas séries, por `setIndex`. */
    private fun primeiraVagaPorExercicio(sets: List<SetLog>): Map<Uuid, Vaga> =
        sets.groupBy { it.exerciseId }.mapValues { (_, doExercicio) ->
            val orderIndex = doExercicio.minOf { it.orderIndex }
            Vaga(orderIndex, doExercicio.filter { it.orderIndex == orderIndex }.sortedBy { it.setIndex })
        }

    private data class Vaga(val orderIndex: Int, val series: List<SetLog>)

    private suspend fun aplicarExercicio(
        usuarioId: Uuid,
        sessaoId: Uuid,
        exerciseId: Uuid,
        vaga: Vaga,
        workout: Workout?,
        catalogo: Map<Uuid, ExercicioParaProgressao>,
        goal: Goal,
        mesociclo: Int,
    ) {
        if (vaga.series.isEmpty()) return
        val papel = workout?.exercises
            ?.firstOrNull { it.exerciseId == exerciseId && it.orderIndex == vaga.orderIndex }
            ?.papel ?: return
        val cat = catalogo[exerciseId] ?: return
        val incremento = cat.incremento
        if (incremento == null) {
            // "progressao-por-repeticao" (2026-11-06): BODYWEIGHT puro progride por reps, nao por
            // carga. BAND/HAND_GRIP continuam sem progressao automatica -- debito separado, mesmo
            // piso de antes (`docs/fontes/debitos.md`).
            if (cat.equipment == "BODYWEIGHT") {
                aplicarExercicioPorRepeticao(usuarioId, sessaoId, exerciseId, vaga, goal, papel, mesociclo)
            }
            return
        }

        val series = vaga.series.map { Progressao.Serie(reps = it.repsDone, rir = it.rir) }
        val estadoAnterior = progressaoRepository.buscar(usuarioId, exerciseId)
            .comLogDeFalha("Não resolvi o estado anterior do exercício $exerciseId")

        val (novoEstado, evento) = if (estadoAnterior?.carga == null) {
            val cargaInicial = vaga.series.firstNotNullOfOrNull { it.weightKg } ?: return
            val faixa = PrescricaoV3.de(goal, papel)
            val estado = Progressao.calibrar(
                cargaInicial = cargaInicial,
                rirsInformados = series.map { it.rir },
                rirAlvo = faixa.rirAlvo,
                barraLivre = cat.barraLivre,
                incremento = incremento,
                cargaInvertida = cat.cargaInvertida,
            )
            estado to AncoraDoIniciante.Evento.Carga(Progressao.Decisao.MANTEM)
        } else {
            val (estado, decisao) = Progressao.decidir(
                estado = estadoAnterior,
                series = series,
                papel = papel,
                objetivo = goal,
                incremento = incremento,
                barraLivre = cat.barraLivre,
                cargaInvertida = cat.cargaInvertida,
            )
            estado to AncoraDoIniciante.Evento.Carga(decisao)
        }

        progressaoRepository.registrar(usuarioId, exerciseId, sessaoId, novoEstado, evento, mesociclo)
            .comLogDeFalha("Não registrei progressão do exercício $exerciseId na sessão $sessaoId")
    }

    /**
     * Eixo de REPETIÇÃO (fatia "progressao-por-repeticao", 2026-11-06) -- `BODYWEIGHT` puro sem
     * `incremento`. Sem calibração: a faixa original (`PrescricaoV3.de`) já é o estado válido da
     * 1ª exposição, diferente do eixo de carga (que precisa descobrir um peso que não existe pra
     * quem nunca fez o exercício). `Evento.Repeticao` conta igual a `Evento.Carga` pro contador de
     * estagnação da âncora do iniciante (decisão do Rafael, 2026-11-06).
     */
    private suspend fun aplicarExercicioPorRepeticao(
        usuarioId: Uuid,
        sessaoId: Uuid,
        exerciseId: Uuid,
        vaga: Vaga,
        goal: Goal,
        papel: Papel,
        mesociclo: Int,
    ) {
        val faixa = PrescricaoV3.de(goal, papel)
        val series = vaga.series.map { ProgressaoPorRepeticao.Serie(reps = it.repsDone, rir = it.rir) }
        val estadoAnterior = progressaoRepository.buscarPorRepeticao(usuarioId, exerciseId)
            .comLogDeFalha("Não resolvi o estado anterior (repetição) do exercício $exerciseId")
        val estadoBase = estadoAnterior ?: ProgressaoPorRepeticao.Estado(repsMin = faixa.repsMin, repsMax = faixa.repsMax)

        val (novoEstado, decisao) = ProgressaoPorRepeticao.decidir(estadoBase, series, faixa)

        progressaoRepository.registrarPorRepeticao(
            usuarioId, exerciseId, sessaoId, novoEstado, AncoraDoIniciante.Evento.Repeticao(decisao), mesociclo,
        ).comLogDeFalha("Não registrei progressão (repetição) do exercício $exerciseId na sessão $sessaoId")
    }

    /** Falha real (erro de banco) vira log e `null`; quem chama já sabe tratar ausência. */
    private fun <T> AppResult<T>.comLogDeFalha(contexto: String): T? = when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> {
            falhas.registrar(contexto, error)
            null
        }
    }
}
