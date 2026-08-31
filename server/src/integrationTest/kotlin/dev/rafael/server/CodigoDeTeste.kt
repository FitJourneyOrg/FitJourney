package dev.rafael.server

import kotlin.uuid.Uuid

/**
 * Um `users.code` válido para inserção DIRETA na tabela, nos testes de integração.
 *
 * ## Por que isto precisou existir
 *
 * A V40 (#35) deixou `code` **NOT NULL + UNIQUE + CHECK de alfabeto**, e os testes de integração
 * inserem em `UsersTable` sem passar pelo `UserRepositoryImpl` — que é quem gera o código no app
 * real. Resultado: **51 dos 62 testes de integração quebraram no `@BeforeAll`**, e o defeito só
 * apareceu na CI, porque a suíte de integração exige Docker e não roda junto com `:server:test`.
 *
 * > Migration que aperta uma coluna quebra todo INSERT direto que já existia — e os testes de
 * > integração são exatamente isso.
 *
 * ## Derivado do id, nunca aleatório
 *
 * Dois testes que sorteassem o mesmo código colidiriam no `UNIQUE` de vez em quando, e **falha
 * intermitente é a pior de depurar**. Derivando do `Uuid`, o mesmo id dá sempre o mesmo código, e
 * ids diferentes praticamente nunca colidem — são 8 bytes distintos alimentando 8 posições.
 *
 * O alfabeto é o mesmo da V40 e do `GroupPolicy`: sem `O`/`0` e sem `I`/`1`, porque estes códigos
 * são **ditados por voz** e digitados à mão.
 */
object CodigoDeTeste {

    private const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"   // 32 símbolos

    fun de(id: Uuid): String {
        val hex = id.toString().replace("-", "")
        return (0 until 8).joinToString("") { i ->
            val byte = hex.substring(i * 2, i * 2 + 2).toInt(16)
            ALFABETO[byte % ALFABETO.length].toString()
        }
    }
}
