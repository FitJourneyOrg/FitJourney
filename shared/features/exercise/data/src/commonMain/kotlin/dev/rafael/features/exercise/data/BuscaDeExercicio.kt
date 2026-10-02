package dev.rafael.features.exercise.data

import dev.rafael.features.exercise.domain.model.Exercise

/**
 * ⭐ **A busca por nome, em memória e sem acento.**
 *
 * ## Por que não é SQL
 *
 * O `LIKE` do SQLite é insensível a maiúscula **só em ASCII**, e **sensível a acento**: `abducao`
 * não acha `Abdução` e `biceps` não acha `Bíceps` — que é exatamente como se digita no celular,
 * com pressa e sem segurar tecla.
 *
 * A saída em SQL seria uma coluna normalizada gravada na inserção, o que custa migração do schema
 * local e deixa as linhas já em cache com ela vazia. Para **923 linhas que a tela já carrega
 * inteiras**, filtrar em memória custa menos e não tem armadilha. Se o acervo crescer a ponto de
 * doer, a coluna continua sendo a saída.
 *
 * ## Por que token, e não `contains` do texto inteiro
 *
 * Com `contains`, digitar `agach bulgaro` não acha nada, porque o nome é
 * *"Agachamento Búlgaro com Salto"* — a pessoa teria de acertar a frase. Com tokens, cada pedaço é
 * procurado por conta própria e **todos** precisam aparecer.
 *
 * > **Quem busca lembra das palavras, não da ordem delas.**
 */

/**
 * Minúscula e sem acento. Tabela escrita à mão porque `java.text.Normalizer` não existe em
 * `commonMain` — e para 27 caracteres não vale `expect`/`actual`.
 *
 * As duas listas têm de ter o MESMO comprimento, e isso é testado: um caractere acrescentado só
 * numa delas faria o `indexOf` apontar para a letra errada, trocando acento por acento.
 */
internal fun normalizarParaBusca(texto: String): String {
    val sb = StringBuilder(texto.length)
    for (c in texto.lowercase()) {
        val i = ACENTUADOS.indexOf(c)
        sb.append(if (i >= 0) SEM_ACENTO[i] else c)
    }
    return sb.toString()
}

internal const val ACENTUADOS = "áàâãäåéèêëíìîïóòôõöúùûüçñýÿ"
internal const val SEM_ACENTO = "aaaaaaeeeeiiiiooooouuuucnyy"

/**
 * Em branco devolve a lista intacta — campo vazio não é filtro, é ausência de filtro. Sem isso,
 * apagar a busca deixaria a tela vazia em vez de voltar ao acervo.
 */
internal fun List<Exercise>.filtradosPorBusca(termo: String): List<Exercise> {
    val pedacos = normalizarParaBusca(termo).split(ESPACOS).filter { it.isNotBlank() }
    if (pedacos.isEmpty()) return this
    return filter { exercicio ->
        val nome = normalizarParaBusca(exercicio.name)
        pedacos.all { nome.contains(it) }
    }
}

private val ESPACOS = Regex("\\s+")
