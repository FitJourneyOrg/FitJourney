package dev.rafael.server.features.stats

import dev.rafael.contract.stats.AchievementDto
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.stats.db.AchievementRepository
import dev.rafael.server.features.user.services.UserService

/**
 * Conquistas do perfil (ARCH #16): avalia, concede e devolve o CATÁLOGO INTEIRO.
 *
 * ONDE A AVALIAÇÃO DISPARA: na própria leitura. Poderia ser no `POST /sessions`, que é o
 * momento em que o progresso muda — mas avaliar na leitura dá de graça o retroativo (quem já
 * tinha 60 treinos recebe na primeira abertura da tela, sem migration de backfill) e mantém o
 * registro de sessão com uma responsabilidade só. O custo é uma escrita eventual num GET, e
 * ela é idempotente.
 *
 * Quando existir NOTIFICAÇÃO de desbloqueio, aí sim o `POST /sessions` precisa avaliar também
 * — senão o usuário só descobre a medalha quando abre a tela. Fica registrado como débito.
 */
class AchievementService(
    private val userService: UserService,
    private val stats: StatsService,
    private val repository: AchievementRepository,
) {
    suspend fun forUser(firebaseUid: String, email: String?): AppResult<List<AchievementDto>> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            stats.forUser(firebaseUid, email).flatMap { s ->
                val progresso = AchievementPolicy.Progresso(
                    sessoesValidas = s.totalSessions,
                    streakDias = s.streakDays,
                    nivel = s.level,
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
                            montarCatalogo(progresso, atualizadas)
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
                    title = c.titulo,
                    description = c.descricao,
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
