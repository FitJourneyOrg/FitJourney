// :shared:features:auth:domain — domínio da feature auth. Kotlin puro (sem Compose/Ktor/SQLDelight).
plugins {
    id("fitjourney.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
        }
    }
}