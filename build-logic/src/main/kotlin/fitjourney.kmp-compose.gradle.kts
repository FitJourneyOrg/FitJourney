plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        compileSdk = 36
        minSdk = 24
        withHostTestBuilder {}
    }

    // SEM iosX64 (simulador de Mac Intel): o Compose Multiplatform não publica mais artefato
    // para esse alvo, então declará-lo quebra a resolução de dependências deste módulo.
    // iosArm64 (device) + iosSimulatorArm64 (simulador Apple Silicon) cobrem o iOS atual.
    iosArm64()
    iosSimulatorArm64()
}