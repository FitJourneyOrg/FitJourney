package dev.rafael.server.features.user.models


import kotlin.uuid.Uuid

/** Usuário como o server o conhece. firebaseUid fica aqui (interno), não vaza no DTO. */
data class User(
    val id: Uuid,
    val firebaseUid: String,
    val email: String?,
    val isPremium: Boolean,
    /** V35 (#33). Sempre presente — a coluna é NOT NULL e o nome nasce junto com a linha. */
    val displayName: String,
)