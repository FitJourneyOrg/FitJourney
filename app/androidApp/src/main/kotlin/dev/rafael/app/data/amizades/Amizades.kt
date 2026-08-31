package dev.rafael.app.data.amizades

import dev.rafael.contract.friendship.FriendRequestDto
import dev.rafael.contract.friendship.PersonDto
import dev.rafael.contract.user.PublicProfileDto
import dev.rafael.contract.user.UserDto
import dev.rafael.core.network.HttpClientFactory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post

/**
 * O grafo social como as TELAS o enxergam (ARCH #35).
 *
 * **Online-only, sem SQLDelight**, pelas mesmas razões do perfil público (C.1) e uma a mais:
 *
 * 1. muda por ação de OUTRAS pessoas — TTL aqui é atraso, não economia;
 * 2. **e o cache mentiria sobre algo que a pessoa vai AGIR em cima.** Uma lista de pedidos velha
 *    faz alguém tocar "Aceitar" num pedido que já foi cancelado. No feed, dado velho é só velho;
 *    aqui, dado velho vira ação errada.
 *
 * Não há escrita otimista (#30) pelo mesmo motivo: aceitar um pedido que o servidor vai recusar
 * — porque o outro cancelou, ou porque o teto de 500 estourou — mostraria "vocês são amigos" e
 * desfaria sozinho um segundo depois.
 */
interface Amizades {

    suspend fun amigos(): AppResult<List<PersonDto>>

    /** Só os pedidos que EU recebi. O contador do menu é o `size` desta lista, não uma rota. */
    suspend fun pedidosRecebidos(): AppResult<List<FriendRequestDto>>

    suspend fun pedir(userId: String): AppResult<Unit>
    suspend fun aceitar(userId: String): AppResult<Unit>
    suspend fun recusar(userId: String): AppResult<Unit>

    /** Cancela o pedido que eu mandei OU desfaz a amizade — quem decide é o estado, no servidor. */
    suspend fun remover(userId: String): AppResult<Unit>

    suspend fun bloquear(userId: String): AppResult<Unit>
    suspend fun desbloquear(userId: String): AppResult<Unit>
    suspend fun bloqueados(): AppResult<List<PersonDto>>

    /**
     * Busca pelo código de 8 caracteres.
     *
     * Devolve o **PERFIL** ([REGRA] #35), não um pedido enviado: sem isso, um erro de digitação
     * viraria pedido de amizade a um desconhecido.
     */
    suspend fun porCodigo(codigo: String): AppResult<PublicProfileDto>

    /** Gera um código novo. O anterior morre na hora (35.5). Devolve o `/me` atualizado. */
    suspend fun regenerarMeuCodigo(): AppResult<UserDto>
}

/** Casca fina sobre a API. Sem cache, e isso é decisão — ver o KDoc da interface. */
class AmizadesApi(private val client: HttpClient) : Amizades {

    private val base = HttpClientFactory.BASE_URL

    override suspend fun amigos(): AppResult<List<PersonDto>> =
        httpResult { client.get("$base/friends").body() }

    override suspend fun pedidosRecebidos(): AppResult<List<FriendRequestDto>> =
        httpResult { client.get("$base/friends/requests").body() }

    override suspend fun pedir(userId: String): AppResult<Unit> =
        httpResult { client.post("$base/friends/$userId").body() }

    override suspend fun aceitar(userId: String): AppResult<Unit> =
        httpResult { client.post("$base/friends/$userId/accept").body() }

    override suspend fun recusar(userId: String): AppResult<Unit> =
        httpResult { client.post("$base/friends/$userId/decline").body() }

    override suspend fun remover(userId: String): AppResult<Unit> =
        httpResult { client.delete("$base/friends/$userId").body() }

    override suspend fun bloquear(userId: String): AppResult<Unit> =
        httpResult { client.post("$base/blocks/$userId").body() }

    override suspend fun desbloquear(userId: String): AppResult<Unit> =
        httpResult { client.delete("$base/blocks/$userId").body() }

    override suspend fun bloqueados(): AppResult<List<PersonDto>> =
        httpResult { client.get("$base/blocks").body() }

    override suspend fun porCodigo(codigo: String): AppResult<PublicProfileDto> =
        // O código vai como veio do campo: quem normaliza é o SERVIDOR (`UserCodePolicy`).
        // Normalizar aqui criaria uma segunda definição do que é um código válido, e as duas
        // divergiriam no dia em que o alfabeto mudasse.
        httpResult { client.get("$base/users/by-code/$codigo").body() }

    override suspend fun regenerarMeuCodigo(): AppResult<UserDto> =
        httpResult { client.post("$base/me/code/regenerate").body() }
}
