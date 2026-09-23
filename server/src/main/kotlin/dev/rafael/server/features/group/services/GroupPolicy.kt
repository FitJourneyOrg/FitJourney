package dev.rafael.server.features.group.services

import dev.rafael.contract.group.CreateGroupRequest
import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupState
import dev.rafael.contract.group.JoinBlock
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.error.ErrorFields
import dev.rafael.contract.limites.Limites

/**
 * Regras do GRUPO (ARCH #33, fatia A.1). Kotlin PURO, sem I/O — como `XpPolicy`,
 * `AchievementPolicy` e `DisplayNamePolicy`.
 *
 * As três decisões de maior consequência da fase moram aqui, e todas são funções: o estado
 * derivado, a validação do formulário e a geração do código. Nada disso pode depender de banco
 * para ser exercitado, porque é onde as regras do produto de fato vivem.
 */
object GroupPolicy {

    // Fonte real: shared-contract (debitos.md "12 frases cravam constante do servidor").
    const val TITULO_MAX = Limites.Group.TITULO_MAX
    const val DESCRICAO_MAX = Limites.Group.DESCRICAO_MAX
    // MAX_MEMBROS alimenta `enum_bloqueio_lotado` no cliente.
    const val MAX_MEMBROS = Limites.Group.MAX_MEMBROS
    const val TAMANHO_DO_CODIGO = 6

    /**
     * Alfabeto do código, **sem `O`/`0` e sem `I`/`1`**.
     *
     * O código é ditado em voz alta e digitado à mão por quem quer entrar. Confundir zero com
     * ó manda a pessoa para "grupo não encontrado" — ou, pior, para outro grupo. Tirar quatro
     * caracteres custa 4% do espaço de combinações e elimina a classe inteira de erro.
     */
    const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /**
     * ESTADO DERIVADO — a decisão que dispensa a coluna `status` e o job de virada.
     *
     * O "hoje" é resolvido no fuso DO GRUPO, não no do servidor nem no do aparelho: um desafio
     * criado em São Paulo tem de virar à meia-noite de São Paulo, mesmo que o servidor rode em
     * UTC e o membro esteja em Lisboa. É a mesma razão pela qual o check-in conta o dia no fuso
     * do grupo (4.6) — sem isso, "um check-in por dia" significaria coisas diferentes para
     * pessoas diferentes no mesmo grupo.
     *
     * O grupo vale do primeiro instante de [inicio] ao último de [fim], ambos inclusive.
     */
    fun estado(inicio: LocalDate, fim: LocalDate, agora: Instant, fuso: TimeZone): GroupState {
        val hoje = agora.toLocalDateTime(fuso).date
        return when {
            hoje < inicio -> GroupState.AGENDADO
            hoje > fim -> GroupState.ENCERRADO
            else -> GroupState.ATIVO
        }
    }

    /** Prazo do link de convite (2.13). O teto real é o início do grupo — ver [validadeDoConvite]. */
    val PRAZO_DO_CONVITE: Duration = 7.days

    /**
     * Quando o link de convite vence: o **menor** entre 7 dias e o instante em que o grupo
     * começa (2.13 + 2-B.0).
     *
     * O início é o teto real porque depois dele a entrada fecha de qualquer jeito. Um link que
     * "funciona" e leva a uma recusa é pior que um link vencido — o vencido ao menos explica o
     * que houve, enquanto o outro parece defeito do app.
     */
    fun validadeDoConvite(agora: Instant, inicio: LocalDate, fuso: TimeZone): Instant {
        val comecoDoGrupo = inicio.atStartOfDayIn(fuso)
        return minOf(agora + PRAZO_DO_CONVITE, comecoDoGrupo)
    }

    /**
     * Dá para entrar neste grupo AGORA? Devolve o impedimento, ou `null` se pode.
     *
     * Função pura e única: a mesma resposta serve a tela de preview (que desabilita o botão) e
     * a rota de entrada (que recusa). Duas implementações — uma para exibir, outra para
     * decidir — divergiriam, e a que estaria errada seria justamente a que o usuário vê.
     */
    fun impedimentoParaEntrar(
        estado: GroupState,
        membros: Int,
        jaEMembro: Boolean,
    ): JoinBlock? = when {
        jaEMembro -> JoinBlock.JA_E_MEMBRO
        estado == GroupState.ATIVO -> JoinBlock.JA_COMECOU
        estado == GroupState.ENCERRADO -> JoinBlock.ENCERRADO
        membros >= MAX_MEMBROS -> JoinBlock.LOTADO
        else -> null
    }

    /**
     * Gera um código candidato. Quem garante a unicidade é o `UNIQUE` do banco — esta função
     * só sorteia; o repositório tenta de novo no conflito.
     *
     * `Random` injetado para o teste ser determinístico. Sortear dentro da função tornaria
     * "o alfabeto não tem caracteres ambíguos" uma afirmação impossível de verificar.
     */
    fun gerarCodigo(random: Random = Random.Default): String =
        (1..TAMANHO_DO_CODIGO).map { ALFABETO[random.nextInt(ALFABETO.length)] }.joinToString("")

