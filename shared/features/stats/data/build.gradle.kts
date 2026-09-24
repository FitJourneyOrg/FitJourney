// :shared:features:stats:data — cliente Ktor (só leitura) + cache local (kv_cache), repo impl.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.stats.data"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.stats.domain)
            implementation(projects.shared.core.result)
            implementation(projects.shared.core.network)
            implementation(projects.shared.core.database)          // <- SQLDelight (cache)
            implementation(projects.sharedContract)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)   // serializa o payload no kv_cache
            implementation(libs.ktor.client.core)
            implementation(libs.sqldelight.coroutinesExtensions)   // <- asFlow().mapToOneOrNull()
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
