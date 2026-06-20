package dev.rafael.server.features.user.models


import kotlin.uuid.Uuid

/** Usuário como o server o conhece. firebaseUid fica aqui (interno), não vaza no DTO. */
data class User(
    val id: Uuid,
    val firebaseUid: String,
    val email: String?,
)