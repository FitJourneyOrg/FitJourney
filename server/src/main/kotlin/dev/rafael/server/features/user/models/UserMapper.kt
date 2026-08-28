package dev.rafael.server.features.user.models

import dev.rafael.contract.user.UserDto

/**
 * User (server) -> UserDto (fio). Aqui o firebaseUid é deixado de fora.
 *
 * Este mapper serve **só ao `/me`**, e é por isso que pode carregar `email` e `code`: os dois são
 * do dono. O perfil de terceiro tem mapper próprio, no `PublicProfileService`, e é lá que a
 * fronteira da 9.3-A é decidida. Reusar este para outra pessoa vazaria os dois campos de uma vez.
 */
fun User.toDto(): UserDto = UserDto(
    id = id.toString(),
    displayName = displayName,
    email = email,
    isPremium = isPremium,
    code = code,
)