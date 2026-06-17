// Convention plugin de cliente COM UI (Compose Multiplatform).
// O que é: tudo do kmp-client + o plugin Compose e o compose-compiler.
//          Pra quem desenha tela: core:designsystem (tema), auth:presentation (telas).
// Por que: só UI carrega Compose. Mantém network/database/domain livres de Compose -> fronteira real.
// Pra que: padroniza a stack de UI num único lugar; módulos de tela aplicam 1 linha.
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

    iosX64()
    iosArm64()
    iosSimulatorArm64()
}