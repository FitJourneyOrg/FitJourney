package dev.rafael.features.wiki.presentation.leitura

/**
 * Minutos estimados de leitura, a partir do corpo do artigo.
 *
 * **Derivado, nunca persistido** — e a razão não é só o princípio que o `ProgramService` já
 * carrega (*estado que é função de outra coisa é derivado*): aqui o valor muda com o IDIOMA,
 * porque a tradução tem outro tamanho. Uma coluna `minutos` nasceria certa em pt-BR e errada em
 * inglês, e ninguém perceberia.
 *
 * 200 palavras por minuto é a faixa usual de leitura silenciosa em prosa comum. Mínimo de 1: texto
 * curto que mostrasse "0 min de leitura" pareceria defeito, não concisão.
 *
 * A marcação conta como palavra e tudo bem — `**negrito**` vira uma palavra do mesmo jeito, e
 * estimativa que erra por segundos não muda decisão nenhuma de quem lê.
 */
fun minutosDeLeitura(corpo: String, palavrasPorMinuto: Int = 200): Int {
    val palavras = corpo.split(Regex("""\s+""")).count { it.isNotBlank() }
    if (palavras == 0) return 1
    return ((palavras + palavrasPorMinuto - 1) / palavrasPorMinuto).coerceAtLeast(1)
}
