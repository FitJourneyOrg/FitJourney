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

    /**
     * V40 (#35). O código de 8 caracteres pelo qual esta pessoa é encontrada.
     *
     * Nasce junto com a linha, como o `displayName` — mesma lição da A.0: coluna que só é
     * preenchida "quando alguém precisar" é coluna nullable, e nullable espalha `?:` por toda
     * tela que a usa até alguém esquecer um.
     *
     * **Nunca vai para o perfil PÚBLICO** — só para o `/me` do dono. Publicá-lo transformaria
     * cada perfil visitado numa forma de colecionar códigos, e o código é o que permite mandar
     * pedido a quem não se conhece.
     */
    val code: String,
)