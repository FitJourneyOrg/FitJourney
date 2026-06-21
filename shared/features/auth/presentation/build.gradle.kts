// :shared:features:auth:presentation — MVI + telas Compose da auth. Cliente + Compose.
plugins {
    id("fitjourney.kmp-client")
}

kotlin {
    androidLibrary {
        namespace = "dev.rafael.feature.auth.presentation"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.auth.domain)
            implementation(projects.shared.core.result)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}