package dev.rafael.app.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.me.Me
import dev.rafael.features.session.domain.HistoricoDeSessoes
import dev.rafael.features.stats.domain.Stats
import dev.rafael.app.navigation.AppRoute
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import dev.rafael.features.profile.domain.repository.ProfileRepository
import dev.rafael.features.program.domain.repository.ProgramRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Decide para onde ir na abertura, e -- só depois do login -- enche o banco local antes de soltar
 * a Home.
 *
 * ## Dois momentos, uma tela
 *
 * | | cold start (`posLogin = false`) | pós-login (`posLogin = true`) |
 * |---|---|---|
 * | banco local | já tem dado | vazio |
 * | o que faz | decide a rota e sincroniza DE FUNDO | decide a rota e **espera** o essencial |
 * | por quê | esperar o que já está no aparelho é atraso puro | sem esperar, a Home se monta na frente do usuário |
 *
 * O caminho de cold start continua idêntico ao que sempre foi, de propósito: ele carrega cicatriz
 * (ver os comentários de timeout e de ordem abaixo) e não é o problema que esta mudança resolve.
 */
class SplashViewModel(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
    private val me: Me,
    private val exercises: ExerciseRepository,
    private val sessionSync: HistoricoDeSessoes,
    private val programs: ProgramRepository,
    private val stats: Stats,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state.asStateFlow()

    private var iniciado = false

    /**
     * ⚠️ **Tem de ser chamado pela tela** -- o construtor não decide nada sozinho, e isto é
     * deliberado: `posLogin` é propriedade da ROTA (`Splash` vs `Preparando`), não do grafo de DI.
     *
     * A alternativa era receber `posLogin` no construtor via parâmetro do Koin. Rejeitada: o
     * `verify()` do `KoinModulesVerifyTest` reflete sobre o construtor e cobraria binding de
     * `Boolean` -- e abrir `Boolean::class` em `extraTypes` furaria a guarda que já pegou um crash
     * de boot real (o `Clock` do `HomeViewModel`). **Não se afrouxa uma guarda que já pagou por
     * si mesma para economizar uma linha.**
     *
     * A guarda [iniciado] não é zelo: em mudança de configuração o Composable re-entra e o
     * ViewModel SOBREVIVE, então sem ela o preparo recomeçaria do primeiro passo.
     */
    fun iniciar(posLogin: Boolean) {
        if (iniciado) return
        iniciado = true

        viewModelScope.launch {
            val destino = resolverDestino(posLogin)

            // Sem sessão não há o que preparar, e no cold start o dado já está no aparelho.
            if (destino == AppRoute.Login || !posLogin) {
                _state.value = SplashState.Decided(destino)
                return@launch
            }

            prepararAntesDaHome()
            _state.value = SplashState.Decided(destino)
        }
    }

    /**
     * A rota, e SÓ a rota. Os aquecimentos de fundo que este caminho dispara continuam onde
     * estavam: o cold start não pode ficar mais lento por causa do pós-login.
     */
    private suspend fun resolverDestino(posLogin: Boolean): AppRoute {
        // Gate pela SESSÃO PERSISTIDA (não por token fresco): quem já logou entra mesmo
        // offline (token pode não renovar sem rede). Só vai pro Login quem nunca logou aqui.
        if (!auth.isLoggedIn()) return AppRoute.Login

        // CACHE-FIRST no gate (ARCH #30). `onboardingCompleted` é MONOTÔNICO: uma vez
        // concluído, nunca volta atrás. Então um `true` cacheado é confiável e dispensa
        // esperar a rede -- o usuário recorrente abre o app instantaneamente, e o perfil
        // sincroniza de fundo.
        //
        // A assimetria é o ponto: `false` ou `null` NÃO podem ser confiados. Pular o
        // onboarding por engano é muito pior que esperar 2 segundos, então nesses casos
        // a rede continua sendo consultada antes de decidir.
        if (profile.cachedOnboardingCompleted() == true) {
            if (!posLogin) {
                appScope.launch { profile.getProfile() }   // atualiza o cache sem travar a tela
                aquecerDeFundo()
            }
            return AppRoute.Home
        }

        // Timeout maior: o 1º request após o boot do server é lento (JIT/pool) e
        // estourava 1,5s -> caía no fallback (cache stale) -> Home errado p/ cadastro novo.
        val result = withTimeoutOrNull(TIMEOUT_PERFIL_MS) { profile.getProfile() }
        val dest = when (result) {
            is AppResult.Success ->
                if (result.value.onboardingCompleted) AppRoute.Home else AppRoute.Nome
            is AppResult.Failure ->
                if (result.error is AppError.NotFound) AppRoute.Nome
                else fallbackFromCache()          // <- rede falhou: usa cache
            null -> fallbackFromCache()            // <- timeout: usa cache
        }

        // SÓ AGORA aquece (fire-and-forget no appScope). Roda DEPOIS da decisão de rota de
        // propósito: se disparado antes, o GET /exercises (catálogo inteiro) concorria com o
        // getProfile na mesma HttpClient e, em server frio, empurrava o getProfile além do
        // timeout -> fallback (cache stale) -> Home errado no cadastro novo.
        //
        // No pós-login isso NÃO roda aqui: lá o mesmo trabalho é esperado, com passo visível,
        // em `prepararAntesDaHome`. Disparar aqui também faria a mesma requisição duas vezes.
        if (!posLogin) aquecerDeFundo()
        return dest
    }

    /**
     * ⭐ O preparo do pós-login: o mesmo trabalho que o cold start faz de fundo, só que ESPERADO
     * e com passo visível.
     *
     * **A ordem é a proteção contra o incidente que o comentário acima descreve.** A rota já foi
     * decidida quando isto começa, então o catálogo (923 exercícios, o mais demorado) não tem mais
     * com o que competir. Ele vem por último pelo mesmo motivo.
     *
     * **O teto de tempo é a proteção contra a rede.** Se algum passo travar, o preparo desiste e a
     * Home abre com o que deu tempo de chegar -- que é exatamente o estado que o app já sabia
     * mostrar antes desta tela existir. Prender alguém num indicador de progresso é pior do que
     * uma Home incompleta.
     *
     * > **Carregamento sem teto não é carregamento: é uma tela sem saída.**
     */
    private suspend fun prepararAntesDaHome() {
        withTimeoutOrNull(TIMEOUT_PREPARO_MS) {
            _state.value = SplashState.Preparando(PassoDoPreparo.PERFIL)
            profile.getProfile()

            // `/me` é OUTRA requisição, e é ela que carrega o NOME. Sem isto o cabeçalho do menu
            // abria no e-mail da sessão do Firebase (o dado que existe offline) e trocava para o
            // nome na cara do usuário assim que o GET voltava.
            //
            // Sem `forcar`: o carimbo do `/me` é POR CONTA (`SyncStamps.Escopo.USUARIO`), então
            // ele só pode pular quando o cache daquela mesma conta já está quente -- que é
            // exatamente o caso em que não há nada para esperar.
            me.sincronizar()

            _state.value = SplashState.Preparando(PassoDoPreparo.PROGRAMAS)
            programs.list()

            _state.value = SplashState.Preparando(PassoDoPreparo.PROGRESSO)
            sessionSync.flush()
            sessionSync.sincronizarHistorico()
            stats.sincronizar()

            _state.value = SplashState.Preparando(PassoDoPreparo.EXERCICIOS)
            exercises.refresh()
        }
    }

    private suspend fun fallbackFromCache(): AppRoute =
        when (profile.cachedOnboardingCompleted()) {
            true  -> AppRoute.Home
            false -> AppRoute.Nome
            null  -> AppRoute.Home   // device novo + offline + nunca cacheou: sem info, chuta Home
        }

    /**
     * Aquecimento do COLD START, fire-and-forget. Roda no appScope (não no viewModelScope): a
     * Splash é destruída assim que decide a rota, e o viewModelScope cancelaria o refresh no meio
     * -- deixando o cache vazio e o WorkoutDetail mostrando "Exercício indisponível".
     */
    private fun aquecerDeFundo() {
        appScope.launch { exercises.refresh() }
        appScope.launch {
            sessionSync.flush()
            sessionSync.sincronizarHistorico()
        }
    }

    private companion object {
        const val TIMEOUT_PERFIL_MS = 5_000L

        /**
         * 20s para o preparo INTEIRO, não por passo. O catálogo sozinho pode levar alguns segundos
         * num aparelho novo com rede ruim, e o custo de estourar é baixo: a Home abre incompleta,
         * como abria antes desta tela existir.
         */
        const val TIMEOUT_PREPARO_MS = 20_000L
    }
}
