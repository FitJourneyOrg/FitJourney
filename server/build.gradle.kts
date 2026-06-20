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

    // firebase
    implementation(libs.firebase.admin)

    // Ktor server (Fase 1)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverConfigYaml)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverDefaultHeaders)
    implementation(libs.ktor.serverAuth)
    implementation(libs.logback)

    // koin
    implementation(libs.koin.ktor)
    implementation(libs.koin.loggerSlf4j)

    // Testes
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)

    implementation(libs.flyway.core)              // API Flyway.configure() é referenciada no código
    runtimeOnly(libs.flyway.databasePostgresql)   // plugin de dialeto: descoberto via service loader, código não toca


    implementation(libs.bundles.exposed)   // core, jdbc, kotlinDatetime (v1)
    implementation(libs.hikari)
    runtimeOnly(libs.postgres)             // driver JDBC: só runtime, código não referencia
    testRuntimeOnly(libs.h2)               // testes (decisão Fase 0)
}


testing {
    suites {
        // suite de integração: Postgres real via Testcontainers (precisa de Docker)
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.flyway.core)
                implementation(libs.hikari)
                runtimeOnly(libs.postgres)
                implementation(libs.kotlin.testJunit)
            }
        }
    }
}

// integração roda depois dos unitários quando ambos rodam juntos (ex.: no check)
tasks.named("integrationTest") {
    shouldRunAfter(tasks.named("test"))
}