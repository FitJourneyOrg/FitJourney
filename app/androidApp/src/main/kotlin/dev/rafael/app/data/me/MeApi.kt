package dev.rafael.app.data.me

import dev.rafael.contract.user.UserDto
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import dev.rafael.features.auth.data.MeDataSource

/**
 * `/me` sobre `AppResult`. Não repete o Ktor: o [MeDataSource] já existe em `auth:data` e é o
 * dono das rotas de `/me` — aqui só se traduz exceção em erro de domínio, como o `StatsApi`.
 */
class MeApi(private val ds: MeDataSource) {

    suspend fun get(): AppResult<UserDto> = httpResult { ds.getMe() }

    suspend fun renomear(nome: String): AppResult<UserDto> =
        httpResult { ds.updateDisplayName(nome) }
}
