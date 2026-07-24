package dev.rafael.features.workout.presentation.di

import dev.rafael.features.workout.presentation.viewmodel.WorkoutDetailViewModel
import dev.rafael.features.workout.presentation.viewmodel.WorkoutFormViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// WorkoutListViewModel/WorkoutGenerateViewModel removidos (ARCH #26): "Meus treinos" (lista
// plana) virou "Meus Programas" (dev.rafael.features.program.presentation); geração por IA
// agora é POST /programs/generate (ProgramGenerateViewModel), não POST /workouts/generate
// (endpoint que nem existia no server — dead code desde antes desta mudança).
val workoutPresentationModule: Module = module {
    viewModel { (workoutId: String) -> WorkoutDetailViewModel(workoutId, get(), get()) }
    viewModel { (workoutId: String?, programId: String?) -> WorkoutFormViewModel(workoutId, programId, get(), get()) }
}