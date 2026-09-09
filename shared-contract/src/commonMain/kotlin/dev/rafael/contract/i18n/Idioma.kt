package dev.rafael.contract.i18n

/**
 * Os idiomas que o app sabe renderizar (ARCH #37).
 *
 * ## Enum aqui dentro, `String` no fio
 *
 * O [tag] é o que viaja na API e o que fica em `users.locale`. O enum existe só do lado de dentro,
 * e a conversão passa obrigatoriamente pelo [IdiomaPolicy].
 *
 * A razão está escrita no `ErrorResponse`, sobre o `code`: *"String (não enum) no fio: código novo
 * no server não quebra cliente antigo"*. Se este enum fosse serializado direto, um servidor
 * atualizado mandando `es` faria o app antigo **falhar ao desserializar o `/me` inteiro** e a pessoa
 * perderia a tela por causa de um idioma que nem escolheu.
 *
 * ## Por que fica no `shared-contract` e não no servidor
 *
 * A cadeia de fallback do [IdiomaPolicy] é necessária dos DOIS lados: o servidor resolve o idioma do
 * push, o cliente resolve o idioma do aparelho. Duas implementações da mesma regra divergem na
 * primeira que alguém esquecer de atualizar, e aí o app mostra uma tela em inglês enquanto o push
 * chega em português.
 *
 * Precedente no módulo: o `SplitCatalog`, que também é regra pura compartilhada entre motor e quiz.
 *
 * ## A ordem das constantes importa
 *
 * O [PADRAO] é o piso da cadeia de fallback e o `DEFAULT` da coluna no banco. Trocá-lo é mudar o
 * idioma de todo mundo que nunca escolheu, então não é edição de enum: é decisão de produto.
 */
enum class Idioma(val tag: String) {

    /**
     * Português do Brasil. É o idioma de ORIGEM: todo texto nasce aqui e as traduções derivam dele.
     *
     * Por isso é também o [PADRAO]. Um idioma de fallback que não fosse o de origem teria frases
     * faltando por definição, já que só o original tem garantia de estar completo.
     */
    PT_BR("pt-BR"),

    /** Inglês, sem região. `en-US` e `en-GB` caem aqui pela regra de língua do [IdiomaPolicy]. */
    EN("en"),
    ;

    /** A parte de língua da tag, sem região. `pt-BR` vira `pt`. */
    val lingua: String get() = tag.substringBefore('-')

    companion object {

        /**
         * O piso de toda resolução, e o `DEFAULT` de `users.locale` (V47).
         *
         * **Não é o idioma do aparelho.** Quem já usa o app continua aqui mesmo com o celular em
         * inglês, porque idioma trocado por uma atualização que ninguém pediu parece defeito e não
         * recurso (ARCH #37, seção 4).
         */
        val PADRAO = PT_BR

        /** O vocabulário que o `CHECK` da V47 espelha. Se um sair daqui, a migration precisa saber. */
        val TODOS: List<Idioma> = entries
    }
}
