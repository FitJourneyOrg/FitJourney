// :shared:features:wiki:domain — domínio do "Aprender" (Fase 8). Kotlin puro.
plugins {
    id("fitjourney.kmp-library")
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)   // WikiCategory (enum é conceito de domínio, ARCH #9)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
