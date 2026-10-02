package dev.rafael.features.wiki.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.contract.wiki.WikiCategory
import dev.rafael.core.result.AppResult
import dev.rafael.features.wiki.domain.model.WikiArticle
import dev.rafael.features.wiki.domain.repository.WikiRepository
import dev.rafael.features.wiki.presentation.state.WikiListEvent
import dev.rafael.features.wiki.presentation.state.WikiListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A lista do "Aprender".
 *
 * Observa o banco LOCAL (ARCH #30) e guarda o acervo inteiro em memória: são ~12 artigos, então o
 * filtro por chip é `filter` numa lista, não consulta nova. Uma ida ao SQL por toque de chip seria
 * I/O para reordenar o que já está na mão.
 */
class WikiViewModel(private val repository: WikiRepository) : ViewModel() {

    private val _state = MutableStateFlow(WikiListState())
    val state: StateFlow<WikiListState> = _state.asStateFlow()

    /** O acervo completo, sem filtro. O estado carrega a fatia visível; isto carrega a verdade. */
    private var acervo: List<WikiArticle> = emptyList()

    init {
        repository.observeArticles()
            .onEach { lista ->
                acervo = lista
                _state.update { atual -> atual.recalculado(acervo) }
            }
            .launchIn(viewModelScope)

        // Sync de fundo, com TTL de 24h no repositório: o conteúdo só muda em deploy.
        refresh(forcar = false)
    }

    fun onEvent(event: WikiListEvent) {
        when (event) {
            is WikiListEvent.CategoriaSelecionada ->
                _state.update { it.copy(categoriaSelecionada = event.categoria).recalculado(acervo) }
            WikiListEvent.Refresh -> refresh(forcar = true)   // o usuário pediu: fura o TTL
        }
    }

    private fun refresh(forcar: Boolean) {
        _state.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.refresh(forcar)) {
                is AppResult.Success -> _state.update { it.copy(isRefreshing = false) }
                // ⚠️ NÃO mexe em `artigos`: offline-first. Falhar o sync não pode apagar da tela o
                // que já está no aparelho -- o erro é aviso, não apagador.
                is AppResult.Failure -> _state.update { it.copy(isRefreshing = false, error = r.error) }
            }
        }
    }
}

/**
 * Recalcula a fatia visível a partir do acervo inteiro e do filtro atual.
 *
 * Isolado como função (e `internal`) porque é a única regra real desta tela: **as categorias saem
 * do que EXISTE**, não do enum, e o destaque só aparece quando não há filtro. Testar isso não pode
 * depender de montar ViewModel.
 */
internal fun WikiListState.recalculado(acervo: List<WikiArticle>): WikiListState {
    // Ordem do enum, não de chegada: a fileira de chips não pode dançar entre um sync e outro.
    val categorias = WikiCategory.entries.filter { c -> acervo.any { it.category == c } }
    val filtrado = categoriaSelecionada?.let { c -> acervo.filter { it.category == c } } ?: acervo
    val destaque = if (categoriaSelecionada == null) filtrado.firstOrNull { it.featured } else null
    return copy(
        destaque = destaque,
        artigos = filtrado.filter { it.slug != destaque?.slug },
        categorias = categorias,
        carregando = false,
    )
}
