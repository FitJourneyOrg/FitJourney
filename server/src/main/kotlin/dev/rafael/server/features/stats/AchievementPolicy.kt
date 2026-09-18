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
     *
     * ⚠️ **Não tem título nem descrição, e isso é a fatia G.5 (ARCH #37).** Até 2026-09-15 este
     * enum carregava as duas frases em português e o `AchievementDto` as transportava prontas, o
     * que deixava a tela de Conquistas INTEIRA em português para quem escolheu inglês.
     *
     * O texto agora vive no `strings.xml` do cliente, com a chave derivada do id
     * (`PRIMEIRO_TREINO` → `conquista_primeiro_treino_titulo`). O que fica aqui é o que é regra:
     * a métrica e o alvo.
     *
     * > **Enum de servidor com propriedade `String` é texto de UI escondido num lugar onde a
     * > varredura de literais não procura.** Quinta ocorrência deste padrão nesta base.
     *
     * O vocabulário de ids está em [dev.rafael.contract.stats.ConquistaIds] e o
     * `AchievementIdsTest` amarra os dois conjuntos: conquista nova aqui sem entrada lá quebra o
     * build, e é lá que o cliente descobre que ela existe.
     */
    enum class Conquista(
        val metrica: Metrica,
        val alvo: Int,
    ) {
        PRIMEIRO_TREINO(Metrica.SESSOES, 1),
        TREINOS_10(Metrica.SESSOES, 10),
        TREINOS_50(Metrica.SESSOES, 50),
        TREINOS_100(Metrica.SESSOES, 100),

        // Streak reusa a definição do XpPolicy, em que DIA DE DESCANSO AGENDADO conta como
        // cumprido. É deliberado: premiar "treinou todo dia" empurraria o usuário contra o
        // próprio programa, que prescreve descanso (#22/#26). A conquista recompensa seguir
        // o plano, não ignorá-lo.
        STREAK_7(Metrica.STREAK, 7),
        STREAK_30(Metrica.STREAK, 30),
        STREAK_90(Metrica.STREAK, 90),

        NIVEL_5(Metrica.NIVEL, 5),
        NIVEL_10(Metrica.NIVEL, 10),
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
