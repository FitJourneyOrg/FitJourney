package dev.rafael.contract.stats

/**
 * Vocabulário de conquistas da API. O servidor concede, o cliente escreve a frase (G.5, ARCH #37).
 *
 * ## Por que estes ids subiram para o contrato
 *
 * Eles já ERAM contrato: o `AchievementPolicy.Conquista` do servidor grava `name` em
 * `user_achievements` e o manda no `AchievementDto.id`, e o KDoc de lá diz que renomear um id
 * reescreveria a história de quem já o tem. O que faltava era o cliente **poder percorrer a
 * lista**.
 *
 * Sem isso, a cobertura do catálogo de textos não tem como existir: o cliente saberia escrever a
 * frase de `PRIMEIRO_TREINO`, mas não saberia que `STREAK_90` existe e ficou sem frase. É o mesmo
 * buraco que o [dev.rafael.contract.error.ErrorCodes] fechou na G.2, pelo mesmo motivo.
 *
 * > **Id que o cliente precisa traduzir tem de ser um id que o cliente consegue ENUMERAR.**
 *
 * ## Isto INVERTE uma decisão escrita no `AchievementDto`, e de propósito
 *
 * O KDoc de lá dizia: *"título e descrição ficam no servidor, então ajustar um texto ou um limiar
 * não exige publicar uma versão nova do app"*. Era verdade e era uma vantagem real — que só
 * funciona enquanto existe **um** idioma.
 *
 * Com dois, o servidor teria de saber em que idioma a TELA está para escolher a frase, e essa é
 * exatamente a premissa que o #37 recusa. A vantagem não some: ela passa a valer para o **limiar**,
 * que continua no servidor. O que vai embora é poder corrigir uma PALAVRA sem release.
 *
 * > **Texto no servidor economiza um release e custa um idioma.**
 *
 * ## Convenções (as mesmas do `ErrorCodes`, pelas mesmas razões)
 *
 * - **`String`, nunca enum, no fio.** Conquista nova no servidor não pode derrubar a
 *   desserialização num app antigo; o desconhecido é IGNORADO pela tela, que é o comportamento que
 *   o `paraConquista()` do servidor já tinha para o caminho inverso.
 * - **Estável para sempre.** Renomear quebra a medalha de quem já a tem, nos dois lados.
 * - **O alvo NÃO mora aqui.** `target` viaja no DTO porque é regra de gamificação, e regra é do
 *   servidor (ARCH #16). Aqui só o vocabulário.
 */
object ConquistaIds {

    // ---- volume de treinos (Metrica.SESSOES) ----
    const val PRIMEIRO_TREINO = "PRIMEIRO_TREINO"
    const val TREINOS_10 = "TREINOS_10"
    const val TREINOS_50 = "TREINOS_50"
    const val TREINOS_100 = "TREINOS_100"

    // ---- constância (Metrica.STREAK) ----
    const val STREAK_7 = "STREAK_7"
    const val STREAK_30 = "STREAK_30"
    const val STREAK_90 = "STREAK_90"

    // ---- nível do perfil (Metrica.NIVEL) ----
    const val NIVEL_5 = "NIVEL_5"
    const val NIVEL_10 = "NIVEL_10"

    /**
     * Todas, para quem precisa percorrer: o teste de cobertura do catálogo no cliente e o teste do
     * servidor que amarra este conjunto ao `AchievementPolicy.Conquista`.
     *
     * Escrito à mão, e não por reflexão, porque em Kotlin Multiplatform a reflexão de `object` não
     * existe em todos os alvos. O custo é lembrar de acrescentar aqui — e é justamente isso que os
     * dois testes cobram.
     */
    val TODOS: Set<String> = setOf(
        PRIMEIRO_TREINO, TREINOS_10, TREINOS_50, TREINOS_100,
        STREAK_7, STREAK_30, STREAK_90,
        NIVEL_5, NIVEL_10,
    )
}
