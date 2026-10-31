package dev.rafael.server.features.exercise.services

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.profile.Level
import dev.rafael.contract.promotion.PromotionStatusDto
import dev.rafael.contract.promotion.PromotionSuggestionDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.MesocicloRepository
import dev.rafael.server.features.exercise.db.ProgressaoRepository
import dev.rafael.server.features.exercise.db.PromotionDeclineRepository
import dev.rafael.server.features.exercise.engine.v3.CicloDoMesociclo
import dev.rafael.server.features.exercise.engine.v3.PromocaoDeNivel
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.user.services.UserService
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Service/rota da promoção de nível — débito (b) de "o motor sabe, o app não mostra"
 * (`docs/fontes/debitos.md`, consolidado da Fatia 11): INICIANTE→INTERMEDIATE fechado em
 * 2026-10-29, INTERMEDIATE→ADVANCED fechado em 2026-10-31 (V72 — `progression_event.um_rm_kg`,
 * `docs/trabalho/promocao-avancado.md`).
 *
 * ## Âncora é comportamento, não ponteiro de UI
 *
 * As âncoras vêm de [ProgressaoRepository.exerciciosComEventoNoMesociclo] — quem a pessoa REALMENTE
 * treinou no mesociclo atual —, nunca de `ProgramRepository`/`users.active_program_id`. Esse campo
 * é documentado como "destaque de UI, não mexe em sessão/histórico" (V60); dar a ele um segundo
 * papel de decisão de domínio é o mesmo padrão que já custou caro três vezes nesta fatia, e o caso
 * nulo (programa nunca "ativado") falharia CALADO — pessoa treina, nunca recebe diagnóstico, nada
 * reporta. Comportamento é imune a troca de programa; ponteiro de UI não é. Emenda registrada em
 * `docs/regras/01-motor-de-geracao.md`.
 *
 * ## Nunca escreve `Level`
 *
 * Ver o KDoc de [PromotionStatusDto] pro porquê de não existir `POST .../accept`: o aceite é o
 * cliente chamar `PUT /me/profile` como no onboarding, e a sugestão desaparece por si na próxima
 * leitura. Este service só grava recusa.
 *
 * ## Avançado (fatia de 2026-10-31) — dado insuficiente ≠ nenhuma promoção a sugerir
 *
 * Sem backfill da V72 (`um_rm_kg`), os primeiros ~2 mesociclos depois do deploy não têm dado
 * recuperável pra NINGUÉM — período cego esperado, não falha. [diagnosticoAvancado] só loga essa
 * distinção (não chega ao contrato: o cliente não precisa saber POR QUE não há sugestão, só que
 * não há) — ver `docs/trabalho/promocao-avancado.md`. Agregação entre âncoras (compostos com
 * `relevancia = 1`, podem ser várias): [PromocaoDeNivel.diagnosticoAvancadoDoPerfil] (maioria
 * estrita, `docs/regras/01-motor-de-geracao.md`).
 */
