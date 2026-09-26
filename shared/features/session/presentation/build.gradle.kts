// :shared:features:session:presentation — MVI da execução de treino. Cliente.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.session.presentation"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.session.domain)
            implementation(projects.shared.features.workout.domain)   // WorkoutRepository
            implementation(projects.shared.core.result)
            implementation(projects.shared.core.catalog)              // ExerciseLookup
            implementation(projects.sharedContract)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
