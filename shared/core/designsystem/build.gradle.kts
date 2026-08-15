// :shared:core:designsystem — tema FitJourney (cores, tipografia, shapes). Compose Multiplatform.
plugins {
    id("fitjourney.kmp-compose")
}

kotlin {
    androidLibrary {
        namespace = "dev.rafael.core.designsystem"
    }
    sourceSets {
        commonMain.dependencies {
            // api: quem depende do designsystem enxerga MaterialTheme/Color sem redeclarar
            api(libs.compose.runtime)      // @Composable
            api(libs.compose.ui)           // Color
            api(libs.compose.material3)    // MaterialTheme, darkColorScheme
        }
    }
}