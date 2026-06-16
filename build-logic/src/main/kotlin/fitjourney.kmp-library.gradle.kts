plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(17)

    // Consumido pelo :server (JVM) e, futuramente, pelo :app:iosApp.
    jvm()

    // iOS desde a Fase 0: só compila num host macOS, então é de graça no CI Linux,
    // e trava uso acidental de API não-multiplataforma em commonMain.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // androidTarget() entra na Fase 2, quando o :app:androidApp consumir
    // código compartilhado (via androidLibrary {} do plugin novo de KMP).
}