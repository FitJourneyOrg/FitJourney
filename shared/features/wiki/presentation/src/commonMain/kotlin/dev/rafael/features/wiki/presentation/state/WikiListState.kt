package dev.rafael.features.wiki.presentation.state

import dev.rafael.contract.wiki.WikiCategory
import dev.rafael.core.result.AppError
import dev.rafael.features.wiki.domain.model.WikiArticle

/**
 * ## Os chips são DERIVADOS do acervo, não do enum ([REGRA] desta fatia)
 *
 * [categorias] lista só as que têm artigo agora. Categoria sem conteúdo não vira chip que leva a
 * uma tela vazia — ela nasce sozinha quando o primeiro artigo dela for escrito. É a mesma regra do
 * *estado derivado* que o resto do app segue, aplicada à navegação.
 *
 * ## [destaque] só aparece sem filtro
 *
 * Com uma categoria selecionada, o "COMECE AQUI" vira um card comum na lista (se ele for daquela
 * categoria) ou some. Um hero que ignora o filtro que a pessoa acabou de tocar parece defeito.
 */
data class WikiListState(
    val destaque: WikiArticle? = null,
    val artigos: List<WikiArticle> = emptyList(),
    val categorias: List<WikiCategory> = emptyList(),
    val categoriaSelecionada: WikiCategory? = null,
    /**
     * Ainda não chegou a PRIMEIRA emissão do banco. Distingue "estou carregando" de "o acervo está
     * vazio" — sem isso, a tela mostraria "nenhum artigo" no meio segundo antes do banco responder.
     */
    val carregando: Boolean = true,
    val isRefreshing: Boolean = false,
    /**
     * Falha do último sync. NÃO limpa [artigos]: offline-first ([REGRA] ARCH #30) — o que já está
     * no aparelho continua na tela, e o erro é um aviso, não um apagador.
     */
    val error: AppError? = null,
)
