package dev.rafael.server.features.program.services

import dev.rafael.contract.workout.WorkoutOrigin

/**
 * DEFINIÇÃO ÚNICA de quem pode ver o quê num programa (ARCH #23, value-first).
 *
 * [REGRA] Em programa gerado por IA, usuário free enxerga apenas o PRIMEIRO treino da agenda.
 * Programa manual (#25) e usuário premium veem tudo.
 *
 * POR QUE existe como objeto próprio, e não inline em cada lugar: a mesma regra é lida de
 * dois ângulos opostos —
 *   - [ProgramBlur] a usa para ESCONDER os dias trancados na listagem;
 *   - `ProgramService.requireReadable` a usa para RECUSAR o recurso trancado.
 *
 * Enquanto o corte vivia inline (`index == 0`) só no blur, o `GET /workouts/{id}` ficou sem
 * gate nenhum: a listagem trancava, mas o recurso entregava o conteúdo inteiro a quem pedisse
 * pelo id. Se cada lado reimplementasse a regra ("índice 0" de um lado, "menor dayOfWeek" do
 * outro), uma reordenação de agenda faria os dois divergirem — e a divergência vaza conteúdo
 * pago para um lado ou tranca o dia grátis para o outro.
 */
object ProgramAccess {

    /** Índice do único treino livre num programa IA para usuário free. */
    private const val INDICE_LIVRE = 0

    /**
     * O treino na posição [index] está liberado para este usuário?
     *
     * @param index posição do treino na agenda do programa (a mesma ordem que o repositório
     *              devolve e que o DTO expõe — ordenada por dia da semana).
     */
    fun liberado(origin: WorkoutOrigin, isPremium: Boolean, index: Int): Boolean =
        isPremium || origin != WorkoutOrigin.AI || index == INDICE_LIVRE
}
