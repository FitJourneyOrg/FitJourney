package dev.rafael.server.features.stats

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.stats.ExerciseDeltaDto
import dev.rafael.contract.stats.ExerciseSummaryDto
import dev.rafael.contract.stats.JanelasDeProgresso
import dev.rafael.contract.stats.ExerciseTrendDto
import dev.rafael.contract.stats.MuscleVolumeDto
import dev.rafael.contract.stats.ExercicioDetalheDto
import dev.rafael.contract.stats.PontoDeSessaoDto
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
 * Os blocos pagos nem sequer sao CALCULADOS para quem e free - nao ha valor a vazar por descuido
 * de serializacao. O `analysisLocked` diz ao cliente que o nulo e portao, nao falta de dado (ver
 * KDoc do [ProgressDto]).
 *
 * ## O que e pago mudou na J.4.1: a PROFUNDIDADE, nao o grafico
 *
 * A carga por semana saiu do portao. Ela era 100% paga, o que significava que ninguem free via
 * oito semanas — e por isso "janela longa" nao vendia nada: nao existia quem visse 8 e quisesse
 * 26. Agora o free ve a janela curta e paga pela longa (26/52), pelo 1RM estimado e pelo volume
 * por grupo. Value-first (#23): a tela do free deixou de ser so um paywall.
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
        /**
         * Tudo, eixo de calendario — o que a tela abre, com a janela em semanas.
         *
         * A janela mora AQUI, e nao num parametro ao lado, porque o recorte tem um eixo de tempo
         * e so um: em calendario ele e "as ultimas N semanas", no programa e "da semana X a Y". Um
         * parametro solto permitiria pedir janela de calendario junto com faixa de programa, que
         * nao e estado valido — e, no cliente, a janela faz parte da chave do cache de graca.
         */
        data class Todos(val semanas: Int = JANELA_PADRAO) : Filtro

        /** Sessao sem programa, ou de programa que foi apagado. Ver `ProgressDto.hasUnassigned`. */
        data class Avulsos(val semanas: Int = JANELA_PADRAO) : Filtro

        /** Um programa, com faixa opcional de semanas DELE. A faixa E a janela aqui. */
        data class DoPrograma(val programId: Uuid, val de: Int? = null, val ate: Int? = null) : Filtro
    }

    companion object {
        // Vem do CONTRATO: o cliente desenha um chip por janela oferecida, e as duas listas
        // divergiriam na primeira vez que alguem acrescentasse uma.
        const val JANELA_PADRAO = JanelasDeProgresso.PADRAO
        const val JANELA_FREE = JanelasDeProgresso.FREE
        val JANELAS = JanelasDeProgresso.OFERECIDAS

        /** Linhas no grafico de evolucao. Mais que tres vira emaranhado em tela de celular. */
        const val EXERCICIOS_NO_GRAFICO = 3
    }

    /**
     * A janela que vai valer, dado o que foi pedido e o plano.
     *
     * Encaixa em silencio, como a faixa do programa: janela e ajuste de VISUALIZACAO. Valor fora
     * da lista cai no padrao em vez de virar 400 — cliente velho pedindo 12 nao pode quebrar.
     *
     * ⚠️ O free e limitado AQUI, no servidor, mesmo que a tela ja nao deixe escolher: o cliente
     * nao e autoridade (#16). E o [ProgressDto.weeksWindow] devolve a janela APLICADA, porque
     * controle dizendo 52 com eixo desenhando 8 e a mesma mentira do rotulo "esta semana".
     */
    private fun janelaValida(pedida: Int, premium: Boolean): Int {
        val naLista = if (pedida in JANELAS) pedida else JANELA_PADRAO
        return if (premium) naLista else minOf(naLista, JANELA_FREE)
    }

    suspend fun forUser(
        firebaseUid: String,
        email: String?,
        idioma: Idioma,
        filtro: Filtro = Filtro.Todos(),
        exercicios: List<Uuid> = emptyList(),
    ): AppResult<ProgressDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            sessions.listByUser(user.id).flatMap { historico ->
                // Os programas do usuario sao no maximo 3 (ProgramLimits.PREMIUM_TOTAL_LIMIT),
                // entao uma consulta resolve as tres perguntas: quais existem (para separar o
                // apagado do avulso), qual o started_at do filtrado, e o que oferecer no filtro.
                programs.findAllByUser(user.id).flatMap { meusProgramas ->
                    montar(historico, meusProgramas, user.isPremium, idioma, filtro, exercicios)
                }
            }
        }

    /**
     * Detalhe de UM exercicio dentro de UM programa (J.5) — a tela que "ver detalhado" abre a
     * partir da lista de exercicios do recorte de programa. Ver KDoc do [ExercicioDetalheDto]
     * para o porque do 403 em vez de campo nulo, e do programa INTEIRO sem faixa.
     */
    suspend fun detalheDoExercicio(
        firebaseUid: String,
        email: String?,
        idioma: Idioma,
        programId: Uuid,
        exercicioId: Uuid,
    ): AppResult<ExercicioDetalheDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            sessions.listByUser(user.id).flatMap { historico ->
                programs.findAllByUser(user.id).flatMap { meusProgramas ->
                    montarDetalhe(user.isPremium, idioma, programId, exercicioId, historico, meusProgramas)
                }
            }
        }

    private suspend fun montarDetalhe(
        premium: Boolean,
        idioma: Idioma,
        programId: Uuid,
        exercicioId: Uuid,
        historico: List<WorkoutSession>,
        meusProgramas: List<Program>,
    ): AppResult<ExercicioDetalheDto> {
        // Autoridade do servidor (#16): o cliente so chega aqui vindo de uma lista que ja e
        // paga, mas quem decide de verdade e o backend. Tela 100% paga, sem bloco gratis pra
        // misturar numa resposta so — por isso 403, nao campo nulo.
        if (!premium) {
            return AppError.Forbidden(
                "Veja a evolução detalhada de cada exercício assinando o premium.",
                ErrorCodes.ENTITLEMENT_REQUIRED,
            ).asFailure()
        }

        // `findAllByUser` ja filtrou por dono — "nao esta aqui" cobre "nao existe" e "nao e seu",
        // mesma decisao do resto da fatia (ver `montar`).
        val programa = meusProgramas.firstOrNull { it.id == programId }
            ?: return AppError.NotFound(
                "Programa não encontrado.",
                ErrorCodes.PROGRAMA_NAO_EXISTE,
            ).asFailure()

        return exercises.paraAnalise(listOf(exercicioId)).flatMap { catalogo ->
            val doCatalogo = catalogo[exercicioId]
                ?: return@flatMap AppError.NotFound(
                    "Exercício não encontrado.",
                    ErrorCodes.EXERCICIO_NAO_EXISTE,
                ).asFailure()

            exercises.nomesTraduzidos(listOf(exercicioId), idioma).map { traduzidos ->
                val nome = traduzidos[exercicioId] ?: doCatalogo.nomePiso

                // Mesmo achatamento do `montar`, so que recortado pelo PROGRAMA direto — sem
                // faixa, sem janela: a tela pede o historico inteiro dele.
                val series = historico
                    .filter { it.programId == programId }
                    .flatMap { sessao ->
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

                val pontos = ProgressPolicy.evolucaoPorSessaoNoPrograma(
                    series,
                    exercicioId,
                    programa.startedAt.date,
                )

                ExercicioDetalheDto(
                    exerciseId = exercicioId.toString(),
                    name = nome,
                    points = pontos.map {
                        PontoDeSessaoDto(
                            date = it.data.toString(),
                            weekNumber = it.semana,
                            estimated1rm = it.e1rm,
                            volumeKg = it.volumeKg,
                            kg = it.kg,
                            reps = it.reps,
                            sets = it.series,
                        )
                    },
                )
            }
        }
    }

    private suspend fun montar(
        historico: List<WorkoutSession>,
        meusProgramas: List<Program>,
        premium: Boolean,
        idioma: Idioma,
        filtro: Filtro,
        exercicios: List<Uuid>,
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

        val hoje = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        // A janela pedida so existe no eixo de calendario: no programa, a faixa E a janela.
        val janela = when (filtro) {
            is Filtro.Todos -> janelaValida(filtro.semanas, premium)
            is Filtro.Avulsos -> janelaValida(filtro.semanas, premium)
            is Filtro.DoPrograma -> null
        }

        // ⚠️ DUAS janelas declaradas, de proposito — e a unica razao pela qual elas divergem:
        //
        // | bloco | janela |
        // |---|---|
        // | totais, "desde", comparacao | TODO o historico do recorte |
        // | carga/semana, 1RM, series por grupo | a janela escolhida |
        //
        // Os totais sao de sempre porque `sinceDate` e a primeira sessao da vida e as conquistas
        // de carga acumulam; a comparacao e sobre o ULTIMO treino, e janela nenhuma muda qual foi.
        // O que nao pode acontecer de novo e cada GRAFICO escolher a sua: era o que fazia as
        // barras mostrarem 2 meses e a linha de 1RM mostrar 2 anos, na mesma tela, sem aviso.
        val seriesDosGraficos = if (janela == null) {
            series
        } else {
            ProgressPolicy.naJanelaDeCalendario(series, hoje, janela)
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
            weeksWindow = janela,
        )
        if (series.isEmpty()) return vazio.asSuccess()

        val comparacao = ProgressPolicy.ultimoVsAnterior(series)
        // TODOS os exercicios da janela, em ordem de relevancia — a lista paga (J.4.2). Sai da
        // JANELA, nao do historico: "mais relevante" tem de significar relevante no periodo que
        // esta na tela, senao o agachamento de marco apareceria na janela de 8 semanas em que a
        // pessoa nem agachou.
        val ordenados = if (premium) ProgressPolicy.porRelevancia(seriesDosGraficos) else emptyList()

        // Quais ganham LINHA. A escolha da pessoa vence o padrao, e o padrao e a cabeca da lista.
        //
        // ⚠️ Id que nao esta na janela e DESCARTADO em silencio, nao recusado: ele nao tem ponto
        // nenhum para plotar, e linha vazia e pior que linha ausente. Diferente do `programId`,
        // que vira 400 — esse muda QUAL dado responde, a selecao so muda o que e desenhado. O
        // `strengthTrend` que volta ja diz quais entraram, entao a resposta se autodescreve.
        val escolhidos = exercicios.filter { it in ordenados }.take(EXERCICIOS_NO_GRAFICO)
        val relevantes = escolhidos.ifEmpty { ordenados.take(EXERCICIOS_NO_GRAFICO) }

        // Um unico par de consultas: leitura crua + traducao na borda, sobre os ids que importam.
        // A lista paga mostra NOME de todos, entao o premium traduz `ordenados` inteiro; o free
        // continua pagando so pelos ids da comparacao.
        val idsDeNome = (comparacao?.deltaPorExercicio?.keys.orEmpty() + ordenados + relevantes).toList()
        val idsDeMusculo = if (premium) seriesDosGraficos.map { it.exercicioId }.distinct() else emptyList()

        return exercises.paraAnalise((idsDeNome + idsDeMusculo).distinct()).flatMap { catalogo ->
            exercises.nomesTraduzidos(idsDeNome, idioma).map { traduzidos ->
                fun nome(id: Uuid): String = traduzidos[id] ?: catalogo[id]?.nomePiso.orEmpty()

                /** 1RM estimado por semana, no eixo do recorte (data ou semana do programa). */
                fun pontosDe(id: Uuid): List<TrendPointDto> = if (inicio != null) {
                    ProgressPolicy.evolucaoNoPrograma(seriesDosGraficos, id, inicio).map {
                        TrendPointDto(
                            weekStart = inicio.plus(DatePeriod(days = (it.semana - 1) * 7)).toString(),
                            estimated1rm = it.e1rm,
                            weekNumber = it.semana,
                        )
                    }
                } else {
                    ProgressPolicy.evolucao(seriesDosGraficos, id).map {
                        TrendPointDto(it.semana.toString(), it.e1rm)
                    }
                }

                val pontosPor = ordenados.associateWith { pontosDe(it) }

                val semanasDaJanela =
                    if (faixa != null) faixa.second - faixa.first + 1 else janela ?: JANELA_PADRAO

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
                    // SEM portao desde a J.4.1: o free ve a janela curta e paga pela longa.
                    weeklyLoad = run {
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
                            ProgressPolicy.porSemana(seriesDosGraficos, hoje, janela ?: JANELA_PADRAO)
                                .map { WeeklyLoadDto(it.semana.toString(), it.kg) }
                        }
                    },
                    // Os pontos de cada exercicio saem de `pontosPor`, calculado UMA vez acima:
                    // o grafico e a lista respondem a mesma pergunta, e calcular duas vezes e
                    // como ter duas definicoes de "1RM da semana".
                    strengthTrend = if (!premium) null else {
                        relevantes.map { id ->
                            val pontos = pontosPor[id].orEmpty()
                            ExerciseTrendDto(
                                exerciseId = id.toString(),
                                name = nome(id),
                                points = pontos,
                                changePercent = variacao(pontos.map { it.estimated1rm }),
                            )
                        }
                    },
                    exerciseSummary = if (!premium) null else {
                        val contagem = ProgressPolicy.contagemPorExercicio(seriesDosGraficos)
                        ordenados.map { id ->
                            val pontos = pontosPor[id].orEmpty()
                            ExerciseSummaryDto(
                                exerciseId = id.toString(),
                                name = nome(id),
                                // A ULTIMA semana em que apareceu, nao a media do periodo: a
                                // pergunta e "onde estou hoje".
                                current1rm = pontos.lastOrNull()?.estimated1rm ?: 0.0,
                                changePercent = variacao(pontos.map { it.estimated1rm }),
                                sets = contagem[id] ?: 0,
                                weeks = pontos.size,
                            )
                        }
                    },
                    setsByMuscle = if (!premium) null else {
                        val v = ProgressPolicy.seriesPorGrupo(
                            series = seriesDosGraficos,
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
