package dev.rafael.app.screens.grupos

import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupInviteDto
import dev.rafael.contract.group.GroupMemberDto
import dev.rafael.contract.group.GroupPreviewDto
import dev.rafael.contract.group.GroupState
import dev.rafael.contract.group.GroupType
import dev.rafael.contract.group.ScoringModel
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A aba Grupos (ARCH #33, fatia A.3).
 *
 * O teste que importa aqui é o da CORRIDA entre o coletor do cache e o sync — os dois escrevem
 * no mesmo estado, de corrotinas diferentes. Ver `GruposViewModel.sincronizar`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GruposViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun grupo(id: String) = GroupDto(
        id = id,
        code = "ABC$id",
        type = GroupType.DESAFIO,
        scoringModel = ScoringModel.CONTAGEM_CHECKINS,
        title = "Desafio $id",
        startDate = "2026-08-01",
        endDate = "2026-09-01",
        timezone = "America/Sao_Paulo",
        state = GroupState.ATIVO,
        memberCount = 3,
    )

    /**
     * Dublê no formato do repositório de verdade: `observar()` é o cache (re-emite quando o sync
     * grava) e `jaSincronizou()` é uma leitura de BANCO — ou seja, **suspende**. Essa suspensão
     * não é detalhe: era exatamente a fresta em que a emissão do cache se perdia.
     */
    private class FakeGroups(private val noServidor: List<GroupDto>) : Groups {
        val cache = MutableStateFlow<List<GroupDto>>(emptyList())
        private var sincronizouAlgumaVez = false
        var chamadasDeSync = 0
            private set

        override fun observar(): Flow<List<GroupDto>> = cache

        override suspend fun sincronizar(forcar: Boolean) {
            chamadasDeSync++
            yield()                     // a rede suspende
            cache.value = noServidor    // gravou no cache: o Flow re-emite
            sincronizouAlgumaVez = true
        }

        override suspend fun jaSincronizou(): Boolean {
            yield()                     // ler o carimbo é um SELECT, e um SELECT suspende
            return sincronizouAlgumaVez
        }

        override suspend fun criar(req: CreateGroupRequest): AppResult<GroupDto> = naoUsado()
        override suspend fun preview(code: String?, inviteToken: String?): AppResult<GroupPreviewDto> = naoUsado()
        override suspend fun entrarPorCodigo(code: String): AppResult<GroupDto> = naoUsado()
        override suspend fun entrarPorConvite(token: String): AppResult<GroupDto> = naoUsado()
        override suspend fun porId(groupId: String): AppResult<GroupDto> = naoUsado()
        override suspend fun membros(groupId: String): AppResult<List<GroupMemberDto>> = naoUsado()
        override suspend fun sair(groupId: String): AppResult<Unit> = naoUsado()
        override suspend fun expulsar(groupId: String, userId: String): AppResult<Unit> = naoUsado()
        override suspend fun transferirAdmin(groupId: String, userId: String): AppResult<Unit> = naoUsado()
        override suspend fun gerarConvite(groupId: String): AppResult<GroupInviteDto> = naoUsado()
        override suspend fun revogarConvite(groupId: String): AppResult<Unit> = naoUsado()

        private fun naoUsado(): Nothing = error("a aba Grupos não chama isto")
    }

    @Test
    fun `o sync NAO apaga a lista que o cache entregou durante a suspensao`() = runTest(dispatcher) {
        // REGRESSÃO. Com 4 grupos no servidor, a tela mostrava "Nenhum desafio ainda" e só se
        // consertava ao sair da aba e voltar (ViewModel novo, cache já cheio).
        //
        // A causa era `_state.value = _state.value.copy(jaSincronizou = groups.jaSincronizou(), …)`:
        // lê o estado, SUSPENDE no meio, e escreve o retrato velho por cima da emissão do cache
        // que chegou nesse intervalo. Como não havia mais nenhuma gravação, nada corrigia depois.
        val servidor = List(4) { grupo("g$it") }
        val fake = FakeGroups(servidor)
        val viewModel = GruposViewModel(fake)

        viewModel.carregar()
        advanceUntilIdle()

        assertEquals(servidor, viewModel.state.value.grupos, "o sync engoliu a emissão do cache")
        assertFalse(viewModel.state.value.carregando)
        assertFalse(viewModel.state.value.vazio)
        assertTrue(viewModel.state.value.jaSincronizou)
    }

    @Test
    fun `sem cache, carregando fica ligado ate o sync responder`() = runTest(dispatcher) {
        // O esqueleto depende disto. Quando o coletor encerrava o carregamento, a emissão VAZIA
        // do cache (primeiro frame, nada guardado) matava o esqueleto antes de a rede responder —
        // e o "Nenhum desafio ainda" piscava na cara de quem tinha grupos.
        val viewModel = GruposViewModel(FakeGroups(listOf(grupo("g1"))))

        assertTrue(viewModel.state.value.carregando, "abre carregando")

        viewModel.carregar()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.carregando, "quem termina o carregamento é o SYNC")
    }

    @Test
    fun `vazio de verdade so aparece depois do primeiro sync`() = runTest(dispatcher) {
        val fake = FakeGroups(emptyList())
        val viewModel = GruposViewModel(fake)

        assertFalse(viewModel.state.value.jaSincronizou, "antes do sync não dá para afirmar nada")

        viewModel.carregar()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.vazio)
        assertTrue(viewModel.state.value.jaSincronizou, "agora sim: 'Nenhum desafio ainda'")
    }
}
