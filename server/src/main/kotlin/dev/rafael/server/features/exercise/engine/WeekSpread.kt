package dev.rafael.server.features.exercise.engine

import dev.rafael.contract.profile.SplitType

/**
 * Distribui os treinos pelos dias da semana com FOLGA entre eles (recuperação). Descanso é
 * implícito: qualquer dia sem treino.
 *
 * Piloto automático (sem dias off): usa um CALENDÁRIO-ÂNCORA curado por nº de dias — folga
 * fisiológica de 48-72h ancorada na segunda, protegendo o fim de semana quando faz sentido.
 * Se o usuário marca dias off, cai no espaçamento uniforme evitando os off.
 */
object WeekSpread {

    /**
     * Dias (1=Seg..7=Dom) para [count] treinos do [split].
     * - unavailable vazio → calendário-âncora curado (ver [curatedDefault]).
     * - unavailable preenchido → espaçamento uniforme nos dias livres.
     */
    fun daysFor(count: Int, split: String = "", unavailable: Set<Int> = emptySet()): List<Int> {
        if (count <= 0) return emptyList()
        if (unavailable.isEmpty()) curatedDefault(count, split)?.let { return it }
        val available = (1..7).filter { it !in unavailable }
        if (available.isEmpty()) return emptyList()
        if (count >= available.size) return available
        return (0 until count)
            .map { i -> available[((i + 0.5) * available.size / count).toInt()] }
            .distinct()
    }

    /**
     * Calendário-âncora ratificado por nº de dias. Regra: 48-72h de folga local e sem dias
     * consecutivos quando o split REPETE músculo. Full Body recruta tudo → nunca em dias
     * seguidos; splits que alternam grupos (U/L, PPL, Arnold) podem empilhar. null → fora de
     * 2..6, usa espaçamento.
     */
    private fun curatedDefault(count: Int, split: String): List<Int>? = when (count) {
        2 -> listOf(1, 4)                          // Seg, Qui
        3 -> listOf(1, 3, 5)                        // Seg, Qua, Sex (DSDN clássico)
        4 -> if (split == SplitType.FULL_BODY.label)
            listOf(1, 3, 5, 7)                      // Full Body: espaçado, sem consecutivos
        else
            listOf(1, 2, 4, 5)                      // U/L: bloco 2x2 (Qua off, sistêmico)
        5 -> listOf(1, 2, 4, 5, 6)                 // Seg, Ter, Qui, Sex, Sáb (folga Qua e Dom)
        6 -> listOf(1, 2, 3, 4, 5, 6)             // Seg–Sáb (folga Dom)
        else -> null
    }
}
