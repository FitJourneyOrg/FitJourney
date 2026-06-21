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