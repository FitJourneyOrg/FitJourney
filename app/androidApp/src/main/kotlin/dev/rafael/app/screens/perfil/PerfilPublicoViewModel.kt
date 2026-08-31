package dev.rafael.app.screens.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.amizades.Amizades
import dev.rafael.app.data.perfil.PerfisPublicos
import dev.rafael.contract.user.PublicProfileDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PerfilPublicoState(
    val perfil: PublicProfileDto? = null,
    val carregando: Boolean = true,
    val erro: AppError? = null,

    /** Uma ação do grafo está em voo. Desabilita o botão para não mandar duas vezes. */
    val agindo: Boolean = false,

    /**
     * Erro da AÇÃO, separado do [erro] de carregar de propósito.
     *
     * Falhar ao adicionar alguém não pode apagar o perfil da tela — é o mesmo princípio de
     * "falha ao recarregar preserva o que já está lá", num eixo diferente. E o lugar do texto é
     * ao lado do botão que falhou (`ErroInline`), não ocupando a tela.
     */
    val erroDaAcao: AppError? = null,
)

/**
 * Perfil de OUTRA pessoa (C.1).
 *
 * **Sem `combine` de três fontes, como o [PerfilViewModel] do dono.** Lá o estado nasce de
 * `Me` + `Stats` + `Achievements`, todos cache-first, porque a tela do dono precisa abrir
 * offline. Aqui é uma requisição só, online, e o estado tem `carregando`/`erro` explícitos —
 * porque falhar é um desfecho normal desta tela e não do outro.
 */
class PerfilPublicoViewModel(
    private val perfis: PerfisPublicos,
    private val amizades: Amizades,
) : ViewModel() {

    private val _state = MutableStateFlow(PerfilPublicoState())
    val state: StateFlow<PerfilPublicoState> = _state.asStateFlow()

    private var jaCarregado: String? = null

    /**
     * Idempotente por `userId`: chamada de novo com o mesmo id no `ON_START` (volta da pilha,
     * rotação) não refaz a requisição. Recarregar de propósito é o [recarregar].
     */
    fun carregar(userId: String) {
        if (jaCarregado == userId) return
        jaCarregado = userId
        buscar(userId)
    }

    fun recarregar() {
        jaCarregado?.let { buscar(it) }
    }

    /**
     * As cinco ações do grafo, todas com o MESMO desfecho: recarregar o perfil.
     *
     * Sem escrita otimista. Trocar o botão na hora mostraria "Pedido enviado" antes de o servidor
     * confirmar — e ele pode recusar por bloqueio, por teto de 500, ou transformar o pedido em
     * AMIZADE (quando o outro já tinha pedido). **O botão certo é o que o servidor decide**, e
     * adivinhá-lo aqui seria reimplementar a regra no cliente.
     */
    fun pedir(userId: String) = agir { amizades.pedir(userId) }
    fun aceitar(userId: String) = agir { amizades.aceitar(userId) }
    fun recusar(userId: String) = agir { amizades.recusar(userId) }
    fun remover(userId: String) = agir { amizades.remover(userId) }
    fun bloquear(userId: String) = agir { amizades.bloquear(userId) }
    fun desbloquear(userId: String) = agir { amizades.desbloquear(userId) }

    private fun agir(acao: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            _state.update { it.copy(agindo = true, erroDaAcao = null) }
            when (val r = acao()) {
                is AppResult.Success -> {
                    _state.update { it.copy(agindo = false) }
                    jaCarregado?.let { buscar(it) }
                }
                is AppResult.Failure ->
                    _state.update { it.copy(agindo = false, erroDaAcao = r.error) }
            }
        }
    }

    private fun buscar(userId: String) {
        _state.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = perfis.de(userId)) {
                is AppResult.Success ->
                    _state.update { it.copy(perfil = r.value, carregando = false, erro = null) }
                is AppResult.Failure ->
                    // O perfil anterior fica na tela de propósito quando já havia um: recarregar
                    // e falhar não deve apagar o que a pessoa está lendo.
                    _state.update { it.copy(carregando = false, erro = r.error) }
            }
        }
    }
}
