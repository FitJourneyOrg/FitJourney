package dev.rafael.features.exercise.presentation.di

import dev.rafael.features.exercise.presentation.viewmodel.ExerciseDetailViewModel
import dev.rafael.features.exercise.presentation.viewmodel.ExerciseListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val exercisePresentationModule: Module = module {
    viewModelOf(::ExerciseListViewModel)
    viewModel { (exerciseId: String) -> ExerciseDetailViewModel(exerciseId, get()) }
}