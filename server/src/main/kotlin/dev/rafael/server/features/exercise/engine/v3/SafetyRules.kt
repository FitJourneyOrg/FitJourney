package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.server.features.exercise.models.Exercise

/**
 * Motor v3, etapa [8], parte 1 — regras de segurança (`docs/trabalho/motor-v3-fatia-7.md`).
 * Espelha `server/src/test/oracle/motor_v3.py::modo_conservador_automatico()`/`rir_minimo()`.
 *
 * Troca de barra por halter/máquina para o iniciante: **não construída nesta fatia.** A
 * especificação (`motor-v3-ESPECIFICACAO.md`, linhas 521/765) manda o gatilho ser `BEGINNER`
 * literal, não o modo conservador. Os 8 exercícios com `rirMinimoSeguranca=true` (V67) são todos
 * `Level.INTERMEDIATE`/`ADVANCED` — a curadoria já garante que nenhum `BEGINNER` os recebe pelo
 * filtro normal de nível do catálogo, então a regra da especificação já está satisfeita sem
 * código, e uma troca "para o iniciante" nunca disparária (regra morta por desenho do catálogo).
 * Não estender para o modo conservador: seria regra nova, fora da especificação (decisão do
 * Rafael, 2026-10-24). O `[INV]` correspondente está em `docs/regras/01-motor-de-geracao.md`.
 */
object SafetyRules {

    /**
     * Especificação, "Modo conservador": iniciante, saúde geral ou 60+ no 1º mesociclo. O
     * gatilho por preferência declarada ("quer começar no modo leve?") fica fora — o campo não
     * existe em `ProfileDto`/nenhuma migration ainda.
     */
    fun modoConservadorAutomatico(nivel: Level, objetivo: Goal, idade: Int?, mesocicloAtual: Int?): Boolean =
        nivel == Level.BEGINNER ||
            objetivo == Goal.GENERAL_HEALTH ||
            (idade != null && idade >= 60 && CapacityModel.primeiroMesociclo(mesocicloAtual))

    /**
     * Piso de RIR de segurança. Não é a tabela de RIR por semana/objetivo — isto é só o piso
     * que pode apertar ela. `3` se o modo conservador estiver ativo (vale em TODO exercício, não
     * só nos de `rirMinimoSeguranca`); senão `2` se o exercício carrega a coluna ou passa sobre
     * a cabeça com peso livre (`exercicio.rirMinimoSeguranca`, V67); senão `null` (nenhum piso).
     *
     * `[INV]` (`docs/regras/01-motor-de-geracao.md`): nenhum perfil `BEGINNER` recebe exercício
     * com `rirMinimoSeguranca=true` — ver KDoc da classe sobre a troca de barra não construída.
     */
    fun rirMinimo(exercicio: Exercise?, modoConservador: Boolean): Int? = when {
        modoConservador -> 3
        exercicio?.rirMinimoSeguranca == true -> 2
        else -> null
    }
}
