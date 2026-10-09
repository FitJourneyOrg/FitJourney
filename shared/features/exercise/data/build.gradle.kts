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
            implementation(libs.kotlinx.serialization.json)   // serializa primaryMuscles/secondaryMuscles no cache (5.sqm)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.sqldelight.coroutinesExtensions)   // <- asFlow().mapToList()
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Paridade `Exercise` x `ExerciseRef` (B12) contra SQLite real. `sqlite-driver` é JVM puro,
        // por isso fica fora de `commonTest` (mesmo motivo do módulo program:data). `core:catalog`
        // entra SÓ no teste: o código de produção deste módulo continua sem enxergá-lo.
        androidHostTest.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.kotlinx.coroutines.test)
            implementation(projects.shared.core.catalog)
        }
    }
}