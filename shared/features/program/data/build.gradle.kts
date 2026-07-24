plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.program.data"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.program.domain)
            // NÃO depende de workout:data (Konsist: data.dependsOn(domain) só — ver
            // program:domain/build.gradle.kts). Mapeia WorkoutDto -> ProgramWorkout aqui mesmo.
            implementation(projects.shared.core.result)
            implementation(projects.shared.core.network)
            implementation(projects.sharedContract)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
        }
    }
}
