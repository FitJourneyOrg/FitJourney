// :konsist — teste de arquitetura (lint de fronteira). JVM puro, fora do grafo de produção.
// Escaneia o projeto via filesystem; não depende de nenhum outro módulo.
plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(libs.kotlin.testJunit)
}