package dev.rafael.server.features.exercise.db

import dev.rafael.server.features.session.db.WorkoutSessionsTable
import dev.rafael.server.features.user.db.UsersTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

/** Espelha `V69__progressao_por_exercicio.sql`. Chave `(usuário, exercício)` — D-1. */
object ExerciseProgressionTable : Table("exercise_progression") {
    val userId = uuid("user_id").references(UsersTable.id)
    val exerciseId = uuid("exercise_id").references(ExercisesTable.id)

    /** `null` = calibração pendente. */
    val cargaKg = double("carga_kg").nullable()
    val exposicoes = integer("exposicoes")
    val posSalto = bool("pos_salto")
    val umRmKg = double("um_rm_kg").nullable()
    val semSubir = integer("sem_subir")

    /**
     * V76, fatia "progressao-por-repeticao". `null` nos dois = o exercício progride por carga
     * (ou nunca foi feito); uma linha só usa ESTE par OU `cargaKg`/`umRmKg`/`posSalto`, nunca os
     * dois -- `incremento` do catálogo é fixo por exercício, não muda em runtime.
     */
    val repsMin = integer("reps_min").nullable()
    val repsMax = integer("reps_max").nullable()

    /** Relógio do SERVIDOR. */
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(userId, exerciseId)
}

/** Mesociclo por PESSOA — D-4. */
object UserMesocycleTable : Table("user_mesocycle") {
    val userId = uuid("user_id").references(UsersTable.id)
    val numero = integer("numero")
    val semana = integer("semana")
    val conservador = bool("conservador")
    val updatedAt = datetime("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

/** Append-only; a PK é a chave de idempotência do D-2. */
object ProgressionEventTable : Table("progression_event") {
    val userId = uuid("user_id").references(UsersTable.id)
    val exerciseId = uuid("exercise_id").references(ExercisesTable.id)
    val sessionId = uuid("session_id").references(WorkoutSessionsTable.id)

    /** `SOBE` | `MANTEM` | `DESCE` | `DELOAD` | `SOBE_REP` | `MANTEM_REP` | `DESCE_REP` (V76). */
    val tipo = text("tipo")
    val createdAt = datetime("created_at")

    /** Ordem de registro (IDENTITY do banco, nunca informada no insert). É por ela que o histórico ordena. */
    val seq = long("seq").databaseGenerated()

    /**
     * V71. Mesociclo da pessoa no momento do evento — fonte das âncoras do `PromotionService`
     * (exercício com evento no mesociclo ATUAL) e, quando o avançado tiver service/rota, do
     * snapshot de 1RM por mesociclo. `null` = evento anterior à V71, sem backfill.
     */
    val mesociclo = integer("mesociclo").nullable()

    /**
     * V72. Snapshot do 1RM estimado (`Progressao.Estado.umRm`) NO MOMENTO deste evento --
     * o estado pós-decisão que `Progressao.decidir()`/`calibrar()` já calcula e só não era
     * persistido por evento. `null` = evento anterior à V72, sem snapshot recuperável (só o
     * ATUAL sobrevive em `exercise_progression`, que não é o mesmo dado). Fonte de
     * "1RM no início do mesociclo N" pro diagnóstico de promoção do avançado
     * (`PromocaoDeNivel.diagnosticoAvancado`), sem tabela de snapshot separada.
     */
    val umRmKg = double("um_rm_kg").nullable()
    override val primaryKey = PrimaryKey(userId, exerciseId, sessionId)
}

/**
 * Espelha `V70__recusa_de_promocao.sql` (Fatia 11). Só a recusa MAIS RECENTE por
 * `(user_id, nivel_proposto)` — histórico completo omitido de propósito, ver cabeçalho da migration.
 */
object PromotionDeclineTable : Table("promotion_decline") {
    val userId = uuid("user_id").references(UsersTable.id)

    /** `INTERMEDIATE` | `ADVANCED`. */
    val nivelProposto = text("nivel_proposto")
    val mesocicloNaRecusa = integer("mesociclo_na_recusa")
    val recusadoEm = datetime("recusado_em")
    override val primaryKey = PrimaryKey(userId, nivelProposto)
}
