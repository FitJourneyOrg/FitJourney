package dev.rafael.app.screens.home

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import dev.rafael.features.program.domain.model.ProgramWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Primeiros testes da Home. Ela era a única tela sem cobertura — e num único dia saíram dela
 * três defeitos que um teste teria pego: o `locked` rebaixado no banco, o `/me/stats` sem TTL
 * e o 403 do dia trancado.
 *
 * Dois destravamentos tornaram isto possível: as interfaces [dev.rafael.app.data.stats.Stats] e
 * [dev.rafael.app.data.session.HistoricoDeSessoes] (antes eram classes com `FitJourneyDatabase`
 * no construtor) e o `Clock` injetado — sem ele, "achou o treino de hoje" passaria na segunda e
 * falharia no domingo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    /** Segunda-feira, 12:00 UTC. Relógio FIXO: o resultado não pode depender do dia do build. */
    private val segunda = relogioEm("2026-08-17T12:00:00Z")

    private fun relogioEm(iso: String) = object : Clock {
        override fun now(): Instant = Instant.parse(iso)
    }

    private fun vm(
        historico: FakeHistorico = FakeHistorico(),
        stats: FakeStats = FakeStats(),
        programas: FakeProgramas = FakeProgramas(),
        treinos: FakeTreinos = FakeTreinos(),
        clock: Clock = segunda,
    ) = HomeViewModel(
        programs = programas,
        workouts = treinos,
        stats = stats,
        sessions = historico,
        clock = clock,
    )

    // ---- treino do dia ----

    @Test
    fun `acha o treino agendado para hoje`() = runTest(dispatcher) {
        val programas = FakeProgramas()
        val viewModel = vm(programas = programas)
        advanceUntilIdle()

        programas.locais.value = listOf(
            programa(
                workouts = listOf(ProgramWorkout(id = "w1", name = "Upper", exerciseCount = 6)),
                schedule = listOf(ProgramScheduleEntry(workoutId = "w1", dayOfWeek = 1)),   // segunda
            ),
        )
        advanceUntilIdle()

        assertEquals("Upper", viewModel.state.value.today?.name)
        assertFalse(viewModel.state.value.semPrograma)
    }

    @Test
    fun `dia sem treino agendado e descanso`() = runTest(dispatcher) {
        val programas = FakeProgramas()
        val viewModel = vm(programas = programas)
        advanceUntilIdle()

        programas.locais.value = listOf(
            programa(
                workouts = listOf(ProgramWorkout(id = "w1", name = "Upper", exerciseCount = 6)),
                schedule = listOf(ProgramScheduleEntry(workoutId = "w1", dayOfWeek = 3)),   // quarta
            ),
        )
        advanceUntilIdle()

        // Descanso é implícito (ARCH #22): sem treino hoje E com programa = dia de descanso.
        assertNull(viewModel.state.value.today)
        assertFalse(viewModel.state.value.semPrograma)
    }

    @Test
    fun `nao busca o detalhe de um dia trancado`() = runTest(dispatcher) {
        // ARCH #23: o servidor recusa com 403. Pedir o que já se sabe negado enchia o log de
        // 403 que parecem bug — e a Home tinha o flag `locked` em mãos antes de chamar.
        val programas = FakeProgramas()
        val treinos = FakeTreinos()
        val viewModel = vm(programas = programas, treinos = treinos)
        advanceUntilIdle()

        programas.locais.value = listOf(
            programa(
                workouts = listOf(ProgramWorkout(id = "w1", name = "Lower", exerciseCount = 6, locked = true)),
                schedule = listOf(ProgramScheduleEntry(workoutId = "w1", dayOfWeek = 1)),
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.today?.locked == true)
        assertTrue(treinos.buscados.isEmpty(), "dia trancado não deve ser buscado no servidor")
        assertEquals(0, viewModel.state.value.today?.minutes)
    }

    // ---- histórico local (offline-first) ----

    @Test
    fun `sessao local de hoje marca treinouHoje sem rede`() = runTest(dispatcher) {
        val historico = FakeHistorico()
        val viewModel = vm(historico = historico)
        advanceUntilIdle()

        historico.historico.value = listOf(sessao(finishedAt = "2026-08-17T10:00:00", pendente = true))
        advanceUntilIdle()

        // Pendente ainda não subiu, mas o treino FOI feito: o card muda na hora (ARCH #30).
        assertTrue(viewModel.state.value.treinouHoje)
        assertEquals(1, viewModel.state.value.sessoesPendentes)
    }

    @Test
    fun `sessao de outro dia nao marca treinouHoje`() = runTest(dispatcher) {
        val historico = FakeHistorico()
        val viewModel = vm(historico = historico)
        advanceUntilIdle()

        historico.historico.value = listOf(sessao(finishedAt = "2026-08-16T10:00:00", pendente = false))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.treinouHoje)
    }

    @Test
    fun `pendencia que cai FORCA a atualizacao do xp`() = runTest(dispatcher) {
        // Quando o WorkManager sincroniza com a tela aberta, o servidor já recalculou o XP.
        // Aqui o TTL precisa ser furado — senão a faixa só atualizaria minutos depois.
        val historico = FakeHistorico()
        val stats = FakeStats()
        val viewModel = vm(historico = historico, stats = stats)
        advanceUntilIdle()

        historico.historico.value = listOf(sessao(finishedAt = "2026-08-17T10:00:00", pendente = true))
        advanceUntilIdle()
        val forcadasAntes = stats.forcadas

        historico.historico.value = listOf(sessao(finishedAt = "2026-08-17T10:00:00", pendente = false))
        advanceUntilIdle()

        assertTrue(stats.forcadas > forcadasAntes, "pendência caiu: o XP mudou, tem que forçar")
        assertEquals(0, viewModel.state.value.sessoesPendentes)
    }

    // ---- "sem programa" vs "sem sync" ----

    @Test
    fun `stats vem do cache local e aparece offline`() = runTest(dispatcher) {
        val stats = FakeStats()
        val viewModel = vm(stats = stats)
        advanceUntilIdle()

        stats.valores.value = stats(xp = 250, streak = 3)
        advanceUntilIdle()

        assertEquals(250, viewModel.state.value.stats?.xp)
        assertEquals(3, viewModel.state.value.stats?.streakDays)
    }

    @Test
    fun `falha de sync com aparelho que ja sincronizou nao vira sem conexao`() = runTest(dispatcher) {
        // O caso que dava falso positivo: conta sem programas que já sincronizou ontem, offline
        // hoje. Antes o marcador era campo de ViewModel e voltava a false em todo cold start.
        val programas = FakeProgramas(
            sincronizouNesteAparelho = true,
            resultadoList = AppResult.Failure(AppError.Connection()),
        )
        val viewModel = vm(programas = programas)
        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.jaSincronizou)
        assertTrue(viewModel.state.value.semPrograma)   // é verdade: não tem programa
        // A tela decide com jaSincronizou: com true, mostra "comece seu primeiro programa".
    }

    @Test
    fun `aparelho que nunca sincronizou registra a falha`() = runTest(dispatcher) {
        val programas = FakeProgramas(
            sincronizouNesteAparelho = false,
            resultadoList = AppResult.Failure(AppError.Connection()),
        )
        val viewModel = vm(programas = programas)
        viewModel.load()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.jaSincronizou)
        assertTrue(viewModel.state.value.erroSync is AppError.Connection)
    }

    // O logout saiu daqui: mudou de casa para a tela de conta (ARCH #34). O teste foi junto —
    // ver `ContaViewModelTest.sair limpa o cache de onboarding antes de deslogar`.
}
