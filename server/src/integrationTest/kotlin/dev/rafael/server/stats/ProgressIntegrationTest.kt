package dev.rafael.server.stats

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.Semear
import dev.rafael.server.features.exercise.db.ExerciseRepositoryImpl
import dev.rafael.server.features.program.db.ProgramRepositoryImpl
import dev.rafael.server.features.session.db.SessionRepositoryImpl
import dev.rafael.server.features.stats.ProgressService
import dev.rafael.server.features.user.db.UserRepositoryImpl
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Analise de progressao (J.2) contra Postgres REAL.
 *
 * POR QUE contra o banco, e nao mais um teste com fake:
 *
 * 1. `primary_muscles` e `array<String>` NULLABLE. Leitura de array nulo pelo driver e o tipo de
 *    coisa que compila, passa com fake e estoura em producao.
 * 2. `session_set_logs.weight_kg` tambem e nullable, e e ele que separa carga externa de peso
 *    corporal. O fake sempre devolve o que o teste mandou; o driver pode devolver outra coisa.
 * 3. O portao de plano le `users.is_premium` de verdade, nao um booleano montado a mao.
 *
 * E a licao da fatia B, de novo: os defeitos daquela bateria estavam todos FORA da logica pura.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgressIntegrationTest {

    private val exercicios = ExerciseRepositoryImpl()
    private val sessoes = SessionRepositoryImpl()
    private val usuarios = UserRepositoryImpl()
    private val programas = ProgramRepositoryImpl()
    private val progresso = ProgressService(
        userService = UserService(usuarios),
        sessions = sessoes,
        exercises = exercicios,
        // Repositorio REAL, e e esse o ponto do Tier 3: o caso "programa apagado com sessao
        // orfa" so existe no Postgres, porque workout_sessions.program_id nao tem FK (V20).
        programs = programas,
        clock = object : Clock { override fun now() = Instant.parse("2026-10-01T10:00:00Z") },
    )

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun uid(usuario: Uuid) = "uid-$usuario"

    // ---- a porta nova, contra o catalogo -----------------------------------

    @Test
    fun `paraAnalise devolve musculos primarios e nome de piso`() = runBlocking {
        val id = Semear.exercicio("Agachamento de teste", listOf("LEGS", "GLUTES"))

        val dado = ok(exercicios.paraAnalise(listOf(id))).getValue(id)

        assertEquals("Agachamento de teste", dado.nomePiso)
        assertEquals(listOf(MuscleGroup.LEGS, MuscleGroup.GLUTES), dado.musculosPrimarios)
    }

    @Test
    fun `exercicio com primary_muscles NULL vem com lista vazia, e nao fora do mapa`() = runBlocking {
        // A coluna é nullable de verdade (V8). Sumir do mapa faria quem chama confundir
        // "não classificado" com "não existe" — e o volume dessas séries evaporaria do gráfico.
        val id = Semear.exercicio("Exercicio sem taxonomia", musculos = null)

        val mapa = ok(exercicios.paraAnalise(listOf(id)))

        assertTrue(id in mapa, "id com primary_muscles NULL tem de estar no mapa")
        assertEquals(emptyList<MuscleGroup>(), mapa.getValue(id).musculosPrimarios)
    }

    @Test
    fun `valor desconhecido na coluna nao derruba a leitura do catalogo`() = runBlocking {
        // Quem escreve por script passa por fora do enum. O mapper ignora o desconhecido em vez
        // de estourar — mesma régua do `toExercise`.
        val id = Semear.exercicio("Exercicio com musculo inventado", listOf("LEGS", "ASA_DELTA"))

        assertEquals(listOf(MuscleGroup.LEGS), ok(exercicios.paraAnalise(listOf(id))).getValue(id).musculosPrimarios)
    }

    @Test
    fun `lista vazia devolve mapa vazio sem ir ao banco`() = runBlocking {
        assertTrue(ok(exercicios.paraAnalise(emptyList())).isEmpty())
    }

    // ---- filtro por programa, contra o banco (J.3.4) -----------------------

    /**
     * ⭐ Este e o caso que SO existe no Postgres: sessao apontando para um programa APAGADO.
     *
     * `workout_sessions.program_id` nao tem FK (V20: a sessao e um snapshot autocontido, e apagar
     * o programa nao pode apagar o historico de quem treinou). Teste de unidade nao PRODUZ esse
     * estado — ele o simula, montando uma lista em que o id nao esta. Aqui o banco produz de
     * verdade: a linha do programa some e a sessao fica apontando para o nada.
     *
     * Se um dia alguem acrescentar a FK "para arrumar", este teste quebra — e e exatamente o
     * aviso que se quer, porque a FK apagaria historico de treino em cascata.
     */
    @Test
    fun `sessao de programa APAGADO cai nos avulsos, e o programa nao vira opcao`() = runBlocking {
        val usuario = Semear.usuario()
        val exercicio = Semear.exercicio("Agachamento ${Uuid.random()}", listOf("LEGS"))
        ok(usuarios.setPremium(usuario, true))
        val inicio = LocalDate.parse("2026-09-21")

        val vivo = Semear.programa(usuario, "Programa A", inicio)
        val apagado = Semear.programa(usuario, "Programa B", inicio)
        Semear.sessao(usuario, "Inferior A", inicio, listOf(Semear.Quadrupla(exercicio, 10, 60.0)), vivo)
        Semear.sessao(usuario, "Inferior B", inicio, listOf(Semear.Quadrupla(exercicio, 10, 30.0)), apagado)
        Semear.sessao(usuario, "Livre", inicio, listOf(Semear.Quadrupla(exercicio, 10, 20.0)))
        Semear.apagarPrograma(apagado)

        val todos = ok(progresso.forUser(uid(usuario), null, Idioma.PADRAO))
        val avulsos = ok(
            progresso.forUser(uid(usuario), null, Idioma.PADRAO, ProgressService.Filtro.Avulsos()),
        )

        assertEquals(1100.0, todos.totalKg, "sem filtro, os tres somam")
        assertEquals(
            listOf(vivo.toString()), todos.availablePrograms,
            "o programa apagado virou opcao de filtro — e o nome dele e DERIVADO, nao existe mais",
        )
        assertTrue(todos.hasUnassigned)
        assertEquals(
            300.0 + 200.0, avulsos.totalKg,
            "a sessao do programa apagado nao caiu no balde dos avulsos, entao sumiu da analise",
        )
        assertEquals(2, avulsos.totalSessions)
    }

    @Test
    fun `filtrar por programa deixa de fora a sessao do outro, com o started_at do banco`() = runBlocking {
        val usuario = Semear.usuario()
        val exercicio = Semear.exercicio("Agachamento ${Uuid.random()}", listOf("LEGS"))
        ok(usuarios.setPremium(usuario, true))
        val inicio = LocalDate.parse("2026-09-21")

        val a = Semear.programa(usuario, "Programa A", inicio)
        val b = Semear.programa(usuario, "Programa B", inicio)
        // MESMA semana nos dois: e o cenario que o filtro conserta — programas COEXISTEM (#27) e
        // o grafico somava os dois sem avisar.
        Semear.sessao(usuario, "Inferior A", inicio, listOf(Semear.Quadrupla(exercicio, 10, 60.0)), a)
        Semear.sessao(
            usuario, "Inferior B", LocalDate.parse("2026-09-28"),
            listOf(Semear.Quadrupla(exercicio, 10, 50.0)), b,
        )

        val soA = ok(
            progresso.forUser(uid(usuario), null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(a)),
        )

        assertEquals(600.0, soA.totalKg)
        assertEquals(1, soA.totalSessions)
        // A semana do programa sai do `started_at` LIDO DO BANCO, nao de um valor montado no teste.
        assertEquals(1, soA.fromWeek)
        assertEquals(8, soA.toWeek, "teto = duracao declarada, porque nao ha dado alem dela")
        assertEquals(600.0, soA.weeklyLoad?.first { it.weekNumber == 1 }?.kg)
    }

    /** Caminho de falha: programa que nao e meu responde NotFound, e nao analise vazia. */
    @Test
    fun `programa de outro usuario responde NotFound`() = runBlocking {
        val meu = Semear.usuario()
        val alheio = Semear.usuario()
        val doOutro = Semear.programa(alheio, "Programa do outro", LocalDate.parse("2026-09-21"))

        val r = progresso.forUser(uid(meu), null, Idioma.PADRAO, ProgressService.Filtro.DoPrograma(doOutro))

        // ⚠️ Nao terminar em `assertIs`: corpo de expressao cujo ultimo valor nao e Unit faz o
        // metodo deixar de ser `void`, e o JUnit recusa a CLASSE inteira na construcao.
        val erro = (r as AppResult.Failure).error
        assertEquals(ErrorCodes.PROGRAMA_NAO_EXISTE, (erro as AppError.NotFound).code)
    }

    // ---- o portao, lendo is_premium do banco -------------------------------

    private fun cenario(premium: Boolean): Pair<Uuid, Uuid> {
        val usuario = Semear.usuario()
        val agachamento = Semear.exercicio("Agachamento ${Uuid.random()}", listOf("LEGS"))
        listOf(LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-28")).forEach { dia ->
            Semear.sessao(
                usuario = usuario,
                treino = "Inferior A",
                dia = dia,
                series = List(3) { Semear.Quadrupla(agachamento, reps = 10, kg = 60.0) },
            )
        }
        if (premium) runBlocking { ok(usuarios.setPremium(usuario, true)) }
        return usuario to agachamento
    }

    @Test
    fun `free recebe a carga por semana e NENHUM bloco pago`() = runBlocking {
        val (usuario, _) = cenario(premium = false)

        val dto = ok(progresso.forUser(uid(usuario), null, Idioma.PADRAO))

        assertEquals(3600.0, dto.totalKg)          // 2 sessões x 3 séries x 10 reps x 60 kg
        assertEquals(2, dto.totalSessions)
        assertNotNull(dto.lastVsPrevious)
        // J.4.1: a carga por semana saiu do portao — o free ve a janela curta e paga pela longa.
        assertEquals(ProgressService.JANELA_FREE, dto.weeklyLoad?.size)
        assertEquals(ProgressService.JANELA_FREE, dto.weeksWindow)
        assertNull(dto.strengthTrend)
        assertNull(dto.setsByMuscle)
        assertTrue(dto.analysisLocked)
    }

    /**
     * ⭐ O limite do free e aplicado contra o `is_premium` do BANCO, nao contra um booleano
     * montado no teste — e e no servidor, porque o cliente nao e autoridade (#16).
     */
    @Test
    fun `free pedindo janela de 52 recebe 8, lendo o plano do banco`() = runBlocking {
        val (usuario, _) = cenario(premium = false)

        val dto = ok(progresso.forUser(uid(usuario), null, Idioma.PADRAO, ProgressService.Filtro.Todos(52)))

        assertEquals(8, dto.weeksWindow)
        assertEquals(8, dto.weeklyLoad?.size)
    }

    @Test
    fun `premium recebe os tres blocos, com o volume vindo do catalogo real`() = runBlocking {
        val (usuario, _) = cenario(premium = true)

        val dto = ok(progresso.forUser(uid(usuario), null, Idioma.PADRAO))

        assertEquals(ProgressService.JANELA_PADRAO, dto.weeklyLoad?.size)
        assertEquals(1, dto.strengthTrend?.size)
        assertNotNull(dto.setsByMuscle?.byMuscle?.get(MuscleGroup.LEGS))
        assertEquals(0.0, dto.setsByMuscle?.unclassified)
        assertTrue(!dto.analysisLocked)
    }

    // ---- weight_kg nullable, lido do driver --------------------------------

    @Test
    fun `peso corporal lido do banco nao entra na tonelagem`() = runBlocking {
        val usuario = Semear.usuario()
        val prancha = Semear.exercicio("Prancha ${Uuid.random()}", listOf("CORE"))
        Semear.sessao(
            usuario = usuario,
            treino = "Core",
            dia = LocalDate.parse("2026-09-28"),
            series = List(4) { Semear.Quadrupla(prancha, reps = 30, kg = null) },
        )

        val dto = ok(progresso.forUser(uid(usuario), null, Idioma.PADRAO))

        assertEquals(0.0, dto.totalKg)
        assertEquals(1, dto.totalSessions, "o treino aconteceu — só não entra no gráfico")
        assertNull(dto.sinceDate)
    }

    @Test
    fun `serie nao concluida gravada no banco nao conta`() = runBlocking {
        val usuario = Semear.usuario()
        val ex = Semear.exercicio("Supino ${Uuid.random()}", listOf("CHEST"))
        Semear.sessao(
            usuario = usuario,
            treino = "Superior A",
            dia = LocalDate.parse("2026-09-28"),
            series = listOf(
                Semear.Quadrupla(ex, reps = 10, kg = 50.0, feita = true),
                Semear.Quadrupla(ex, reps = 10, kg = 50.0, feita = false),
            ),
        )

        assertEquals(500.0, ok(progresso.forUser(uid(usuario), null, Idioma.PADRAO)).totalKg)
    }

    @Test
    fun `historico de outro usuario nao entra no meu progresso`() = runBlocking {
        val (meu, _) = cenario(premium = false)
        val outro = Semear.usuario()

        assertEquals(0.0, ok(progresso.forUser(uid(outro), null, Idioma.PADRAO)).totalKg)
        assertEquals(3600.0, ok(progresso.forUser(uid(meu), null, Idioma.PADRAO)).totalKg)
    }
}
