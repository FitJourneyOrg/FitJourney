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
            implementation(libs.sqldelight.runtime)
            implementation(libs.koin.core)
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