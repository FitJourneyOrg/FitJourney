package dev.rafael.contract.wiki

import kotlinx.serialization.Serializable

/**
 * Um artigo do "Aprender" (Fase 8), já resolvido no idioma pedido.
 *
 * ## O que NÃO está aqui, e por quê
 *
 * **Tempo de leitura.** É função do tamanho do [body], então é derivado — e derivado por IDIOMA,
 * porque a tradução tem outro tamanho. Uma coluna `minutos` nasceria certa em pt-BR e errada em
 * inglês. Quem calcula é a tela.
 *
 * > **Estado que é função de outra coisa é DERIVADO, não persistido.**
 *
 * **Imagem.** A v1 não tem foto em card nem hero no artigo: seriam de 10 a 14 imagens a produzir ou
 * licenciar, e isso é o segundo gargalo de conteúdo depois do texto. O card usa cor e ícone da
 * categoria.
 *
 * ## [body] é markdown MÍNIMO, não HTML
 *
 * Parágrafo, **negrito**, `##` e lista — só. HTML foi recusado com precedente: os campos
 * `descricao`/`descricao_en` em HTML do catálogo de exercícios foram deletados como peso morto na
 * fatia H, porque ninguém os lia. Quem renderiza é uma função pura no cliente, testável sem tela.
 *
 * [updatedAt] viaja como `yyyy-MM-dd` (ISO), e **não formatado**: a tela decide como escrever a data
 * no idioma dela. Texto pronto vindo do servidor é exatamente o que a G.5 passou uma fatia inteira
 * desfazendo.
 */
@Serializable
data class WikiArticleDto(
    val id: String,
    /** Estável e imutável: é por ele que o seed casa a linha e que um deep link futuro vai achar o artigo. */
    val slug: String,
    val category: WikiCategory,
    val title: String,
    val body: String,
    /** O "COMECE AQUI". No máximo um em todo o acervo — garantido por índice único parcial na V61. */
    val featured: Boolean = false,
    /** Ordem editorial dentro do acervo. O cliente cacheia e reordena por isto, não pela ordem de chegada. */
    val orderIndex: Int,
    val updatedAt: String,
)
