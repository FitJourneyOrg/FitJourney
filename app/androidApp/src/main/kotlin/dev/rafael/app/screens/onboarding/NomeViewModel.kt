package dev.rafael.app.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.me.Me
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NomeState(
    val nome: String = "",
    val carregando: Boolean = true,
    val salvando: Boolean = false,
    val erro: AppError? = null,
    val pronto: Boolean = false,
)

/**
 * Primeiro passo do onboarding: **confirmar o nome** (decisão 1-A.2).
 *
 * Note o verbo: confirmar, não preencher. O nome já existe quando esta tela abre — nasce junto
 * com a linha em `users`, derivado do e-mail (ARCH #33). Se ele fosse criado só aqui, haveria
 * uma janela entre o primeiro `GET /me` e o fim do quiz em que a coluna `NOT NULL` não teria
 * valor. O campo vem preenchido e a pessoa ajusta se quiser.
 *
 * ## Por que é uma tela do módulo `app`, e não um passo do `QuizViewModel`
 *
 * O quiz vive em `features/profile/presentation` e escreve em `profiles`. O nome mora em
 * `users` e vai por `PATCH /me`, cujo datasource está em `auth:data`. Um passo dentro do quiz
 * faria `profile` depender de `auth` — [REGRA] feature nunca depende de feature. Quem pode
 * costurar as duas é a camada de aplicação, e é aqui que ela está.
 */
class NomeViewModel(private val me: Me) : ViewModel() {

    private val _state = MutableStateFlow(NomeState())
    val state: StateFlow<NomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // `forcar`: a conta pode ter sido criada há segundos, e um cache velho de outra
            // sessão neste aparelho mostraria o nome de outra pessoa no campo.
            me.sincronizar(forcar = true)
        }
        viewModelScope.launch {
            me.observar().collect { usuario ->
                val nome = usuario?.displayName.orEmpty()
                _state.value = _state.value.copy(
                    // Não sobrescreve o que a pessoa está digitando.
                    nome = if (_state.value.nome.isBlank()) nome else _state.value.nome,
                    carregando = usuario == null,
                )
            }
        }
    }

    fun aoDigitar(v: String) { _state.value = _state.value.copy(nome = v, erro = null) }

    /**
     * Salva só se MUDOU. Quem gostou do nome sugerido toca em "Continuar" e segue — sem um
     * `PATCH` que não altera nada e sem risco de erro de rede barrando o onboarding por
     * uma escrita desnecessária.
     */
    fun continuar(nomeOriginal: String) {
        val novo = _state.value.nome
        if (novo.trim() == nomeOriginal.trim()) {
            _state.value = _state.value.copy(pronto = true)
            return
        }
        _state.value = _state.value.copy(salvando = true, erro = null)
        viewModelScope.launch {
            _state.value = when (val r = me.renomear(novo)) {
                is AppResult.Success -> _state.value.copy(salvando = false, nome = r.value, pronto = true)
                is AppResult.Failure -> _state.value.copy(salvando = false, erro = r.error)
            }
        }
    }
}
