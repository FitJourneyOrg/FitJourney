package dev.rafael.server.features.notificacao.db

import dev.rafael.core.result.AppResult
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

interface DeviceTokenRepository {

    /**
     * Registra o aparelho, ou **troca o dono** se o token já existe.
     *
     * A troca de dono é caso NORMAL, não conflito: o FCM devolve o mesmo token para a mesma
     * instalação, e o aparelho pode receber login de outra conta — alguém empresta o celular, ou
     * o dono tem conta de teste e conta real.
     *
     * `ON CONFLICT (token) DO UPDATE`. Falhar aqui deixaria o segundo usuário sem push nenhum.
     */
    suspend fun registrar(token: String, userId: Uuid, quando: LocalDateTime): AppResult<Unit>

    /** Todos os aparelhos do usuário. É a única leitura desta tabela. */
    suspend fun doUsuario(userId: Uuid): AppResult<List<String>>

    /**
     * Apaga tokens específicos — usado em DOIS momentos, e os dois importam:
     *
     * 1. **No logout**, com o token deste aparelho. Sem isso, quem saiu continua recebendo
     *    notificação até a próxima pessoa fazer login, e nesse intervalo o push chega para quem
     *    não deveria ver. É o item mais fácil de esquecer da F.1 e o de pior consequência.
     * 2. **Na resposta do FCM**, quando ele diz que o token morreu (app desinstalado). Sem isso a
     *    tabela cresce para sempre com lixo.
     */
    suspend fun apagar(tokens: List<String>): AppResult<Unit>
}
