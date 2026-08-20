package dev.rafael.server.features.user.services

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlin.uuid.Uuid

/**
 * Regras do `display_name` (ARCH #33, fatia A.0). Kotlin PURO, sem I/O — como `XpPolicy` e
 * `AchievementPolicy`.
 *
 * Duas responsabilidades, e as duas existem por um motivo específico:
 *
 * [inicial] — o nome que o usuário JÁ TEM quando aparece pela primeira vez. O usuário nasce no
 * `GET /me` do splash, **antes** do onboarding: se o nome só fosse preenchido no quiz, haveria
 * uma janela em que a linha existe sem nome, e a coluna é NOT NULL. Então o nome nasce com a
 * linha, e o onboarding **confirma ou edita** em vez de preencher do zero. É o que torna
 * "todo usuário tem nome" uma invariante de verdade, e não uma promessa do fim do quiz.
 *
 * [normalizar] — a validação de quando o usuário escolhe o nome. Roda no SERVIDOR porque
 * [REGRA] a autoridade é do backend; a UI pode antecipar, mas quem decide é aqui.
 *
 * ATENÇÃO: [inicial] espelha o backfill de `V35__add_display_name_to_users.sql`. Se uma mudar,
 * a outra muda junto — senão quem foi migrado ganha nome diferente de quem entrou depois.
 */
object DisplayNamePolicy {
    const val MIN = 2
    const val MAX = 30

    /**
     * Nome inicial, derivado do que já se sabe sobre a pessoa.
     *
     * `email` é NULLABLE em `users` desde a V1 (login por provedor pode não devolver e-mail), e
     * a parte local pode ser curta demais para virar nome ("a@x.com"). Nos dois casos cai no
     * fallback pelo id — feio, mas único, estável e sempre válido. O usuário edita depois.
     */
    fun inicial(email: String?, id: Uuid): String {
        val local = email?.substringBefore('@')?.trim().orEmpty()
        return if (local.length >= MIN) local.take(MAX)
        else "Atleta-" + id.toString().replace("-", "").take(6)
    }

    /**
     * Valida e normaliza o nome escolhido pelo usuário.
     *
     * Colapsa espaços internos antes de medir: "  Rafael   Souza " tem 20 caracteres crus e 13
     * reais. Medir o cru recusaria nome legítimo por causa de espaço que a gente mesmo ia
     * descartar. O colapso também neutraliza `\n` e `\t` colados de outro app, que na tela
     * quebrariam o layout da linha do ranking.
     */
    fun normalizar(bruto: String): AppResult<String> {
        val nome = bruto.trim().replace(ESPACOS, " ")
        return when {
            nome.length < MIN -> erro("Use pelo menos $MIN caracteres.")
            nome.length > MAX -> erro("Use no máximo $MAX caracteres.")
            else -> nome.asSuccess()
        }
    }

    private val ESPACOS = Regex("\\s+")

    private fun erro(msg: String): AppResult<String> = AppError.Validation(
        message = msg,
        fieldErrors = mapOf("displayName" to msg),
    ).asFailure()
}
