package dev.rafael.app.screens.conta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.me.Me
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ContaState(
    val nome: String = "",
    val email: String? = null,
    val premium: Boolean = false,
    /** Rascunho do campo de texto. Separado de [nome] para o "Cancelar" ter o que restaurar. */
    val rascunho: String = "",
    val editando: Boolean = false,
    val salvando: Boolean = false,
    val erro: AppError? = null,
) {
    /** Salvar só faz sentido se mudou. Evita um PATCH que não altera nada. */
    val podeSalvar: Boolean get() = !salvando && rascunho.trim() != nome
}

/**
 * CONFIGURAÇÕES DA CONTA (ARCH #34) — privada, nunca renderiza outra pessoa.
 *
 * Também é a nova casa do LOGOUT. Ele morava no `HomeViewModel`, que só dependia de
 * `AuthRepository` e `ProfileRepository` por causa dele; com a mudança, a Home deixou de
 * conhecer autenticação e ficou só com o "treino de hoje".
 */
class ContaViewModel(
    private val me: Me,
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ContaState())
    val state: StateFlow<ContaState> = _state.asStateFlow()

    private val _saiu = MutableStateFlow(false)
    val saiu: StateFlow<Boolean> = _saiu.asStateFlow()

    init {
        viewModelScope.launch {
            me.observar().collect { usuario ->
                _state.value = _state.value.copy(
                    nome = usuario?.displayName.orEmpty(),
                    email = usuario?.email,
                    premium = usuario?.isPremium == true,
                    // Não sobrescreve o que a pessoa está digitando: um sync chegando no meio
                    // da edição apagaria o texto na mão dela.
                    rascunho = if (_state.value.editando) _state.value.rascunho
                    else usuario?.displayName.orEmpty(),
                )
            }
        }
        viewModelScope.launch { me.sincronizar() }
    }

    fun editar() {
        _state.value = _state.value.copy(editando = true, rascunho = _state.value.nome, erro = null)
    }

    fun cancelar() {
        _state.value = _state.value.copy(editando = false, rascunho = _state.value.nome, erro = null)
    }

    /** Limpa o erro ao digitar: manter o campo vermelho enquanto a pessoa corrige é ruído. */
    fun aoDigitar(texto: String) {
        _state.value = _state.value.copy(rascunho = texto, erro = null)
    }

    /**
     * Quem valida é o SERVIDOR ([REGRA] autoridade do backend). O cliente não repete a regra de
     * 2 a 30 caracteres aqui: duas cópias da mesma validação divergem, e a que estaria errada
     * seria justamente a que o usuário vê. O erro volta em `fieldErrors["displayName"]` e a
     * tela marca o campo.
     */
    fun salvar() {
        val novo = _state.value.rascunho
        _state.value = _state.value.copy(salvando = true, erro = null)
        viewModelScope.launch {
            when (val r = me.renomear(novo)) {
                is AppResult.Success -> {
                    // `r.value` é o nome NORMALIZADO pelo servidor, não o texto digitado. Reler
                    // o Flow do cache aqui seria uma corrida: o SQLDelight re-emite depois.
                    _state.value = _state.value.copy(
                        salvando = false,
                        editando = false,
                        nome = r.value,
                        rascunho = r.value,
                    )
                }
                is AppResult.Failure ->
                    _state.value = _state.value.copy(salvando = false, erro = r.error)
            }
        }
    }

    fun sair() {
        viewModelScope.launch {
            // ORDEM IMPORTA: sem limpar, o próximo cadastro herda o `onboardingCompleted = true`
            // e cai direto na Home, pulando o quiz.
            profile.clearOnboardingCache()
            auth.signOut()
            _saiu.value = true
        }
    }
}
