package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.ReportTarget
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.checkin.db.ModeracaoRepository
import dev.rafael.server.features.checkin.models.AcaoDeModeracao
import dev.rafael.server.features.checkin.models.CasoAberto
import dev.rafael.server.features.checkin.models.NovaDenuncia
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Denúncias em memória (fatia E.2), com o **índice único de verdade**.
 *
 * A chave é `(alvoId, denuncianteId)` — a mesma dos dois índices parciais da V45. Um dublê que
 * guardasse numa lista aceitaria a segunda denúncia da mesma pessoa, e o teste da 6.11 passaria
 * contando 2 onde o Postgres contaria 1. **Fake sem o índice do banco prova o oposto do que se
 * quer.** É a mesma escolha do `FakeCheckInRepository` (um por dia) e do `FakeSocialRepository`
 * (uma reação por pessoa).
 *
 * O que este dublê NÃO reproduz — e por isso existe teste de integração:
 *
 * - o `CASCADE` que apaga a denúncia junto com o comentário;
 * - o `CHECK` do arco exclusivo;
 * - o `CHECK` do motivo não-vazio.
 */
class FakeModeracaoRepository : ModeracaoRepository {

    /** As denúncias, chaveadas como o índice único do banco. */
    val denuncias = mutableMapOf<Pair<Uuid, Uuid>, NovaDenuncia>()

    /** Quando cada alvo foi resolvido. Ausente = está na fila. */
    val resolvidas = mutableMapOf<Uuid, LocalDateTime>()

    /**
     * O livro-razão append-only (6.6). **Uma lista, nunca um mapa**: a chave esconderia a segunda
     * decisão sobre o mesmo alvo, que é justamente o que o invariante quer preservar.
     */
    val acoes = mutableListOf<AcaoDeModeracao>()

    override suspend fun denunciar(nova: NovaDenuncia): AppResult<Boolean> {
        val chave = nova.alvoId to nova.denuncianteId
        if (chave in denuncias) return false.asSuccess()   // o índice recusou
        denuncias[chave] = nova
        return true.asSuccess()
    }

    override suspend fun fila(groupId: Uuid): AppResult<List<CasoAberto>> =
        abertas(groupId).agrupar().asSuccess()

    override suspend fun casoAberto(groupId: Uuid, alvoId: Uuid): AppResult<CasoAberto?> =
        abertas(groupId).filter { it.alvoId == alvoId }.agrupar().singleOrNull().asSuccess()

    override suspend fun pendentes(groupId: Uuid): AppResult<Int> =
        abertas(groupId).agrupar().size.asSuccess()

    override suspend fun resolver(alvoId: Uuid, quando: LocalDateTime): AppResult<Unit> {
        resolvidas[alvoId] = quando
        return Unit.asSuccess()
    }

    override suspend fun registrar(acao: AcaoDeModeracao): AppResult<Unit> {
        acoes += acao
        return Unit.asSuccess()
    }

    override suspend fun jaDenunciei(alvoId: Uuid, userId: Uuid): AppResult<Boolean> =
        ((alvoId to userId) in denuncias).asSuccess()

    // ---- utilidades ----

    private fun abertas(groupId: Uuid) = denuncias.values
        .filter { it.groupId == groupId && it.alvoId !in resolvidas }
        .sortedBy { it.createdAt }

    /** 6.11: várias do mesmo alvo viram UM caso, com contador. Igual ao repositório real. */
    private fun List<NovaDenuncia>.agrupar(): List<CasoAberto> =
        groupBy { it.alvoId }
            .map { (alvoId, linhas) ->
                CasoAberto(
                    alvo = linhas.first().alvo,
                    alvoId = alvoId,
                    quantidade = linhas.size,
                    motivos = linhas.map { it.motivo },
                    primeiraEm = linhas.minOf { it.createdAt },
                )
            }
            .sortedBy { it.primeiraEm }

    /** Atalho de leitura para os testes: o que já se decidiu sobre este alvo. */
    fun decisoesSobre(alvoId: Uuid, tipo: ReportTarget) =
        acoes.filter { it.alvoId == alvoId && it.alvo == tipo }
}
