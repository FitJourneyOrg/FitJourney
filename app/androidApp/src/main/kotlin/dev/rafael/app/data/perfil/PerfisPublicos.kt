package dev.rafael.app.data.perfil

import dev.rafael.contract.user.PublicProfileDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/**
 * Perfil público de terceiro (C.1, #34 + emenda 9.3-A).
 *
 * **Online-only, sem cache e fora do SQLDelight** — três motivos, e o terceiro é o que decide:
 *
 * 1. É dado de OUTRA pessoa, que muda por ação dela. TTL aqui é atraso, não economia — a mesma
 *    lição do feed e da lista de grupos.
 * 2. Ninguém abre o perfil da mesma pessoa duas vezes seguidas. Cache resolve releitura, e
 *    releitura é justamente o que não acontece aqui.
 * 3. **Guardar localmente o perfil de terceiros seria montar no aparelho de cada usuário uma
 *    cópia do diretório de gente do app.** Não guardar é a escolha barata; guardar exigiria
 *    decidir por quanto tempo e o que fazer quando a pessoa mudar de nome.
 *
 * O próprio perfil continua vindo do `Me`/`Stats`/`Achievements`, cache-first. São caminhos
 * diferentes de propósito: o dono precisa da tela dele offline, e do perfil dos outros, não.
 */
interface PerfisPublicos {
    suspend fun de(userId: String): AppResult<PublicProfileDto>
}

class PerfisPublicosApi(private val client: HttpClient) : PerfisPublicos {

    private val base = HttpClientFactory.BASE_URL

    override suspend fun de(userId: String): AppResult<PublicProfileDto> =
        httpResult { client.get("$base/users/$userId/profile").body() }
}
