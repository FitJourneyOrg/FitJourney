// :shared:core:network — cliente Ktor (HttpClientFactory, datasources). android + iOS, sem UI.
plugins {
    id("fitjourney.kmp-client")
}

kotlin {
    androidLibrary {
        namespace = "dev.rafael.core.network"
    }
}