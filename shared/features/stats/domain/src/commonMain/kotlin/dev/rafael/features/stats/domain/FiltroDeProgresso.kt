package dev.rafael.features.stats.domain

import dev.rafael.contract.stats.JanelasDeProgresso

/**
 * O recorte da analise de progressao (J.3): tudo, um programa, ou o que ficou fora de programa.
 *
 * ## Por que tipo fechado, e nao `programId: String?`
 *
 * Porque "nulo" teria de significar DUAS coisas — "sem filtro" e "sem programa" —, e elas pedem
 * dados opostos: a primeira traz tudo, a segunda traz justamente o que as outras excluem. Um
 * `String?` faria as duas compilarem como o mesmo estado, e o defeito apareceria como numero
 * errado na tela, nao como erro.
 *
 * ## [chave] e [parametro] sao coisas diferentes, de proposito
 *
 * A [chave] identifica o recorte no CACHE LOCAL e por isso carrega a faixa: pedir as semanas 1-4
 * e 5-8 do mesmo programa sao duas respostas diferentes, e guardar as duas sob a mesma chave
 * mostraria a faixa errada ate o TTL vencer. O [parametro] e o que vai na query, onde a faixa
 * viaja em campos proprios.
 */
sealed interface FiltroDeProgresso {

    /** Identidade no cache local. Estavel: mudou a chave, mudou a resposta guardada. */
    val chave: String

    /** O valor de `?programId=`. `null` = nao mandar o parametro. */
    val parametro: String?

    val de: Int? get() = null
    val ate: Int? get() = null

    /**
     * Janela de calendario em semanas (`?semanas=`). Nula no recorte de programa, onde quem
     * define o periodo e a faixa [de]..[ate] — duas janelas no mesmo recorte nao e estado valido.
     */
    val semanas: Int? get() = null

    /** Tudo, eixo de calendario. E o que a tela abre, e o unico recorte sempre em cache. */
    data class Todos(override val semanas: Int = JanelasDeProgresso.PADRAO) : FiltroDeProgresso {
        override val chave = "todos:$semanas"
        override val parametro: String? = null
    }

    /**
     * Sessao sem programa, ou de programa apagado.
     *
     * Os dois no mesmo balde porque o segundo nao tem NOME: o programa sumiu, e o nome dele era
     * derivado dele (V48). Oferecer "Programa removido" seria inventar identidade para o que nao
     * existe mais.
     */
    data class Avulsos(override val semanas: Int = JanelasDeProgresso.PADRAO) : FiltroDeProgresso {
        override val chave = "avulsos:$semanas"
        override val parametro = "avulsos"
    }

    /**
     * A janela trocada, mantendo o recorte — e um no-op no recorte de programa, onde ela nao tem
     * referente. Evita um `when` sobre as tres variantes em cada chamador.
     */
    fun comJanela(semanas: Int): FiltroDeProgresso = when (this) {
        is Todos -> copy(semanas = semanas)
        is Avulsos -> copy(semanas = semanas)
        is DoPrograma -> this
    }

    /** Um programa, com faixa opcional de semanas DELE (1-based, inclusiva). */
    data class DoPrograma(
        val programId: String,
        override val de: Int? = null,
        override val ate: Int? = null,
    ) : FiltroDeProgresso {
        // Sem janela na chave: no programa a FAIXA e a janela, e ela ja esta aqui.
        override val chave = "p:$programId:${de ?: ""}-${ate ?: ""}"
        override val parametro = programId
    }
}
