package dev.rafael.app.screens.progress

import dev.rafael.app.screens.home.FakeHistorico
import dev.rafael.app.screens.home.FakeProgramas
import dev.rafael.app.screens.home.FakeProgresso
import dev.rafael.app.screens.home.FakeStats
import dev.rafael.contract.stats.ProgressDto
import dev.rafael.features.stats.domain.FiltroDeProgresso
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
import kotlin.test.assertTrue

/**
 * G.6 -- "arraste para atualizar" na tela de Progresso. Reusa os fakes da Home
 * ([FakeHistorico]/[FakeStats]): mesma interface ([dev.rafael.features.session.domain.HistoricoDeSessoes],
 * [dev.rafael.features.stats.domain.Stats]), mesmo comportamento simulado -- não faz sentido duplicar.
 *
 * Sem teste de caminho de falha aqui de propósito: as duas interfaces são documentadas como
 * "nunca falha" / "falha em silêncio" (o `AppResult`/erro já é engolido por dentro da
 * implementação real). Não existe um branch de erro no `ProgressViewModel` pra exercitar --
 * forçar uma exceção nos fakes só pra ter "um caminho de falha" testaria um cenário que o
 * contrato da interface promete que não acontece.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    /**
     * ⭐ O `flush` ficou aqui mesmo depois do histórico sair da tela (desmembramento de
     * 2026-10-01), e este teste é o que impede alguém de "limpar" essa dependência que parece
     * sobra: as métricas são derivadas das sessões no SERVIDOR, então subir o treino feito
     * offline tem de acontecer ANTES de pedir os números.
     *
     * Sem ele, terminar o segundo treino offline e abrir o Progresso mostraria "1 treino".
     */
    @Test
    fun `sincronizar sobe as sessoes pendentes antes de pedir as metricas`() = runTest(dispatcher) {
        val historico = FakeHistorico()
        val stats = FakeStats()
        val analise = FakeProgresso()
        val vm = ProgressViewModel(historico, stats, analise, FakeProgramas())
        advanceUntilIdle()   // consome o sync automático do init

        vm.sincronizar()   // o gesto de arraste chama isto
        advanceUntilIdle()

        assertEquals(2, historico.flushes, "parou de subir o que foi feito offline")
        assertEquals(2, stats.sincronizacoes)
        assertEquals(2, analise.sincronizacoes, "a analise tambem sai das sessoes - tem de ser repedida")
        assertEquals(
            2, analise.forcadas,
            "depois do flush o TTL de 2 min nao vale: SABEMOS que o numero mudou",
        )
        assertEquals(
            0, historico.sincronizacoes,
            "o Progresso não mostra mais o histórico -- baixá-lo aqui é requisição sem tela",
        )
    }

    @Test
    fun `sincronizando volta a false ao terminar (indicador nao fica preso girando)`() = runTest(dispatcher) {
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), FakeProgresso(), FakeProgramas())
        advanceUntilIdle()

        assertFalse(vm.state.value.sincronizando)

        vm.sincronizar()
        advanceUntilIdle()

        assertFalse(vm.state.value.sincronizando)
    }

    /**
     * ⭐ O portao vem do SERVIDOR, nunca do nulo.
     *
     * Os tres blocos pagos tambem vem nulos para quem so treina peso corporal. Se a tela
     * deduzisse "trancado" do nulo, mostraria paywall a quem nao conseguiria usar a analise nem
     * pagando — e esconderia a explicacao que ela precisa ler.
     */
    @Test
    fun `trancado sai do analysisLocked, e nao da ausencia dos blocos`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())

        analise.valores.value = ProgressDto(totalKg = 0.0, totalSessions = 3, analysisLocked = true)
        advanceUntilIdle()
        assertTrue(vm.state.value.trancado)

        // mesmos blocos nulos, mas premium: e estado vazio, nao paywall
        analise.valores.value = ProgressDto(totalKg = 0.0, totalSessions = 3, analysisLocked = false)
        advanceUntilIdle()
        assertFalse(vm.state.value.trancado)
        assertTrue(vm.state.value.semCarga, "sem sinceDate nao ha o que desenhar")
    }

    @Test
    fun `quem tem carga nao cai no estado vazio`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())

        analise.valores.value = ProgressDto(
            totalKg = 3600.0,
            totalSessions = 2,
            sinceDate = "2026-09-21",
        )
        advanceUntilIdle()

        assertFalse(vm.state.value.semCarga)
        assertFalse(vm.state.value.trancado)
    }


    // ---- filtro por programa (J.3) -----------------------------------------

    /**
     * ⭐ Trocar de chip troca a CHAVE observada, nao so o que se pede.
     *
     * O cache e por recorte. Um ViewModel que observasse um fluxo fixo e so disparasse o sync
     * passaria num fake de fluxo unico — e na tela mostraria o grafico do recorte anterior ate a
     * rede responder, que e o defeito mais dificil de notar: o numero esta certo, mas e de outro
     * filtro.
     */
    @Test
    fun `selecionar um programa passa a observar o recorte DELE`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())
        advanceUntilIdle()

        val doPrograma = FiltroDeProgresso.DoPrograma("prog-x")
        analise.fluxo(FiltroDeProgresso.Todos).value = ProgressDto(totalKg = 999.0, totalSessions = 9)
        analise.fluxo(doPrograma).value = ProgressDto(totalKg = 111.0, totalSessions = 1)

        vm.selecionar(doPrograma)
        advanceUntilIdle()

        assertEquals(111.0, vm.state.value.analise?.totalKg, "mostrou o recorte errado")
        assertEquals(doPrograma, vm.state.value.filtro)
        assertTrue(doPrograma in analise.sincronizados)
    }

    @Test
    fun `selecionar o mesmo filtro de novo nao repede nada`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())
        advanceUntilIdle()
        val antes = analise.sincronizacoes

        val f = FiltroDeProgresso.DoPrograma("prog-x")
        vm.selecionar(f)
        advanceUntilIdle()
        vm.selecionar(f)
        advanceUntilIdle()

        assertEquals(antes + 1, analise.sincronizacoes, "tocar no chip ja ativo foi a rede de novo")
    }

    @Test
    fun `o arraste-pra-atualizar repede o recorte ATUAL, nao o padrao`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())
        advanceUntilIdle()

        val f = FiltroDeProgresso.DoPrograma("prog-x")
        vm.selecionar(f)
        advanceUntilIdle()
        analise.sincronizados.clear()

        vm.sincronizar()
        advanceUntilIdle()

        // Tipado: `listOf(f)` sozinho da List<DoPrograma> contra MutableList<FiltroDeProgresso>,
        // e o assertEquals nao acha um T comum entre as sobrecargas.
        assertEquals(
            listOf<FiltroDeProgresso>(f),
            analise.sincronizados.toList(),
            "voltou a pedir 'todos' depois de filtrar",
        )
    }

    // ---- faixa de semanas (J.3.3b) -----------------------------------------

    /**
     * ⭐ A faixa troca o recorte MANTENDO o programa.
     *
     * `DoPrograma(id, 10, 14)` e um filtro diferente de `DoPrograma(id)` — chave de cache
     * propria, requisicao propria. O que este teste trava e o `copy`: montar um `DoPrograma(id,
     * de, ate)` novo a partir do zero perderia qualquer campo que a faixa venha a ganhar depois.
     */
    @Test
    fun `selecionarFaixa troca a faixa e conserva o programa`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())
        advanceUntilIdle()

        vm.selecionar(FiltroDeProgresso.DoPrograma("prog-x"))
        advanceUntilIdle()
        vm.selecionarFaixa(10, 14)
        advanceUntilIdle()

        assertEquals(FiltroDeProgresso.DoPrograma("prog-x", de = 10, ate = 14), vm.state.value.filtro)
        assertTrue(
            FiltroDeProgresso.DoPrograma("prog-x", 10, 14) in analise.sincronizados,
            "a faixa nova nao foi pedida — a tela mostraria o recorte antigo",
        )
    }

    /**
     * Caminho de falha: faixa sem programa nao tem referente.
     *
     * "Semana 10" so existe contado do inicio de UM programa (ARCH #27: eles coexistem, entao a
     * mesma data e semana 3 de um e 11 de outro). O controle nem aparece sem programa, mas o
     * ViewModel nao pode depender disso: aceitar a chamada montaria um filtro invalido, e o
     * servidor devolveria 400 por um estado que a tela nunca deveria ter criado.
     */
    @Test
    fun `selecionarFaixa e ignorada quando o recorte nao e de um programa`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())
        advanceUntilIdle()
        val antes = analise.sincronizacoes

        vm.selecionarFaixa(10, 14)
        vm.selecionar(FiltroDeProgresso.Avulsos)
        advanceUntilIdle()
        vm.selecionarFaixa(2, 3)
        advanceUntilIdle()

        assertEquals(FiltroDeProgresso.Avulsos, vm.state.value.filtro)
        assertEquals(antes + 1, analise.sincronizacoes, "faixa sem programa virou requisicao")
    }

    /**
     * ⭐ O nulo do recorte em voo NAO apaga os controles.
     *
     * Cache e por recorte, entao escolher um recorte novo da miss garantido e o `analise` fica
     * nulo ate a rede responder. Se os controles lessem o `analise`, o chip desapareceria no
     * instante em que fosse tocado e o slider sumiria sob o dedo — com o layout saltando duas
     * vezes por gesto.
     */
    @Test
    fun `a estrutura do filtro sobrevive ao recorte que ainda nao chegou`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())
        analise.fluxo(FiltroDeProgresso.Todos).value = ProgressDto(
            totalKg = 10.0,
            totalSessions = 1,
            sinceDate = "2026-09-21",
            availablePrograms = listOf("prog-x", "prog-y"),
            hasUnassigned = true,
        )
        advanceUntilIdle()

        vm.selecionar(FiltroDeProgresso.DoPrograma("prog-x"))   // sem dado no fake: emite nulo
        advanceUntilIdle()

        assertEquals(null, vm.state.value.analise, "o fake precisa emitir nulo pro teste valer")
        assertEquals(listOf("prog-x", "prog-y"), vm.state.value.estrutura.programas)
        assertTrue(vm.state.value.estrutura.temAvulsos)
    }

    /**
     * ⭐ O teto do slider tem de ser do programa que esta na tela.
     *
     * Enquanto o recorte novo carrega, a estrutura ainda fala do anterior. Sem dizer de QUAL
     * programa o numero de semanas e, trocar um programa de 12 semanas por um de 8 desenharia o
     * segundo com o teto do primeiro — e deixaria escolher a semana 11 de um programa que tem 8.
     */
    @Test
    fun `a estrutura diz de qual programa o numero de semanas fala`() = runTest(dispatcher) {
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())
        val x = FiltroDeProgresso.DoPrograma("prog-x")
        analise.fluxo(x).value = ProgressDto(
            totalKg = 10.0,
            totalSessions = 1,
            sinceDate = "2026-09-21",
            programWeeks = 12,
            fromWeek = 1,
            toWeek = 12,
        )
        advanceUntilIdle()

        vm.selecionar(x)
        advanceUntilIdle()
        assertEquals("prog-x", vm.state.value.estrutura.programaMedido)
        assertEquals(12, vm.state.value.estrutura.semanas)

        // Programa sem dado no fake: a estrutura ANTIGA sobrevive, mas aponta para o programa
        // antigo — e e isso que a tela usa pra nao desenhar o slider errado.
        vm.selecionar(FiltroDeProgresso.DoPrograma("prog-y"))
        advanceUntilIdle()
        assertEquals("prog-x", vm.state.value.estrutura.programaMedido, "colou o teto no programa novo")
    }

    @Test
    fun `recorte ainda nao baixado e carregando, nao vazio`() = runTest(dispatcher) {
        // Sem isto a tela diria "voce nao treinou nisso" para um recorte que ela so nao baixou.
        val analise = FakeProgresso()
        val vm = ProgressViewModel(FakeHistorico(), FakeStats(), analise, FakeProgramas())
        analise.valores.value = ProgressDto(totalKg = 10.0, totalSessions = 1, sinceDate = "2026-09-21")
        advanceUntilIdle()

        vm.selecionar(FiltroDeProgresso.DoPrograma("prog-novo"))
        advanceUntilIdle()

        assertTrue(vm.state.value.carregandoRecorte)
        assertFalse(vm.state.value.semCarga, "recorte sem cache nao e 'sem carga'")
    }

}
