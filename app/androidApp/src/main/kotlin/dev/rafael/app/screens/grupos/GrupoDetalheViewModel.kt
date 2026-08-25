package dev.rafael.app.screens.grupos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.checkin.CheckIns
import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupMemberDto
import dev.rafael.contract.group.MemberRole
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class GrupoDetalheState(
    val grupo: GroupDto? = null,
    val membros: List<GroupMemberDto> = emptyList(),
    /** O FEED (8.0): os check-ins do grupo, mais recente primeiro. */
    val feed: List<CheckInDto> = emptyList(),
    val carregandoFeed: Boolean = true,
    val carregando: Boolean = true,
    val ocupado: Boolean = false,
    val erro: AppError? = null,
    val saiu: Boolean = false,
) {
    val souAdmin: Boolean get() = grupo?.myRole == MemberRole.ADMIN

    /**
     * Sou admin E estou sozinho — sair aqui APAGA o desafio (2.5-A).
     *
     * Vem de `memberCount`, do grupo, e não de `membros.size`: a lista pode ter falhado enquanto
     * o grupo carregou, e nesse caso `size` seria 0 e o diálogo ofereceria "Excluir" a quem tem
     * cinquenta pessoas no desafio. A contagem e a lista têm origens diferentes; quem manda numa
     * decisão irreversível é a que o servidor apurou.
     */
    val souUltimo: Boolean get() = souAdmin && grupo?.memberCount == 1
}

/**
 * Detalhe do grupo e gerência de membros (fatia A.4).
 *
 * **Gerar/revogar link de convite ficou de fora de propósito.** As rotas existem no servidor
 * desde a A.2 e estão testadas, mas link só serve com deep link, e deep link só funciona de
 * verdade com domínio https + App Links — as mensagerias não linkificam esquema próprio. Sem
 * domínio definido, o botão entregaria um UUID que ninguém consegue usar. O convite hoje
 * compartilha o CÓDIGO, que funciona em qualquer aplicativo. Ver DEBITOS.
 *
 * Vai à rede a cada abertura, sem cache: o **estado** do grupo é derivado do relógio do servidor,
 * e é dele que dependem as ações desta tela. Um `AGENDADO` velho ofereceria "convidar" para um
 * grupo que já começou — o servidor recusaria, e a culpa pareceria do app.
 */
