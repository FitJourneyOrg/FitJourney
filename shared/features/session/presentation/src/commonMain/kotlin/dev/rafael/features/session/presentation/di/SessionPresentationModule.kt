package dev.rafael.features.session.presentation.di

import dev.rafael.features.session.presentation.viewmodel.WorkoutSessionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sessionPresentationModule = module {
    viewModel { (workoutId: String) -> WorkoutSessionViewModel(workoutId, get(), get(), get()) }
}
