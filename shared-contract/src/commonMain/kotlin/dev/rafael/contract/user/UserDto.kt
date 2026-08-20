package dev.rafael.contract.user

import kotlinx.serialization.Serializable

/**
 * Usuário exposto pela API (resposta do /me).
 * Omite firebase_uid de propósito: é detalhe interno da auth, o cliente já tem o uid.
 * `id` é o identificador interno (UUID) — é o que features futuras vão referenciar.
 */
@Serializable
data class UserDto(
    val id: String,
    /**
     * Nome de exibição (V35, ARCH #33). SEM default de propósito: a coluna é NOT NULL e o
     * servidor sempre manda. Um default `""` deixaria um erro de serialização virar usuário
     * sem nome na tela, silenciosamente — melhor falhar alto.
     */
    val displayName: String,
    val email: String?,
    val isPremium: Boolean = false,   // <- novo, default false (não quebra clientes antigos)
)

/**
 * Corpo do `PATCH /me`.
 *
 * Rota própria em vez de pendurar o nome no `ProfileDto`: `profiles` é o QUIZ (objetivo, nível,
 * dias por semana) e `display_name` mora em `users`. Um DTO que escrevesse nas duas tabelas
 * vazaria essa fronteira para o contrato — e o nome também é editado fora do onboarding.
 *
 * Um campo só por enquanto. Quando houver mais coisa editável no usuário, cada campo vira
 * anulável com "null = não mexer", que é o que um PATCH significa.
 */
@Serializable
data class UpdateMeRequest(
    val displayName: String,
)
