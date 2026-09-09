package dev.rafael.contract.i18n

/**
 * Resolve uma tag de idioma qualquer para um [Idioma] que o app sabe renderizar (ARCH #37).
 *
 * ## [INV] A cadeia de fallback existe ANTES da lista de idiomas
 *
 * Com dois idiomas dá para não pensar nisso. Com dez, é a única coisa que importa: aparelho em
 * `fr-CA`, não temos francês, o que aparece?
 *
 * ```
 * exato        pt-BR  -> PT_BR
 * só a língua  pt-PT  -> PT_BR    (ignora a região)
 *              en-US  -> EN
 * piso         fr-CA  -> PT_BR
 * ```
 *
 * > **Idioma que não existe precisa cair em algum lugar decidido, ou cai onde der.**
 *
 * A regra é escrita e testada agora, com dois idiomas, justamente porque adicionar o terceiro só
 * funciona se ela já existir. Um idioma novo é uma constante no enum: esta classe não muda.
 *
 * ## Por que aceita `String?` e nunca falha
 *
 * As três origens de uma tag são a coluna do banco, o corpo de um `PATCH` e a lista de locales do
 * aparelho. Nenhuma delas justifica derrubar a requisição:
 *
 * - a coluna tem `CHECK` fechado, então um valor estranho ali só chega por escrita direta, e nesse
 *   caso mostrar o app em português é melhor que não mostrar o app;
 * - o `PATCH` com tag inválida é recusado ANTES, por [valida], que devolve erro de verdade em vez
 *   de aceitar em silêncio e cair no padrão.
 *
 * A diferença entre [de] e [valida] é essa: **entrada de sistema tolera, entrada de usuário
 * recusa.** Uma função só para os dois casos escolheria errado em um deles.
 */
object IdiomaPolicy {

    /**
     * Resolve uma tag para o idioma mais próximo que existe. Nunca falha.
     *
     * Tolera o que o mundo real manda: `pt_BR` com sublinhado (formato do `java.util.Locale`),
     * caixa trocada (`PT-br`), espaço em volta, e `null`.
     */
    fun de(tag: String?): Idioma {
        val limpa = tag?.trim()?.replace('_', '-').orEmpty()
        if (limpa.isEmpty()) return Idioma.PADRAO

        Idioma.TODOS.firstOrNull { it.tag.equals(limpa, ignoreCase = true) }?.let { return it }

        // Só a língua. `pt-PT` e `pt` procuram qualquer idioma cuja língua seja `pt`, e é isso que
        // faz um português europeu ver o app em vez de cair no piso por causa da região.
        val lingua = limpa.substringBefore('-')
        Idioma.TODOS.firstOrNull { it.lingua.equals(lingua, ignoreCase = true) }?.let { return it }

        return Idioma.PADRAO
    }

    /**
     * A primeira tag da lista que resolve para um idioma SUPORTADO de verdade.
     *
     * O Android entrega as preferências do aparelho em ordem (`fr-CA`, `en-US`, `pt-BR`), e a
     * ordem é a vontade da pessoa. Usar só a primeira e cair no padrão jogaria fora a segunda
     * escolha dela, que neste exemplo é um idioma que o app TEM.
     *
     * Devolve [Idioma.PADRAO] se nenhuma tag da lista casar.
     */
    fun preferido(tags: List<String>): Idioma {
        tags.forEach { tag ->
            val limpa = tag.trim().replace('_', '-')
            if (limpa.isEmpty()) return@forEach
            val exato = Idioma.TODOS.firstOrNull { it.tag.equals(limpa, ignoreCase = true) }
            if (exato != null) return exato
            val lingua = limpa.substringBefore('-')
            val porLingua = Idioma.TODOS.firstOrNull { it.lingua.equals(lingua, ignoreCase = true) }
            if (porLingua != null) return porLingua
        }
        return Idioma.PADRAO
    }

    /**
     * Para entrada de USUÁRIO: `null` quando a tag não é um idioma que o app tem.
     *
     * Usado pelo `PATCH /me`. Aqui o silêncio seria pior que o erro: alguém que pede `es` e recebe
     * `200 OK` acredita que escolheu espanhol, e vai atribuir a falha de tradução ao app em vez de
     * saber que o idioma não existe.
     *
     * **Não faz fallback de língua de propósito.** `pt-PT` cai em `pt-BR` quando o aparelho pede,
     * porque ali a alternativa é não mostrar nada. Quando a PESSOA escolhe numa lista, ela só pode
     * escolher o que está na lista, e uma tag fora dela é erro de cliente, não preferência.
     */
    fun valida(tag: String?): Idioma? {
        val limpa = tag?.trim()?.replace('_', '-') ?: return null
        return Idioma.TODOS.firstOrNull { it.tag.equals(limpa, ignoreCase = true) }
    }
}
