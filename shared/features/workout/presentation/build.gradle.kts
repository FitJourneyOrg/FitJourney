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
            implementation(projects.sharedContract)   // ErrorCodes (403 de entitlement → paywall)
            implementation(projects.shared.core.catalog)
            implementation(projects.shared.core.result)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)   // runTest + Dispatchers.setMain
        }
    }
}