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
    testImplementation(libs.kotlinx.coroutines.core)   // runBlocking em testes de service suspend

    implementation(libs.flyway.core)              // API Flyway.configure() é referenciada no código
    runtimeOnly(libs.flyway.databasePostgresql)   // plugin de dialeto: descoberto via service loader, código não toca


    implementation(libs.bundles.exposed)   // core, jdbc, kotlinDatetime (v1)
    implementation(libs.hikari)
    // Driver JDBC. Era `runtimeOnly` com a nota "código não referencia" — uma restrição que
    // descrevia uma independência de banco que o projeto já não tem: as migrations usam JSONB,
    // índice parcial, ON CONFLICT e INTERVAL, nenhum deles portável. A F.1 precisou de `PGobject`
    // para gravar JSONB (ver `server.db.JsonbText`) e a restrição caiu.
    // ⚪ Dívida: regra de Konsist para manter o import do driver restrito a `server.db`.
    implementation(libs.postgres)
    testRuntimeOnly(libs.h2)               // testes (decisão Fase 0)
}


testing {
    suites {
        // suite de integração: Postgres real via Testcontainers (precisa de Docker)
        val integrationTest by registering(JvmTestSuite::class) {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(project(":shared-contract"))   // ProfileDto/ProgramDto e enums (server usa como implementation, não api)
                // AppResult/AppError: o server usa como `implementation`, então não vaza para
                // os testes. Necessário desde que o teste passou a assertar o retorno dos
                // repositórios direto (idempotência do POST — ARCH #30).
                implementation(project(":shared:core:result"))
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.junitJupiter)
                implementation(libs.flyway.core)
                implementation(libs.hikari)
                runtimeOnly(libs.postgres)
                implementation(libs.kotlin.testJunit)
                // Exposed p/ gerar treino (connect + query de nomes). Bundle não é aceito no
                // DependencyCollector da suite; declara individual.
                implementation(libs.exposed.core)
                implementation(libs.exposed.jdbc)
                implementation(libs.exposed.kotlinDatetime)
                implementation(libs.kotlinx.coroutines.core)  // runBlocking p/ o suspend generate()
            }
        }
    }
}

/*
 * ⭐ MESMO DEFEITO DO `:konsist:test` (corrigido em 2026-09-11, ver konsist/build.gradle.kts),
 * AGORA AQUI (P0.2, 2026-09-22).
 *
 * `BUILD SUCCESSFUL in 948ms` com Testcontainers é impossível — o container sozinho leva
 * segundos pra subir. A task ficou `UP-TO-DATE` depois de duas guardas novas em
 * `CaminhosDeMidiaIntegrationTest.kt`: elas nunca rodaram, e ninguém percebeu porque o build
 * "passou verde". `JvmTestSuite` já rastreia os `.kt` da própria suíte automaticamente — o que
 * faltava é o que a suíte lê em TEMPO DE EXECUÇÃO e o Gradle não vê como entrada: as migrations
 * (`server/src/main/resources/db/migration/*.sql`), aplicadas via Flyway/`Migrations.run(ds)`
 * dentro do teste, não na compilação. Mudar uma migration sem tocar em nenhum `.kt` da suíte é
 * exatamente o cenário que ficava cego.
 *
 * > **Número de teste que ninguém mediu é pior que número nenhum: parece verificado.**
 */
val migrationsDoServidor = fileTree("$projectDir/src/main/resources/db/migration") {
    include("*.sql")
}

// integração roda depois dos unitários quando ambos rodam juntos (ex.: no check)
tasks.named<Test>("integrationTest") {
    shouldRunAfter(tasks.named("test"))
    testLogging { showStandardStreams = true }   // mostra o println do treino gerado no console

    inputs.files(migrationsDoServidor)
        .withPropertyName("migrationsDoServidor")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // `val` local pelo mesmo motivo do konsist: a ação roda na EXECUÇÃO, quando o objeto do
    // script `.kts` já não existe — capturar a propriedade direto quebra o cache de configuração
    // (`cannot serialize Gradle script object references`).
    val migrations = migrationsDoServidor

    doFirst {
        val quantas = migrations.files.size
        check(quantas >= 40) {
            "o fileTree de migrations casou com $quantas arquivo(s): o caminho está errado e o " +
                ":server:integrationTest voltaria a ficar cego pra mudança de migration"
        }
        logger.lifecycle("integrationTest: $quantas migrations declaradas como entrada")
    }
}