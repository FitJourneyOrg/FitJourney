// :shared:features:achievements:presentation — MVI das conquistas. Cliente.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.achievements.presentation"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.achievements.domain)
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)          // AchievementDto (State)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
