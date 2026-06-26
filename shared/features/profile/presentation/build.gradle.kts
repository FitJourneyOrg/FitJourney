// :shared:features:profile:presentation — MVI do quiz. Cliente.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.profile.presentation"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.profile.domain)
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)         // <- enums do quiz (se opção A)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
    }
}