    /**
     * Valida o formulário de criação (2-A) e devolve os dados já normalizados.
     *
     * @param agora relógio do SERVIDOR ([REGRA]: nunca o do cliente — senão bastaria adiantar o
     * aparelho para criar um grupo que nasce ativo e pular a janela de entrada).
     */
    fun validarCriacao(req: CreateGroupRequest, agora: Instant): AppResult<GrupoValidado> {
        // Valor = CÓDIGO, não frase (débito "erroDoCampo devolve frase do servidor", debitos.md,
        // fechado 2026-09-23) — o cliente traduz via TextosDeErro.de(), igual ao `code` de nível
        // superior. `fieldErrors` diz QUAL campo E QUAL dos dez motivos; o `code` de fora
        // (CAMPOS_DO_GRUPO_INVALIDOS) só diz "revise o formulário".
        val erros = mutableMapOf<String, String>()

        val titulo = req.title.trim().replace(ESPACOS, " ")
        if (titulo.isBlank()) erros[ErrorFields.TITLE] = ErrorCodes.GRUPO_TITULO_VAZIO
        else if (titulo.length > TITULO_MAX) erros[ErrorFields.TITLE] = ErrorCodes.GRUPO_TITULO_LONGO

        val descricao = req.description?.trim()?.replace(ESPACOS, " ")?.takeIf { it.isNotBlank() }
        if (descricao != null && descricao.length > DESCRICAO_MAX) {
            erros[ErrorFields.DESCRIPTION] = ErrorCodes.GRUPO_DESCRICAO_LONGA
        }

        val fuso = fusoValido(req.timezone)
        if (fuso == null) erros[ErrorFields.TIMEZONE] = ErrorCodes.GRUPO_FUSO_INVALIDO

        val inicio = req.startDate.paraData()
        val fim = req.endDate.paraData()
        if (inicio == null) erros[ErrorFields.START_DATE] = ErrorCodes.GRUPO_DATA_INICIO_INVALIDA
        if (fim == null) erros[ErrorFields.END_DATE] = ErrorCodes.GRUPO_DATA_FIM_INVALIDA

        if (inicio != null && fim != null && fuso != null) {
            if (fim <= inicio) {
                erros[ErrorFields.END_DATE] = ErrorCodes.GRUPO_FIM_ANTES_DO_INICIO
            }
            // O grupo precisa nascer AGENDADO. Se pudesse começar hoje, nasceria ATIVO — e como
            // AGENDADO é a ÚNICA janela de entrada (2-B), a janela de convite teria duração
            // zero. Ninguém entraria, e o convite é o gargalo do produto (2-B.0).
            val hoje = agora.toLocalDateTime(fuso).date
            if (inicio <= hoje) {
                erros[ErrorFields.START_DATE] = ErrorCodes.GRUPO_INICIO_MUITO_CEDO
            }
        }

        // [INVARIANTE] EMOJI_DO_DIA implica FOTO: reproduzir um emoji exige onde mostrá-lo. Sem
        // a amarração dá para configurar um grupo impossível de cumprir.
        val regras = req.rules.toSet()
        if (GroupRule.EMOJI_DO_DIA in regras && GroupRule.FOTO !in regras) {
            erros[ErrorFields.RULES] = ErrorCodes.GRUPO_REGRA_EMOJI_SEM_FOTO
        }
        if (GroupRule.GYM_PASS in regras) {
            erros[ErrorFields.RULES] = ErrorCodes.GRUPO_REGRA_GYMPASS_INDISPONIVEL
        }

        if (erros.isNotEmpty()) {
            return AppError.Validation("Revise os campos do grupo.", erros, code = ErrorCodes.CAMPOS_DO_GRUPO_INVALIDOS).asFailure()
        }
        return GrupoValidado(
            titulo = titulo,
            descricao = descricao,
            inicio = inicio!!,
            fim = fim!!,
            fuso = fuso!!,
            regras = regras,
        ).asSuccess()
    }

    /** Saída de [validarCriacao]: os mesmos dados, já aparados e com os tipos certos. */
    data class GrupoValidado(
        val titulo: String,
        val descricao: String?,
        val inicio: LocalDate,
        val fim: LocalDate,
        val fuso: TimeZone,
        val regras: Set<GroupRule>,
    )

    /**
     * Fuso NOMEADO, nunca OFFSET NUMÉRICO.
     *
     * `TimeZone.of()` sozinho não basta: aceita `-03:00` e devolve um `FixedOffsetTimeZone` sem
     * reclamar. Um grupo criado assim funcionaria até o primeiro domingo de horário de verão e
     * então erraria a virada do dia em uma hora — calado, meses depois, num check-in das 23h30
     * que cairia no dia seguinte e sumiria do ranking.
     *
     * **A regra recusa o que é offset, e não "o que não tem barra".** A primeira versão exigia
     * `UTC` ou barra, e recusou `GMT` — que o próprio emulador reporta como fuso do aparelho, e
     * que é nomeado e **sem horário de verão**, ou seja, imune ao problema. Validação estrita
     * demais bloqueia o usuário honesto sem impedir nada: é o mesmo erro que a ideia do EXIF
     * cometia (ver ARCH #33).
     */
    private fun fusoValido(bruto: String): TimeZone? {
        val id = bruto.trim()
        if (OFFSET.matches(id)) return null
        return runCatching { TimeZone.of(id) }.getOrNull()
    }

    /** `-03:00`, `+05:30`, `UTC-3`, `GMT+2` — tudo que é deslocamento cravado, não lugar. */
    private val OFFSET = Regex("^(UTC|GMT|Z)?[+-].*$|^[+-].*$")

    private val ESPACOS = Regex("\\s+")

    private fun String.paraData(): LocalDate? = runCatching { LocalDate.parse(trim()) }.getOrNull()
}
