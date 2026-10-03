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
import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import dev.rafael.core.result.asFailure
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.program.db.ProgramRepository
import dev.rafael.server.features.program.models.Program
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.session.models.WorkoutSession
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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
    private val programs: ProgramRepository,
    private val clock: Clock = Clock.System,
) {
    /**
     * O recorte pedido. Tipo fechado em vez de tres parametros anulaveis: "programa nulo mas
     * faixa preenchida" nao e estado valido, e com parametros soltos ele compila.
     */
    sealed interface Filtro {
        /** Tudo, eixo de calendario — o que a tela abre. */
        data object Todos : Filtro

        /** Sessao sem programa, ou de programa que foi apagado. Ver `ProgressDto.hasUnassigned`. */
        data object Avulsos : Filtro

        /** Um programa, com faixa opcional de semanas DELE. */
        data class DoPrograma(val programId: Uuid, val de: Int? = null, val ate: Int? = null) : Filtro
    }

    companion object {
        /** Janela da analise. Oito semanas cobrem um mesociclo inteiro com deload. */
        const val SEMANAS = 8

        /** Linhas no grafico de evolucao. Mais que tres vira emaranhado em tela de celular. */
        const val EXERCICIOS_NO_GRAFICO = 3
    }

    suspend fun forUser(
        firebaseUid: String,
        email: String?,
        idioma: Idioma,
        filtro: Filtro = Filtro.Todos,
    ): AppResult<ProgressDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            sessions.listByUser(user.id).flatMap { historico ->
                // Os programas do usuario sao no maximo 10 (ProgramLimits.PREMIUM_TOTAL_LIMIT),
                // entao uma consulta resolve as tres perguntas: quais existem (para separar o
                // apagado do avulso), qual o started_at do filtrado, e o que oferecer no filtro.
                programs.findAllByUser(user.id).flatMap { meusProgramas ->
                    montar(historico, meusProgramas, user.isPremium, idioma, filtro)
                }
            }
        }

    private suspend fun montar(
        historico: List<WorkoutSession>,
        meusProgramas: List<Program>,
        premium: Boolean,
        idioma: Idioma,
        filtro: Filtro,
    ): AppResult<ProgressDto> {
        val existentes = meusProgramas.associateBy { it.id }

        // O que o filtro pode OFERECER. So sessao valida conta: programa em que a pessoa criou
        // treino mas nunca executou nao vira opcao que abre um grafico vazio.
        val validas = historico.filter { s -> s.sets.any { it.done } }
        val comPrograma = validas.mapNotNull { it.programId }.toSet()
        val disponiveis = comPrograma.filter { it in existentes }
        val temAvulsas = validas.any { it.programId == null || it.programId !in existentes }

        // Programa pedido: `findAllByUser` ja filtrou por dono, entao "nao esta aqui" cobre
        // "nao existe" e "nao e seu" — mesma decisao do PROGRAMA_NAO_EXISTE, que nao distingue
        // os dois de proposito.
        val programa = (filtro as? Filtro.DoPrograma)?.let { existentes[it.programId] }
        if (filtro is Filtro.DoPrograma && programa == null) {
            return AppError.NotFound(
                "Programa não encontrado.",
                ErrorCodes.PROGRAMA_NAO_EXISTE,
            ).asFailure()
        }

        val doFiltro = when (filtro) {
            is Filtro.Todos -> historico
            is Filtro.Avulsos -> historico.filter { it.programId == null || it.programId !in existentes }
            is Filtro.DoPrograma -> historico.filter { it.programId == filtro.programId }
        }

        val todasAsSeries = doFiltro.flatMap { sessao ->
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

        // Faixa de semanas DO PROGRAMA. O teto nao e `durationWeeks` sozinho: a janela declarada
        // e um PLANO, e quem continua treinando depois dela nao pode ver o proprio historico
        // sumir do grafico. Por isso o maior entre a duracao e a ultima semana com dado.
        val inicio = programa?.startedAt?.date
        val ultimaSemana = if (inicio == null) null else {
            maxOf(
                programa.durationWeeks,
                todasAsSeries.maxOfOrNull { ProgressPolicy.semanaDoPrograma(it.data, inicio) } ?: 1,
            )
        }
        val faixa = if (filtro is Filtro.DoPrograma && inicio != null && ultimaSemana != null) {
            val de = (filtro.de ?: 1).coerceIn(1, ultimaSemana)
            val ate = (filtro.ate ?: ultimaSemana).coerceIn(de, ultimaSemana)
            de to ate
        } else {
            null
        }

        // UM recorte, usado por todos os blocos. Recortar bloco a bloco e como quatro recortes
        // discordam sobre o que e "a faixa".
        val series = if (faixa != null && inicio != null) {
            ProgressPolicy.naFaixaDoPrograma(todasAsSeries, inicio, faixa.first, faixa.second)
        } else {
            todasAsSeries
        }

        // Treino conta como treino mesmo sem carga: calistenia nao deixa de ser sessao so porque
        // nao entra no grafico. Mesmo criterio do StatsService (ao menos uma serie feita).
        //
        // ⚠️ Conta pela DATA, nao pelas series recortadas: a sessao de peso corporal nao produz
        // nenhuma `SerieFeita`, entao derivar a contagem de `series` apagaria justamente o treino
        // que a regra acima manda contar. E tem de obedecer a faixa — tonelagem de 5 semanas ao
        // lado da contagem do programa inteiro e o mesmo defeito do rotulo "esta semana" com
        // faixa: dois numeros vizinhos medindo periodos diferentes, sem nada dizendo isso.
        val sessoesDoRecorte = if (faixa != null && inicio != null) {
            doFiltro.filter {
                ProgressPolicy.semanaDoPrograma(it.finishedAt.date, inicio) in faixa.first..faixa.second
            }
        } else {
            doFiltro
        }
        val sessoesValidas = sessoesDoRecorte.count { s -> s.sets.any { it.done } }

        val vazio = ProgressDto(
            totalKg = 0.0,
            totalSessions = sessoesValidas,
            analysisLocked = !premium,
            availablePrograms = disponiveis.map { it.toString() },
            hasUnassigned = temAvulsas,
            programWeeks = ultimaSemana,
            fromWeek = faixa?.first,
            toWeek = faixa?.second,
        )
        if (series.isEmpty()) return vazio.asSuccess()

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
                val semanasDaJanela = if (faixa != null) faixa.second - faixa.first + 1 else SEMANAS

                vazio.copy(
                    totalKg = ProgressPolicy.tonelagem(series),
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
                        if (faixa != null && inicio != null) {
                            ProgressPolicy.porSemanaDoPrograma(series, inicio, faixa.first, faixa.second)
                                .map { s ->
                                    WeeklyLoadDto(
                                        // A data da segunda ainda viaja: o toque na barra mostra
                                        // "semana 5" e a data, e uma sem a outra deixa a pessoa
                                        // sem saber QUANDO foi a semana 5 dela.
                                        weekStart = inicio.plus(DatePeriod(days = (s.semana - 1) * 7)).toString(),
                                        kg = s.kg,
                                        weekNumber = s.semana,
                                    )
                                }
                        } else {
                            ProgressPolicy.porSemana(series, hoje, SEMANAS)
                                .map { WeeklyLoadDto(it.semana.toString(), it.kg) }
                        }
                    },
                    strengthTrend = if (!premium) null else {
                        relevantes.map { id ->
                            val pontos = if (inicio != null) {
                                ProgressPolicy.evolucaoNoPrograma(series, id, inicio).map {
                                    TrendPointDto(
                                        weekStart = inicio.plus(DatePeriod(days = (it.semana - 1) * 7)).toString(),
                                        estimated1rm = it.e1rm,
                                        weekNumber = it.semana,
                                    )
                                }
                            } else {
                                ProgressPolicy.evolucao(series, id).map {
                                    TrendPointDto(it.semana.toString(), it.e1rm)
                                }
                            }
                            ExerciseTrendDto(
                                exerciseId = id.toString(),
                                name = nome(id),
                                points = pontos,
                                changePercent = variacao(pontos.map { it.estimated1rm }),
                            )
                        }
                    },
                    setsByMuscle = if (!premium) null else {
                        val v = ProgressPolicy.seriesPorGrupo(
                            series = series,
                            musculos = catalogo.mapValues { (_, e) -> e.musculosPrimarios },
                            // A media acompanha a JANELA: num recorte de 4 semanas, dividir por 8
                            // diria metade do volume real e a pessoa concluiria que treina pouco.
                            semanas = semanasDaJanela,
                        )
                        MuscleVolumeDto(byMuscle = v.porGrupo, unclassified = v.semClassificacao)
                    },
                )
            }
        }
    }

    /**
     * Variacao do primeiro ao ultimo ponto, em %.
     *
     * Recebe os VALORES, e nao os pontos: com a J.3 o ponto passou a ter duas formas (data ou
     * semana do programa), e a conta e a mesma nas duas. Tipar pela forma obrigaria a duplicar
     * a funcao para uma diferenca que ela nao usa.
     */
    private fun variacao(e1rms: List<Double>): Double {
        if (e1rms.size < 2) return 0.0
        val primeiro = e1rms.first()
        if (primeiro <= 0.0) return 0.0
        return (e1rms.last() - primeiro) / primeiro * 100.0
    }
}
