package dev.rafael.server.features.program.services

import dev.rafael.contract.program.ProgramDto
import dev.rafael.contract.workout.WorkoutOrigin

/**
 * Blur/value-first (ARCH #23) — pura, sem HTTP/banco. Pra usuário NÃO-premium, o
 * programa GERADO POR IA mostra o Dia 1 livre (prova) e tranca os dias de índice > 0:
 * o servidor ESVAZIA os exercícios e marca `locked`, guardando quantos eram em
 * `lockedExerciseCount` (FOMO). Blur no cliente com dado real seria falso bloqueio
 * (proxy lê o JSON) — o corte é aqui, entitlement é autoritativo do servidor (§8).
 *
 * Programa MANUAL (o usuário montou, #25) e usuário PREMIUM veem tudo.
 */
object ProgramBlur {

    fun apply(program: ProgramDto, isPremium: Boolean): ProgramDto {
        if (isPremium || program.origin != WorkoutOrigin.AI) return program

        val workouts = program.workouts.mapIndexed { index, w ->
            if (index == 0) {
                w
            } else {
                w.copy(
                    locked = true,
                    lockedExerciseCount = w.exercises.size,
                    exercises = emptyList(),
                )
            }
        }
        return program.copy(workouts = workouts, locked = workouts.any { it.locked })
    }
}
