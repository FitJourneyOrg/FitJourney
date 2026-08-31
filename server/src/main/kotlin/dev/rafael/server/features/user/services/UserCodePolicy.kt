package dev.rafael.server.features.user.services

import kotlin.random.Random

/**
 * O código de 8 caracteres pelo qual uma pessoa é encontrada (35.5).
 *
 * ## Por que mora em `user` e não em `friendship`
 *
 * É regra de **IDENTIDADE**, não de amizade: o código endereça a PESSOA, e amizade é só uma das
 * coisas que se pode fazer depois de chegar nela. Se um dia o modelo de amizade mudar de forma,
 * o código continua.
 *
 * E há uma razão mais dura: o `UserService` precisa dele para gerar o código na criação da linha.
 * Com ele em `friendship`, `user` passaria a importar `friendship`, que importa `user` — um
 * CICLO entre features. Cada uma sozinha parecia certa; o ciclo só aparece olhando as duas.
 */
object UserCodePolicy {

    const val TAMANHO = 8

    /**
     * O MESMO alfabeto do código de grupo (`GroupPolicy.ALFABETO`), 32 caracteres sem `O`/`0` e
     * sem `I`/`1`.
     *
     * Duplicado de propósito em vez de importado: `friendship` não pode depender de `group`
     * ([REGRA] feature nunca depende de feature). São dois códigos com vidas diferentes que hoje
     * concordam sobre o alfabeto; amarrá-los faria mudar um mudar o outro sem ninguém pedir.
     */
    const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val FORMATO = Regex("^[$ALFABETO]{$TAMANHO}$")

    fun gerar(random: Random = Random.Default): String =
        (1..TAMANHO).map { ALFABETO[random.nextInt(ALFABETO.length)] }.joinToString("")

    /**
     * Normaliza antes de validar: maiúsculas, sem espaços.
     *
     * Gente digita código com espaço e em minúscula — recusar por isso seria recusar o usuário
     * por causa do teclado dele. `null` = não é um código possível, e nem chega a consultar o
     * banco (é o que faz o limite de tentativas contar só tentativa de verdade).
     */
    fun normalizar(bruto: String): String? =
        bruto.trim().replace(" ", "").uppercase().takeIf { FORMATO.matches(it) }

    /** 10 por hora, por conta (decidido em 2026-08-27). Ver `LimitadorDeResgate`. */
    const val TENTATIVAS_POR_HORA = 10
}
