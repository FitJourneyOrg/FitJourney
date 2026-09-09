package dev.rafael.server.features.user.models


import dev.rafael.contract.i18n.Idioma
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

    /**
     * V47 (ARCH #37). O idioma em que esta pessoa recebe **notificação**.
     *
     * ## Enum aqui, `String` na coluna e no fio
     *
     * O mapeamento passa pelo `IdiomaPolicy`, que nunca falha: um valor inesperado no banco vira
     * `PADRAO` em vez de derrubar a requisição. O `CHECK` da V47 deveria impedir que isso aconteça,
     * mas quem escreve direto por script passa por fora do `CHECK` de nada e por dentro deste
     * mapeamento.
     *
     * ## Só a notificação depende disto
     *
     * A UI escolhe o idioma pela locale ATIVA do aparelho, não por esta coluna, e as duas podem
     * divergir por um instante: a pessoa troca no app, e o `PATCH` ainda está em voo. Não é
     * problema, é a mesma cópia otimista de sempre. O que não pode divergir por muito tempo é o
     * push, e por isso a sincronização acontece ao entrar na tela, não só ao trocar.
     *
     * Tem `default` porque o `Aviso` precisa de um idioma mesmo para linha antiga, e porque a
     * coluna também tem.
     */
    val idioma: Idioma = Idioma.PADRAO,
)