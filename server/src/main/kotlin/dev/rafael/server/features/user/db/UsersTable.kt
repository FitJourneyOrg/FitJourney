package dev.rafael.server.features.user.db

import dev.rafael.contract.i18n.Idioma
import org.jetbrains.exposed.v1.core.Table

/** Espelha V1__create_users.sql (+ V35, V40, V47). firebase_uid = cola com a auth (índice único); PK é UUID interno. */
object UsersTable : Table("users") {
    val id = uuid("id")
    val firebaseUid = varchar("firebase_uid", 128).uniqueIndex()
    val email = varchar("email", 320).nullable()
    val isPremium = bool("is_premium").default(false)
    /**
     * V35. NOT NULL e sem `.default()` de propósito: quem insere é obrigado a decidir o nome,
     * e quem decide é `DisplayNamePolicy`. Um default aqui deixaria passar insert sem nome.
     */
    val displayName = varchar("display_name", 30)

    /**
     * V40 (#35). CHAR(8) único — o endereço pelo qual outra pessoa te encontra.
     *
     * Sem `.default()` pela mesma razão do `displayName`: quem insere decide, e quem decide é
     * `UserCodePolicy.gerar()`. Um default aqui deixaria passar insert sem código, e o código é
     * NOT NULL no banco.
     */
    val code = varchar("code", 8)

    /**
     * V47 (ARCH #37). A tag do idioma, como `pt-BR`.
     *
     * **Com `.default()`, ao contrário do nome e do código.** Aqueles não têm default porque quem
     * insere precisa DECIDIR o valor, e a decisão mora numa policy. Aqui o valor de quem não
     * escolheu é uma constante, e é exatamente o que o `DEFAULT 'pt-BR'` da coluna diz.
     *
     * É `String` e não o enum `Idioma` porque a coluna é `String`. A conversão passa pelo
     * `IdiomaPolicy` no mapeamento, num lugar só, e é ela que garante que um valor inesperado no
     * banco vira português em vez de derrubar a requisição.
     */
    val locale = varchar("locale", 5).default(Idioma.PADRAO.tag)

    override val primaryKey = PrimaryKey(id)
}