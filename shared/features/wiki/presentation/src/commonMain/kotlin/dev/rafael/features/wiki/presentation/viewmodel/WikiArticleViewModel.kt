package dev.rafael.features.wiki.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.features.wiki.domain.model.WikiArticle
import dev.rafael.features.wiki.domain.repository.WikiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * [carregando] existe pelo mesmo motivo do estado da lista: antes da primeira emissão do banco,
 * `artigo == null` significa "ainda não sei", e não "este slug não existe". Sem a distinção, abrir
 * um artigo mostraria "não encontrado" por um instante em toda abertura.
 */
data class WikiArticleState(
    val artigo: WikiArticle? = null,
    val carregando: Boolean = true,
)

/**
 * Lê do banco LOCAL pelo slug, não de um argumento de navegação carregando o artigo inteiro: a
 * tela reabre igual depois de morte de processo, e um deep link futuro (#36 já tem a infra de
 * push) chega com um slug na mão, não com um objeto.
 */
class WikiArticleViewModel(
    slug: String,
    repository: WikiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WikiArticleState())
    val state: StateFlow<WikiArticleState> = _state.asStateFlow()

    init {
        repository.observeArticle(slug)
            .onEach { encontrado -> _state.update { it.copy(artigo = encontrado, carregando = false) } }
            .launchIn(viewModelScope)
    }
}
