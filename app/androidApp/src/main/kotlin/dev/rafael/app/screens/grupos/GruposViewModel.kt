package dev.rafael.app.screens.grupos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.group.GroupDto
import dev.rafael.core.result.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GruposState(
    val grupos: List<GroupDto> = emptyList(),
    val carregando: Boolean = true,
    /** O usuário PEDIU atualização (puxou a lista). Diferente de carregar sozinho. */
    val atualizando: Boolean = false,
    /**
     * Já baixou alguma vez nesta conta e neste aparelho.
     *
     * Distingue "não baixei ainda" de "você não tem grupo nenhum" — a mesma lição da Home, onde
     * a falta disso convidava quem já tinha programas a criar tudo de novo.
     */
    val jaSincronizou: Boolean = false,
    val erroSync: AppError? = null,
) {
    val vazio: Boolean get() = grupos.isEmpty()
}

/**
 * A aba Grupos (ARCH #33, fatia A.3). Cache-first: a lista aparece no primeiro frame, offline
 * inclusive, e o sync de fundo só atualiza.
 */
class GruposViewModel(private val groups: Groups) : ViewModel() {

    private val _state = MutableStateFlow(GruposState())
    val state: StateFlow<GruposState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            groups.observar().collect { lista ->
                // NÃO mexe em `carregando`. O cache emite no primeiro frame, e emite VAZIO
                // quando não há nada guardado — se isso encerrasse o carregamento, o esqueleto
                // nunca apareceria e o "Nenhum desafio ainda" piscaria antes de a rede
                // responder. Quem termina o carregamento é o SYNC.
                //
                // `update` e não `_state.value = _state.value.copy(...)`: este coletor e o sync
                // escrevem no MESMO estado a partir de corrotinas diferentes. Ver o comentário
                // em `sincronizar` — a versão com leitura-e-escrita perdia esta emissão.
                _state.update { it.copy(grupos = lista) }
            }
        }
        // Sem sync aqui: quem dispara é a tela ao entrar (`carregar`), e o primeiro `ON_RESUME`
        // acontece logo após a criação do VM. Sincronizar nos dois lugares daria duas
        // requisições na abertura.
    }

    /**
     * A tela ENTROU em foco. Vai à rede **ignorando o TTL**.
     *
     * Por que aqui o TTL não serve: ele protege o que muda pela ação do PRÓPRIO usuário — o XP
     * da Home é assim, e por isso lá o cache-first com invalidação na mutação cobre tudo. A
     * contagem de membros muda por ação de OUTRAS pessoas, e nesse caso o TTL não é economia,
     * é atraso: quem acabou de dizer "entra aí" para um amigo abre a lista e vê o número velho,
     * sem nada a fazer além de esperar.
     *
     * O custo é um `GET /groups` por entrada na aba — uma requisição pequena, e não as seis que
     * motivaram o TTL na Home.
     *
     * A solução definitiva é a notificação agregada (decisão 10.6, fatia F): aí o aviso chega
     * sem ninguém precisar abrir nada.
     */
    fun carregar() = sincronizar(forcar = true)

    /**
     * O usuário PUXOU a lista. Ignora o TTL de propósito.
     *
     * A contagem de membros muda por ação de OUTRAS pessoas — é o caso clássico de "o que você
     * não controla", e por isso o TTL de 5 minutos está certo como padrão. Mas sem um gesto de
     * forçar, quem sabe que alguém acabou de entrar fica esperando cinco minutos sem entender
     * por quê. Puxar é intenção explícita, e intenção explícita vence cache.
     */
    fun atualizar() = sincronizar(forcar = true)

    /**
     * **Nunca leia-e-escreva `_state` atravessando uma suspensão.**
     *
     * A primeira versão fazia `_state.value = _state.value.copy(jaSincronizou = groups.jaSincronizou(), ...)`.
     * Parece uma linha só, mas são três passos: lê `_state.value`, SUSPENDE em `jaSincronizou()`,
     * e escreve. Na suspensão a thread principal fica livre — e é exatamente aí que o coletor do
     * cache entrega a lista recém-gravada. A escrita seguinte usava o retrato ANTIGO e apagava os
     * grupos que tinham acabado de chegar.
     *
     * O sintoma era perfeito: esqueleto rápido, "Nenhum desafio ainda" com 4 grupos no banco, e
     * nada mais acontecia — porque não havia mais nenhuma gravação para disparar outra emissão.
     * Sair da aba e voltar "consertava" porque criava um ViewModel novo, que lê o cache já cheio.
     *
     * Duas defesas: resolver o valor suspenso ANTES de tocar no estado, e `update`, que refaz o
     * cálculo se alguém escreveu no meio.
     */
    private fun sincronizar(forcar: Boolean) {
        if (forcar) _state.update { it.copy(atualizando = true) }
        viewModelScope.launch {
            groups.sincronizar(forcar)
            val sincronizou = groups.jaSincronizou()
            _state.update {
                it.copy(jaSincronizou = sincronizou, carregando = false, atualizando = false)
            }
        }
    }
}
