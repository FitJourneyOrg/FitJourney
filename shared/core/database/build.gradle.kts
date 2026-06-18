// :shared:core:database — SQLDelight offline-first; driver expect/actual por plataforma (Fase 4).
// android + iOS, sem UI.
plugins {
    id("fitjourney.kmp-client")
}

kotlin {
    androidLibrary {
        namespace = "dev.rafael.core.database"
    }
}