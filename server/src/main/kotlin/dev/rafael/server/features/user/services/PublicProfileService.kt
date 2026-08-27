package dev.rafael.server.features.user.services

import dev.rafael.contract.user.PublicAchievementDto
import dev.rafael.contract.user.PublicProfileDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.server.features.stats.AchievementPolicy
import dev.rafael.server.features.stats.StatsService
import dev.rafael.server.features.stats.db.AchievementRepository
import dev.rafael.server.features.user.db.UserRepository
import kotlin.uuid.Uuid

/**
 * O perfil de alguém, público (ARCH #34, emenda 9.3-A).
 *
 * ## O que sai daqui
 *
 * Nome, nível, XP e **conquistas já desbloqueadas**. Nada além disso — a garantia é o
 * `PublicProfileDto`, que não tem campo para treino, sessão, peso ou triagem de saúde.
 *
 * ## Três decisões
 *
 * **Serviço próprio, e não um método no `AchievementService`.** Aquele CONCEDE conquistas ao ler:
 * calcula o progresso, grava as novas e relê. Isso é caminho de ESCRITA, e faz sentido para o
 * dono abrindo a própria tela. Rodá-lo porque um estranho abriu o perfil seria conceder medalha a
 * alguém por causa da curiosidade de outra pessoa.
 *
 * **Só as desbloqueadas, e sem progresso.** O `AchievementDto` do dono traz `current`/`target`
 * para desenhar "7 de 10". Progresso é histórico de treino, e a 9.3-A o manteve privado. No
 * perfil público existe a medalha, não o caminho até ela.
 *
 * **Não existe rota que devolva "todos os usuários".** Perfil se alcança por id — de um nome no
 * feed, do ranking, da lista de amigos ou do código (#35). Não é diretório.
 */
class PublicProfileService(
    private val userService: UserService,
    private val users: UserRepository,
    private val gamificacaoDe: GamificacaoDe,
    private val achievements: AchievementRepository,
) {

    /**
     * Porta estreita para a gamificação de um usuário, no molde do `CheckInDeHoje` do
     * `GroupService`.
     *
     * **Por que não receber o `StatsService` direto.** Ele depende do `ProgramService`, que depende
     * do `WorkoutGenerator` — tudo por causa do streak, que este serviço não usa. Depender da
     * classe inteira arrastaria metade do motor de treino para dentro do perfil, e o teste do
     * perfil teria que montar um gerador de treino para perguntar o XP de alguém.
     *
     * A porta é declarada aqui, por QUEM CONSOME, não por quem implementa. É o mesmo sentido único
     * de dependência de sempre: o perfil diz o que precisa, o `stats` atende.
     */
    fun interface GamificacaoDe {
        suspend operator fun invoke(userId: Uuid): AppResult<StatsService.Gamificacao>
    }

    /**
     * @param quemPede o `uid` do Firebase de quem está OLHANDO — resolve o [PublicProfileDto.me].
     * @param userId o id interno de quem está sendo olhado, como veio da URL (string, ainda não
     *   validado: id malformado é indistinguível de id inexistente para quem pergunta).
     */
    suspend fun porId(
        quemPede: String,
        emailDeQuemPede: String?,
        userId: String,
    ): AppResult<PublicProfileDto> =
        userService.findOrCreate(quemPede, emailDeQuemPede).flatMap { eu ->
            val alvo = runCatching { Uuid.parse(userId) }.getOrNull() ?: return@flatMap naoEncontrado()

            users.findById(alvo).flatMap { pessoa ->
                if (pessoa == null) return@flatMap naoEncontrado()

                gamificacaoDe(alvo).flatMap { g ->
                    achievements.listByUser(alvo).flatMap { concedidas ->
                        PublicProfileDto(
                            userId = pessoa.id.toString(),
                            displayName = pessoa.displayName,
                            level = g.nivel,
                            xp = g.xp,
                            achievements = medalhas(concedidas),
                            me = pessoa.id == eu.id,
                        ).asSuccess()
                    }
                }
            }
        }

    /**
     * Traduz os ids gravados em medalhas com título e descrição.
     *
     * Id que não existe mais no código — conquista removida numa versão futura — é ignorado em
     * silêncio. A linha órfã não faz mal a ninguém, e derrubar o perfil por causa dela seria
     * desproporcional. Mesma escolha que o catálogo do dono já fazia.
     */
    private fun medalhas(concedidas: Map<String, kotlinx.datetime.LocalDateTime>): List<PublicAchievementDto> =
        concedidas.entries
            .mapNotNull { (id, quando) ->
                val c = AchievementPolicy.Conquista.entries.firstOrNull { it.name == id }
                    ?: return@mapNotNull null
                PublicAchievementDto(
                    id = c.name,
                    title = c.titulo,
                    description = c.descricao,
                    unlockedAt = quando.toString(),
                )
            }
            // Mais recente primeiro, e o id como desempate ESTÁVEL: conquistas do mesmo lote
            // compartilham o `unlocked_at` (um `now()` por lote, de propósito), então sem isto a
            // ordem entre elas ficaria à mercê do banco e mudaria entre duas aberturas da tela.
            .sortedWith(compareByDescending<PublicAchievementDto> { it.unlockedAt }.thenBy { it.id })

    /**
     * **404 e nunca 403**, e nem um perfil vazio.
     *
     * Id que não existe e id que existe respondem diferente de propósito — o primeiro é a
     * verdade. O que não pode acontecer é responder "sem permissão", porque isso confirmaria a
     * existência de uma conta que a pessoa não deveria conseguir sondar.
     */
    private fun naoEncontrado(): AppResult<Nothing> =
        AppError.NotFound("Perfil não encontrado").asFailure()
}
