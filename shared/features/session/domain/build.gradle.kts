// :shared:features:session:domain — domínio da feature session. Kotlin puro.
plugins {
    id("fitjourney.kmp-library")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)   // WorkoutSessionDto
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
