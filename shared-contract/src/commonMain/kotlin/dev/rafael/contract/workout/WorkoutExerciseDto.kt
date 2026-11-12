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
    val rir: Int? = null,            // ARCH #26 — RIR alvo (por papel); null = manual não prescreve
    val papel: Papel? = null,        // fatia "rir-chega-e-registra" — null = manual, sem prescrição
    val sets: List<WorkoutSetDto>,
    // fatia "sugestao-de-progressao" (2026-11-06) — RESPOSTA, servidor só lê no POST/PUT.
    // Nunca os dois eixos juntos: incremento do catálogo decide carga XOR reps (ver
    // ExerciseRepository.paraProgressao). null em ambos = sem sugestão (manual, calibração
    // pendente, ou eixo BAND/HAND_GRIP ainda sem motor).
    val cargaSugeridaKg: Double? = null,
    val repsMinSugerido: Int? = null,
    val repsMaxSugerido: Int? = null,
)
