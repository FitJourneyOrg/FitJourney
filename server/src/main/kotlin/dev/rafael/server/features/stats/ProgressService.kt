package dev.rafael.server.features.stats

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.stats.ExerciseDeltaDto
import dev.rafael.contract.stats.ExerciseTrendDto
import dev.rafael.contract.stats.MuscleVolumeDto
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.contract.stats.TrendPointDto
import dev.rafael.contract.stats.WeeklyLoadDto
import dev.rafael.contract.stats.WorkoutComparisonDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.session.models.WorkoutSession
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Monta a analise de progressao (J.2). Toda a matematica vive no [ProgressPolicy]; aqui so
 * acontece o que precisa de I/O: ler o historico, resolver nomes e **aplicar o portao de plano**.
 *
 * ## O filtro de carga mora na BORDA
 *
 * `ProgressPolicy.elegivel` decide, mas quem o aplica e este achatamento. A politica recebe
 * [ProgressPolicy.SerieFeita] com `kg` nao-nulo por construcao: regra que ja chega filtrada nao
 * tem como ser esquecida la dentro.
 *
 * ## [INV] Free nao recebe o numero pago em campo nenhum
 *
 * Os tres blocos pagos nem sequer sao CALCULADOS para quem e free - nao ha valor a vazar por
 * descuido de serializacao. O `analysisLocked` diz ao cliente que o nulo e portao, nao falta de
 * dado (ver KDoc do [ProgressDto]).
 */
class ProgressService(
    private val userService: UserService,
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val clock: Clock = Clock.System,
) {
    companion object {
        /** Janela da analise. Oito semanas cobrem um mesociclo inteiro com deload. */
        const val SEMANAS = 8

        /** Linhas no grafico de evolucao. Mais que tres vira emaranhado em tela de celular. */
        const val EXERCICIOS_NO_GRAFICO = 3
    }

    suspend fun forUser(firebaseUid: String, email: String?, idioma: Idioma): AppResult<ProgressDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            sessions.listByUser(user.id).flatMap { historico ->
                montar(historico, premium = user.isPremium, idioma = idioma)
            }
        }

    private suspend fun montar(
        historico: List<WorkoutSession>,
        premium: Boolean,
        idioma: Idioma,
    ): AppResult<ProgressDto> {
        val series = historico.flatMap { sessao ->
            sessao.sets.mapNotNull { set ->
                val kg = set.weightKg ?: return@mapNotNull null
                if (!ProgressPolicy.elegivel(set.done, kg)) return@mapNotNull null
                ProgressPolicy.SerieFeita(
                    sessaoId = sessao.id,
                    data = sessao.finishedAt.date,
                    nomeDoTreino = sessao.workoutName,
                    exercicioId = set.exerciseId,
                    reps = set.repsDone,
                    kg = kg,
                )
            }
        }

        // Treino conta como treino mesmo sem carga: calistenia nao deixa de ser sessao so porque
        // nao entra no grafico. Mesmo criterio do StatsService (ao menos uma serie feita).
        val sessoesValidas = historico.count { s -> s.sets.any { it.done } }

        if (series.isEmpty()) {
            return ProgressDto(
                totalKg = 0.0,
                totalSessions = sessoesValidas,
                analysisLocked = !premium,
            ).asSuccess()
        }

        val comparacao = ProgressPolicy.ultimoVsAnterior(series)
        val relevantes = if (premium) {
            ProgressPolicy.maisRelevantes(series, EXERCICIOS_NO_GRAFICO)
        } else {
            emptyList()
        }

        // Um unico par de consultas: leitura crua + traducao na borda, sobre os ids que importam.
        val idsDeNome = (comparacao?.deltaPorExercicio?.keys.orEmpty() + relevantes).toList()
        val idsDeMusculo = if (premium) series.map { it.exercicioId }.distinct() else emptyList()

        return exercises.paraAnalise((idsDeNome + idsDeMusculo).distinct()).flatMap { catalogo ->
            exercises.nomesTraduzidos(idsDeNome, idioma).map { traduzidos ->
                fun nome(id: Uuid): String = traduzidos[id] ?: catalogo[id]?.nomePiso.orEmpty()

                val hoje = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

                ProgressDto(
                    totalKg = ProgressPolicy.tonelagem(series),
                    totalSessions = sessoesValidas,
                    sinceDate = series.minOf { it.data }.toString(),
                    lastVsPrevious = comparacao?.let { c ->
                        WorkoutComparisonDto(
                            workoutName = c.nomeDoTreino,
                            currentDate = c.dataAtual.toString(),
                            previousDate = c.dataAnterior.toString(),
                            currentKg = c.kgAtual,
                            previousKg = c.kgAnterior,
                            exercises = c.deltaPorExercicio.entries
                                .sortedByDescending { it.value }
                                .map { (id, delta) ->
                                    ExerciseDeltaDto(
                                        exerciseId = id.toString(),
                                        name = nome(id),
                                        currentKg = c.kgAtualPorExercicio[id] ?: 0.0,
                                        deltaKg = delta,
                                    )
                                },
                        )
                    },
                    weeklyLoad = if (!premium) null else {
                        ProgressPolicy.porSemana(series, hoje, SEMANAS)
                            .map { WeeklyLoadDto(it.semana.toString(), it.kg) }
                    },
                    strengthTrend = if (!premium) null else {
                        relevantes.map { id ->
                            val pontos = ProgressPolicy.evolucao(series, id)
                            ExerciseTrendDto(
                                exerciseId = id.toString(),
                                name = nome(id),
                                points = pontos.map { TrendPointDto(it.semana.toString(), it.e1rm) },
                                changePercent = variacao(pontos),
                            )
                        }
                    },
                    setsByMuscle = if (!premium) null else {
                        val v = ProgressPolicy.seriesPorGrupo(
                            series = series,
                            musculos = catalogo.mapValues { (_, e) -> e.musculosPrimarios },
                            semanas = SEMANAS,
                        )
                        MuscleVolumeDto(byMuscle = v.porGrupo, unclassified = v.semClassificacao)
                    },
                    analysisLocked = !premium,
                )
            }
        }
    }

    private fun variacao(pontos: List<ProgressPolicy.PontoDeEvolucao>): Double {
        if (pontos.size < 2) return 0.0
        val primeiro = pontos.first().e1rm
        if (primeiro <= 0.0) return 0.0
        return (pontos.last().e1rm - primeiro) / primeiro * 100.0
    }
}
