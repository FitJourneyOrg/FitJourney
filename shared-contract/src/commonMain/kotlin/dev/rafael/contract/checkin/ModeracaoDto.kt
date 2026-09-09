package dev.rafael.contract.checkin

import kotlinx.serialization.Serializable

/**
 * O que se pode denunciar (6.1 + 6.4).
 *
 * Fechado no contrato, como o [CheckInStatus]: acrescentar um alvo é mexer aqui, no schema e nas
 * duas telas ao mesmo tempo — que é exatamente o trabalho que um `String` livre esconderia até
 * alguém receber `"perfil"` numa fila que não sabe abrir perfil.
 */
@Serializable
enum class ReportTarget { CHECK_IN, COMMENT }

/**
 * O que se manda ao denunciar. O alvo vai na ROTA, não aqui.
 *
 * Sem campo de categoria: uma lista fechada de motivos ("spam", "conteúdo impróprio", "fraude")
 * seria adivinhação numa v1, e o admin do grupo conhece as pessoas — ele lê a frase. O dia em que
 * a moderação sair do grupo para uma equipe, categoria passa a valer a pena.
 *
 * **Obrigatório** (decisão de 2026-09-07), e sem valor padrão: escrever o motivo é o atrito que
 * separa a denúncia pensada do toque irritado. O tipo não-anulável faz o compilador cobrar —
 * `String? = null` deixaria qualquer chamador esquecer.
 */
@Serializable
data class DenunciarRequest(
    /** De 1 a 300 caracteres, já aparado. Só espaços é recusado como vazio. */
    val reason: String,
)

/**
 * Um item da fila do admin (6.2, 6.11).
 *
 * ## O que NÃO está aqui é a decisão de produto
 *
 * **Não existe campo de denunciante.** O banco guarda quem abriu cada denúncia — precisa, para o
 * "uma por pessoa" e para a auditoria — mas o nome não atravessa esta fronteira. Num grupo de
 * conhecidos, denúncia identificada é denúncia que ninguém faz, e o resultado seria uma fila vazia
 * com o problema intacto.
 *
 * O que o admin vê é **o conteúdo, o contador e os motivos** — o suficiente para julgar o fato sem
 * julgar quem reclamou.
 */
@Serializable
data class ReportItemDto(
    /** Id do ALVO (check-in ou comentário), não da denúncia: é nele que o admin age. */
    val targetId: String,
    val target: ReportTarget,

    /** Quantas pessoas denunciaram este mesmo alvo (6.11). Nunca quem. */
    val count: Int,

    /**
     * Os motivos escritos, sem autoria, na ordem em que chegaram.
     *
     * Uma LISTA e não um texto concatenado: três pessoas dizendo a mesma coisa por escrito é
     * informação diferente de uma dizendo três coisas, e só a lista preserva a distinção. O
     * `count` acima e o tamanho desta lista são sempre iguais — o motivo é obrigatório.
     */
    val reasons: List<String> = emptyList(),

    /** ISO-8601 da denúncia mais ANTIGA — é por ela que a fila se ordena. */
    val firstReportedAt: String,

    /**
     * O conteúdo denunciado, já pronto para desenhar.
     *
     * Exatamente um dos dois vem preenchido, espelhando o arco exclusivo da V45. Mandar só o id e
     * fazer a tela buscar o conteúdo criaria N+1 de rede numa fila que pode ter dezenas de itens.
     */
    val checkIn: CheckInDto? = null,
    val comment: CommentDto? = null,
)

/**
 * O julgamento (6.3, 6.6).
 *
 * Booleano e não enum de ação: as ações possíveis dependem do alvo — invalidar um check-in,
 * remover um comentário — mas a DECISÃO é a mesma pergunta nos dois casos. Quem traduz "acatei"
 * para o efeito certo é o servidor, que sabe o que está julgando.
 *
 * **Não há campo de justificativa**, e a ausência é 6.7: não há recurso, então não há a quem
 * justificar. O que fica registrado é a decisão, append-only, para auditoria.
 */
@Serializable
data class JulgarRequest(
    /** `true` invalida o check-in / remove o comentário. `false` mantém e fecha o caso. */
    val acatar: Boolean,
)

/** Quantos casos esperam o admin. Alimenta o badge da toolbar sem baixar a fila inteira. */
@Serializable
data class ModerationBadgeDto(val pending: Int = 0)
