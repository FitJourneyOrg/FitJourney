plugins {
    id("fitjourney.kmp-client")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("FitJourneyDatabase") {
            packageName.set("dev.rafael.core.database")
            // Offline-first: o banco local guarda dado que só existe aqui (sessões pendentes),
            // então NÃO pode mais ser descartável.
            //
            // O SCHEMA MORA NAS MIGRATIONS (migrations/1.sqm, 2.sqm, ...) e os .sq guardam
            // apenas as queries. É o que dá histórico de verdade: o SQLDelight sabe como era
            // o schema em cada versão e consegue migrar um banco antigo. Se o schema ficasse
            // nos .sq, ele só conheceria o estado atual — e nenhuma migration faria sentido.
            deriveSchemaFromMigrations.set(true)
        }
    }
}

kotlin {
    androidLibrary {
        namespace = "dev.rafael.core.database"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.core.catalog)
            // `api`: AppResult/AppError aparecem na assinatura de ExecutorDeOperacao, que os
            // módulos de feature implementam. Com `implementation` eles não enxergariam o tipo.
            api(projects.shared.core.result)
            implementation(libs.sqldelight.runtime)
            // asFlow/mapToList: a fila do outbox é OBSERVADA pela UI (selo de pendente).
            implementation(libs.sqldelight.coroutinesExtensions)
            // `api` e não `implementation`: Outbox.observar() devolve Flow no tipo público.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            // A compactação do outbox é lógica PURA (não toca SQLDelight), então tem teste
            // de verdade — é onde mora a regra que decide o que sobe para o servidor.
            implementation(kotlin("test"))
            // runTest: o processador é suspend (o compactador não era).
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.koin.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
    }
}