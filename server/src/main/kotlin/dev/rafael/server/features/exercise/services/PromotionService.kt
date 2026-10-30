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
import dev.rafael.server.features.exercise.db.MesocicloRepository
import dev.rafael.server.features.exercise.db.ProgressaoRepository
import dev.rafael.server.features.exercise.db.PromotionDeclineRepository
import dev.rafael.server.features.exercise.engine.v3.CicloDoMesociclo
import dev.rafael.server.features.exercise.engine.v3.PromocaoDeNivel
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.user.services.UserService
import kotlin.uuid.Uuid

/**
 * Service/rota da promoção de nível — débito (b) de "o motor sabe, o app não mostra"
 * (`docs/fontes/debitos.md`, consolidado da Fatia 11), fechado aqui para INICIANTE→INTERMEDIATE.
 * O AVANÇADO (`PromocaoDeNivel.diagnosticoAvancado`) continua em aberto: falta decidir o disparo
 * real (RIR não chega do cliente ainda — mesmo débito), a própria "O motor sabe, o app não
 * mostra" documenta a lacuna; a V71 já deixa o schema pronto (coluna `mesociclo` em
 * `progression_event`) pro dia em que o service dele existir.
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
 */
class PromotionService(
    private val userService: UserService,
    private val profileRepository: ProfileRepository,
    private val progressaoRepository: ProgressaoRepository,
    private val mesocicloRepository: MesocicloRepository,
    private val promotionDeclineRepository: PromotionDeclineRepository,
) {

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
     * `null` = nada a sugerir: sem perfil, nível já não é `BEGINNER` (avançado é débito, não esta
     * fatia), diagnóstico do perfil ainda não é [PromocaoDeNivel.Diagnostico.PROMOVER], ou já
     * recusado neste mesociclo ([PromocaoDeNivel.podeSugerirDeNovo]).
     */
    private suspend fun sugestaoPendente(userId: Uuid): AppResult<PromotionSuggestionDto?> =
        profileRepository.findByUserId(userId).flatMap { profile ->
            if (profile == null || profile.level != Level.BEGINNER) {
                null.asSuccess()
            } else {
                diagnosticarIniciante(userId)
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
}
