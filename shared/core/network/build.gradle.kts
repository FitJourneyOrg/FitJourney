// :shared:core:network — cliente Ktor (HttpClientFactory, datasources). android + iOS, sem UI.
plugins {
    id("fitjourney.kmp-client")
    alias(libs.plugins.kotlinSerialization)
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.core.network"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.sharedContract)
            implementation(projects.shared.core.result)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}