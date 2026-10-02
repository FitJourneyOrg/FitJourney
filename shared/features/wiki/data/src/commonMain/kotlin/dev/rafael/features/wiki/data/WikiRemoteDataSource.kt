package dev.rafael.features.wiki.data

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.wiki.WikiArticleDto
import dev.rafael.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * ## Interface, diferente do `ExerciseRemoteDataSource` — e a diferença é o que torna o
 * offline-first testável
 *
 * O módulo de exercício tem data sources CONCRETOS, e o KDoc dele registra o preço: *"não dá pra
 * fake nela sem refactor"*, então a regra de ir à rede teve de ser extraída como função pura para
 * ser testada, e o comportamento do repositório em si ficou sem teste.
 *
 * Aqui o código é novo, então a interface não custa refactor nenhum — e com ela o cenário que mais
 * importa neste recurso (**rede caiu, conteúdo local continua na tela**) vira teste de verdade em
 * vez de promessa de KDoc.
 */
interface WikiRemoteDataSource {
    suspend fun getArticles(idioma: Idioma): List<WikiArticleDto>
}

/**
 * O `locale` vai EXPLÍCITO na requisição, como no catálogo de exercícios: o `users.locale` do
 * servidor é cópia reconciliada numa direção só, e se estiver atrasado o servidor devolveria o
 * idioma antigo enquanto o cliente carimbaria o cache com o novo.
 *
 * > **Quem cacheia a resposta tem de ser quem escolhe a pergunta.**
 */
class WikiRemoteDataSourceKtor(private val client: HttpClient) : WikiRemoteDataSource {
    override suspend fun getArticles(idioma: Idioma): List<WikiArticleDto> =
        client.get("${HttpClientFactory.BASE_URL}/wiki") {
            parameter("locale", idioma.tag)
        }.body()
}
