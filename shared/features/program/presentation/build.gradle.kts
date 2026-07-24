plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.program.presentation"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.program.domain)
            implementation(projects.shared.core.result)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
    }
}
