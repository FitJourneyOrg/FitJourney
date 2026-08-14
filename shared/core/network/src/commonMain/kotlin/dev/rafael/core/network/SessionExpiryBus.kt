package dev.rafael.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Sinal global de "a sessão morreu de verdade".
 *
 * Emitido pelo cliente HTTP quando um 401 SOBREVIVE à renovação do token — ou seja, quando
 * nenhum retry resolve e a única saída é o usuário entrar de novo. Sem isto, o app mostra
 * "Sessão expirada" e fica ali: o botão "Tentar de novo" repete o mesmo 401 pra sempre.
 *
 * Mora em `core:network` (e não numa feature) porque qualquer request de qualquer feature
 * pode disparar — inclusive o SyncWorker rodando com o app fechado, longe de qualquer tela.
 *
 * `extraBufferCapacity = 1` + `tryEmit`: o emissor nunca bloqueia, mesmo se ninguém estiver
 * ouvindo no instante (app em background).
 */
class SessionExpiryBus {
    private val _eventos = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val eventos: SharedFlow<Unit> = _eventos.asSharedFlow()

    fun sinalizar() {
        _eventos.tryEmit(Unit)
    }
}
