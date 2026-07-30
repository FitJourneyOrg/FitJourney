package dev.rafael.server.features.exercise.engine

/**
 * Distribui os treinos pelos dias da semana com FOLGA entre eles (recuperação).
 * Antes o programa caía em dias consecutivos (1..N); aqui os N treinos são espalhados
 * pelos dias disponíveis (1=Seg..7=Dom), evitando os que o usuário marcou como off.
 * Descanso é implícito: qualquer dia sem treino.
 */
object WeekSpread {

    /**
     * Escolhe [count] dias (1..7) espaçados uniformemente, pulando os [unavailable].
     * - count >= dias disponíveis → devolve todos os disponíveis (sem folga a distribuir).
     * - amostragem no ponto médio de cada "fatia" → não empilha nos extremos da semana.
     */
    fun daysFor(count: Int, unavailable: Set<Int> = emptySet()): List<Int> {
        if (count <= 0) return emptyList()
        val available = (1..7).filter { it !in unavailable }
        if (available.isEmpty()) return emptyList()
        if (count >= available.size) return available
        return (0 until count)
            .map { i -> available[((i + 0.5) * available.size / count).toInt()] }
            .distinct()
    }
}
