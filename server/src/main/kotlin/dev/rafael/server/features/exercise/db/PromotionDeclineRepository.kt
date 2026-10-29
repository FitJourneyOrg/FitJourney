package dev.rafael.server.features.exercise.db

import dev.rafael.contract.profile.Level
import dev.rafael.core.result.AppResult
import kotlin.uuid.Uuid

/**
 * Recusa de sugestão de promoção de nível (motor v3, Fatia 11). Schema: `V70__recusa_de_promocao.sql`,
 * desenho em `docs/trabalho/motor-v3-fatia-11.md`.
 *
 * A SUGESTÃO em si não é persistida — é derivada do histórico que [ProgressaoRepository] já guarda
 * ([dev.rafael.server.features.exercise.engine.v3.PromocaoDeNivel.diagnosticoIniciante]/
 * `diagnosticoAvancado`). Este repositório existe só para o motor saber que já perguntou e quando
 * pode perguntar de novo — ver
 * [dev.rafael.server.features.exercise.engine.v3.PromocaoDeNivel.podeSugerirDeNovo].
 *
 * `nivelProposto` é restrito a `INTERMEDIATE`/`ADVANCED` pela CHECK da V70 — `BEGINNER` nunca é
 * destino de promoção.
 */
interface PromotionDeclineRepository {

    /** `null` = nunca recusou esse nível — [PromocaoDeNivel.podeSugerirDeNovo] sempre `true` nesse caso. */
    suspend fun mesocicloDaUltimaRecusa(usuarioId: Uuid, nivelProposto: Level): AppResult<Int?>

    /** Upsert (D-1): guarda só a recusa MAIS RECENTE por `(usuário, nível proposto)`. */
    suspend fun registrarRecusa(usuarioId: Uuid, nivelProposto: Level, mesocicloAtual: Int): AppResult<Unit>
}
