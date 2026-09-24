package dev.rafael.features.achievements.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.features.achievements.domain.Achievements
import dev.rafael.features.achievements.presentation.state.AchievementsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
