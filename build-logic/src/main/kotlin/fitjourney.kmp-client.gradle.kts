// Convention plugin de cliente SEM UI.
// O que é: KMP com android + iOS, sem Compose. Pra módulos que rodam no app (Android/iOS)
//          mas não desenham tela: core:network (Ktor client), core:database (SQLDelight), auth:data.
// Por que: o kmp-library (puro) não tem android target; e arrastar Compose pra cá seria peso morto
//          e furo de fronteira. Este é o meio-termo: cliente, sem UI.
// Pra que: elimina o boilerplate de fábrica desses módulos e padroniza targets/SDK num lugar só.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        // namespace é definido por módulo (cada um tem o seu) — ver nota no plano de migração.
        compileSdk = 36
        minSdk = 24
        withHostTestBuilder {}
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()
    // SEM binaries.framework: módulo core interno não exporta XCFramework (isso é do umbrella iOS, Fase 11).
}