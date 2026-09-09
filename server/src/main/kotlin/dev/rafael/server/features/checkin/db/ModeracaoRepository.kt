package dev.rafael.server.features.checkin.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.checkin.models.AcaoDeModeracao
import dev.rafael.server.features.checkin.models.CasoAberto
import dev.rafael.server.features.checkin.models.NovaDenuncia
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/**
 * Denúncias e decisões do admin (fatia E.2).
 *
 * Repositório próprio, e não mais métodos no [SocialRepository], pelo mesmo motivo que separou o
 * social do check-in: são tabelas com regras próprias, e um repositório que cresce sem parar vira
 * o lugar onde tudo cabe — e aí nada nele é encontrável.
 */
interface ModeracaoRepository {

    /**
     * Grava a denúncia. Devolve `false` quando **esta pessoa já denunciou este alvo**.
     *
     * Quem decide isso é o índice único parcial da V45, não um `SELECT` antes — mesma escolha do
     * check-in do dia (4.3). Dois toques com a rede lenta são duas requisições em voo, e só o
     * banco resolve o empate.
     *
     * `false` **não é erro**: a segunda denúncia da mesma pessoa não muda nada, e é isso que 6.11
     * quer dizer com "viram uma solicitação".
     */
    suspend fun denunciar(nova: NovaDenuncia): AppResult<Boolean>

    /**
     * A FILA do grupo: casos abertos, agregados por alvo, **mais antigos primeiro**.
     *
     * Ordem inversa à do feed (8.0.4), de propósito: fila se atende por ordem de chegada. Como o
     * admin não tem prazo (6.12), a fila pode crescer — e ordem justa vale mais quando há espera.
     */
    suspend fun fila(groupId: Uuid): AppResult<List<CasoAberto>>

    /** Um caso específico, ou `null` se não há nada aberto sobre este alvo. */
    suspend fun casoAberto(groupId: Uuid, alvoId: Uuid): AppResult<CasoAberto?>

    /** Quantos casos esperam. Alimenta o badge sem baixar a fila. */
    suspend fun pendentes(groupId: Uuid): AppResult<Int>

    /**
     * Fecha TODAS as denúncias abertas deste alvo — o caso é um só (6.11).
     *
     * Marca, não apaga: apagada a linha, o índice único deixaria a mesma pessoa reabrir o mesmo
     * caso quantas vezes quisesse.
     */
    suspend fun resolver(alvoId: Uuid, quando: LocalDateTime): AppResult<Unit>

    /**
     * Grava a decisão. **Append-only** (6.6) — não existe `atualizar` nem `apagar` aqui, e a
     * ausência é o invariante.
     */
    suspend fun registrar(acao: AcaoDeModeracao): AppResult<Unit>

    /** Já denunciei este alvo? Usado só na conferência de um alvo por vez, nunca no feed. */
    suspend fun jaDenunciei(alvoId: Uuid, userId: Uuid): AppResult<Boolean>
}
