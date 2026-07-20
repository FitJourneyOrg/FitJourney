package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.program.ProgramDto

/**
 * Gera um PROGRAMA semanal a partir do perfil (ARCH #22 — era WorkoutDto na Fatia D).
 * Implementação: DeterministicWorkoutGenerator (ARCH #20). O 'prompt' é resquício da
 * era LLM e é ignorado pelo motor determinístico — mantido por compatibilidade até a
 * G.1 limpar. Lança IllegalArgumentException se o perfil não tem environment (política A).
 */
interface WorkoutGenerator {
    suspend fun generate(profile: ProfileDto, prompt: String?): ProgramDto
}