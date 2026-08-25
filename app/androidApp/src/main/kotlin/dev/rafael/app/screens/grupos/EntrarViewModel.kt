package dev.rafael.app.screens.grupos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.group.GroupPreviewDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EntrarState(
    val codigo: String = "",
    val preview: GroupPreviewDto? = null,
    val carregando: Boolean = false,
    val entrando: Boolean = false,
    val erro: AppError? = null,
    val entrou: Boolean = false,
)

/**
 * Entrar num grupo (decisões 2.1 e 2-B.0).
 *
 * O fluxo tem **duas etapas de propósito**: primeiro o PREVIEW, depois a entrada. A tela do
 * meio não é cerimônia — é onde mora o opt-in do #17. A pessoa precisa ver que o grupo exige
 * localização (ou foto) **antes** de aceitar; descobrir isso depois de entrar transformaria
 * uma regra combinada em imposição.
 */
class EntrarViewModel(private val groups: Groups) : ViewModel() {

    private val _state = MutableStateFlow(EntrarState())
    val state: StateFlow<EntrarState> = _state.asStateFlow()

    /** Código digitado: maiúsculas e sem espaço, porque é assim que ele existe no servidor. */
    fun aoDigitarCodigo(v: String) {
        _state.value = _state.value.copy(
            codigo = v.trim().uppercase().take(6),
            erro = null,
            preview = null,
        )
    }

    fun buscarPorCodigo() {
        val codigo = _state.value.codigo
        if (codigo.length < 6) return
        buscar { groups.preview(code = codigo) }
    }

    /** Chegou por link: o token vem da URL e a busca é imediata, sem digitar nada. */
    fun buscarPorConvite(token: String) = buscar { groups.preview(inviteToken = token) }

    private fun buscar(bloco: suspend () -> AppResult<GroupPreviewDto>) {
        _state.value = _state.value.copy(carregando = true, erro = null)
        viewModelScope.launch {
            when (val r = bloco()) {
                is AppResult.Success ->
                    _state.value = _state.value.copy(carregando = false, preview = r.value)
                is AppResult.Failure ->
                    _state.value = _state.value.copy(carregando = false, erro = r.error)
            }
        }
    }

    fun entrar(inviteToken: String?) {
        _state.value = _state.value.copy(entrando = true, erro = null)
        viewModelScope.launch {
            val r = if (inviteToken != null) groups.entrarPorConvite(inviteToken)
            else groups.entrarPorCodigo(_state.value.codigo)

            _state.value = when (r) {
                is AppResult.Success -> _state.value.copy(entrando = false, entrou = true)
                is AppResult.Failure -> _state.value.copy(entrando = false, erro = r.error)
            }
        }
    }
}
