package dev.rafael.app.screens.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.achievements.Achievements
import dev.rafael.app.data.me.Me
import dev.rafael.app.data.stats.Stats
import dev.rafael.contract.stats.AchievementDto
import dev.rafael.contract.stats.UserStatsDto
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PerfilState(
    val id: String = "",
    val nome: String = "",
    val stats: UserStatsDto? = null,
    val conquistas: List<AchievementDto> = emptyList(),
) {
    val desbloqueadas: Int get() = conquistas.count { it.unlocked }
}

/**
 * PERFIL — a vitrine do perfil individual (ARCH #34).
 *
 * O que aparece aqui é o que QUALQUER PESSOA poderá ver quando a fatia A.1 permitir abrir o
 * perfil de outro usuário: nome, inicial colorida, nível, conquistas e totais. XP bruto e
 * streak ficam de fora de propósito — são os números que mudam todo dia, e é neles que a
 * comparação social pega. Nível e conquistas são marcos.
 *
 * Escrever a tela já com essa disciplina, antes de existir perfil de terceiro, é o que evita
 * a fatia A.1 virar uma auditoria de "o que aqui não pode vazar".
 */
class PerfilViewModel(
    private val me: Me,
    private val stats: Stats,
    private val conquistas: Achievements,
) : ViewModel() {

    val state: StateFlow<PerfilState> =
        combine(me.observar(), stats.observar(), conquistas.observar()) { usuario, progresso, medalhas ->
            PerfilState(
                id = usuario?.id.orEmpty(),
                nome = usuario?.displayName.orEmpty(),
                stats = progresso,
                conquistas = medalhas,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerfilState())

    init { atualizar() }

    /** Cache-first: a tela desenha do local no primeiro frame; isto só busca o que envelheceu. */
    fun atualizar() {
        viewModelScope.launch { me.sincronizar() }
        viewModelScope.launch { stats.sincronizar() }
        viewModelScope.launch { conquistas.sincronizar() }
    }
}
