package dev.rafael.features.program.presentation.di

import dev.rafael.features.program.presentation.viewmodel.ProgramDetailViewModel
import dev.rafael.features.program.presentation.viewmodel.ProgramGenerateViewModel
import dev.rafael.features.program.presentation.viewmodel.ProgramListViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val programPresentationModule: Module = module {
    viewModelOf(::ProgramListViewModel)
    viewModelOf(::ProgramGenerateViewModel)
    viewModel { (programId: String) -> ProgramDetailViewModel(programId, get()) }
}
