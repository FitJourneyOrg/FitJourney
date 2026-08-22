package dev.rafael.app.screens.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.me.Me
import dev.rafael.app.data.sessao.SairDaConta
import dev.rafael.app.data.stats.Stats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 * Este ViewModel é criado junto com o `AppNavHost`, ou seja, **antes do login** — e vive
 * enquanto a Activity viver. Isso já causou o cabeçalho eternamente em "?": os repositórios
 * resolviam a chave do cache uma vez, no início da coleta, e ficavam presos ao uid nulo.
 * A correção mora onde o defeito nascia: `TokenProvider.uidFlow()` re-chaveia quando a sessão
 * muda, então aqui um `combine` simples basta.
 */
class MenuViewModel(
    private val me: Me,
    private val stats: Stats,
    private val sairDaConta: SairDaConta,
) : ViewModel() {

    private val _saiu = MutableStateFlow(false)
    val saiu: StateFlow<Boolean> = _saiu.asStateFlow()

    val state: StateFlow<MenuState> =
        combine(me.observar(), stats.observar()) { usuario, progresso ->
            MenuState(
                id = usuario?.id.orEmpty(),
                nome = usuario?.displayName.orEmpty(),
                nivel = progresso?.level,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuState())

    /**
     * Chamado quando o menu ABRE. Sem `forcar`: o TTL decide, então abrir e fechar cinco vezes
     * não vira cinco `GET /me`. Nome e nível mudam por ação do próprio usuário — é o caso em
     * que o TTL protege bem (ver a emenda da regra de frescor no Painel).
     */
    fun aoAbrir() {
        viewModelScope.launch { me.sincronizar() }
        viewModelScope.launch { stats.sincronizar() }
    }

    /**
     * O "Sair" do rodapé agora SAI, em vez de navegar para Configurações.
     *
     * A sequência é a mesma que a tela de conta usa — [SairDaConta] é o dono. Duas portas para
     * a mesma ação são normais; duas cópias da lógica é que seriam o problema.
     */
    fun sair() {
        viewModelScope.launch {
            sairDaConta()
            _saiu.value = true
        }
    }
}
