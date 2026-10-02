package dev.rafael.server.features.stats

import dev.rafael.contract.stats.AchievementDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.stats.db.AchievementRepository
import dev.rafael.server.features.user.services.UserService
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Conquistas do perfil (ARCH #16): avalia, concede e devolve o CATÁLOGO INTEIRO.
 *
 * ONDE A AVALIAÇÃO DISPARA: em DOIS lugares (débito fechado em 2026-09-24).
 *
 * - Na LEITURA ([forUser]): dá de graça o retroativo (quem já tinha 60 treinos recebe na
 *   primeira abertura da tela, sem migration de backfill). NUNCA notifica — abrir a tela não é
 *   "acabei de conquistar agora", e notificar aqui inundaria quem abre a tela pela primeira vez
 *   após uma migration com um push por medalha antiga.
 * - No `POST /sessions` ([avaliarAposSessao]): é o momento em que o progresso de fato muda, e é
 *   o único que sabe que a conquista é NOVA agora, não retroativa — por isso é o único que
 *   notifica.
 *
 * As duas rodam o mesmo núcleo ([avaliarEConceder]), que é idempotente (PK composta + ON
 * CONFLICT DO NOTHING no repositório), então acontecer nos dois no mesmo dia não duplica nada.
 */
class AchievementService(
    private val userService: UserService,
    private val stats: ProgressoDeStats,
    private val repository: AchievementRepository,
    // Porta estreita para o push: `stats` não importa `notificacao`. Default não faz nada — o
    // grafo funciona sem notificação, mesmo padrão do FriendshipService/SocialService/ModeracaoService.
    private val avisarDesbloqueio: suspend (destinatario: Uuid, achievementId: String) -> Unit = { _, _ -> },
) {
    private val log = LoggerFactory.getLogger(AchievementService::class.java)

    suspend fun forUser(firebaseUid: String, email: String?): AppResult<List<AchievementDto>> =
        avaliarEConceder(firebaseUid, email).map { montarCatalogo(it.progresso, it.concedidas) }

    /**
     * Mesma avaliação de [forUser], mas dispara [avisarDesbloqueio] para cada conquista NOVA
     * deste lote. Chamada pelo `SessionService.record()` depois que a sessão é salva.
     *
     * **Nunca falha para o chamador.** A sessão já foi salva quando isto roda: um erro aqui é só
     * o aviso que não saiu, não o treino que deixou de ser salvo — mesma régua do
     * `NotificacaoService.avisar()`.
     */
    suspend fun avaliarAposSessao(firebaseUid: String, email: String?) {
        runCatching {
            when (val r = avaliarEConceder(firebaseUid, email)) {
                is AppResult.Success -> r.value.novas.forEach { conquista ->
                    avisarDesbloqueio(r.value.userId, conquista.name)
                }
                is AppResult.Failure -> log.warn("Não avaliei conquistas após sessão: {}", r.error)
            }
        }.onFailure { e ->
            log.warn("Avaliação de conquistas após sessão lançou: {}", e.toString())
        }
    }

    /** O que [avaliarEConceder] devolve — o suficiente pra montar o catálogo OU pra notificar. */
    private data class Avaliacao(
        val userId: Uuid,
        val progresso: AchievementPolicy.Progresso,
        val novas: Set<AchievementPolicy.Conquista>,
        val concedidas: Map<String, kotlinx.datetime.LocalDateTime>,
    )

    private suspend fun avaliarEConceder(firebaseUid: String, email: String?): AppResult<Avaliacao> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            stats.forUser(firebaseUid, email).flatMap { s ->
                val progresso = AchievementPolicy.Progresso(
                    sessoesValidas = s.totalSessions,
                    streakDias = s.streakDays,
                    nivel = s.level,
                    cargaTotalKg = s.totalKg,
                )
                repository.listByUser(user.id).flatMap { jaConcedidas ->
                    val novas = AchievementPolicy.aConceder(
                        progresso = progresso,
                        jaConcedidas = jaConcedidas.keys.mapNotNull { it.paraConquista() }.toSet(),
                    )
                    repository.grant(user.id, novas.map { it.name }.toSet()).flatMap {
                        // Relê DEPOIS de conceder: as recém-criadas precisam sair com a data
                        // real gravada pelo banco, não com uma calculada aqui. Uma requisição a
                        // mais em troca de uma fonte única para o `unlockedAt`.
                        repository.listByUser(user.id).map { atualizadas ->
                            Avaliacao(user.id, progresso, novas, atualizadas)
                        }
                    }
                }
            }
        }

    /**
     * Devolve TODAS as conquistas, bloqueadas incluídas — a tela mostra as que faltam em cinza,
     * com o progresso. Ordem: desbloqueadas primeiro (mais recente antes), depois as bloqueadas
     * pela proximidade do alvo, que é o que sugere o próximo passo ao usuário.
     */
    private fun montarCatalogo(
        progresso: AchievementPolicy.Progresso,
        concedidas: Map<String, kotlinx.datetime.LocalDateTime>,
    ): List<AchievementDto> =
        AchievementPolicy.Conquista.entries
            .map { c ->
                AchievementDto(
                    id = c.name,
                    unlockedAt = concedidas[c.name]?.toString(),
                    // Limitado ao alvo: "150 de 100" não faz sentido numa barra de progresso.
                    current = progresso.valorDe(c.metrica).coerceAtMost(c.alvo),
                    target = c.alvo,
                )
            }
            .sortedWith(
                compareByDescending<AchievementDto> { it.unlockedAt != null }
                    .thenByDescending { it.unlockedAt }
                    // Proximidade do alvo SÓ desempata bloqueadas — é o que sugere o próximo
                    // passo. Nas desbloqueadas o progresso continua mudando (streak quebra), e
                    // usá-lo faria medalha já ganha trocar de lugar sozinha entre duas aberturas
                    // da tela. Observado no emulador: apagar sessões reordenou a fileira de cima.
                    .thenByDescending { if (it.unlocked) 0.0 else it.current.toDouble() / it.target }
                    // Desempate final ESTÁVEL: conquistas do mesmo lote compartilham o
                    // `unlocked_at` (um `now()` por lote, de propósito), então sem isto a ordem
                    // entre elas fica à mercê do banco.
                    .thenBy { it.id },
            )

    /**
     * Id gravado no banco pode não existir mais no código — conquista removida numa versão
     * futura. Ignorar em silêncio é melhor que estourar: a linha órfã não faz mal a ninguém, e
     * derrubar a tela de conquistas por causa dela seria desproporcional.
     */
    private fun String.paraConquista(): AchievementPolicy.Conquista? =
        runCatching { AchievementPolicy.Conquista.valueOf(this) }.getOrNull()
}
