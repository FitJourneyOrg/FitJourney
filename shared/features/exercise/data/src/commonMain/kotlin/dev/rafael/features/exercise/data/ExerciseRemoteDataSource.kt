package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.network.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * ## O `locale` vai EXPLÍCITO na requisição (fatia H, ARCH #37)
 *
 * O servidor guarda `users.locale` desde a V47 e poderia decidir sozinho. Foi recusado: aquela
 * coluna é cópia **reconciliada na abertura, numa direção só**, então um `PATCH /me` que falhou
 * sem rede a deixa atrasada. O servidor devolveria o catálogo no idioma antigo enquanto o cliente
 * carimbaria o cache com o novo — e a divergência ficaria congelada por 24h de TTL.
 *
 * > **Quem cacheia a resposta tem de ser quem escolhe a pergunta.**
 *
 * Tag desconhecida não é erro: o servidor aplica o `IdiomaPolicy.de` e devolve o piso. Um app
 * antigo pedindo um idioma que o servidor ainda não conhece recebe português, não um 400.
 */
class ExerciseRemoteDataSource(private val client: HttpClient) {
    suspend fun getExercises(category: ExerciseCategory?, idioma: Idioma): List<ExerciseDto> =
        client.get("${HttpClientFactory.BASE_URL}/exercises") {
            category?.let { parameter("category", it.name) }
            parameter("locale", idioma.tag)
        }.body()

    suspend fun getAlternatives(exerciseId: String, idioma: Idioma): List<ExerciseDto> =
        client.get("${HttpClientFactory.BASE_URL}/exercises/$exerciseId/alternatives") {
            parameter("locale", idioma.tag)
        }.body()
}