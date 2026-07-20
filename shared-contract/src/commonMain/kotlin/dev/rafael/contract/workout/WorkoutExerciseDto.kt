package dev.rafael.contract.workout

import kotlinx.serialization.Serializable

/**
 * Exercício dentro de um treino.
 * restSeconds: descanso entre séries deste exercício (segundos).
 *   - O motor (F.2) gera por nível: ~60-90s iniciante, 90-120s intermediário, 120-180s avançado.
 *   - Default 90 = neutro seguro (treino manual sem descanso definido; espelha o default da coluna).
 *   - É do exercício, não da série: não se descansa diferente entre séries do mesmo exercício.
 */
@Serializable
data class WorkoutExerciseDto(
    val exerciseId: String,          // FK → catálogo (o cliente cruza p/ nome/thumb)
    val orderIndex: Int,
    val restSeconds: Int = 90,       // NOVO — descanso entre séries (F.2)
    val sets: List<WorkoutSetDto>,
)
