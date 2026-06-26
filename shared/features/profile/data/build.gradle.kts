// :shared:features:profile:data — datasource (Ktor), mapper, impl do repositório.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.data"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.profile.domain)
            implementation(projects.shared.core.result)
            implementation(projects.shared.core.network)
            implementation(projects.sharedContract)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
        }
    }
}