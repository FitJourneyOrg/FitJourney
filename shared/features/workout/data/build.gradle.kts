plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.workout.data"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.workout.domain)
            implementation(projects.shared.core.result)
            implementation(projects.shared.core.network)
            implementation(projects.shared.core.database)   // cache local (leitura offline)
            implementation(projects.sharedContract)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)   // serializa o cache
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}