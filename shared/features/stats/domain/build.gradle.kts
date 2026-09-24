// :shared:features:stats:domain — domínio da feature stats. Kotlin puro.
plugins {
    id("fitjourney.kmp-library")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)   // UserStatsDto
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
