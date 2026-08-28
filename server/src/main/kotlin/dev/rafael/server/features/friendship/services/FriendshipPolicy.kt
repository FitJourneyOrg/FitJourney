package dev.rafael.server.features.friendship.services

import kotlin.uuid.Uuid

/**
 * As regras de amizade, PURAS (ARCH #35). Sem banco, sem relógio, sem Ktor.
 *
 * Modelo **Facebook**: simétrica, com aceite (35.1). Não existe "seguir".
 */
object FriendshipPolicy {

    /** 35.4 — teto de amizades ACEITAS por pessoa. */
    const val TETO_DE_AMIZADES = 500

    /** Estados de `friendships`. **`BLOQUEADA` não está aqui** (emenda 35.2): bloqueio é `blocks`. */
    enum class Estado { PENDENTE, ACEITA, RECUSADA }

    /**
     * O par em ordem CANÔNICA: menor uuid primeiro.
     *
     * É o que faz a chave primária impedir **pedido cruzado** — A→B e B→A produzem o mesmo par,
     * então a segunda inserção bate na PK em vez de criar dois pedidos vivos. A ordem também
     * está no `CHECK` da V40, porque invariante que só o código respeita não é invariante.
     */
    fun par(um: Uuid, outro: Uuid): Pair<Uuid, Uuid> =
        if (um.toString() < outro.toString()) um to outro else outro to um

    /** Por que um pedido não pode ser criado. `null` = pode. */
    enum class Impedimento {
        A_SI_MESMO,
        JA_SAO_AMIGOS,
        JA_EXISTE_PEDIDO,
        BLOQUEIO,
        TETO_ATINGIDO;

        /**
         * A frase que o usuário lê. Mora aqui e não na rota porque o `.name` do enum já vazou
         * para a tela uma vez (`JoinBlock`, fatia A.2) e apareceu como "TETO_ATINGIDO".
         *
         * **`BLOQUEIO` é deliberadamente vago.** Dizer "esta pessoa bloqueou você" transforma o
         * bloqueio num recado, e quem bloqueia quer sumir, não avisar. A mesma frase serve para
         * os dois sentidos do bloqueio, então nem quem bloqueou nem quem foi bloqueado descobre
         * pela mensagem de qual lado veio.
         */
        fun frase(): String = when (this) {
            A_SI_MESMO -> "Você não pode adicionar a si mesmo."
            JA_SAO_AMIGOS -> "Vocês já são amigos."
            JA_EXISTE_PEDIDO -> "Você já enviou um pedido para esta pessoa."
            BLOQUEIO -> "Não é possível adicionar esta pessoa."
            TETO_ATINGIDO -> "Você atingiu o limite de $TETO_DE_AMIZADES amizades."
        }
    }

    /**
     * Pode pedir amizade?
     *
     * **PEDIDO CRUZADO não é impedimento** (decisão de 2026-08-27): se o alvo já me pediu, os
     * dois manifestaram a mesma intenção, e a leitura honesta é que são amigos — não que há um
     * conflito a resolver. Quem trata isso é o serviço, virando ACEITE. Por isso `existente ==
     * PENDENTE` só impede quando **EU** fui quem pediu.
     *
     * @param existente o estado da relação atual entre os dois, se houver linha.
     * @param euPedi quem mandou o pedido pendente fui eu? Só faz sentido com [existente] PENDENTE.
     * @param haBloqueio em QUALQUER sentido — quem bloqueou também não deve conseguir pedir, ou
     *   o app o levaria a se desbloquear sem perceber.
     * @param amizadesDeQuemPede contagem de ACEITAS de quem está pedindo. O teto do ALVO não é
     *   verificado de propósito: recusar porque o outro está cheio revelaria quantos amigos ele
     *   tem, e o pedido dele ficaria pendente até alguém sair — o servidor barra na hora do
     *   ACEITE, que é quando a linha vira ACEITA de verdade.
     */
    fun impedimentoParaPedir(
        quemPede: Uuid,
        alvo: Uuid,
        existente: Estado?,
        euPedi: Boolean,
        haBloqueio: Boolean,
        amizadesDeQuemPede: Int,
    ): Impedimento? = when {
        quemPede == alvo -> Impedimento.A_SI_MESMO
        haBloqueio -> Impedimento.BLOQUEIO
        existente == Estado.ACEITA -> Impedimento.JA_SAO_AMIGOS
        // Só é "já existe pedido" se EU o mandei. Pedido do outro lado vira aceite, não erro.
        existente == Estado.PENDENTE && euPedi -> Impedimento.JA_EXISTE_PEDIDO
        amizadesDeQuemPede >= TETO_DE_AMIZADES -> Impedimento.TETO_ATINGIDO
        // RECUSADA e null caem aqui: recusar não é banir. Quem foi recusado pode pedir de novo —
        // para banir de vez existe o bloqueio, que é explícito e reversível pelo dono.
        else -> null
    }

    /**
     * Só o DESTINATÁRIO responde.
     *
     * Sem esta regra, quem mandou o pedido poderia aceitá-lo sozinho — que é a falha mais óbvia
     * de um fluxo com aceite, e a mais fácil de deixar passar, porque as duas pessoas estão na
     * mesma linha da tabela.
     */
    fun podeResponder(quemAge: Uuid, requestedBy: Uuid, estado: Estado): Boolean =
        estado == Estado.PENDENTE && quemAge != requestedBy

    /** Só quem MANDOU cancela o próprio pedido. O outro lado recusa, que é diferente. */
    fun podeCancelar(quemAge: Uuid, requestedBy: Uuid, estado: Estado): Boolean =
        estado == Estado.PENDENTE && quemAge == requestedBy

    /** Desfazer amizade: qualquer um dos dois, e só se ela existe. */
    fun podeDesfazer(estado: Estado): Boolean = estado == Estado.ACEITA
}
