package dev.rafael.features.wiki.domain.repository

import dev.rafael.core.result.AppResult
import dev.rafael.features.wiki.domain.model.WikiArticle
import kotlinx.coroutines.flow.Flow

interface WikiRepository {

    /**
     * O acervo do banco LOCAL (ARCH #30), já ordenado (destaque primeiro, depois a ordem
     * editorial). Pinta offline e se atualiza sozinho quando o sync grava algo novo.
     *
     * Sem parâmetro de categoria de propósito: são ~12 artigos, o filtro por chip é operação de
     * tela, e uma consulta por toque de chip seria SQL para reordenar o que já está na memória.
     */
    fun observeArticles(): Flow<List<WikiArticle>>

    /**
     * UM artigo, pelo slug. Do banco LOCAL, como a lista — a tela de leitura abre offline e não
     * depende de a lista ainda estar viva na memória (sobrevive a morte de processo).
     *
     * `null` = slug que não existe no acervo local. Pode ser artigo removido num deploy, ou um
     * deep link velho; quem chama decide o que dizer.
     */
    fun observeArticle(slug: String): Flow<WikiArticle?>

    /**
     * Sincroniza o acervo local. Respeita janela de frescor — o conteúdo é SEMIESTÁTICO, entra por
     * migration repetível no servidor, então só muda em deploy (mesmo raciocínio do catálogo de
     * exercícios).
     *
     * `forcar = true` só quando o USUÁRIO pede.
     */
    suspend fun refresh(forcar: Boolean = false): AppResult<Unit>
}
