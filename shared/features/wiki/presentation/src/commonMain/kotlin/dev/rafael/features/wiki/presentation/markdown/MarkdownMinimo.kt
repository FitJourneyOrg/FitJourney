package dev.rafael.features.wiki.presentation.markdown

/** Um pedaço de linha, já sabendo se vai em negrito. A tela só desenha; quem decide é aqui. */
data class Trecho(val texto: String, val negrito: Boolean)

/**
 * O que a tela do artigo sabe desenhar. Fechado de propósito: `when` exaustivo na UI, e bloco novo
 * quebra o BUILD em vez de sumir da tela — mesmo mecanismo que o `TextosDeAviso` usa para idioma.
 */
sealed interface Bloco {
    data class Paragrafo(val trechos: List<Trecho>) : Bloco
    data class Subtitulo(val texto: String) : Bloco
    data class Item(val trechos: List<Trecho>) : Bloco
}

/**
 * Markdown MÍNIMO: parágrafo, **negrito**, `##` e lista com `-`. Nada além disso.
 *
 * ## Por que não é um parser de markdown de verdade
 *
 * Porque o corpo não é markdown de verdade: é prosa escrita pelo Rafael com quatro marcações, e o
 * gerador de conteúdo (`conteudo/gen_wiki.py`) já recusa o que fugir disso. Uma biblioteca traria
 * tabela, link, imagem, HTML embutido e citação — coisas que a tela não desenha e que, sem parser,
 * simplesmente não existem. O precedente é a fatia H: o catálogo tinha `descricao` em HTML que
 * ninguém renderizava, e ela foi DELETADA como peso morto.
 *
 * > **Formato que a tela não desenha não é recurso: é dado morto esperando um bug.**
 *
 * ## Função pura, pelo mesmo motivo do `paragrafosDaDescricao`
 *
 * Sem `Context`, sem Compose, sem idioma: entra texto, sai estrutura. É testável em milissegundos,
 * e é onde mora a única lógica de verdade da tela de leitura.
 *
 * ## O que acontece com marcação torta
 *
 * `**` sem par fecha nada e vira **texto literal**, não exceção. Conteúdo com um asterisco solto
 * tem de aparecer levemente errado, nunca derrubar o artigo inteiro — a mesma escolha do
 * `toDomainOrNull` no mapper, onde perder um artigo é melhor que perder a lista.
 */
fun blocosDoArtigo(corpo: String): List<Bloco> =
    corpo.split("\n\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .flatMap { bloco -> blocosDe(bloco) }

private fun blocosDe(bloco: String): List<Bloco> {
    val linhas = bloco.lines().map { it.trim() }.filter { it.isNotBlank() }

    // Lista: o bloco inteiro é de itens quando a PRIMEIRA linha abre com "- ". Linha solta que
    // começa com hífen no meio de um parágrafo continua sendo parágrafo.
    if (linhas.firstOrNull()?.startsWith("- ") == true) {
        return linhas.filter { it.startsWith("- ") }
            .map { Bloco.Item(trechos(it.removePrefix("- "))) }
    }

    if (linhas.size == 1 && linhas[0].startsWith("## ")) {
        return listOf(Bloco.Subtitulo(linhas[0].removePrefix("## ").trim()))
    }

    // Junta as linhas com espaço ANTES de procurar negrito: os .md são quebrados em ~100 colunas
    // para o diff do git ficar legível, e a quebra é do arquivo, não do texto. Sem juntar, um
    // `**negrito que atravessa a quebra**` sairia com os asteriscos literais -- exatamente o
    // defeito que o `genlib.py` dos PDFs registra ter cometido em 2026-08-19.
    return listOf(Bloco.Paragrafo(trechos(linhas.joinToString(" "))))
}

private val negrito = Regex("""\*\*(.+?)\*\*""")

/** Divide a linha em trechos normais e em negrito, preservando a ordem. */
internal fun trechos(linha: String): List<Trecho> {
    val saida = mutableListOf<Trecho>()
    var cursor = 0
    for (m in negrito.findAll(linha)) {
        if (m.range.first > cursor) {
            saida += Trecho(linha.substring(cursor, m.range.first), negrito = false)
        }
        saida += Trecho(m.groupValues[1], negrito = true)
        cursor = m.range.last + 1
    }
    if (cursor < linha.length) saida += Trecho(linha.substring(cursor), negrito = false)
    // Linha vazia não existe aqui (o chamador filtra), mas um texto que seja SÓ "**" devolveria
    // lista vazia -- e bloco sem trecho nenhum some da tela sem explicação.
    return saida.ifEmpty { listOf(Trecho(linha, negrito = false)) }
}
