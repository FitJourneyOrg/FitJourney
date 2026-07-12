package dev.rafael.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Login : AppRoute
    @Serializable data object Quiz : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute
    @Serializable data object Workout : AppRoute

    @Serializable data class WorkoutDetail(val id: String) : AppRoute
    @Serializable data object WorkoutCreate : AppRoute
    @Serializable data class WorkoutEdit(val id: String) : AppRoute
    @Serializable data object WorkoutGenerate : AppRoute
}