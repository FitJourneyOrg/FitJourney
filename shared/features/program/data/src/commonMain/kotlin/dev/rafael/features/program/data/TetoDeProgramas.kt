package dev.rafael.features.program.data

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.limites.Limites
import dev.rafael.core.result.AppError

/**
 * Cópia otimista do teto de programas (ARCH #27), no cliente.
 *
 * A criação manual é offline-first: grava local, enfileira e responde sucesso. Se o servidor
 * recusasse DEPOIS (403), o programa ficava na lista como "pendente" e a pessoa só descobria o
 * motivo ao tocar nele. Perguntar ANTES evita o programa fantasma e dá a resposta na hora.
 *
 * Os números vêm de [Limites], a mesma fonte do servidor. O servidor continua sendo a autoridade:
 * plano desconhecido não bloqueia, e quem passar daqui por cache velho ainda é recusado lá.
 */
internal object TetoDeProgramas {

    /**
     * @param total programas que a pessoa tem agora, de qualquer tipo.
     * @param premium plano conhecido (`null` = não se sabe: deixa passar).
     * @return o erro a mostrar, ou `null` se pode criar.
     */
    fun recusa(total: Int, premium: Boolean?): AppError? = when {
        premium == null -> null
        premium && total >= Limites.Program.PREMIUM_TOTAL_LIMIT -> AppError.Forbidden(
            "Você atingiu o limite máximo de ${Limites.Program.PREMIUM_TOTAL_LIMIT} programas.",
            ErrorCodes.LIMITE_DE_PROGRAMAS_PREMIUM,
        )
        !premium && total >= Limites.Program.FREE_TOTAL_LIMIT -> AppError.Forbidden(
            "Limite de programas do plano grátis: ${Limites.Program.FREE_TOTAL_LIMIT}. " +
                "Assine o premium pra criar mais.",
            ErrorCodes.LIMITE_DE_PROGRAMAS_GRATIS,
        )
        else -> null
    }
}
