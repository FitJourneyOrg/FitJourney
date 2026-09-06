package dev.rafael.app.data.sessao

import dev.rafael.core.network.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A TRANSIÇÃO entre sessões — a lacuna que a bateria da F.1 expôs.
 *
 * ## Por que este arquivo existe
 *
 * Dois dos nove defeitos da F.1 moravam entre um logout e o login seguinte, e **nenhum dava
 * sintoma**: o app funcionava, a tela não reclamava, e a pessoa simplesmente parava de receber
 * notificação. Nenhum teste do projeto atravessava essa fronteira — todos partiam de um estado e
 * verificavam o resultado.
 *
 * > **Teste que parte de um estado não pega defeito que mora na transição.**
 *
 * Os dois estavam dentro do `AppNavHost`, num `@Composable`, onde só uma bateria manual os
 * alcançaria. Extrair a regra para [ReagirASessao] foi o que tornou este arquivo possível.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReagirASessaoTest {

    /**
     * `Unconfined`, e não `Standard` — aqui a escolha do dispatcher É o teste.
     *
     * Com `StandardTestDispatcher`, a corrotina do `collect` só avança quando o teste manda, e
     * cada mutação do `StateFlow` precisaria de um `advanceUntilIdle()` no lugar exato. A primeira
     * versão deste arquivo fez isso e **os quatro testes que esperavam registro viam zero**: o
     * coletor não tinha chegado ao `collect` quando o uid mudou.
     *
     * `UnconfinedTestDispatcher` processa a emissão no instante em que ela acontece, que é
     * exatamente o que se quer ao testar quem OBSERVA um fluxo. Os outros ViewModels do projeto
     * usam `Standard` com razão — lá o que se testa é estado depois de uma ação, e o controle fino
     * do tempo é o ponto.
     *
     * > **O dispatcher de teste não é detalhe: ele decide o que o teste consegue observar.**
     */
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    /** Liga o coletor. Com `Unconfined` ele já está dentro do `collect` quando isto retorna. */
    private fun TestScope.ligar(reagir: ReagirASessao) {
        backgroundScope.launch { reagir.observar() }
    }

    private class FakeToken(
        val uid: MutableStateFlow<String?> = MutableStateFlow(null),
    ) : TokenProvider {
        override suspend fun currentToken(): String? = uid.value?.let { "token-$it" }
        override suspend fun currentUid(): String? = uid.value
        override fun uidFlow(): Flow<String?> = uid
    }

    /** Conta as chamadas — é assim que se prova que o registro aconteceu DUAS vezes. */
    private class Contagem {
        var registros = 0
        var atualizacoes = 0
    }

    private fun cenario(token: FakeToken, c: Contagem) =
        ReagirASessao(token, { c.registros++ }, { c.atualizacoes++ })

    /**
     * ⭐ O TESTE QUE FALTAVA.
     *
     * `"u1"` → `null` → `"u1"`: sair e entrar **na mesma conta**, sem matar o app.
     *
     * Duas versões desta regra falharam aqui, por motivos diferentes:
     *
     * 1. `LaunchedEffect(Unit)` no Composable — dispara uma vez por composição, e login não
     *    recompõe o `AppNavHost`. O segundo registro nunca acontecia.
     * 2. `filterNotNull()` **antes** de `distinctUntilChanged()` — o `null` do logout era removido,
     *    sobravam dois `"u1"` consecutivos, e o `distinct` descartava o segundo.
     *
     * O detalhe que torna o defeito 2 traiçoeiro: **entrar em outra conta funcionava**. Só a mesma
     * conta falhava, que é o caso comum e o menos provável de alguém testar por curiosidade.
     */
    @Test
    fun `sair e entrar na MESMA conta registra DUAS vezes`() = runTest(dispatcher) {
        val token = FakeToken()
        val c = Contagem()
        ligar(cenario(token, c))

        token.uid.value = "u1"          // login
        advanceUntilIdle()
        assertEquals(1, c.registros, "o primeiro login tem de registrar")

        token.uid.value = null          // logout
        advanceUntilIdle()
        assertEquals(1, c.registros, "logout não registra — a baixa é do SairDaConta")

        token.uid.value = "u1"          // login DE NOVO, mesma conta
        advanceUntilIdle()

        assertEquals(
            2,
            c.registros,
            "sair e entrar na mesma conta tem de re-registrar o aparelho — senão a pessoa fica " +
                "sem push e nada na tela diz isso",
        )
        assertEquals(2, c.atualizacoes, "o contador acompanha o registro")
    }

    @Test
    fun `trocar de conta registra as duas`() = runTest(dispatcher) {
        // O caso que FUNCIONAVA mesmo com o defeito, e por isso o escondia: uids diferentes passam
        // pelo `distinctUntilChanged` em qualquer ordem dos operadores.
        val token = FakeToken()
        val c = Contagem()
        ligar(cenario(token, c))

        token.uid.value = "u1"
        advanceUntilIdle()
        token.uid.value = null
        advanceUntilIdle()
        token.uid.value = "u2"
        advanceUntilIdle()

        assertEquals(2, c.registros)
    }

    @Test
    fun `boot COM sessao registra uma vez`() = runTest(dispatcher) {
        // Abrir o app já logado: o `uidFlow` emite o uid de saída, sem transição nenhuma.
        val token = FakeToken(MutableStateFlow("u1"))
        val c = Contagem()
        ligar(cenario(token, c))

        assertEquals(1, c.registros)
    }

    @Test
    fun `sem sessao nao registra nada`() = runTest(dispatcher) {
        // Instalação nova, ninguém logado. Registrar aqui mandaria um POST /me/devices sem token,
        // que volta 401 — e 401 acorda o SessionExpiryBus, que empurra o usuário para o Login.
        val token = FakeToken()
        val c = Contagem()
        ligar(cenario(token, c))

        assertEquals(0, c.registros)
    }

    @Test
    fun `emissoes repetidas do MESMO uid nao viram registros repetidos`() = runTest(dispatcher) {
        // O `uidFlow` pode re-emitir sem que a sessão mude. Sem o `distinctUntilChanged`, cada
        // emissão viraria um POST /me/devices.
        val token = FakeToken()
        val c = Contagem()
        ligar(cenario(token, c))

        token.uid.value = "u1"
        advanceUntilIdle()
        repeat(5) { token.uid.value = "u1" }
        advanceUntilIdle()

        assertEquals(1, c.registros, "mesma sessão, um registro")
    }
}
