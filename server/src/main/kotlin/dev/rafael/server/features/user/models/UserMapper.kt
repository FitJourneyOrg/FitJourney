package dev.rafael.server.features.user.models

import dev.rafael.contract.user.UserDto

/** User (server) -> UserDto (fio). Aqui o firebaseUid é deixado de fora. */
fun User.toDto(): UserDto = UserDto(
    id = id.toString(),
    email = email,
)