package dev.rafael.features.wiki.presentation.state

import dev.rafael.contract.wiki.WikiCategory

sealed interface WikiListEvent {
    /** `null` = "Tudo" (sem filtro). */
    data class CategoriaSelecionada(val categoria: WikiCategory?) : WikiListEvent
    data object Refresh : WikiListEvent
}
