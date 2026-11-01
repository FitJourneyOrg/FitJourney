package dev.rafael.server.features.program.models

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object ProgramsTable : Table("programs") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val name = varchar("name", 100)          // ARCH #27 — auto-gerado, editável via PUT /programs/{id}
    val origin = varchar("origin", 16)       // WorkoutOrigin.name — AI (motor) ou MANUAL (shell pra treino avulso)
    val daysPerWeek = integer("days_per_week")
    val split = varchar("split", 64).nullable()   // SplitType.name; null = MANUAL (shell). V56: era .label
    val focusMuscles = text("focus_muscles").nullable()   // JSON List<MuscleGroup>; null = legado (pré-V56)
    // V73 (débito (c)/(d) de "o motor sabe, o app não mostra"): snapshot gravado na criação,
    // mesmo padrão de focusMuscles. null só em linha de antes da V73; motor que não calcula
    // ainda (StructureEngine, produção hoje) grava "[]" (lista vazia), não null -- dormente
    // até o v3 virar o WorkoutGenerator.
    val avisos = text("avisos").nullable()                              // JSON List<ProgramAvisoDto>
    val grupamentosReduzidosPeloFoco = text("grupamentos_reduzidos_pelo_foco").nullable()  // JSON List<String>
    val locked = bool("locked").default(false)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    val durationWeeks = integer("duration_weeks")   // ARCH #22: janela do cronograma (semanas)
    val startedAt = datetime("started_at")           // início da janela (autoridade do servidor)

    override val primaryKey = PrimaryKey(id)
}
