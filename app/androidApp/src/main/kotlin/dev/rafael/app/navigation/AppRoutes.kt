package dev.rafael.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Login : AppRoute
    @Serializable data object Quiz : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute

    // ARCH #26: "Meus treinos" (lista plana) virou "Meus Programas" (programas com
    // treinos aninhados). Workout.* continua existindo, mas Create agora exige programId
    // e só é alcançável a partir de ProgramDetail.
    @Serializable data object Programs : AppRoute
    @Serializable data class ProgramDetail(val id: String) : AppRoute
    @Serializable data object ProgramGenerate : AppRoute

    @Serializable data class WorkoutDetail(val id: String) : AppRoute
    @Serializable data class WorkoutCreate(val programId: String) : AppRoute
    @Serializable data class WorkoutEdit(val id: String) : AppRoute
}