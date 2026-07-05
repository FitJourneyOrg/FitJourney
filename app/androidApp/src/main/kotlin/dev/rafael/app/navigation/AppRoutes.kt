package dev.rafael.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Login : AppRoute
    @Serializable data object Quiz : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute
}