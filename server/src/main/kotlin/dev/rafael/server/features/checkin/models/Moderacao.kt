package dev.rafael.server.features.checkin.models

import dev.rafael.contract.checkin.ReportTarget
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * O que se grava ao denunciar (fatia E.2).
 *
 * O alvo é um par `(tipo, id)` aqui dentro, e vira duas colunas no banco. A tradução acontece no
 * repositório, num lugar só: espalhar `if (tipo == CHECK_IN)` pelo serviço faria o arco exclusivo
 * da V45 vazar para a camada que não deveria conhecê-lo.
 */
data class NovaDenuncia(
    val id: Uuid,
    val groupId: Uuid,
    val alvo: ReportTarget,
    val alvoId: Uuid,
    val denuncianteId: Uuid,
    /** Já tratado pela `ModeracaoPolicy` — o repositório não normaliza. */
    val motivo: String,
    val createdAt: LocalDateTime,
)

/**
 * Um caso na fila do admin: **todas as denúncias abertas do mesmo alvo, somadas** (6.11).
 *
 * **Não há campo de denunciante**, e não é esquecimento: a agregação acontece no banco justamente
 * para que os `user_id` fiquem lá. Um `List<Uuid>` aqui seria um dado que alguém acabaria expondo
 * — o jeito de garantir que não vaza é não carregar.
 */
data class CasoAberto(
    val alvo: ReportTarget,
    val alvoId: Uuid,
    val quantidade: Int,
    val motivos: List<String>,
    /** A denúncia mais ANTIGA. É por ela que a fila se ordena — fila se atende por chegada. */
    val primeiraEm: LocalDateTime,
)

/**
 * Uma decisão do admin, para a tabela append-only (6.6).
 *
 * Sem campo de "denúncia julgada": a decisão aponta para o **alvo**, não para o pedido. É o que
 * permite registrar a invalidação direta da 6.10, que acontece sem denúncia nenhuma — e o que
 * mantém uma leitura só ("o que já se decidiu sobre este check-in?") cobrindo os dois caminhos.
 */
data class AcaoDeModeracao(
    val id: Uuid,
    val groupId: Uuid,
    val adminId: Uuid,
    val alvo: ReportTarget,
    val alvoId: Uuid,
    val acao: TipoDeAcao,
    val createdAt: LocalDateTime,
)

/**
 * O que o admin fez. Fechado no Kotlin **e** no `CHECK` da V45.
 *
 * Ao contrário do emoji (vocabulário aberto no banco, fechado no código), aqui os dois lados
 * fecham — e a diferença é o motivo. Emoji é decisão de produto e muda; o conjunto de decisões que
 * um moderador pode tomar é estrutural, e uma linha com uma ação que o código não sabe executar
 * seria uma decisão gravada que nunca aconteceu.
 */
enum class TipoDeAcao {
    INVALIDAR_CHECK_IN,
    MANTER_CHECK_IN,
    REMOVER_COMENTARIO,
    MANTER_COMENTARIO,

    /**
     * O desafio **encerrou** antes de o admin julgar (V46, decisão de 2026-09-07).
     *
     * Único valor que não é decisão de ninguém — o `admin_id` fica nulo. Existe para que quem
     * perguntar "por que esta denúncia sumiu?" seis meses depois receba uma resposta diferente de
     * "alguém julgou".
     *
     * **Não confundir com expiração por tempo**, que foi proposta e recusada no mesmo dia: lá o
     * caso ainda podia produzir efeito, e sumir seria perdoar por inércia do admin. Aqui o ranking
     * já congelou e invalidar mudaria um resultado comemorado (6.5).
     */
    ENCERRADO_SEM_JULGAMENTO,
}
