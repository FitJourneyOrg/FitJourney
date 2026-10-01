// :shared:features:wiki:presentation — MVI do "Aprender" (Fase 8). Cliente.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.wiki.presentation"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.wiki.domain)
            implementation(projects.shared.core.result)
            implementation(projects.sharedContract)          // WikiCategory (State/Event)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
