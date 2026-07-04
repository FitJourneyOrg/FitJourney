// :shared:features:exercise:presentation — MVI da biblioteca de exercícios. Cliente.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.exercise.presentation"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.exercise.domain)
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)          // ExerciseCategory (State/Event)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
    }
}