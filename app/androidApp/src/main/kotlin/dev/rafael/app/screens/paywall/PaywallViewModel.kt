package dev.rafael.app.screens.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.domain.repository.Billing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallState(
    val isSubscribing: Boolean = false,
    val subscribed: Boolean = false,   // one-shot: a tela fecha e volta
    val error: AppError? = null,
)

/**
 * Página de paywall (Fase 7 — conversão). O passo de compra fica atrás da interface Billing
 * (hoje = compra simulada; Play Billing pluga depois). No sucesso, `subscribed` vira true e a
 * tela se fecha, voltando pra origem — que recarrega e já enxerga o premium (lido do servidor).
 */
class PaywallViewModel(
    private val billing: Billing,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallState())
    val state: StateFlow<PaywallState> = _state.asStateFlow()

    fun subscribe() {
        _state.update { it.copy(isSubscribing = true, error = null) }
        viewModelScope.launch {
            when (billing.subscribe()) {
                is AppResult.Success -> _state.update { it.copy(isSubscribing = false, subscribed = true) }
                is AppResult.Failure ->
                    _state.update { it.copy(isSubscribing = false, error = AppError.Unexpected("Não deu pra concluir a assinatura. Tente de novo.")) }
            }
        }
    }
}
