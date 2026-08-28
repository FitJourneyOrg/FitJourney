package dev.rafael.server.features.user.services

import dev.rafael.contract.friendship.FriendStatus
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
    private val relacaoCom: RelacaoCom,
) {

    /**
     * Porta estreita para o grafo social (#35 + emenda 35.6), no molde do [GamificacaoDe].
     *
     * `user` não importa `friendship`: recebe uma função. E a função devolve as DUAS coisas que o
     * perfil precisa numa consulta só — o botão a desenhar e se quem pede foi bloqueado.
     *
     * As duas juntas de propósito. Separadas seriam duas idas ao banco para responder sobre o
     * mesmo par de pessoas, e abririam a chance de uma responder "somos amigos" enquanto a outra
     * responde "você foi bloqueado" — estados que não podem coexistir, porque bloquear apaga a
     * amizade na mesma transação.
     */
    fun interface RelacaoCom {
        suspend operator fun invoke(dono: Uuid, quemPede: Uuid): AppResult<Relacao>
    }

    /**
     * @param status o que quem pede pode FAZER com este perfil.
     * @param meBloqueou o dono bloqueou quem pede? DIRECIONAL — quem bloqueou continua vendo.
     */
    data class Relacao(
        val status: FriendStatus,
        val meBloqueou: Boolean,
    )

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
     * Pelo CÓDIGO de 8 caracteres (35.5).
     *
     * **[REGRA] digitar o código abre o PERFIL, não manda um pedido.** Sem isso, um erro de
     * digitação viraria pedido de amizade a um desconhecido — e com o perfil público a garantia
     * sai de graça, porque a tela de confirmação que o ADR previa é o próprio perfil.
     *
     * O limite de tentativas é aplicado na ROTA, não aqui: ele é sobre quem PERGUNTA, e este
     * serviço é sobre quem é perguntado.
     *
     * 404 quando não existe — igual ao id inexistente, pelo mesmo motivo.
     */
    suspend fun porCodigo(
        quemPede: String,
        emailDeQuemPede: String?,
        codigo: String,
    ): AppResult<PublicProfileDto> {
        val normalizado = UserCodePolicy.normalizar(codigo) ?: return naoEncontrado()
        return users.findByCode(normalizado).flatMap { pessoa ->
            if (pessoa == null) naoEncontrado()
            else porId(quemPede, emailDeQuemPede, pessoa.id.toString())
        }
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

                relacaoCom(alvo, eu.id).flatMap { relacao ->
                    if (relacao.meBloqueou) indisponivel(alvo).asSuccess()
                    else montar(pessoa, eu.id, relacao.status)
                }
            }
        }

    private suspend fun montar(
        pessoa: dev.rafael.server.features.user.models.User,
        eu: Uuid,
        status: FriendStatus,
    ): AppResult<PublicProfileDto> =
        gamificacaoDe(pessoa.id).flatMap { g ->
            achievements.listByUser(pessoa.id).flatMap { concedidas ->
                PublicProfileDto(
                    userId = pessoa.id.toString(),
                    displayName = pessoa.displayName,
                    level = g.nivel,
                    xp = g.xp,
                    achievements = medalhas(concedidas),
                    me = pessoa.id == eu,
                    friendStatus = status,
                ).asSuccess()
            }
        }

    /**
     * O perfil que quem foi bloqueado recebe — e **o mesmo de uma conta excluída**.
     *
     * Zerado no SERVIDOR, não escondido na tela: `displayName` vazio significa que o nome
     * verdadeiro não atravessou o fio. Uma tela que recebesse o nome e decidisse não mostrá-lo
     * seria a fronteira na UI de novo, que é o erro que a 9.3-A já nos custou reconhecer.
     *
     * `level = 0` é sentinela: o nível real começa em 1 (#16), então zero nunca é ambíguo.
     */
    private fun indisponivel(alvo: Uuid) = PublicProfileDto(
        userId = alvo.toString(),
        displayName = "",
        level = 0,
        xp = 0,
        achievements = emptyList(),
        me = false,
        available = false,
    )

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
