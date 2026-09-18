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

    /**
     * MEU código de 8 caracteres (V40, #35) — o endereço que eu passo para alguém me adicionar.
     *
     * **Só existe aqui, no `/me`.** Não está no `PublicProfileDto` de propósito: publicá-lo faria
     * de cada perfil visitado uma forma de colecionar códigos, e o código é justamente o que
     * permite mandar pedido a quem não te conhece. O meu é meu para dar; o dos outros não é meu
     * para pegar.
     *
     * Default `""` porque cliente antigo não conhece o campo — e nesse caso a tela de amigos
     * simplesmente não mostra o código, em vez de falhar ao desserializar.
     */
    val code: String = "",

    /**
     * O idioma em que este usuário recebe **notificação** (V47, ARCH #37).
     *
     * ## `String` e não enum, de propósito
     *
     * Mesma razão do `code` do `ErrorResponse`, que já está escrita lá: *"String (não enum) no fio:
     * código novo no server não quebra cliente antigo"*. Se isto fosse `Idioma`, um servidor
     * atualizado mandando `es` faria o app antigo **falhar ao desserializar o `/me` inteiro**, e a
     * pessoa perderia a tela de conta por causa de um idioma que nem escolheu.
     *
     * Quem converte é o `IdiomaPolicy`, que nunca falha e cai em `pt-BR`.
     *
     * ## Não é o idioma da INTERFACE
     *
     * A UI segue a locale ativa do aparelho. Este campo é o que o servidor usa para montar o push,
     * e as duas podem divergir por um instante enquanto o `PATCH` está em voo. É a mesma cópia
     * otimista de sempre.
     *
     * Default `pt-BR` porque cliente antigo não conhece o campo, e nesse caso ele simplesmente não
     * mostra idioma na tela, em vez de falhar ao desserializar.
     */
    val locale: String = "pt-BR",
)

/**
 * Corpo do `PATCH /me`.
 *
 * Rota própria em vez de pendurar o nome no `ProfileDto`: `profiles` é o QUIZ (objetivo, nível,
 * dias por semana) e `display_name` mora em `users`. Um DTO que escrevesse nas duas tabelas
 * vazaria essa fronteira para o contrato, e o nome também é editado fora do onboarding.
 *
 * ## O segundo campo chegou, e o PATCH virou PATCH de verdade (G.1, ARCH #37)
 *
 * A versão anterior tinha um campo só e avisava: *"quando houver mais coisa editável no usuário,
 * cada campo vira anulável com null = não mexer, que é o que um PATCH significa"*. É o que
 * aconteceu.
 *
 * `null` em ambos é requisição válida e não faz nada. Recusá-la como erro obrigaria o cliente a
 * saber que não mudou nada antes de mandar, o que é o tipo de conhecimento que a tela não tem
 * quando salva um formulário inteiro.
 */
@Serializable
data class UpdateMeRequest(
    /**
     * `null` = não mexer no nome.
     *
     * Deixou de ser obrigatório nesta fatia. A tela de Conta que só troca o idioma não deveria ter
     * de reenviar o nome atual, porque reenviar o nome atual é a forma mais comum de sobrescrever
     * sem querer o nome que outra tela acabou de mudar.
     */
    val displayName: String? = null,

    /**
     * `null` = não mexer no idioma. A tag, como `pt-BR` (V47).
     *
     * **Tag não suportada é recusa, não fallback.** Quem pede `es` e recebe `200 OK` acredita que
     * escolheu espanhol, e vai atribuir a falta de tradução a um defeito do app em vez de saber que
     * o idioma não existe.
     *
     * É a diferença entre `IdiomaPolicy.valida`, usada aqui, e `IdiomaPolicy.de`, usada ao ler a
     * coluna: **entrada de usuário recusa, entrada de sistema tolera.**
     */
    val locale: String? = null,
)
