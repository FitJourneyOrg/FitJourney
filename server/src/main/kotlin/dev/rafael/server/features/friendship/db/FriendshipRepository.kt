package dev.rafael.server.features.friendship.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.friendship.models.Amizade
import dev.rafael.server.features.friendship.models.Pessoa
import dev.rafael.server.features.friendship.models.PedidoRecebido
import dev.rafael.server.features.friendship.services.FriendshipPolicy
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * O grafo (ARCH #35). Amizade e bloqueio no mesmo repositório porque toda operação de uma
 * consulta a outra — bloquear apaga amizade, pedir consulta bloqueio — e separá-los obrigaria a
 * coordenar duas transações para manter um invariante que é de um par só.
 */
interface FriendshipRepository {

    /** A relação entre os dois, se existir. Aceita os ids em qualquer ordem. */
    suspend fun entre(um: Uuid, outro: Uuid): AppResult<Amizade?>

    /**
     * Cria o pedido. **`insertIgnore`**: se o par já existe, não sobrescreve — devolve `false`.
     *
     * A corrida real: os dois se adicionam ao mesmo tempo. Sem isto, o segundo insert estouraria
     * a PK e viraria 500. Com isto, o segundo vira "já existe um pedido", que é a verdade.
     */
    suspend fun pedir(quemPede: Uuid, alvo: Uuid, quando: LocalDateTime): AppResult<Boolean>

    /** PENDENTE → ACEITA/RECUSADA. `false` se a linha mudou de estado no meio do caminho. */
    suspend fun responder(
        um: Uuid,
        outro: Uuid,
        novo: FriendshipPolicy.Estado,
        quando: LocalDateTime,
    ): AppResult<Boolean>

    /** Apaga a linha do par. Serve para cancelar pedido e para desfazer amizade. */
    suspend fun apagar(um: Uuid, outro: Uuid): AppResult<Boolean>

    /** Quantas ACEITAS a pessoa tem. É o insumo do teto de 500 (35.4). */
    suspend fun contarAmizades(userId: Uuid): AppResult<Int>

    /** Meus amigos, em ordem alfabética — a lista é para procurar alguém, não para navegar. */
    suspend fun amigos(userId: Uuid): AppResult<List<Pessoa>>

    /**
     * Pedidos que EU recebi e ainda não respondi.
     *
     * Só os recebidos: os que eu mandei aparecem no perfil da pessoa como "pedido enviado", e não
     * numa lista — uma caixa de saída de pedidos é tela que ninguém abre duas vezes.
     */
    suspend fun pedidosRecebidos(userId: Uuid): AppResult<List<PedidoRecebido>>

    // ---- bloqueio ----

    /**
     * Alguém bloqueou o outro, em QUALQUER sentido?
     *
     * Uma pergunta só para os dois sentidos porque é isso que o pedido de amizade precisa saber:
     * se há bloqueio, não há pedido, venha ele de quem vier.
     */
    suspend fun haBloqueioEntre(um: Uuid, outro: Uuid): AppResult<Boolean>

    /**
     * `alvo` bloqueou `quemPergunta`?
     *
     * DIRECIONAL, diferente do [haBloqueioEntre]: é esta que o perfil público consulta, porque só
     * quem FOI bloqueado vê o perfil indisponível — quem bloqueou continua vendo (35.6).
     */
    suspend fun bloqueouMe(alvo: Uuid, quemPergunta: Uuid): AppResult<Boolean>

    /**
     * Bloqueia e **apaga a amizade ou o pedido na mesma transação**.
     *
     * As duas coisas juntas de propósito: bloquear com o pedido ainda de pé deixaria a pessoa
     * bloqueada com um "aguardando resposta" que nunca vai ser respondido. Em transações
     * separadas, uma falha no meio deixaria exatamente esse estado.
     */
    suspend fun bloquear(bloqueador: Uuid, bloqueado: Uuid, quando: LocalDateTime): AppResult<Unit>

    /** Desbloqueia. **Não** restaura a amizade apagada — ela some, e refazer é ato deliberado. */
    suspend fun desbloquear(bloqueador: Uuid, bloqueado: Uuid): AppResult<Boolean>

    /** Quem eu bloqueei. Alimenta Configurações da conta → Bloqueados. */
    suspend fun bloqueados(userId: Uuid): AppResult<List<Pessoa>>
}
