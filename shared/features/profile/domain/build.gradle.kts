// :shared:features:profile:domain — domínio da feature profile. Kotlin puro.
plugins {
    id("fitjourney.kmp-library")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)   // <- enums do quiz (Goal/Level/MuscleGroup)
        }
    }
}