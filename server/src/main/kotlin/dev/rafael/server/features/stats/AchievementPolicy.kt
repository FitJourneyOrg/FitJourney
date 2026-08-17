package dev.rafael.server.features.stats

/**
 * Catálogo e regra das CONQUISTAS (ARCH #16 — gamificação é do PERFIL INDIVIDUAL).
 *
 * [REGRA] Autoridade do servidor: a conquista é decidida aqui, a partir do histórico. O cliente
 * nunca envia "desbloqueei" — só exibe o que o servidor concedeu.
 *
 * Kotlin puro, sem I/O: a regra inteira é testável sem banco, como o [XpPolicy].
 *
 * DERIVADA NA REGRA, PERSISTIDA NO DESBLOQUEIO. Diferente do XP, que é 100% recalculado a cada
 * consulta, a conquista é gravada com a data em que caiu. O motivo é uma armadilha específica de
 * gamificação: se amanhã o limiar de um streak subir de 7 para 10, o cálculo puro faria a medalha
 * **desaparecer** do perfil de quem já a tinha visto. Tirar do usuário uma recompensa já entregue
 * destrói a confiança em todas as outras. Por isso: uma vez concedida, nunca é retirada — e o
 * `unlockedAt` vira fato histórico, não consequência da versão atual do código.
 */
object AchievementPolicy {

    /**
     * O que a conquista mede. Existe para a UI mostrar progresso ("7 de 10") nas bloqueadas
     * sem precisar saber o que cada id significa.
     */
    enum class Metrica { SESSOES, STREAK, NIVEL }

    /**
     * [REGRA] Os ids são CONTRATO — vão para o banco e nunca mudam de significado. Renomear um
     * id existente reescreveria a história de quem já o tem; conquista nova ganha id novo.
     */
    enum class Conquista(
        val titulo: String,
        val descricao: String,
        val metrica: Metrica,
        val alvo: Int,
    ) {
        PRIMEIRO_TREINO("Começou", "Registre seu primeiro treino", Metrica.SESSOES, 1),
        TREINOS_10("Dez na conta", "Registre 10 treinos", Metrica.SESSOES, 10),
        TREINOS_50("Meio century", "Registre 50 treinos", Metrica.SESSOES, 50),
        TREINOS_100("Cem treinos", "Registre 100 treinos", Metrica.SESSOES, 100),

        // Streak reusa a definição do XpPolicy, em que DIA DE DESCANSO AGENDADO conta como
        // cumprido. É deliberado: premiar "treinou todo dia" empurraria o usuário contra o
        // próprio programa, que prescreve descanso (#22/#26). A conquista recompensa seguir
        // o plano, não ignorá-lo.
        STREAK_7("Uma semana", "7 dias seguindo o plano", Metrica.STREAK, 7),
        STREAK_30("Um mês", "30 dias seguindo o plano", Metrica.STREAK, 30),
        STREAK_90("Três meses", "90 dias seguindo o plano", Metrica.STREAK, 90),

        NIVEL_5("Nível 5", "Alcance o nível 5", Metrica.NIVEL, 5),
        NIVEL_10("Nível 10", "Alcance o nível 10", Metrica.NIVEL, 10),
        ;
    }

    /** Os números do usuário que o catálogo consulta. */
    data class Progresso(
        val sessoesValidas: Int,
        val streakDias: Int,
        val nivel: Int,
    ) {
        fun valorDe(metrica: Metrica): Int = when (metrica) {
            Metrica.SESSOES -> sessoesValidas
            Metrica.STREAK -> streakDias
            Metrica.NIVEL -> nivel
        }
    }

    /** Tudo que o progresso atual já alcança — inclusive o que já estava concedido. */
    fun alcancadas(progresso: Progresso): Set<Conquista> =
        Conquista.entries.filter { progresso.valorDe(it.metrica) >= it.alvo }.toSet()

    /**
     * O que falta CONCEDER: alcançado agora menos o que já está no banco.
     *
     * A subtração é o que torna a concessão idempotente e o retroativo automático — quem já
     * tinha 60 treinos quando a feature nasceu recebe as quatro de sessões de uma vez, na
     * primeira avaliação, sem migration de backfill.
     */
    fun aConceder(progresso: Progresso, jaConcedidas: Set<Conquista>): Set<Conquista> =
        alcancadas(progresso) - jaConcedidas
}
