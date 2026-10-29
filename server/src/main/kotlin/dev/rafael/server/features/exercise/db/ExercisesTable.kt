package dev.rafael.server.features.exercise.db

import org.jetbrains.exposed.v1.core.Table

/** Espelha V3__create_exercises.sql. Catálogo read-only de 923 exercícios (fatia H, V54/V55). */
object ExercisesTable : Table("exercises") {
    val id = uuid("id")
    val name = varchar("name", 200)
    val category = varchar("category", 32)
    val description = text("description").nullable()
    val videoRef = varchar("video_ref", 300)
    val thumbRef = varchar("thumb_ref", 300)

    // --- taxonomia (V8) ---
    val modality = varchar("modality", 16).nullable()
    val movementPattern = varchar("movement_pattern", 24).nullable()
    val secondaryPattern = varchar("secondary_pattern", 24).nullable()
    val isCompound = bool("is_compound").nullable()
    val equipment = varchar("equipment", 24).nullable()
    val primaryMuscles = array<String>("primary_muscles").nullable()
    val secondaryMuscles = array<String>("secondary_muscles").nullable()
    val unilateral = bool("unilateral").nullable()
    val prescriptionType = varchar("prescription_type", 12).nullable()
    val level = varchar("level", 16).nullable()
    val contraindications = array<String>("contraindications").nullable()

    // --- V16: exercício principal (base) vs variação/secundário ---
    val isBase = bool("is_base")

    // --- V64: alvo de volume do motor — UM valor por exercício, vocabulário de TargetMuscle.
    // NULL = fora da contabilidade do motor. Não é primary_muscles: aquele serve catálogo, UI e foco.
    val volumeTarget = varchar("volume_target", 16).nullable()

    // --- V65: como o motor escolhe QUAL exercício entra na vaga. Preenchidos só na lista
    // principal (volume_target NOT NULL) — o CHECK chk_exercises_selecao amarra os quatro a ela.
    val prioridade = short("prioridade").nullable()
    val relevancia = short("relevancia").nullable()
    val nivelMaximo = varchar("nivel_maximo", 16).nullable()
    val ambiente = varchar("ambiente", 16).nullable()

    // --- V65: padrão mecânico, no catálogo inteiro. Regra do motor: no máximo um por família
    // na mesma sessão. Serve também à troca manual na biblioteca.
    val familia = varchar("familia", 32).nullable()

    // --- V65: o que o exercício exige além do equipamento. NOT NULL DEFAULT false: não marcado
    // significa "não exige", nunca "não sei".
    val requiresBench = bool("requires_bench").default(false)
    val requiresElevatedSurface = bool("requires_elevated_surface").default(false)
    val requiresPullupBar = bool("requires_pullup_bar").default(false)
    val requiresDipStation = bool("requires_dip_station").default(false)
    val requiresStep = bool("requires_step").default(false)
    val requiresAnchorPoint = bool("requires_anchor_point").default(false)

    // --- V67 (fatia 7): RIR minimo por propriedade, aprovado por nome. NOT NULL DEFAULT false.
    val rirMinimoSeguranca = bool("rir_minimo_seguranca").default(false)

    override val primaryKey = PrimaryKey(id)
}