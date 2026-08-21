package dev.rafael.app.screens.grupos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.groups.Groups
import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupRule
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

data class GrupoFormState(
    val titulo: String = "",
    val descricao: String = "",
    val inicio: LocalDate,
    val fim: LocalDate,
    /** Fuso do APARELHO como sugestão. O admin pode trocar enquanto o grupo é `AGENDADO`. */
    val fuso: String,
    val regras: Set<GroupRule> = emptySet(),
    val salvando: Boolean = false,
    val erro: AppError? = null,
    val criadoId: String? = null,
)

/**
 * Criação de grupo (decisão 2-A).
 *
 * O cliente **não repete** as validações do servidor (título ≤ 60, início ≥ amanhã, fuso IANA,
 * emoji exige foto). Duas cópias da mesma regra divergem, e a errada seria a que o usuário vê.
 * O que ele faz é **sugerir bem**: datas iniciais que já passam na regra e o fuso do aparelho.
 * O erro volta em `fieldErrors` e cada campo se marca sozinho (#31).
 *
 * A única regra espelhada é a amarração `EMOJI_DO_DIA ⇒ FOTO`, e não como validação: marcar o
 * emoji **liga** a foto na hora. Deixar o usuário montar uma combinação impossível para o
 * servidor recusar depois seria desenhar uma armadilha.
 */
class GrupoFormViewModel(
    private val groups: Groups,
    clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(inicial(clock))
    val state: StateFlow<GrupoFormState> = _state.asStateFlow()

    fun aoDigitarTitulo(v: String) { _state.value = _state.value.copy(titulo = v, erro = null) }
    fun aoDigitarDescricao(v: String) { _state.value = _state.value.copy(descricao = v, erro = null) }
    fun aoEscolherInicio(d: LocalDate) { _state.value = _state.value.copy(inicio = d, erro = null) }
    fun aoEscolherFim(d: LocalDate) { _state.value = _state.value.copy(fim = d, erro = null) }
    fun aoEscolherFuso(v: String) { _state.value = _state.value.copy(fuso = v, erro = null) }

    /** [INVARIANTE] `EMOJI_DO_DIA` implica `FOTO` — reproduzir um emoji exige onde mostrá-lo. */
    fun alternarRegra(regra: GroupRule) {
        val atuais = _state.value.regras
        val novas = if (regra in atuais) {
            // Desmarcar a foto desmarca o emoji junto: sem foto, não há onde mostrá-lo.
            if (regra == GroupRule.FOTO) atuais - GroupRule.FOTO - GroupRule.EMOJI_DO_DIA
            else atuais - regra
        } else {
            // Marcar o emoji liga a foto na hora, em vez de deixar o servidor recusar depois.
            if (regra == GroupRule.EMOJI_DO_DIA) atuais + GroupRule.EMOJI_DO_DIA + GroupRule.FOTO
            else atuais + regra
        }
        _state.value = _state.value.copy(regras = novas, erro = null)
    }

    fun criar() {
        val s = _state.value
        _state.value = s.copy(salvando = true, erro = null)
        viewModelScope.launch {
            val req = CreateGroupRequest(
                title = s.titulo,
                description = s.descricao.takeIf { it.isNotBlank() },
                startDate = s.inicio.toString(),
                endDate = s.fim.toString(),
                timezone = s.fuso,
                rules = s.regras.toList(),
            )
            when (val r = groups.criar(req)) {
                is AppResult.Success ->
                    _state.value = _state.value.copy(salvando = false, criadoId = r.value.id)
                is AppResult.Failure ->
                    _state.value = _state.value.copy(salvando = false, erro = r.error)
            }
        }
    }
}

/**
 * Sugestão inicial do formulário.
 *
 * Começa **amanhã** porque é o mínimo que o servidor aceita (a janela de entrada precisa existir),
 * e dura 30 dias porque é a duração típica de um desafio. Sugerir bem economiza o erro previsível
 * de quem só quer criar rápido — sem tirar do servidor a palavra final.
 */
private fun inicial(clock: Clock): GrupoFormState {
    val fuso = TimeZone.currentSystemDefault()
    val hoje = clock.now().toLocalDateTime(fuso).date
    return GrupoFormState(
        inicio = hoje.plus(1, DateTimeUnit.DAY),
        fim = hoje.plus(31, DateTimeUnit.DAY),
        fuso = fuso.id,
    )
}
