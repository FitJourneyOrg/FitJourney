package dev.rafael.server.features.checkin.db

import dev.rafael.contract.checkin.ReportTarget
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.map
import dev.rafael.server.features.checkin.models.AcaoDeModeracao
import dev.rafael.server.features.checkin.models.CasoAberto
import dev.rafael.server.features.checkin.models.NovaDenuncia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

class ModeracaoRepositoryImpl : ModeracaoRepository {

    /**
     * `insertIgnore` e releitura, em vez de "SELECT e depois INSERT".
     *
     * Quem decide o empate é o índice único parcial da V45. Um `SELECT` antes deixaria a janela
     * clássica: dois toques passam pela checagem, os dois inserem, e um estoura com 500 na cara de
     * quem está denunciando. Mesma forma do `criar` do check-in — código novo imita código
     * existente.
     */
    override suspend fun denunciar(nova: NovaDenuncia): AppResult<Boolean> = dbQuery {
        transaction {
            GroupReportsTable.insertIgnore {
                it[id] = nova.id
                it[groupId] = nova.groupId
                it[checkInId] = nova.alvoId.takeIf { _ -> nova.alvo == ReportTarget.CHECK_IN }
                it[commentId] = nova.alvoId.takeIf { _ -> nova.alvo == ReportTarget.COMMENT }
                it[reporterId] = nova.denuncianteId
                it[reason] = nova.motivo
                it[createdAt] = nova.createdAt
            }

            // A linha que está lá é a MINHA? Se não, esta pessoa já tinha denunciado este alvo.
            GroupReportsTable
                .selectAll()
                .where { GroupReportsTable.id eq nova.id }
                .empty()
                .not()
        }
    }

    override suspend fun fila(groupId: Uuid): AppResult<List<CasoAberto>> = dbQuery {
        transaction {
            GroupReportsTable
                .selectAll()
                .where { (GroupReportsTable.groupId eq groupId) and GroupReportsTable.resolvedAt.isNull() }
                .orderBy(GroupReportsTable.createdAt to SortOrder.ASC)
                .map { it.toLinha() }
                .agrupar()
        }
    }

    override suspend fun casoAberto(groupId: Uuid, alvoId: Uuid): AppResult<CasoAberto?> = dbQuery {
        transaction {
            GroupReportsTable
                .selectAll()
                .where {
                    (GroupReportsTable.groupId eq groupId) and
                        GroupReportsTable.resolvedAt.isNull() and
                        ((GroupReportsTable.checkInId eq alvoId) or (GroupReportsTable.commentId eq alvoId))
                }
                .orderBy(GroupReportsTable.createdAt to SortOrder.ASC)
                .map { it.toLinha() }
                .agrupar()
                .singleOrNull()
        }
    }

    /**
     * Conta CASOS, não denúncias.
     *
     * Cinco pessoas denunciando o mesmo check-in são **um** item para o admin abrir (6.11). Um
     * `COUNT(*)` cru diria "5 pendentes" e a fila mostraria uma linha — o badge mentiria sobre o
     * tamanho do trabalho.
     */
    override suspend fun pendentes(groupId: Uuid): AppResult<Int> =
        fila(groupId).map { it.size }

    override suspend fun resolver(alvoId: Uuid, quando: LocalDateTime): AppResult<Unit> = dbQuery {
        transaction {
            GroupReportsTable.update({
                GroupReportsTable.resolvedAt.isNull() and
                    ((GroupReportsTable.checkInId eq alvoId) or (GroupReportsTable.commentId eq alvoId))
            }) {
                it[resolvedAt] = quando
            }
        }
    }.map { }

    /**
     * Só `insert`. **Não existe `update` nem `delete` nesta tabela em nenhum lugar do código**, e a
     * ausência é o invariante 6.6 — decisões do admin são imutáveis, correção é registro novo.
     */
    override suspend fun registrar(acao: AcaoDeModeracao): AppResult<Unit> = dbQuery {
        transaction {
            ModerationActionsTable.insert {
                it[id] = acao.id
                it[groupId] = acao.groupId
                it[adminId] = acao.adminId
                it[targetType] = acao.alvo.name
                it[targetId] = acao.alvoId
                it[action] = acao.acao.name
                it[createdAt] = acao.createdAt
            }
        }
    }.map { }

    override suspend fun jaDenunciei(alvoId: Uuid, userId: Uuid): AppResult<Boolean> = dbQuery {
        transaction {
            GroupReportsTable
                .selectAll()
                .where {
                    (GroupReportsTable.reporterId eq userId) and
                        ((GroupReportsTable.checkInId eq alvoId) or (GroupReportsTable.commentId eq alvoId))
                }
                .empty()
                .not()
        }
    }

    // ---- utilidades ----

    /**
     * Uma denúncia crua, já com o alvo resolvido do arco exclusivo.
     *
     * A tradução "duas colunas anuláveis → um par (tipo, id)" acontece **aqui e só aqui**. Deixar o
     * arco vazar para o serviço espalharia `if (checkInId != null)` por toda a fatia.
     */
    private data class Linha(
        val alvo: ReportTarget,
        val alvoId: Uuid,
        val motivo: String,
        val quando: LocalDateTime,
    )

    private fun ResultRow.toLinha(): Linha {
        val checkIn = this[GroupReportsTable.checkInId]
        return if (checkIn != null) {
            Linha(ReportTarget.CHECK_IN, checkIn, this[GroupReportsTable.reason], this[GroupReportsTable.createdAt])
        } else {
            Linha(
                ReportTarget.COMMENT,
                // Não-nulo pelo `CHECK` da V45: exatamente um dos dois está preenchido. O `!!` é o
                // ponto em que o código confia numa garantia que o BANCO dá — e é o único.
                this[GroupReportsTable.commentId]!!,
                this[GroupReportsTable.reason],
                this[GroupReportsTable.createdAt],
            )
        }
    }

    /**
     * 6.11: várias denúncias do mesmo alvo viram UM caso, com contador.
     *
     * Agrupado em memória e não com `GROUP BY`, porque os **motivos** precisam vir junto: um
     * `string_agg` no Postgres devolveria os textos concatenados e perderia a separação entre "três
     * pessoas disseram a mesma coisa" e "uma disse três". A fila de um grupo tem dezenas de linhas,
     * não milhares — o custo é irrelevante e a clareza não.
     */
    private fun List<Linha>.agrupar(): List<CasoAberto> =
        groupBy { it.alvoId }
            .map { (alvoId, linhas) ->
                CasoAberto(
                    alvo = linhas.first().alvo,
                    alvoId = alvoId,
                    quantidade = linhas.size,
                    motivos = linhas.map { it.motivo },
                    primeiraEm = linhas.minOf { it.quando },
                )
            }
            // Mais antigo primeiro: fila se atende por ordem de chegada. O `groupBy` preserva a
            // ordem de inserção, mas depender disso seria depender de detalhe de implementação.
            .sortedBy { it.primeiraEm }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