class GrupoDetalheViewModel(
    private val groups: Groups,
    private val checkIns: CheckIns,
) : ViewModel() {

    private val _state = MutableStateFlow(GrupoDetalheState())
    val state: StateFlow<GrupoDetalheState> = _state.asStateFlow()

    private var enquete: Job? = null

    /**
     * POLLING de ~10s enquanto a tela está aberta (8.3, 10.2).
     *
     * Polling e não push: notificação é a fatia F, e mesmo lá a decisão foi **rejeitar** sync
     * silenciosa por push. Com a tela aberta, 10 segundos é o intervalo em que "o check-in do
     * amigo apareceu sozinho" ainda parece imediato.
     *
     * Amarrado ao ciclo de vida pela TELA (`ON_START`/`ON_STOP`): um laço que sobrevive à tela em
     * segundo plano é bateria e requisição que ninguém pediu.
     */
    fun iniciarEnquete(groupId: String) {
        if (enquete?.isActive == true) return
        enquete = viewModelScope.launch {
            while (isActive) {
                delay(INTERVALO_DO_FEED)
                carregarFeed(groupId)
            }
        }
    }

    fun pararEnquete() {
        enquete?.cancel()
        enquete = null
    }

    /**
     * Recarrega o feed sem mexer no resto da tela.
     *
     * **Falha em silêncio de propósito.** Este método roda a cada 10 segundos; transformar um
     * tropeço de rede em erro vermelho faria a tela piscar sozinha enquanto a pessoa lê. A lista
     * conhecida continua valendo — a mesma escolha do `sincronizar` dos grupos.
     */
    fun carregarFeed(groupId: String) {
        viewModelScope.launch {
            val r = checkIns.feed(groupId)
            _state.update { atual ->
                when (r) {
                    is AppResult.Success -> atual.copy(feed = r.value, carregandoFeed = false)
                    is AppResult.Failure -> atual.copy(carregandoFeed = false)
                }
            }
        }
    }

    /**
     * Apagar o próprio check-in (4.11).
     *
     * Recarrega o GRUPO inteiro, não só o feed: apagar libera o slot do dia, e quem sabe disso é
     * o `myCheckInToday` do grupo — é ele que faz o botão de check-in voltar. Recarregar só o
     * feed tiraria o item da lista e deixaria a pessoa sem o botão, sem entender por quê.
     */
    fun apagarCheckIn(groupId: String, checkInId: String) = agir(groupId, recarrega = true) {
        (checkIns.apagar(groupId, checkInId) as? AppResult.Failure)?.error
    }

    /**
     * Recarrega grupo + membros + feed.
     *
     * O esqueleto só aparece quando **ainda não há grupo**. Esta função também roda toda vez que a
     * tela volta ao foco — voltando do check-in, por exemplo — e ligar `carregando` ali faria a
     * tela inteira piscar em cima de um conteúdo que já estava correto.
     */
    fun carregar(groupId: String) {
        _state.update { it.copy(carregando = it.grupo == null, erro = null) }
        viewModelScope.launch {
            when (val g = groups.porId(groupId)) {
                is AppResult.Failure ->
                    _state.update { it.copy(carregando = false, erro = g.error) }
                is AppResult.Success -> {
                    // A falha da lista de membros PRECISA aparecer. A primeira versão fazia
                    // `as? AppResult.Success ?: emptyList()` e engolia o erro: a tela mostrava
                    // "PARTICIPANTES · 1" com nenhum participante embaixo, sem explicação
                    // nenhuma — e o defeito só apareceu porque eu reparei na contradição entre
                    // a contagem e a lista. Erro engolido é o mais caro de achar.
                    val m = groups.membros(groupId)
                    _state.update {
                        it.copy(
                            grupo = g.value,
                            membros = (m as? AppResult.Success)?.value.orEmpty(),
                            erro = (m as? AppResult.Failure)?.error,
                            carregando = false,
                        )
                    }
                    carregarFeed(groupId)
                }
            }
        }
    }

    override fun onCleared() {
        pararEnquete()
        super.onCleared()
    }

    fun expulsar(groupId: String, userId: String) = agir(groupId, recarrega = true) {
        (groups.expulsar(groupId, userId) as? AppResult.Failure)?.error
    }

    fun transferirAdmin(groupId: String, userId: String) = agir(groupId, recarrega = true) {
        (groups.transferirAdmin(groupId, userId) as? AppResult.Failure)?.error
    }

    fun sair(groupId: String) = agir(groupId) {
        when (val r = groups.sair(groupId)) {
            is AppResult.Success -> { _state.update { it.copy(saiu = true) }; null }
            is AppResult.Failure -> r.error
        }
    }

    /**
     * Um caminho só para toda ação: marca ocupado, executa, e recarrega quando a ação mudou a
     * composição do grupo. Sem isto, cada botão repetiria o mesmo cerimonial e um deles
     * esqueceria de recarregar — e a tela mostraria alguém que acabou de ser expulso.
     */
    private fun agir(groupId: String, recarrega: Boolean = false, bloco: suspend () -> AppError?) {
        // `update` e não leitura-e-escrita: o polling do feed escreve no MESMO estado a partir de
        // outra corrotina, e `bloco()` suspende no meio. É exatamente a corrida que fez a lista de
        // grupos sumir na fatia A.4.
        _state.update { it.copy(ocupado = true, erro = null) }
        viewModelScope.launch {
            val erro = bloco()
            _state.update { it.copy(ocupado = false, erro = erro) }
            if (erro == null && recarrega) carregar(groupId)
        }
    }

    private companion object {
        val INTERVALO_DO_FEED = 10.seconds
    }
}
