// :shared:features:achievements:domain — domínio da feature achievements. Kotlin puro.
plugins {
    id("fitjourney.kmp-library")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)   // AchievementDto
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
