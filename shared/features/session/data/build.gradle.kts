// :shared:features:session:data — cliente Ktor + cache local (outbox de verdade), repo impl.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.session.data"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.session.domain)
            implementation(projects.shared.core.result)
            implementation(projects.shared.core.network)
            implementation(projects.shared.core.database)          // <- SQLDelight (outbox local)
            implementation(projects.sharedContract)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.coroutinesExtensions)   // <- asFlow().mapToList()
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
