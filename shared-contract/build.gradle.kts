// shared-contract: DTOs + ApiRoutes — fonte única da API.
// Kotlin puro consumido por :server (jvm) e cliente. SEM android target,
// SEM framework iOS (isso é papel do :app:iosApp na Fase 11), SEM teste instrumentado.
plugins {
    id("fitjourney.kmp-library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                // Sem mais nada: contrato não depende de ninguém.
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}