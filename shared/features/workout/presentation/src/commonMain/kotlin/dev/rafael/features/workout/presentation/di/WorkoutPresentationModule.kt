package dev.rafael.features.workout.presentation.di

import dev.rafael.features.workout.presentation.viewmodel.WorkoutDetailViewModel
import dev.rafael.features.workout.presentation.viewmodel.WorkoutFormViewModel
import dev.rafael.features.workout.presentation.viewmodel.WorkoutListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val workoutPresentationModule: Module = module {
    viewModelOf(::WorkoutListViewModel)
    viewModel { (workoutId: String) -> WorkoutDetailViewModel(workoutId, get(), get()) }
    viewModel { (workoutId: String?) -> WorkoutFormViewModel(workoutId, get(), get()) }
}