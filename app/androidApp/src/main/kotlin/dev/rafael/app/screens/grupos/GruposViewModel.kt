package dev.rafael.app.screens.grupos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.group.GroupDto
import dev.rafael.core.result.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GruposState(
    val grupos: List<GroupDto> = emptyList(),
    val carregando: Boolean = true,
    /**
     * Já baixou alguma vez nesta conta e neste aparelho.
     *
     * Distingue "não baixei ainda" de "você não tem grupo nenhum" — a mesma lição da Home, onde
     * a falta disso convidava quem já tinha programas a criar tudo de novo.
     */
    val jaSincronizou: Boolean = false,
    val erroSync: AppError? = null,
) {
    val vazio: Boolean get() = grupos.isEmpty()
}

/**
 * A aba Grupos (ARCH #33, fatia A.3). Cache-first: a lista aparece no primeiro frame, offline
 * inclusive, e o sync de fundo só atualiza.
 */
class GruposViewModel(private val groups: Groups) : ViewModel() {

    private val _state = MutableStateFlow(GruposState())
    val state: StateFlow<GruposState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            groups.observar().collect { lista ->
                _state.value = _state.value.copy(grupos = lista, carregando = false)
            }
        }
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            groups.sincronizar()
            _state.value = _state.value.copy(
                jaSincronizou = groups.jaSincronizou(),
                carregando = false,
            )
        }
    }
}
