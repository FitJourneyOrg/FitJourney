package dev.rafael.server.features.stats

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.ExercicioParaAnalise
import dev.rafael.server.features.program.db.ProgramRepository
import dev.rafael.server.features.program.models.Program
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.session.models.SetLog
import dev.rafael.server.features.session.models.WorkoutSession
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertIs as assertIsTipo
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Orquestracao da analise (J.2): o portao de plano, o estado vazio da calistenia e a
 * traducao na borda. A matematica tem teste proprio no [ProgressPolicyTest].
 */
class ProgressServiceTest {

    private val agachamento = Uuid.parse("00000001-0000-0000-0000-000000000000")
    private val prancha = Uuid.parse("00000002-0000-0000-0000-000000000000")

    /** 2026-10-01, uma quinta. */
    private val relogio: Clock = object : Clock {
        override fun now() = Instant.parse("2026-10-01T10:00:00Z")
    }

    private fun user(premium: Boolean) = User(
        id = Uuid.random(), firebaseUid = "fb", email = null,
        isPremium = premium, displayName = "Atleta-teste", code = "TESTE234",
    )

    private class FakeUserRepo(private val u: User) : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) = AppResult.Success<User?>(u)
        override suspend fun findById(userId: Uuid) = AppResult.Success<User?>(u)
        override suspend fun findByCode(code: String) = AppResult.Success<User?>(u)
        override suspend fun updateCode(userId: Uuid, code: String) = AppResult.Success<User?>(u)
        override suspend fun create(id: Uuid, firebaseUid: String, email: String?, displayName: String, code: String) =
            AppResult.Success(u)
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = AppResult.Success<User?>(u)
        override suspend fun setActiveProgram(userId: Uuid, programId: Uuid?) = AppResult.Success<User?>(u)
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) = AppResult.Success<User?>(u)
        override suspend fun updateIdioma(userId: Uuid, idioma: Idioma) = AppResult.Success<User?>(u)
    }

    private class FakeSessions(private val historico: List<WorkoutSession>) : SessionRepository {
        override suspend fun save(session: WorkoutSession) = Unit.asSuccess()
        override suspend fun listByUser(userId: Uuid) = historico.asSuccess()
    }

    /** Espia QUAIS ids foram pedidos — e o que prova que o free nao paga consulta de musculo. */
    private class FakeCatalogo(
        private val dados: Map<Uuid, ExercicioParaAnalise>,
        private val traducoes: Map<Uuid, String> = emptyMap(),
    ) : ExerciseRepository {
        var idsPedidos: List<Uuid> = emptyList()
        override suspend fun paraAnalise(ids: List<Uuid>): AppResult<Map<Uuid, ExercicioParaAnalise>> {
            idsPedidos = ids
            return dados.filterKeys { it in ids }.asSuccess()
        }
        override suspend fun nomesTraduzidos(ids: List<Uuid>, idioma: Idioma) =
            (if (idioma == Idioma.PADRAO) emptyMap() else traducoes.filterKeys { it in ids }).asSuccess()
        override suspend fun findAll(idioma: Idioma) = error("não usado")
        override suspend fun findByCategory(category: dev.rafael.contract.exercise.ExerciseCategory, idioma: Idioma) = error("não usado")
        override suspend fun findById(id: Uuid, idioma: Idioma) = error("não usado")
        override suspend fun existsByIds(ids: List<Uuid>) = error("não usado")
    }

    private fun serie(ex: Uuid, kg: Double?, reps: Int = 10, done: Boolean = true, ordem: Int = 0) =
        SetLog(ex, ordem, 0, reps, reps, kg, done)

    private fun sessao(dia: Int, treino: String, sets: List<SetLog>, programId: Uuid? = null) = WorkoutSession(
        id = Uuid.random(), userId = Uuid.random(), programId = programId, workoutId = null,
        workoutName = treino,
        startedAt = LocalDateTime(2026, 9, dia, 18, 0),
        finishedAt = LocalDateTime(2026, 9, dia, 19, 0),
        sets = sets,
    )

    /** Sessao em data arbitraria — o [sessao] fixa setembro, e janela de 26 semanas sai do mes. */
    private fun sessaoEm(iso: String, treino: String, sets: List<SetLog>) = WorkoutSession(
        id = Uuid.random(), userId = Uuid.random(), programId = null, workoutId = null,
        workoutName = treino,
        startedAt = LocalDateTime.parse(iso + "T18:00:00"),
        finishedAt = LocalDateTime.parse(iso + "T19:00:00"),
        sets = sets,
    )

    private val catalogo = mapOf(
        agachamento to ExercicioParaAnalise("Agachamento Livre com Barra", listOf(MuscleGroup.LEGS)),
        prancha to ExercicioParaAnalise("Prancha Isométrica", emptyList()),
    )

    /** Programas que o usuario AINDA tem. O que nao esta aqui conta como apagado (J.3). */
    private class FakeProgramas(private val lista: List<Program>) : ProgramRepository {
        override suspend fun findAllByUser(userId: Uuid) = lista.asSuccess()
        override suspend fun counts(userId: Uuid) = error("não usado")
        override suspend fun createForUser(userId: Uuid, program: Program) = error("não usado")
        override suspend fun findByIdForUser(userId: Uuid, programId: Uuid) = error("não usado")
        override suspend fun rename(userId: Uuid, programId: Uuid, name: String) = error("não usado")
        override suspend fun delete(userId: Uuid, programId: Uuid) = error("não usado")
        override suspend fun setSchedule(userId: Uuid, programId: Uuid, schedule: Map<Uuid, Int>) = error("não usado")
    }

    private fun programa(id: Uuid, inicio: String, semanas: Int = 8) = Program(
        id = id, userId = Uuid.random(), name = "", origin = WorkoutOrigin.AI,
        daysPerWeek = 4, split = null, focusMuscles = emptyList(), locked = false,
        workouts = emptyList(),
        createdAt = LocalDateTime(2026, 8, 1, 10, 0),
        updatedAt = LocalDateTime(2026, 8, 1, 10, 0),
        durationWeeks = semanas,
        startedAt = LocalDateTime.parse(inicio + "T10:00:00"),
    )

    private fun servico(
        premium: Boolean,
        historico: List<WorkoutSession>,
        catalogoFake: FakeCatalogo = FakeCatalogo(catalogo),
        programas: List<Program> = emptyList(),
    ) = ProgressService(
        userService = UserService(FakeUserRepo(user(premium))),
        sessions = FakeSessions(historico),
        exercises = catalogoFake,
        programs = FakeProgramas(programas),
        clock = relogio,
    ) to catalogoFake

    private fun <T> ok(r: AppResult<T>): T = assertIsTipo<AppResult.Success<T>>(r).value

    private val duasSessoesIguais = listOf(
        sessao(21, "Inferior A", listOf(serie(agachamento, 60.0))),
        sessao(28, "Inferior A", listOf(serie(agachamento, 65.0))),
    )

    // ---- o portao ----------------------------------------------------------

    /**
     * ⭐ O que e pago e a PROFUNDIDADE, nao o grafico (J.4.1).
     *
     * A carga por semana saiu do portao. Antes ela era 100% paga, e por isso "janela longa" nao
     * vendia nada: nao existia quem visse 8 semanas e quisesse 26. O `analysisLocked` continua
     * true — ele diz que a ANALISE esta trancada, e o nulo dos outros dois e portao, nao falta
     * de dado.
     */
    @Test
    fun `free recebe a carga por semana, mas nao os blocos pagos`() = runBlocking {
        val (s, _) = servico(premium = false, historico = duasSessoesIguais)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value

        assertEquals(ProgressService.JANELA_FREE, dto.weeklyLoad?.size, "o free perdeu o grafico de novo")
        assertNull(dto.strengthTrend)
        assertNull(dto.setsByMuscle)
        assertTrue(dto.analysisLocked)
    }

    @Test
    fun `free ainda ve o que e gratis - total, treinos e ultimo vs anterior`() = runBlocking {
        val (s, _) = servico(premium = false, historico = duasSessoesIguais)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value

        assertEquals(650.0 + 600.0, dto.totalKg)
        assertEquals(2, dto.totalSessions)
        assertNotNull(dto.lastVsPrevious)
        assertEquals(50.0, dto.lastVsPrevious!!.exercises.first().deltaKg)
    }

    @Test
    fun `free nem paga a consulta de musculo - so os ids de nome sao pedidos`() = runBlocking {
        val espiao = FakeCatalogo(catalogo)
        val (s, _) = servico(premium = false, historico = duasSessoesIguais, catalogoFake = espiao)
        s.forUser("fb", null, Idioma.PADRAO)

        // só o agachamento, porque ele aparece na comparação; nenhum id extra para volume
        assertEquals(listOf(agachamento), espiao.idsPedidos)
    }

    @Test
    fun `premium recebe os tres blocos`() = runBlocking {
        val (s, _) = servico(premium = true, historico = duasSessoesIguais)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value

        assertEquals(ProgressService.JANELA_PADRAO, dto.weeklyLoad?.size)
        assertEquals(1, dto.strengthTrend?.size)
        assertEquals(1.0 / ProgressService.JANELA_PADRAO * 2, dto.setsByMuscle?.byMuscle?.get(MuscleGroup.LEGS))
        assertTrue(!dto.analysisLocked)
    }

    // ---- janela de calendario (J.4.1) --------------------------------------

    /** Relogio em 2026-10-01: janela de 8 comeca em 2026-08-10; a de 26, em 2026-04-06. */
    private val antesEDepois = listOf(
        sessaoEm("2026-06-15", "Inferior A", List(4) { serie(agachamento, 60.0, ordem = it) }),
        sessaoEm("2026-09-28", "Inferior A", List(2) { serie(agachamento, 65.0, ordem = it) }),
    )

    /**
     * ⭐ O defeito que a J.4.1 veio consertar.
     *
     * `seriesPorGrupo` recebia o historico INTEIRO e dividia por 8. Quem treina ha seis meses
     * tinha a media semanal inflada — e esse e justamente o bloco cujo trabalho inteiro e ser um
     * diagnostico. Passou na validacao original porque o seed tem exatamente 8 semanas: o numero
     * certo por coincidencia.
     */
    @Test
    fun `a media de series divide pela janela, e nao pelo historico inteiro`() = runBlocking {
        val (s, _) = servico(premium = true, historico = antesEDepois)

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.Todos(8)))

        // So as 2 series de 28/09 entram na janela: 2/8. Com o bug eram 6/8.
        assertEquals(2.0 / 8, dto.setsByMuscle?.byMuscle?.get(MuscleGroup.LEGS))
        assertEquals(
            4 * 10 * 60.0 + 2 * 10 * 65.0, dto.totalKg,
            "o TOTAL e de sempre: 'desde' e a primeira sessao da vida e as conquistas acumulam",
        )
    }

    /**
     * ⭐ O segundo: as barras mostravam 2 meses e a linha de 1RM mostrava o historico inteiro, na
     * mesma tela, sem nada dizendo isso. `evolucao` nao recortava nada.
     */
    @Test
    fun `a linha de 1RM respeita a mesma janela das barras`() = runBlocking {
        val (s, _) = servico(premium = true, historico = antesEDepois)

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.Todos(8)))

        assertEquals(1, dto.strengthTrend?.first()?.points?.size, "plotou ponto de fora da janela")
        assertEquals(8, dto.weeklyLoad?.size)
    }

    @Test
    fun `janela de 26 alcanca o que a de 8 cortava`() = runBlocking {
        val (s, _) = servico(premium = true, historico = antesEDepois)

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.Todos(26)))

        assertEquals(26, dto.weeksWindow)
        assertEquals(26, dto.weeklyLoad?.size)
        assertEquals(2, dto.strengthTrend?.first()?.points?.size, "a sessao de junho tinha de entrar")
        assertEquals(6.0 / 26, dto.setsByMuscle?.byMuscle?.get(MuscleGroup.LEGS), "6 series em 26 semanas")
    }

    /**
     * Caminho de falha do portao: o limite do free e no SERVIDOR.
     *
     * A tela nao deixa escolher 52 no free, mas cliente nao e autoridade (#16). E o DTO devolve a
     * janela APLICADA, nao a pedida — seletor marcando 52 com o eixo desenhando 8 e a mesma
     * mentira do rotulo "esta semana" numa faixa que terminou semanas atras.
     */
    @Test
    fun `free pedindo 52 semanas recebe 8, e o DTO diz 8`() = runBlocking {
        val (s, _) = servico(premium = false, historico = antesEDepois)

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.Todos(52)))

        assertEquals(8, dto.weeksWindow, "o cliente pediria 52 e a tela mostraria 52 em cima de 8")
        assertEquals(8, dto.weeklyLoad?.size)
    }

    /**
     * Caminho de falha da entrada: janela fora da lista encaixa, nao recusa.
     *
     * Janela e ajuste de VISUALIZACAO — mesma decisao da faixa do programa. Cliente velho pedindo
     * 12 nao pode receber erro. Id de programa invalido continua 400, porque esse muda QUAL dado
     * responde.
     */
    @Test
    fun `janela fora da lista cai no padrao, e nao em erro`() = runBlocking {
        val (s, _) = servico(premium = true, historico = antesEDepois)

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.Todos(12)))

        assertEquals(ProgressService.JANELA_PADRAO, dto.weeksWindow)
        assertEquals(ProgressService.JANELA_PADRAO, dto.weeklyLoad?.size)
    }

    @Test
    fun `no recorte de programa a janela e a faixa, e weeksWindow vem nulo`() = runBlocking {
        val (s, _) = servico(
            premium = true,
            historico = listOf(sessao(28, "Inferior X", List(2) { serie(agachamento, 60.0) }, programId = progX)),
            programas = listOf(programa(progX, "2026-09-21")),
        )

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX)))

        assertNull(dto.weeksWindow, "duas janelas concorrentes no mesmo recorte")
        assertEquals(1, dto.fromWeek)
    }

    // ---- calistenia --------------------------------------------------------

    @Test
    fun `historico so de peso corporal da zero sem trancar nada para premium`() = runBlocking {
        val so = listOf(sessao(28, "Core", listOf(serie(prancha, kg = null))))
        val (s, _) = servico(premium = true, historico = so)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value

        assertEquals(0.0, dto.totalKg)
        assertEquals(1, dto.totalSessions)   // o treino aconteceu, mesmo sem carga
        assertNull(dto.sinceDate)
        assertTrue(!dto.analysisLocked)      // vazio por falta de dado, nao por plano
    }

    @Test
    fun `serie nao concluida nao entra na carga`() = runBlocking {
        val h = listOf(sessao(28, "Inferior A", listOf(serie(agachamento, 60.0, done = false))))
        val (s, _) = servico(premium = true, historico = h)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value
        assertEquals(0.0, dto.totalKg)
    }

    // ---- traducao na borda -------------------------------------------------

    @Test
    fun `nome vem traduzido quando ha traducao no idioma pedido`() = runBlocking {
        val espiao = FakeCatalogo(catalogo, traducoes = mapOf(agachamento to "Barbell Back Squat"))
        val (s, _) = servico(premium = false, historico = duasSessoesIguais, catalogoFake = espiao)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.EN)).value

        assertEquals("Barbell Back Squat", dto.lastVsPrevious!!.exercises.first().name)
    }

    @Test
    fun `sem traducao o nome cai no piso do catalogo, nao vem vazio`() = runBlocking {
        val espiao = FakeCatalogo(catalogo, traducoes = emptyMap())
        val (s, _) = servico(premium = false, historico = duasSessoesIguais, catalogoFake = espiao)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.EN)).value

        assertEquals("Agachamento Livre com Barra", dto.lastVsPrevious!!.exercises.first().name)
    }

    // ---- comparacao --------------------------------------------------------

    @Test
    fun `treinos de nomes diferentes nao viram comparacao`() = runBlocking {
        val h = listOf(
            sessao(21, "Superior A", listOf(serie(agachamento, 60.0))),
            sessao(28, "Inferior A", listOf(serie(agachamento, 65.0))),
        )
        val (s, _) = servico(premium = false, historico = h)
        val dto = assertIs<AppResult.Success<ProgressDto>>(s.forUser("fb", null, Idioma.PADRAO)).value
        assertNull(dto.lastVsPrevious)
    }

    // ---- filtro por programa e faixa de semanas (J.3) -----------------------

    private val progX = Uuid.parse("0000000a-0000-0000-0000-000000000000")
    private val progY = Uuid.parse("0000000b-0000-0000-0000-000000000000")

    /** X e Y na MESMA semana: e exatamente o cenario que o grafico de hoje soma sem avisar. */
    private fun historicoDeDoisProgramas() = listOf(
        sessao(21, "Inferior X", List(3) { serie(agachamento, 60.0) }, programId = progX),
        sessao(22, "Inferior Y", List(3) { serie(agachamento, 20.0) }, programId = progY),
    )

    @Test
    fun `filtrar por programa deixa de fora a sessao do outro`() = runBlocking {
        val (s, _) = servico(
            premium = true,
            historico = historicoDeDoisProgramas(),
            programas = listOf(programa(progX, "2026-09-21"), programa(progY, "2026-09-21")),
        )

        val todos = ok(s.forUser("fb", null, Idioma.PADRAO)).totalKg
        val soX = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX))).totalKg

        assertEquals(2400.0, todos, "sem filtro, os dois programas somam")
        assertEquals(1800.0, soX, "com filtro, so o X")
    }

    @Test
    fun `avulsos junta sessao sem programa e sessao de programa apagado`() = runBlocking {
        val apagado = Uuid.parse("0000000c-0000-0000-0000-000000000000")
        val h = listOf(
            sessao(21, "Livre", List(2) { serie(agachamento, 50.0) }, programId = null),
            sessao(22, "Antigo", List(2) { serie(agachamento, 30.0) }, programId = apagado),
            sessao(23, "Inferior X", List(2) { serie(agachamento, 100.0) }, programId = progX),
        )
        // `apagado` NAO esta na lista de programas: foi excluido.
        val (s, _) = servico(premium = true, historico = h, programas = listOf(programa(progX, "2026-09-21")))

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.Avulsos()))

        assertEquals(1000.0 + 600.0, dto.totalKg, "o apagado conta como avulso, nao some")
        assertTrue(dto.hasUnassigned)
        assertEquals(listOf(progX.toString()), dto.availablePrograms, "programa apagado nao vira opcao")
    }

    @Test
    fun `programa que nao e meu responde NotFound, e nao analise vazia`() = runBlocking {
        // Analise vazia diria "voce nao treinou nisso", que e uma afirmacao sobre o programa de
        // outra pessoa. O findAllByUser ja filtrou por dono, entao ausencia aqui cobre "nao
        // existe" e "nao e seu" sem distinguir os dois -- mesma decisao do PROGRAMA_NAO_EXISTE.
        val (s, _) = servico(premium = true, historico = historicoDeDoisProgramas(), programas = emptyList())

        val r = s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX))

        // ⚠️ Terminar em `assertIs` quebraria a CLASSE INTEIRA: corpo de expressao cujo ultimo
        // valor nao e Unit faz o metodo deixar de ser `void`, e o JUnit recusa a classe na
        // construcao -- os 15 testes somem do relatorio como UMA falha.
        val erro = assertIsTipo<AppResult.Failure>(r).error
        assertEquals(ErrorCodes.PROGRAMA_NAO_EXISTE, assertIsTipo<AppError.NotFound>(erro).code)
    }

    @Test
    fun `com programa escolhido o eixo vira semana DELE`() = runBlocking {
        val (s, _) = servico(
            premium = true,
            historico = listOf(sessao(28, "Inferior X", List(3) { serie(agachamento, 60.0) }, programId = progX)),
            programas = listOf(programa(progX, "2026-09-21")),   // segunda; 28/09 = semana 2
        )

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX)))

        assertEquals(1, dto.fromWeek)
        assertEquals(8, dto.toWeek, "sem faixa pedida, a janela inteira do programa")
        assertEquals(8, dto.weeklyLoad?.size)
        assertEquals((1..8).toList(), dto.weeklyLoad?.map { it.weekNumber })
        assertEquals(1800.0, dto.weeklyLoad?.first { it.weekNumber == 2 }?.kg)
    }

    @Test
    fun `a faixa recorta, e a media de series acompanha a janela pedida`() = runBlocking {
        // Duas semanas com treino; pedindo 1..2, a media divide por 2 -- nao por 8.
        val h = listOf(
            sessao(21, "Inferior X", List(2) { serie(agachamento, 60.0) }, programId = progX),
            sessao(28, "Inferior X", List(2) { serie(agachamento, 60.0) }, programId = progX),
        )
        val (s, _) = servico(premium = true, historico = h, programas = listOf(programa(progX, "2026-09-21")))

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX, de = 1, ate = 2)))

        assertEquals(1 to 2, dto.fromWeek to dto.toWeek)
        assertEquals(2, dto.weeklyLoad?.size)
        assertEquals(2.0, dto.setsByMuscle?.byMuscle?.get(MuscleGroup.LEGS), "4 series em 2 semanas")
    }

    /**
     * ⭐ A contagem de treinos obedece a faixa.
     *
     * Dois numeros vizinhos no mesmo cartao medindo periodos diferentes e o defeito mais barato
     * de cometer e o mais caro de notar: ninguem desconfia de "2 treinos", so do grafico.
     */
    @Test
    fun `a contagem de treinos obedece a faixa, nao o programa inteiro`() = runBlocking {
        val h = listOf(
            sessao(21, "Inferior X", List(2) { serie(agachamento, 60.0) }, programId = progX),
            sessao(28, "Inferior X", List(2) { serie(agachamento, 60.0) }, programId = progX),
        )
        val (s, _) = servico(premium = true, historico = h, programas = listOf(programa(progX, "2026-09-21")))

        val tudo = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX)))
        val soSemana2 = ok(
            s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX, de = 2, ate = 2)),
        )

        assertEquals(2, tudo.totalSessions)
        assertEquals(1, soSemana2.totalSessions, "contou o treino da semana 1, que esta fora da faixa")
        assertEquals(1200.0, soSemana2.totalKg, "a tonelagem ja obedecia — era a contagem que nao")
    }

    /**
     * Caminho de falha do criterio: treino de peso corporal DENTRO da faixa continua contando.
     *
     * A contagem sai da data, nao das series elegiveis. Derivar de `series` seria mais curto e
     * apagaria exatamente a sessao de calistenia — que a regra manda contar como treino.
     */
    @Test
    fun `treino sem carga dentro da faixa conta como treino`() = runBlocking {
        val h = listOf(
            sessao(28, "Peso do corpo", List(3) { serie(agachamento, null) }, programId = progX),
        )
        val (s, _) = servico(premium = true, historico = h, programas = listOf(programa(progX, "2026-09-21")))

        val dto = ok(
            s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX, de = 2, ate = 2)),
        )

        assertEquals(1, dto.totalSessions)
        assertEquals(0.0, dto.totalKg, "sem carga externa nao ha tonelagem")
    }

    @Test
    fun `faixa fora da janela e encaixada, nao recusada`() = runBlocking {
        // Faixa e ajuste de VISUALIZACAO: corrigir em silencio ali e o certo. Id errado, nao --
        // esse muda qual dado responde, e vira 400 na rota.
        val (s, _) = servico(
            premium = true,
            historico = listOf(sessao(28, "Inferior X", List(2) { serie(agachamento, 60.0) }, programId = progX)),
            programas = listOf(programa(progX, "2026-09-21")),
        )

        val dto = ok(s.forUser("fb", null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(progX, de = 0, ate = 99)))

        assertEquals(1, dto.fromWeek)
        assertEquals(8, dto.toWeek)
    }

}
