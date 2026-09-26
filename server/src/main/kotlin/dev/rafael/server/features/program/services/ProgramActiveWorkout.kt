package dev.rafael.server.features.program.services

import dev.rafael.contract.program.ProgramDto

/**
 * V59 — marca `isActive` no treino que bate com o ponteiro do usuário. Pura, sem HTTP/banco,
 * mesmo espírito do [ProgramBlur]. Aplicada na ROTA (`GET /programs`), depois do blur: o blur
 * pode esvaziar `exercises` de um dia trancado, mas nunca troca o `id`, então a comparação
 * não quebra mesmo pra quem não é premium.
 */
object ProgramActiveWorkout {

    fun apply(program: ProgramDto, activeWorkoutId: String?): ProgramDto {
        if (activeWorkoutId == null) return program
        return program.copy(
            workouts = program.workouts.map { it.copy(isActive = it.id == activeWorkoutId) },
        )
    }
}
