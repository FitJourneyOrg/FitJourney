plugins {
    id("fitjourney.kmp-client")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("FitJourneyDatabase") {
            packageName.set("dev.rafael.core.database")
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