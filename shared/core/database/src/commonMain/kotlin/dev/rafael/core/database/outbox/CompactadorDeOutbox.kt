package dev.rafael.core.database.outbox

/** O que a operação faz com o recurso — decide o verbo HTTP e como ela se funde com as outras. */
enum class TipoOperacao {
    CRIAR_TREINO,
    EDITAR_TREINO,
    EXCLUIR_TREINO,
    CRIAR_PROGRAMA,
    RENOMEAR_PROGRAMA,
    EXCLUIR_PROGRAMA,
    DEFINIR_AGENDA,
    ;

    val ehCriacao: Boolean get() = this == CRIAR_TREINO || this == CRIAR_PROGRAMA
    val ehExclusao: Boolean get() = this == EXCLUIR_TREINO || this == EXCLUIR_PROGRAMA
}

/** Uma linha da fila, como o compactador a enxerga. */
data class Operacao(
    val seq: Long,
    val tipo: TipoOperacao,
    val alvoId: String,
    val payload: String,
)

/**
 * COMPACTAÇÃO da fila (ARCH #30, outbox).
 *
 * O QUE faz: transforma a sequência de operações que o usuário produziu offline no MENOR
 * conjunto de requisições que leva o servidor ao mesmo estado final.
 *
 * POR QUE: 30 segundos mexendo num treino geram facilmente 12 comandos. Sem compactar seriam
 * 12 round-trips, e os 11 primeiros gravariam estado que o último sobrescreve. Pior: se a rede
 * cair no meio de uma sequência criar→excluir, o servidor fica com um recurso fantasma que o
 * usuário já tinha apagado.
 *
 * As três regras, por alvo:
 *
 * | sequência                | vira            | por quê |
 * |--------------------------|-----------------|---------|
 * | criar + editar + editar  | criar (último)  | o servidor nunca viu o recurso; manda o estado final direto |
 * | criar + ... + excluir    | **nada**        | recurso que o servidor nunca conheceu e já não existe |
 * | editar + editar          | editar (último) | o servidor já tem o recurso; só o estado final importa |
 * | editar + excluir         | excluir         | o estado intermediário é irrelevante |
 *
 * [REGRA] A ORDEM GLOBAL é preservada. Não basta agrupar por alvo: criar um treino e depois
 * agendá-lo produz duas operações em que a segunda referencia a primeira, e o servidor recusa
 * a agenda se o treino ainda não existe. Cada alvo mantém a posição de sua PRIMEIRA operação.
 */
object CompactadorDeOutbox {

    fun compactar(fila: List<Operacao>): List<Operacao> {
        // Posição de estreia de cada alvo — é ela que define a ordem do resultado.
        val estreia = LinkedHashMap<String, Long>()
        fila.forEach { estreia.putIfAbsent(it.alvoId, it.seq) }

        return fila.groupBy { it.alvoId }
            .mapNotNull { (_, doAlvo) -> resolver(doAlvo) }
            .sortedBy { estreia[it.alvoId] }
    }

    /** Resolve as operações de UM alvo numa só (ou em nenhuma). */
    private fun resolver(doAlvo: List<Operacao>): Operacao? {
        val criou = doAlvo.any { it.tipo.ehCriacao }
        val ultima = doAlvo.last()

        return when {
            // Criado e excluído offline: o servidor nunca soube que existiu. Nada a enviar.
            criou && ultima.tipo.ehExclusao -> null

            // Nasceu offline: uma criação só, já com o estado final. Preserva o TIPO da criação
            // (é ele que decide POST) e o PAYLOAD da última edição.
            criou -> doAlvo.first { it.tipo.ehCriacao }.copy(
                payload = ultima.payload.ifEmpty { doAlvo.last { it.payload.isNotEmpty() }.payload },
                seq = ultima.seq,
            )

            // Já existia no servidor: vale a última intenção (editar ou excluir).
            else -> ultima
        }
    }
}
