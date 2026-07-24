package dev.rafael.server.features.workout.models

import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

data class Workout(
    val id: Uuid,
    val userId: Uuid,
    val name: String,
    // Nullable (ARCH #26): órfãos pré-existentes (dev, débito assumido na V12) ainda
    // têm NULL no banco. Toda criação nova exige programId — validado na rota.
    val programId: Uuid?,
    val exercises: List<WorkoutExercise>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

// --------- 1) MODEL INTERNO (server) — adicionar o campo ---------
// No arquivo do model (dev.rafael.server.features.workout.models ou .domain):
data class WorkoutExercise(
    val id: Uuid,
    val exerciseId: Uuid,
    val orderIndex: Int,
    val restSeconds: Int,        // <- NOVO (sem default: força passar em todo ponto de construção)
    val rir: Int?,               // ARCH #27 — RIR alvo (null no manual). Sem default: compilador cobra.
    val sets: List<WorkoutSet>,
)

data class WorkoutSet(
    val id: Uuid,
    val reps: Int,
    val orderIndex: Int,
)

/** Resumo pra lista. */
data class WorkoutSummary(
    val id: Uuid,
    val name: String,
    val exerciseCount: Int,
    val updatedAt: LocalDateTime,
)