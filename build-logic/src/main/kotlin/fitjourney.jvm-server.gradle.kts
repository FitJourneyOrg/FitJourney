plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

// A fence "server não vê módulos de cliente" NÃO é imposta aqui (Gradle não é
// o lugar). Vira teste de arquitetura (Konsist, Fase 0.4) + deps explícitas no
// build do :server. Permitido: shared-contract, core:domain, core:result (ARCH #3).