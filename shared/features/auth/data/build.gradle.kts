// :shared:features:auth:data — datasource (Ktor/SQLDelight), mapper DTO->domínio, impl do repositório.
plugins {
    id("fitjourney.kmp-client")
}
kotlin {
    androidLibrary {
        namespace = "dev.rafael.features.auth.data"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared.features.auth.domain)
            implementation(projects.shared.core.result)
            implementation(libs.koin.core)
            implementation(libs.gitlive.firebase.auth)
            implementation(libs.kotlinx.coroutines.core)
            implementation(projects.shared.core.network)
            implementation(projects.sharedContract)
            implementation(libs.ktor.client.core)            // <- novo: HttpClient, get, body
            implementation(libs.ktor.client.contentNegotiation) // <- pra .body() desserializar
        }
        androidMain.dependencies {
            // GitLive Android delega ao SDK Firebase nativo — BoM fixa as versões
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.auth)
        }
    }
}