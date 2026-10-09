package dev.rafael.app.screens.splash

import dev.rafael.app.data.me.Me
import dev.rafael.app.navigation.AppRoute
import dev.rafael.app.screens.home.FakeAuth
import dev.rafael.app.screens.home.FakeHistorico
import dev.rafael.app.screens.home.FakePerfil
import dev.rafael.app.screens.home.FakeProgramas
import dev.rafael.app.screens.home.FakeStats
import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.user.UserDto
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.model.FiltroDeExercicios
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import dev.rafael.features.profile.domain.model.Profile
import dev.rafael.features.profile.domain.repository.ProfileRepository
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.repository.ProgramRepository
import dev.rafael.features.session.domain.HistoricoDeSessoes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
 * Primeiros testes do Splash — ele nasceu sem nenhum, e ganhou responsabilidade nova (esperar o
 * preparo depois do login) que é justamente do tipo que quebra em silêncio: se o preparo voltar a
 * rodar de fundo, ninguém vê nada, a Home só volta a se montar na frente do usuário.
 *
 * ## Por que os passos são observados pelos DUBLÊS, e não por um coletor do `state`
 *
 * A primeira versão coletava `vm.state` e conferia as emissões. Não funciona, por dois motivos
 * que se somam: os dublês não suspendem, então os quatro `_state.value =` acontecem no MESMO
 * despacho e o `StateFlow` **conflaciona** (só o último sobrevive); e o coletor precisaria de um
 * escopo que o `advanceUntilIdle` drene.
 *
 * Aqui cada dublê anota o passo que a tela mostrava **no instante em que ele foi chamado**. É uma
 * afirmação mais forte que a original — "quando o perfil estava sendo buscado, a tela dizia
 * PERFIL" — e não depende de nenhuma sutileza de agendamento.
 *
 * > **Teste que depende do agendador está testando o agendador.**
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    /** O catálogo: o passo mais demorado do preparo, e o único sem dublê pronto no projeto. */
    private class FakeExercicios : ExerciseRepository {
        var atualizacoes = 0
        override fun observeExercises(filtro: FiltroDeExercicios): Flow<List<Exercise>> =
            flowOf(emptyList())
        override suspend fun refresh(forcar: Boolean): AppResult<Unit> {
            atualizacoes++
            return AppResult.Success(Unit)
        }
        override suspend fun alternatives(exerciseId: String): AppResult<List<Exercise>> =
            AppResult.Success(emptyList())
        override suspend fun getDetail(exerciseId: String): AppResult<Exercise> =
            AppResult.Failure(dev.rafael.core.result.AppError.NotFound())
    }

    /** O `/me`: o nome que o cabeçalho do menu mostra. Sem dublê pronto fora do MenuViewModel. */
    private class FakeMe : Me {
        var sincronizacoes = 0
        override fun observar(): Flow<UserDto?> = flowOf(null)
        override suspend fun sincronizar(forcar: Boolean) { sincronizacoes++ }
        override suspend fun renomear(nome: String): AppResult<String> = AppResult.Success(nome)
        override suspend fun definirIdioma(idioma: Idioma): AppResult<Unit> = AppResult.Success(Unit)
    }

    // ---- espiões: delegam TUDO e só interceptam o método que o preparo chama ----
    //
    // ⚠️ **UM espião por PASSO, não por colaborador.** O passo PROGRESSO chama três coisas
    // (`flush`, `sincronizarHistorico`, `stats.sincronizar`); espiar duas delas anotava PROGRESSO
    // em dobro e quebrava a lista. Por isso o `stats` entra cru — quem prova que ele foi chamado
    // é o contador do próprio `FakeStats`, logo abaixo nas asserções.

    private class PerfilEspiao(private val alvo: FakePerfil, private val aoChamar: () -> Unit) :
        ProfileRepository by alvo {
        override suspend fun getProfile(): AppResult<Profile> { aoChamar(); return alvo.getProfile() }
    }

    private class ProgramasEspiao(private val alvo: FakeProgramas, private val aoChamar: () -> Unit) :
        ProgramRepository by alvo {
        override suspend fun list(): AppResult<List<Program>> { aoChamar(); return alvo.list() }
    }

    private class HistoricoEspiao(private val alvo: FakeHistorico, private val aoChamar: () -> Unit) :
        HistoricoDeSessoes by alvo {
        override suspend fun flush() { aoChamar(); alvo.flush() }
    }

    private class ExerciciosEspiao(private val alvo: FakeExercicios, private val aoChamar: () -> Unit) :
        ExerciseRepository by alvo {
        override suspend fun refresh(forcar: Boolean): AppResult<Unit> {
            aoChamar()
            return alvo.refresh(forcar)
        }
    }

    private class Cenario(
        val auth: FakeAuth = FakeAuth(),
        val perfil: FakePerfil = FakePerfil(),
        val me: FakeMe = FakeMe(),
        val exercicios: FakeExercicios = FakeExercicios(),
        val historico: FakeHistorico = FakeHistorico(),
        val programas: FakeProgramas = FakeProgramas(),
        val stats: FakeStats = FakeStats(),
    ) {
        /** Os passos que a TELA mostrava, na ordem em que o preparo bateu em cada colaborador. */
        val passosVistos = mutableListOf<PassoDoPreparo>()

        private var vm: SplashViewModel? = null

        /**
         * Fora do preparo o estado não é [SplashState.Preparando] — o `as?` devolve nulo e nada é
         * anotado. É por isso que o aquecimento de fundo do cold start não polui a lista.
         */
        private fun anotar() {
            (vm?.state?.value as? SplashState.Preparando)?.let { passosVistos += it.passo }
        }

        fun vm(appScope: CoroutineScope): SplashViewModel = SplashViewModel(
            auth = auth,
            profile = PerfilEspiao(perfil, ::anotar),
            // cru, não espião: PERFIL já é anotado pelo `getProfile` -- um espião por PASSO.
            me = me,
            exercises = ExerciciosEspiao(exercicios, ::anotar),
            sessionSync = HistoricoEspiao(historico, ::anotar),
            programs = ProgramasEspiao(programas, ::anotar),
            stats = stats,
            appScope = appScope,
        ).also { vm = it }
    }

    @Test
    fun `cold start decide sem esperar preparo e sem mostrar passo`() = runTest(dispatcher) {
        val cenario = Cenario()
        val fundo = CoroutineScope(dispatcher)
        val vm = cenario.vm(fundo)

        vm.iniciar(posLogin = false)
        advanceUntilIdle()

        assertEquals(SplashState.Decided(AppRoute.Home), vm.state.value)
        assertTrue(
            cenario.passosVistos.isEmpty(),
            "o cold start mostrou passo de preparo -- ele não espera nada, esperar o que já está " +
                "no aparelho é atraso puro",
        )
        // O aquecimento de fundo continua acontecendo: é o que abastece o cache pro recorrente.
        assertEquals(1, cenario.exercicios.atualizacoes, "o cold start parou de aquecer o catálogo")

        fundo.cancel()
    }

    @Test
    fun `pos-login percorre os quatro passos e so entao libera a Home`() = runTest(dispatcher) {
        val cenario = Cenario()
        val fundo = CoroutineScope(dispatcher)
        val vm = cenario.vm(fundo)

        vm.iniciar(posLogin = true)
        advanceUntilIdle()

        assertEquals(
            listOf(
                PassoDoPreparo.PERFIL,
                PassoDoPreparo.PROGRAMAS,
                PassoDoPreparo.PROGRESSO,
                PassoDoPreparo.EXERCICIOS,
            ),
            cenario.passosVistos,
            "a ORDEM é a proteção contra o incidente do catálogo concorrendo com o perfil",
        )
        assertEquals(SplashState.Decided(AppRoute.Home), vm.state.value)

        // Cada colaborador uma vez só: o pós-login NÃO dispara também o aquecimento de fundo,
        // senão o aparelho baixaria o catálogo inteiro duas vezes no primeiro minuto de conta.
        assertEquals(1, cenario.perfil.buscas)
        assertEquals(1, cenario.me.sincronizacoes, "o nome do cabeçalho não foi carregado antes da Home")
        assertEquals(1, cenario.exercicios.atualizacoes)
        assertEquals(1, cenario.historico.flushes)
        assertEquals(1, cenario.stats.sincronizacoes)

        fundo.cancel()
    }

    @Test
    fun `sem sessao vai pro Login sem preparar nada`() = runTest(dispatcher) {
        val cenario = Cenario(auth = FakeAuth().apply { logado = false })
        val fundo = CoroutineScope(dispatcher)
        val vm = cenario.vm(fundo)

        vm.iniciar(posLogin = true)
        advanceUntilIdle()

        assertEquals(SplashState.Decided(AppRoute.Login), vm.state.value)
        assertEquals(0, cenario.perfil.buscas, "preparou perfil antes de saber de quem é a conta")
        assertEquals(0, cenario.me.sincronizacoes)
        assertEquals(0, cenario.exercicios.atualizacoes)

        fundo.cancel()
    }

    @Test
    fun `passo travado estoura o teto e a Home abre incompleta em vez de prender`() =
        runTest(dispatcher) {
            val cenario = Cenario(perfil = FakePerfil(travarGetProfile = true))
            val fundo = CoroutineScope(dispatcher)
            val vm = cenario.vm(fundo)

            vm.iniciar(posLogin = true)
            advanceUntilIdle()   // tempo VIRTUAL: atravessa o teto de 20s sem esperar de verdade

            assertEquals(
                SplashState.Decided(AppRoute.Home), vm.state.value,
                "carregamento sem saída -- o teto de tempo não liberou a rota",
            )
            assertEquals(
                0, cenario.exercicios.atualizacoes,
                "o preparo continuou depois de estourar o teto",
            )

            fundo.cancel()
        }

    @Test
    fun `iniciar de novo nao recomeca o preparo`() = runTest(dispatcher) {
        val cenario = Cenario()
        val fundo = CoroutineScope(dispatcher)
        val vm = cenario.vm(fundo)

        vm.iniciar(posLogin = true)
        advanceUntilIdle()
        vm.iniciar(posLogin = true)   // é o que acontece em mudança de configuração
        advanceUntilIdle()

        assertEquals(1, cenario.exercicios.atualizacoes, "o preparo recomeçou do primeiro passo")
        assertEquals(4, cenario.passosVistos.size, "o preparo percorreu os passos duas vezes")
        assertFalse(vm.state.value is SplashState.Preparando)

        fundo.cancel()
    }
}
