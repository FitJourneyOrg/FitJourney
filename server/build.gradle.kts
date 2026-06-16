// :server — Ktor JVM. Depende de shared-contract, core:domain, core:result (ARCH #3).
// NUNCA de módulos de cliente (network, database, designsystem, features).
plugins {
    id("fitjourney.jvm-server")
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
}

group = "dev.rafael"
version = "1.0.0"

application {
    mainClass = "dev.rafael.server.ApplicationKt"
}

dependencies {
    // Módulos do projeto (typesafe accessors)
    implementation(projects.sharedContract)
    implementation(projects.shared.core.domain)
    implementation(projects.shared.core.result)

    // Ktor server (Fase 1)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverConfigYaml)
    implementation(libs.ktor.serialization.json)
    implementation(libs.logback)

    // Testes
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}