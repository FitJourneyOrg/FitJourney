package dev.rafael.contract.user

import dev.rafael.contract.friendship.FriendStatus
import kotlinx.serialization.Serializable

/**
 * O perfil de alguém, como QUALQUER usuário autenticado o vê (ARCH #34, emenda 9.3-A).
 *
 * ## [INVARIANTE] A fronteira é este arquivo
 *
 * A 9.3-A tornou públicos **nome, nível, XP e conquistas — e nada além disso**. Treinos, sessões,
 * cargas, programas, peso, altura, idade, objetivo e triagem de saúde continuam privados.
 *
 * **Por que um DTO próprio e não o `UserStatsDto`.** Ele traz `totalSessions`, `sessionsThisWeek`,
 * `streakDays` e `trainedToday` — que são **histórico de treino**, justamente o que a emenda
 * manteve privado. Reusá-lo aqui vazaria por conveniência o que a decisão protegeu de propósito.
 *
 * É a mesma lição que já custou caro neste projeto: toda vez que o isolamento dependeu de alguém
 * lembrar de omitir um campo, ele falhou. Um campo que **não existe** no DTO não vaza por
 * descuido.
 *
 * **Desafios concluídos não estão aqui** — o #34 os decidiu privados, e a 9.3-A não os tocou:
 * eles não são gamificação, são grafo social. De quais desafios alguém participa, e com quem, é
 * outra categoria de informação. Vêm de `/me/challenges`, rota separada, só para o dono.
 */
@Serializable
data class PublicProfileDto(
    val userId: String,
    val displayName: String,

    /** Nível atual, começando em 1 (#16). */
    val level: Int,

    /** XP total acumulado. Sem `xpInLevel`/`xpForNextLevel`: a barra é da tela do dono. */
    val xp: Int,

    /** Só as CONQUISTADAS — ver [PublicAchievementDto]. */
    val achievements: List<PublicAchievementDto> = emptyList(),

    /**
     * Este perfil sou eu?
     *
     * Resolvido no servidor, como o `myRole` do grupo e o `mine` do check-in. É o que decide
     * entre o lápis de editar e o botão de adicionar — e a tela não compara ids para isso.
     */
    val me: Boolean = false,

    /**
     * Este perfil pode ser mostrado? (emenda **35.6**, 2026-08-27)
     *
     * `false` quando quem pediu **foi bloqueado** pelo dono deste perfil. Nesse caso o servidor
     * zera tudo: `displayName` vazio, `level` 0 (que nunca é nível real — começa em 1), `xp` 0 e
     * nenhuma conquista. **O nome verdadeiro não sai do servidor** — a tela não recebe dado que
     * ela precise lembrar de esconder.
     *
     * A mesma resposta serve para **conta excluída**, e isso é o ponto: quem foi bloqueado não
     * consegue distinguir os dois casos. Se a tela dissesse "você foi bloqueado", o bloqueio
     * viraria um recado — e quem bloqueia normalmente quer sumir, não avisar.
     *
     * **Assimétrico:** quem BLOQUEOU continua vendo o perfil normalmente. Sem isso, a lista de
     * Configurações → Bloqueados seria uma fileira de perfis vazios e desbloquear viraria
     * adivinhação.
     */
    val available: Boolean = true,

    /**
     * Como quem está olhando se relaciona com esta pessoa (#35) — é o que decide **qual botão**
     * a tela desenha: Adicionar, Cancelar pedido, Aceitar, Desfazer ou Desbloquear.
     *
     * Resolvido no SERVIDOR, como o `myRole` do grupo e o `mine` do check-in. A tela não cruza a
     * lista de amigos com a de pedidos para descobrir em que estado está — isso daria cinco
     * botões possíveis calculados em três lugares diferentes, e um deles ficaria errado.
     *
     * Vem junto com o perfil, e não numa segunda requisição: quem abre um perfil quase sempre
     * abre para AGIR, e o botão chegar depois do resto faria a tela pular na frente do dedo.
     */
    val friendStatus: FriendStatus = FriendStatus.NENHUMA,
)

/**
 * Uma conquista no perfil público. **Só as desbloqueadas chegam aqui.**
 *
 * O `AchievementDto` do dono carrega `current`/`target` para desenhar "7 de 10" e a barra. Isso é
 * PROGRESSO, e progresso é histórico de treino: dizer que alguém está em 7 de 10 treinos revela
 * o que a 9.3-A manteve privado.
 *
 * No perfil público existe a medalha, não o caminho até ela.
 */
@Serializable
data class PublicAchievementDto(
    val id: String,
    val title: String,
    val description: String,
    /** ISO. Nunca nulo: conquista bloqueada não entra na lista. */
    val unlockedAt: String,
)
