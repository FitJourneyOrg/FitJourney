package dev.rafael.app.screens.splash

import dev.rafael.app.navigation.AppRoute

sealed interface SplashState {
    data object Loading : SplashState
    data class Decided(val destination: AppRoute) : SplashState
}