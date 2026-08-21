package dev.rafael.app.screens.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.me.Me
import dev.rafael.app.data.stats.Stats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MenuState(
    val id: String = "",
    val nome: String = "",
    val nivel: Int? = null,
)

/**
 * Cabeçalho do menu lateral: quem é você e em que nível está.
 *
 * Lê os DOIS caches locais (ARCH #30) e não a rede. O menu abre por gesto em qualquer tela, a
 * qualquer momento — se dependesse de requisição, abriria vazio no avião e o app inteiro
 * pareceria quebrado.
 *
 * ## Por que existe o [gatilho], e não um `combine` direto
 *
 * Este ViewModel é criado junto com o `AppNavHost` — o conteúdo do drawer entra em composição
 * antes de qualquer tela, inclusive antes do LOGIN. E `observar()` resolve a chave do cache
 * (`me:<uid>`) **uma vez, no início da coleta**. Criado antes do login, o uid é nulo, a chave
 * vira `"me:"` e o Flow fica preso a ela **para sempre**, porque este VM vive enquanto a
 * Activity viver. Resultado observado: cabeçalho eternamente em "?" / "Você", enquanto a tela
 * de perfil — cujo VM nasce depois do login — mostrava o nome certo.
 *
 * O [gatilho] faz a coleta REINICIAR quando o menu abre, e reiniciar é o que reavalia a chave.
 *
 * É a mesma família de defeito que o projeto já enfrentou ([REGRA] todo dado local é chaveado
 * por uid): o isolamento por conta continua correto, mas quem captura a chave cedo demais
 * captura a chave errada. Fica o débito: `Stats` e `Achievements` têm o mesmo padrão e só
 * escapam porque seus VMs nascem depois do login.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MenuViewModel(
    private val me: Me,
    private val stats: Stats,
) : ViewModel() {

    private val gatilho = MutableStateFlow(0)

    val state: StateFlow<MenuState> =
        gatilho.flatMapLatest {
            combine(me.observar(), stats.observar()) { usuario, progresso ->
                MenuState(
                    id = usuario?.id.orEmpty(),
                    nome = usuario?.displayName.orEmpty(),
                    nivel = progresso?.level,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuState())

    /**
     * Chamado quando o menu ABRE.
     *
     * Sem `forcar`: o TTL decide, então abrir e fechar cinco vezes não vira cinco `GET /me`.
     * O que se repete de graça é a releitura da chave.
     */
    fun aoAbrir() {
        gatilho.value++
        viewModelScope.launch { me.sincronizar() }
        viewModelScope.launch { stats.sincronizar() }
    }
}
