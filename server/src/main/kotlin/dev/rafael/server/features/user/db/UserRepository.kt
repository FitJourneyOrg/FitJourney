package dev.rafael.server.features.user.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.user.models.User
import kotlin.uuid.Uuid

/** Acesso a dados de usuário. Interface no server (não há domain/data separados no backend). */
interface UserRepository {
    suspend fun findByFirebaseUid(firebaseUid: String): AppResult<User?>

    /**
     * Por id interno — o caminho de quem chega a uma PESSOA, não à própria conta (C.1, #34).
     *
     * Devolve o [User] inteiro, `email` e `firebaseUid` inclusive, como todo o resto deste
     * repositório. A fronteira do que é público **não é aqui**, é o `PublicProfileDto`: campo que
     * não existe no DTO não vaza por descuido, e isolamento que depende de alguém lembrar de
     * omitir já falhou neste projeto antes.
     */
    suspend fun findById(userId: Uuid): AppResult<User?>

    /**
     * O `id` e o `displayName` vêm de FORA (V35, #33). O repositório não inventa nem um nem
     * outro: id é decisão de domínio — a mesma escolha do outbox no cliente (#30), onde quem
     * gera é quem manda — e o nome é `DisplayNamePolicy`, que precisa do id para o fallback.
     */
    suspend fun create(
        id: Uuid,
        firebaseUid: String,
        email: String?,
        displayName: String,
        code: String,
    ): AppResult<User>

    /**
     * Por CÓDIGO (V40, #35). `null` = ninguém tem este código.
     *
     * O código já chega normalizado por `UserCodePolicy.normalizar` — este método não conserta
     * caixa nem espaço, porque consertar aqui deixaria a comparação depender de o chamador ter
     * lembrado, e um chamador novo esqueceria.
     */
    suspend fun findByCode(code: String): AppResult<User?>

    /**
     * Troca o código (35.5). `null` = usuário não existe.
     *
     * O código anterior morre na hora e não é guardado: essa é a defesa que devolve controle a
     * quem está sendo importunado. Manter histórico de códigos antigos anularia o efeito.
     */
    suspend fun updateCode(userId: Uuid, code: String): AppResult<User?>

    /** Liga/desliga o premium do usuário. Retorna o user atualizado, ou null se não existe. */
    suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?>

    /** Renomeia. O nome já vem validado por `DisplayNamePolicy`. Null = usuário não existe. */
    suspend fun updateDisplayName(userId: Uuid, displayName: String): AppResult<User?>
}