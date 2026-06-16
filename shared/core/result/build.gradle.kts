// shared:core:result — AppResult / AppError. Kotlin puro, zero dependências.
plugins {
    id("fitjourney.kmp-library")
}

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}