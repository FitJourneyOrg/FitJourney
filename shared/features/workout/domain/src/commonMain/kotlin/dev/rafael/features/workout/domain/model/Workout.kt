package dev.rafael.features.workout.domain.model

data class Workout(
    val id: String?,                      // null ao criar; server gera
    val name: String,
    // Obrigatório na criação (ARCH #27 — todo treino vive dentro de um programa).
    // Nullable no tipo porque também é usado pra ler treinos legados sem programa.
    val programId: String?,
    val dayOfWeek: Int? = null,           // dia da semana (1=Seg..7=Dom); escolhido no create manual
    val exercises: List<WorkoutExercise>,
    val createdAt: String?,
    val updatedAt: String?,
)

data class WorkoutExercise(
    val exerciseId: String,
    val orderIndex: Int,
    val restSeconds: Int = 90,   // <- NOVO
    val rir: Int? = null,        // ARCH #26 — preservado no round-trip (senão editar apaga o RIR)
    val sets: List<WorkoutSet>,
    // fatia "sugestao-de-progressao" (2026-11-06) — SÓ LEITURA, nunca escrito de volta no toDto
    // (ver WorkoutMapper.toDto): o servidor recalcula a cada GET, não lê o que o cliente manda.
    val cargaSugeridaKg: Double? = null,
    val repsMinSugerido: Int? = null,
    val repsMaxSugerido: Int? = null,
)

data class WorkoutSet(
    val reps: Int,
    val orderIndex: Int,
)