// shared:core:domain — entidades transversais (User, Session). Kotlin puro.
// Pode depender de core:result (AppResult/AppError nas assinaturas). Nada além.
plugins {
    id("fitjourney.kmp-library")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.shared.core.result)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}