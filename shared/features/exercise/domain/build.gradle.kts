// :shared:features:exercise:domain — domínio da feature exercise. Kotlin puro.
plugins {
    id("fitjourney.kmp-library")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)   // ExerciseCategory (enum é conceito de domínio, ARCH #9)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}