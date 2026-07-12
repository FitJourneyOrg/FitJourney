package dev.rafael.server.features.workout.ai

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.server.features.profile.models.Profile

/** Gera a estrutura de um treino a partir do perfil + prompt.
 *  A saída NÃO é confiável — a rota valida como treino manual antes de persistir (§4.3). */
interface WorkoutGenerator {
    suspend fun generate(profile: ProfileDto, prompt: String?): WorkoutDto
}