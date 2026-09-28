package dev.rafael.server.features.program.services

import dev.rafael.contract.program.ProgramDto
import kotlin.time.Clock

/**
 * V60 — reverte a V59: "ativo" agora é PONTEIRO DE PROGRAMA (`user.activeProgramId`), não de
 * treino. Aplicada na ROTA (`GET /programs`), depois do blur: o blur pode esvaziar `exercises`
 * de um dia trancado, mas nunca troca o `id`, então a comparação não quebra pra quem não é
 * premium.
 *
 * O treino "ativo hoje" dentro do programa ativo é DERIVADO do `dayOfWeek` de cada treino
 * contra o dia da semana ATUAL do servidor ([DiaDaSemanaAtual]) — não é mais uma escolha
 * manual por treino. Programa ativo em dia de descanso (nenhum `dayOfWeek` bate com hoje) =
 * nenhum treino marcado, de propósito: não há "treino de hoje" pra sugerir.
 */
object ProgramActiveWorkout {

    fun apply(program: ProgramDto, activeProgramId: String?, clock: Clock = Clock.System): ProgramDto {
        val ehOAtivo = activeProgramId != null && program.id == activeProgramId
        if (!ehOAtivo) {
            return program.copy(
                isActive = false,
                workouts = program.workouts.map { it.copy(isActive = false) },
            )
        }
        val hoje = DiaDaSemanaAtual.iso(clock)
        return program.copy(
            isActive = true,
            workouts = program.workouts.map { it.copy(isActive = it.dayOfWeek == hoje) },
        )
    }
}
