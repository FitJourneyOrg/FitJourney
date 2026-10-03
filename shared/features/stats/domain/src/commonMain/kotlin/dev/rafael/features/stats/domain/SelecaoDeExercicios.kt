package dev.rafael.features.stats.domain

/**
 * Quais exercicios ganham LINHA no grafico de 1RM (J.4.4).
 *
 * ## Por que nao mora no [FiltroDeProgresso]
 *
 * O filtro e o recorte — que dado responde. A selecao e o que se DESENHA sobre ele, e qualquer
 * recorte aceita qualquer selecao: todas as combinacoes sao validas. No filtro, o campo teria de
 * existir nas tres variantes. Sao dois eixos, nao um.
 *
 * Mas os dois entram na chave do cache, porque os dois mudam a resposta: o servidor so manda os
 * PONTOS dos escolhidos (mandar os de 27 exercicios seriam dezenas de KB que ninguem desenha).
 *
 * ## Vazia significa "o padrao do servidor"
 *
 * E nao "nenhum". Vazia, o servidor desenha os tres de maior volume — e por isso [alternar]
 * precisa receber quem esta desenhado agora: o primeiro toque tem de partir dos tres visiveis,
 * senao ele levaria o grafico de tres linhas para uma so, do nada.
 */
data class SelecaoDeExercicios(val ids: List<String> = emptyList()) {

    /** Identidade no cache. Vazia nao acrescenta nada a chave: e o recorte sem selecao. */
    val chave: String get() = ids.joinToString(",")

    /** O valor de `?exercicios=`. Nulo = nao mandar o parametro (deixa o servidor escolher). */
    val parametro: String? get() = chave.ifEmpty { null }

    /**
     * Poe ou tira [id], partindo de [visiveis] quando ainda nao ha escolha explicita.
     *
     * Tres regras, e cada uma existe por um motivo:
     *
     * - **Parte dos visiveis.** Sem isso, o primeiro toque trocaria tres linhas por uma.
     * - **Teto de [MAXIMO].** E o limite da escala COMPARTILHADA, nao de tela: leg press a 200 kg
     *   e rosca a 20 kg no mesmo eixo achatam a rosca numa reta, e ela pode ter subido 30%. No
     *   teto, o novo entra e o mais ANTIGO sai — o toque sempre faz alguma coisa.
     * - **Nunca esvazia.** Tirar o ultimo deixaria um cartao de 200dp desenhando nada. O toque
     *   que esvaziaria e ignorado.
     */
    fun alternar(id: String, visiveis: List<String>): SelecaoDeExercicios {
        val base = ids.ifEmpty { visiveis }
        return when {
            id !in base -> SelecaoDeExercicios((base + id).takeLast(MAXIMO))
            base.size > 1 -> SelecaoDeExercicios(base - id)
            else -> this
        }
    }

    companion object {
        /** Tem de bater com o `EXERCICIOS_NO_GRAFICO` do servidor, que tambem corta em tres. */
        const val MAXIMO = 3

        val PADRAO = SelecaoDeExercicios()
    }
}
