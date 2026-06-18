// :shared:features:auth:data — datasource (Ktor/SQLDelight), mapper DTO->domínio, impl do repositório.
plugins {
    id("fitjourney.kmp-client")
}

kotlin {
    androidLibrary {
        namespace = "dev.rafael.feature.auth.data"
    }
}