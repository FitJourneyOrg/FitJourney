// :shared:features:exercise:data — datasource remoto (Ktor) + local (SQLDelight), mapper, repo impl.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.exercise.data"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.exercise.domain)
            implementation(projects.shared.core.result)
            implementation(projects.shared.core.network)
            implementation(projects.shared.core.database)          // <- SQLDelight (cache)
            implementation(projects.sharedContract)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.sqldelight.coroutinesExtensions)   // <- asFlow().mapToList()
        }
    }
}