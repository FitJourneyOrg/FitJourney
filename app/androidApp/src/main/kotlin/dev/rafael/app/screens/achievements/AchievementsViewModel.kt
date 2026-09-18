package dev.rafael.app.screens.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.achievements.Achievements
import dev.rafael.contract.stats.AchievementDto
import dev.rafael.core.result.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AchievementsState(
    val conquistas: List<AchievementDto> = emptyList(),
    val carregandoInicial: Boolean = true,
    /** Já baixou o catálogo alguma vez. Sem isto, "não tem" e "não chegou" são o mesmo pixel. */
    val jaSincronizou: Boolean = false,
    /** Só importa quando a grade está vazia: com catálogo em cache, falha de sync é silêncio. */
    val erroSync: AppError? = null,
) {
    val desbloqueadas: List<AchievementDto> get() = conquistas.filter { it.unlocked }
    val bloqueadas: List<AchievementDto> get() = conquistas.filterNot { it.unlocked }

    /** "3 de 9" no topo — dá a dimensão do que falta sem obrigar a contar a grade. */
    val total: Int get() = conquistas.size
}

/**
 * Conquistas — OFFLINE-FIRST, como o Progresso.
 *
 * ## ⚠️ O raciocínio que estava aqui tinha um buraco (corrigido em 2026-09-11)
 *
 * Este KDoc dizia: *"não existe estado de erro aqui, de propósito: a grade vem do cache local,
 * então a rede falhar não tem consequência visível"*. A conclusão está certa **quando a premissa
 * vale** — e a premissa é *"há cache local"*.
 *
 * Na PRIMEIRA execução não há. Sem cache e sem rede, a tela não ficava vazia: ela afirmava, em
 * lima e negrito, `0 de 0` — dizendo que você tem zero de zero conquistas EXISTENTES, quando o
 * catálogo simplesmente não tinha chegado. Não é ausência de informação, é informação errada.
 *
 * > **Tela que não distingue "não tem" de "não chegou" não fica incompleta: ela mente com**
 * > **confiança.**
 *
 * O nível 1 do ARCH #31 (silêncio) continua valendo, e agora com a condição explícita: silêncio
 * **quando há dado local**. Sem dado local, é nível 2.
 */
class AchievementsViewModel(
    private val achievements: Achievements,
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    init {
        achievements.observar()
            .onEach { lista ->
                _state.update { it.copy(conquistas = lista, carregandoInicial = false) }
            }
            .launchIn(viewModelScope)
        sincronizar()
    }

    /**
     * @param forcar use ao voltar de um treino: o progresso mudou e a janela de 2 min do TTL
     * seguraria justamente a medalha que o usuário acabou de merecer.
     */
    fun sincronizar(forcar: Boolean = false) {
        viewModelScope.launch {
            val erro = achievements.sincronizar(forcar)
            val sincronizou = achievements.jaSincronizou()
            _state.update { it.copy(jaSincronizou = sincronizou, erroSync = erro) }
        }
    }
}