class PromotionService(
    private val userService: UserService,
    private val profileRepository: ProfileRepository,
    private val progressaoRepository: ProgressaoRepository,
    private val exerciseRepository: ExerciseRepository,
    private val mesocicloRepository: MesocicloRepository,
    private val promotionDeclineRepository: PromotionDeclineRepository,
) {

    private val log = LoggerFactory.getLogger(PromotionService::class.java)

    suspend fun status(firebaseUid: String, email: String?): AppResult<PromotionStatusDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            sugestaoPendente(user.id).map { PromotionStatusDto(it) }
        }

    /**
     * Recusa a sugestão ATUAL (recalculada, nunca lida de volta do cliente — ver
     * [PromotionStatusDto]). 409/[ErrorCodes.SEM_SUGESTAO_DE_PROMOCAO] se não houver nenhuma: não
     * é dado velho pra repetir, é a regra de negócio recusando um recurso que o servidor nem
     * oferece agora.
     */
    suspend fun decline(firebaseUid: String, email: String?): AppResult<Unit> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            sugestaoPendente(user.id).flatMap { sugestao ->
                if (sugestao == null) {
                    AppError.Conflict(
                        "Não há sugestão de promoção pendente",
                        code = ErrorCodes.SEM_SUGESTAO_DE_PROMOCAO,
                    ).asFailure()
                } else {
                    mesocicloRepository.buscar(user.id).flatMap { mesociclo ->
                        promotionDeclineRepository.registrarRecusa(
                            user.id,
                            sugestao.nivelProposto,
                            CicloDoMesociclo.paraPerfil(mesociclo),
                        )
                    }
                }
            }
        }

    /**
     * `null` = nada a sugerir: sem perfil, nível `ADVANCED` (não há pra onde promover) ou
     * diagnóstico ainda não fechou PROMOVER — ver [diagnosticarIniciante]/[diagnosticarAvancado]
     * pra cada nível.
     */
    private suspend fun sugestaoPendente(userId: Uuid): AppResult<PromotionSuggestionDto?> =
        profileRepository.findByUserId(userId).flatMap { profile ->
            when (profile?.level) {
                Level.BEGINNER -> diagnosticarIniciante(userId)
                Level.INTERMEDIATE -> diagnosticarAvancado(userId)
                else -> null.asSuccess()
            }
        }

    private suspend fun diagnosticarIniciante(userId: Uuid): AppResult<PromotionSuggestionDto?> =
        mesocicloRepository.buscar(userId).flatMap { mesocicloEstado ->
            val mesocicloAtual = CicloDoMesociclo.paraPerfil(mesocicloEstado)
            progressaoRepository.exerciciosComEventoNoMesociclo(userId, mesocicloAtual).flatMap { ancoras ->
                if (ancoras.isEmpty()) {
                    null.asSuccess()
                } else {
                    diagnosticoPorAncora(userId, ancoras).flatMap { porAncora ->
                        if (PromocaoDeNivel.diagnosticoDoPerfil(porAncora) != PromocaoDeNivel.Diagnostico.PROMOVER) {
                            null.asSuccess()
                        } else {
                            promotionDeclineRepository.mesocicloDaUltimaRecusa(userId, Level.INTERMEDIATE).map { mesocicloNaRecusa ->
                                if (PromocaoDeNivel.podeSugerirDeNovo(mesocicloAtual, mesocicloNaRecusa)) {
                                    PromotionSuggestionDto(Level.INTERMEDIATE)
                                } else {
                                    null
                                }
                            }
                        }
                    }
                }
            }
        }

    /**
     * Um [ProgressaoRepository.historico] por âncora, SEM filtro de mesociclo — o diagnóstico
     * precisa do deload anterior pra achar "retomou"/"não retomou", que pode estar numa janela
     * anterior à que selecionou a âncora. Sequencial (fan-in simples, poucas âncoras por perfil —
     * 5 a 17, `[INV]` da Fatia 11); primeira falha interrompe e sobe.
     */
    private suspend fun diagnosticoPorAncora(
        userId: Uuid,
        ancoras: List<Uuid>,
    ): AppResult<List<PromocaoDeNivel.Diagnostico?>> {
        val diagnosticos = mutableListOf<PromocaoDeNivel.Diagnostico?>()
        for (ancoraId in ancoras) {
            when (val historico = progressaoRepository.historico(userId, ancoraId)) {
                is AppResult.Failure -> return historico
                is AppResult.Success -> diagnosticos += PromocaoDeNivel.diagnosticoIniciante(historico.value)
            }
        }
        return diagnosticos.asSuccess()
    }

    /**
     * `null` = nada a sugerir: sem perfil ou nível diferente de `INTERMEDIATE` (tratado em
     * [sugestaoPendente]), sem âncora avançada (compostos `relevancia = 1` com evento no
     * mesociclo atual), [PromocaoDeNivel.diagnosticoAvancadoDoPerfil] ainda não é PROMOVER, ou já
     * recusado neste mesociclo. "Ainda não é o 1º mesociclo com dado comparável" (sem mesociclo
     * ANTERIOR, ou toda âncora com [ProgressaoRepository.primeiroUmRmNoMesociclo] nulo) também
     * devolve `null` — mas loga como DADO INSUFICIENTE, não como diagnóstico negativo, em
     * [diagnosticoAvancadoPorAncora].
     */
    private suspend fun diagnosticarAvancado(userId: Uuid): AppResult<PromotionSuggestionDto?> =
        mesocicloRepository.buscar(userId).flatMap { mesocicloEstado ->
            val mesocicloAtual = CicloDoMesociclo.paraPerfil(mesocicloEstado)
            val mesocicloAnterior = mesocicloAtual - 1
            if (mesocicloAnterior < 1) {
                log.info("promocao avancado: dado insuficiente (usuario {} sem mesociclo anterior, atual={})", userId, mesocicloAtual)
                return@flatMap null.asSuccess()
            }
            progressaoRepository.exerciciosComEventoNoMesociclo(userId, mesocicloAtual).flatMap { candidatos ->
                if (candidatos.isEmpty()) {
                    null.asSuccess()
                } else {
                    exerciseRepository.ancorasAvancadas(candidatos).flatMap { ancoras ->
                        if (ancoras.isEmpty()) {
                            null.asSuccess()
                        } else {
                            diagnosticoAvancadoPorAncora(userId, ancoras, mesocicloAnterior).flatMap { porAncora ->
                                if (PromocaoDeNivel.diagnosticoAvancadoDoPerfil(porAncora) != PromocaoDeNivel.Diagnostico.PROMOVER) {
                                    null.asSuccess()
                                } else {
                                    promotionDeclineRepository.mesocicloDaUltimaRecusa(userId, Level.ADVANCED).map { mesocicloNaRecusa ->
                                        if (PromocaoDeNivel.podeSugerirDeNovo(mesocicloAtual, mesocicloNaRecusa)) {
                                            PromotionSuggestionDto(Level.ADVANCED)
                                        } else {
                                            null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    /**
     * Um [PromocaoDeNivel.diagnosticoAvancado] por âncora — `umRmInicio` vem do mesociclo
     * ANTERIOR ([ProgressaoRepository.primeiroUmRmNoMesociclo]), `umRmFim` é o estado ATUAL
     * ([ProgressaoRepository.buscar]), `reducoesNoPeriodo` conta `DESCE` desde o mesociclo
     * anterior ([ProgressaoRepository.reducoesDesde]). Sequencial, mesmo padrão de
     * [diagnosticoPorAncora].
     *
     * **DADO INSUFICIENTE, registrado, não silencioso**: `umRmInicio == null` (sem evento do
     * exercício naquele mesociclo, OU evento anterior à V72 sem snapshot) entra como `null` no
     * diagnóstico — correto pra [PromocaoDeNivel.diagnosticoAvancado] — mas é logado em separado
     * daqui, porque sem este log "sem dado" e "diagnosticou e não promove" ficam indistinguíveis
     * pra quem olha de fora (ninguém saberia, daqui a um mês, se a regra funciona ou quebrou).
     */
    private suspend fun diagnosticoAvancadoPorAncora(
        userId: Uuid,
        ancoras: List<Uuid>,
        mesocicloAnterior: Int,
    ): AppResult<List<PromocaoDeNivel.Diagnostico?>> {
        val diagnosticos = mutableListOf<PromocaoDeNivel.Diagnostico?>()
        var dadoInsuficiente = 0
        for (ancoraId in ancoras) {
            val umRmInicio = when (val r = progressaoRepository.primeiroUmRmNoMesociclo(userId, ancoraId, mesocicloAnterior)) {
                is AppResult.Failure -> return r
                is AppResult.Success -> r.value
            }
            if (umRmInicio == null) {
                dadoInsuficiente++
                diagnosticos += null
                continue
            }
            val umRmFim = when (val r = progressaoRepository.buscar(userId, ancoraId)) {
                is AppResult.Failure -> return r
                is AppResult.Success -> r.value?.umRm
            }
            val reducoes = when (val r = progressaoRepository.reducoesDesde(userId, ancoraId, mesocicloAnterior)) {
                is AppResult.Failure -> return r
                is AppResult.Success -> r.value
            }
            diagnosticos += PromocaoDeNivel.diagnosticoAvancado(umRmInicio, umRmFim, reducoes)
        }
        when {
            dadoInsuficiente == ancoras.size ->
                log.info("promocao avancado: dado insuficiente para TODAS as {} ancoras do usuario {} (periodo cego pos-V72?)", ancoras.size, userId)
            dadoInsuficiente > 0 ->
                log.info("promocao avancado: dado insuficiente para {}/{} ancoras do usuario {}", dadoInsuficiente, ancoras.size, userId)
        }
        return diagnosticos.asSuccess()
    }
}
