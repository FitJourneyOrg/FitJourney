package dev.rafael.app.screens.splash

import dev.rafael.app.navigation.AppRoute

/**
 * Os passos do preparo pós-login, na ordem em que rodam.
 *
 * ⚠️ **A ordem não é cosmética.** O perfil resolve a rota ANTES de qualquer outra coisa começar,
 * e o catálogo (923 exercícios) vem por último. O `SplashViewModel` registra o incidente: quando
 * o catálogo era disparado junto, ele competia com o `getProfile` na mesma HttpClient, empurrava
 * a decisão além do timeout e mandava cadastro novo para a rota errada.
 */
enum class PassoDoPreparo { PERFIL, PROGRAMAS, PROGRESSO, EXERCICIOS }

sealed interface SplashState {
    data object Loading : SplashState

    /** Só no caminho pós-login: a rota já foi decidida, mas o app ainda está enchendo o banco local. */
    data class Preparando(val passo: PassoDoPreparo) : SplashState

    data class Decided(val destination: AppRoute) : SplashState
}
