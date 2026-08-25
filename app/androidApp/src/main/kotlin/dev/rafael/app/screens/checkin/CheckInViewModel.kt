package dev.rafael.app.screens.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.checkin.CheckIns
import dev.rafael.app.data.checkin.LocalSugerido
import dev.rafael.app.data.checkin.Localizador
import dev.rafael.app.data.checkin.PrecisaoDoLocal
import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.GroupRule
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckInState(
    val grupo: GroupDto? = null,
    val carregando: Boolean = true,

    /** JPEG já comprimido e com a rotação aplicada — pronto para subir. */
    val foto: ByteArray? = null,

    val local: LocalSugerido? = null,
    /** O que a pessoa vai publicar. Começa na sugestão e é editável (5.2). */
    val nomeDoLocal: String = "",
    val buscandoLocal: Boolean = false,

    val enviando: Boolean = false,
    val erro: AppError? = null,
    val pronto: Boolean = false,
) {
    val exigeFoto: Boolean
        get() = grupo?.rules.orEmpty().let { GroupRule.FOTO in it || GroupRule.EMOJI_DO_DIA in it }

    val exigeLocal: Boolean get() = GroupRule.LOCALIZACAO in grupo?.rules.orEmpty()

    /**
     * Espelha a checagem ESTRUTURAL do servidor (`CheckInPolicy.regrasNaoCumpridas`).
     *
     * Duplicar a regra aqui é deliberado, e não desconfiança do servidor: é o que permite o botão
     * nascer desabilitado em vez de a pessoa tocar e levar um 400. Quem decide continua sendo o
     * servidor — isto só evita oferecer o que vai ser recusado.
     */
    val podeEnviar: Boolean
        get() = !enviando && grupo != null &&
            (!exigeFoto || foto != null) &&
            (!exigeLocal || (nomeDoLocal.isNotBlank() && local != null))

    // O `ByteArray` no state quebra o equals/hashCode gerado (compara referência). Não comparamos
    // estados, e o StateFlow usa igualdade só para descartar emissões repetidas — descartar de
    // menos é inofensivo, descartar de mais não acontece.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Fazer check-in (fatia B).
 *
 * **Online-only, sem otimismo** (10.1): o dia em que o check-in cai é decidido pelo fuso do
 * servidor, e o "um por dia" é um índice único no banco. Mostrar um check-in local antes da
 * confirmação seria arriscar apagá-lo da tela logo depois de a pessoa comemorar.
 */
class CheckInViewModel(
    private val groups: Groups,
    private val checkIns: CheckIns,
    private val localizador: Localizador,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckInState())
    val state: StateFlow<CheckInState> = _state.asStateFlow()

    fun carregar(groupId: String) {
        _state.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            when (val r = groups.porId(groupId)) {
                is AppResult.Failure -> _state.update { it.copy(carregando = false, erro = r.error) }
                is AppResult.Success -> _state.update { it.copy(grupo = r.value, carregando = false) }
            }
        }
    }

    fun aoFotografar(jpeg: ByteArray) = _state.update { it.copy(foto = jpeg, erro = null) }

    fun descartarFoto() = _state.update { it.copy(foto = null) }

    /**
     * Busca a posição. Chamada quando a permissão já foi concedida — quem pede é a tela.
     *
     * A precisão EFETIVA vem do `Localizador`, não do que pedimos: sem a permissão fina o sistema
     * devolve dado grosso em silêncio, e um rótulo de rua calculado a partir dele seria uma rua
     * onde a pessoa nunca esteve.
     */
    fun localizar(precisao: PrecisaoDoLocal = PrecisaoDoLocal.APROXIMADA) {
        _state.update { it.copy(buscandoLocal = true) }
        viewModelScope.launch {
            val achado = localizador.onde(precisao)
            _state.update { atual ->
                atual.copy(
                    local = achado,
                    buscandoLocal = false,
                    // Só sobrescreve o texto se a pessoa ainda não digitou o dela. Trocar o que
                    // ela escreveu por uma sugestão nova seria apagar trabalho na frente dela.
                    nomeDoLocal = if (atual.nomeDoLocal.isBlank() || atual.nomeDoLocal == atual.local?.sugestao) {
                        achado?.sugestao.orEmpty()
                    } else {
                        atual.nomeDoLocal
                    },
                )
            }
        }
    }

    fun aoDigitarLocal(texto: String) =
        _state.update { it.copy(nomeDoLocal = texto.take(MAX_LOCAL), erro = null) }

    fun enviar(groupId: String) {
        val atual = _state.value
        if (!atual.podeEnviar) return
        _state.update { it.copy(enviando = true, erro = null) }
        viewModelScope.launch {
            val r = checkIns.fazer(
                groupId = groupId,
                foto = atual.foto,
                nomeDoLocal = atual.nomeDoLocal.trim().takeIf { it.isNotBlank() },
                latitude = atual.local?.latitude,
                longitude = atual.local?.longitude,
            )
            _state.update {
                when (r) {
                    is AppResult.Success -> it.copy(enviando = false, pronto = true)
                    is AppResult.Failure -> it.copy(enviando = false, erro = r.error)
                }
            }
        }
    }

    private companion object {
        /** Espelha `CheckInPolicy.MAX_NOME_DO_LOCAL` no servidor. */
        const val MAX_LOCAL = 60
    }
}
