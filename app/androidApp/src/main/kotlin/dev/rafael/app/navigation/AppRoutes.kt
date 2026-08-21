package dev.rafael.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Login : AppRoute
    @Serializable data object Quiz : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute
    @Serializable data class ExerciseDetail(val id: String) : AppRoute

    @Serializable data object Grupos : AppRoute
    @Serializable data object GrupoNovo : AppRoute

    /** Entrar num desafio. `inviteToken` != null = chegou por link; null = vai digitar o código. */
    @Serializable data class GrupoEntrar(val inviteToken: String? = null) : AppRoute

    // Aba ainda sem implementação (placeholder) — Progresso é Fase 5 (#16).
    @Serializable data object Progresso : AppRoute

    /** Conquistas (ARCH #16). Tela própria, alcançada pelo Progresso — não é aba. */
    @Serializable data object Conquistas : AppRoute

    /**
     * Perfil PÚBLICO (ARCH #34). `userId = null` significa "o meu".
     *
     * O parâmetro já nasce aqui, mesmo que na fatia A.0 só o próprio perfil seja alcançável:
     * é ele que garante que a tela seja escrita para renderizar QUALQUER pessoa desde o
     * primeiro dia. Uma tela nascida "só minha" acumula suposições que depois viram vazamento
     * quando alguém a aponta para outro usuário.
     */
    @Serializable data class Perfil(val userId: String? = null) : AppRoute

    /** Configurações da conta (ARCH #34): privada, nunca renderiza outra pessoa. */
    @Serializable data object Conta : AppRoute

    /** Itens do menu que ainda não existem — abrem EmBreve com o selo da fase. */
    @Serializable data object Wiki : AppRoute
    @Serializable data object Duvidas : AppRoute

    // ARCH #27: "Meus treinos" (lista plana) virou "Meus Programas" (programas com
    // treinos aninhados). Workout.* continua existindo, mas Create agora exige programId
    // e só é alcançável a partir de ProgramDetail.
    @Serializable data object Programs : AppRoute
    @Serializable data class ProgramDetail(val id: String) : AppRoute
    @Serializable data object ProgramGenerate : AppRoute
    /**
     * Fim do onboarding: pergunta se o usuário QUER o primeiro programa (Fase 7).
     * Existe pra que nada seja criado no servidor sem alguém pedir — antes o Reveal gerava
     * no `init`, então todo mundo ganhava um programa quisesse ou não.
     */
    @Serializable data object ProgramOffer : AppRoute
    @Serializable data object ProgramReveal : AppRoute   // revelação do onboarding (Fase 7 — conversão)

    /**
     * Página de assinatura. `voltarParaHome` distingue os dois contextos de abertura:
     * - onboarding (vindo do Reveal): recusar tem que levar pra Home. Voltar pro Reveal, que
     *   é a tela que oferece premium, dá a sensação de gaiola — o usuário recusou e caiu de
     *   volta na oferta.
     * - dentro do app (programa trancado): recusar volta pra tela de onde veio, como sempre.
     */
    @Serializable data class Paywall(val voltarParaHome: Boolean = false) : AppRoute

    // editLocked = true quando o treino pertence a um programa IA trancado p/ o usuário
    // (free): o botão editar barra na hora com paywall, sem entrar na tela de edição.
    @Serializable data class WorkoutDetail(val id: String, val editLocked: Boolean = false) : AppRoute
    // takenDays = CSV dos dias já ocupados no programa (ex.: "1,3,5") — o form desabilita esses.
    @Serializable data class WorkoutCreate(val programId: String, val takenDays: String = "") : AppRoute
    @Serializable data class WorkoutEdit(val id: String) : AppRoute
    @Serializable data class WorkoutSession(val id: String) : AppRoute   // execução do treino (Fase 5)
}