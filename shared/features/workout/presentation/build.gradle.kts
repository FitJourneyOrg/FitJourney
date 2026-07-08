plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.workout.presentation"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.workout.domain)
            implementation(projects.shared.core.result)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
    }
}