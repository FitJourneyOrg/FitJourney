package dev.rafael.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Login : AppRoute
    @Serializable data object Quiz : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute
    @Serializable data class ExerciseDetail(val id: String) : AppRoute

    // ARCH #27: "Meus treinos" (lista plana) virou "Meus Programas" (programas com
    // treinos aninhados). Workout.* continua existindo, mas Create agora exige programId
    // e só é alcançável a partir de ProgramDetail.
    @Serializable data object Programs : AppRoute
    @Serializable data class ProgramDetail(val id: String) : AppRoute
    @Serializable data object ProgramGenerate : AppRoute
    @Serializable data object ProgramReveal : AppRoute   // revelação do onboarding (Fase 7 — conversão)
    @Serializable data object Paywall : AppRoute          // página de assinatura (Free vs Premium)

    // editLocked = true quando o treino pertence a um programa IA trancado p/ o usuário
    // (free): o botão editar barra na hora com paywall, sem entrar na tela de edição.
    @Serializable data class WorkoutDetail(val id: String, val editLocked: Boolean = false) : AppRoute
    // takenDays = CSV dos dias já ocupados no programa (ex.: "1,3,5") — o form desabilita esses.
    @Serializable data class WorkoutCreate(val programId: String, val takenDays: String = "") : AppRoute
    @Serializable data class WorkoutEdit(val id: String) : AppRoute
    @Serializable data class WorkoutSession(val id: String) : AppRoute   // execução do treino (Fase 5)
}