import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        /**
         * 17 para ALINHAR com os módulos compartilhados, que usam `jvmToolchain(17)` pelos
         * convention plugins.
         *
         * Estava em 11, e a divergência tinha uma consequência que ninguém escolheu: nenhuma
         * função `inline` do core podia ser chamada daqui. `AppResult.map`/`flatMap`, que são o
         * idioma do projeto, falhavam com "Cannot inline bytecode built with JVM target 17".
         * O `StatsRepository` já usava `when` por causa disso — parecia estilo, era restrição.
         *
         * Java 17 como language level no Android é suportado desde o AGP 8, e o AGP 9 já exige
         * JDK 17 para compilar. Não muda o que é preciso instalar; muda o bytecode gerado.
         */
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(projects.sharedContract)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // navigation compose
    implementation(libs.androidx.navigation.compose)

    // Coil 3 — carregamento async de imagem (thumbs de exercício) + fetcher de rede
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.network.ktor3)   // fotos de check-in: rota autenticada (fatia B)

    // Media3 / ExoPlayer — mp4 em loop mudo no detalhe do exercício
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // WorkManager — reenvia a outbox quando a rede volta (mesmo com o app fechado)
    implementation(libs.androidx.work.runtime)

    // CameraX — foto do check-in tirada DENTRO do app (4.4)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Localização do check-in (5.2). O Geocoder é da plataforma, não precisa de dependência.
    implementation(libs.playServices.location)

    // Upload multipart do check-in. Base64 em JSON infla 33% e some com o Content-Type da parte.
    implementation(libs.ktor.client.contentNegotiation)

    // Ícones do Material, os dois pelo catálogo e do MESMO fornecedor. Ver o comentário no
    // `libs.versions.toml`: um deles estava sem versão e quebrava o lint do release.
    implementation(libs.composeIcons.core)
    implementation(libs.composeIcons.extended)

    // Koin (DI no cliente)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(projects.shared.core.designsystem)   // tema Volt Athletic (dark-first)
    implementation(projects.shared.core.network)
    implementation(projects.shared.core.database)
    implementation(projects.shared.core.result)
    implementation(projects.shared.core.catalog)      // ExerciseLookup (execução da sessão)
    implementation(libs.kotlinx.datetime)             // timestamps da sessão (Fase 5)
    implementation(libs.ktor.client.core)             // SessionApi (data no app — extrair p/ session:data depois)
    implementation(libs.sqldelight.coroutinesExtensions)  // histórico de sessões observado por Flow
    implementation(libs.kotlinx.serialization.json)   // serializa o payload da outbox

    // Testes de ViewModel do app. Antes este source set não existia, e por isso a Home — a
    // tela mais importante — era a única sem cobertura nenhuma.
    // kotlin-test-junit (não só kotlin-test): num módulo Android o runner é JUnit4, e é esta
    // ponte que faz as anotações `kotlin.test.*` serem reconhecidas. Assim o código de teste
    // fica idêntico ao dos módulos KMP, que também usam kotlin.test.
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)   // runTest + Dispatchers.setMain
    // Features (o app agrega os módulos Koin e usa o ViewModel)
    implementation(projects.shared.features.auth.domain)
    implementation(projects.shared.features.auth.presentation)
    implementation(projects.shared.features.auth.data)

    implementation(projects.shared.features.profile.presentation)
    implementation(projects.shared.features.profile.domain)
    implementation(projects.shared.features.profile.data)

    implementation(projects.shared.features.exercise.presentation)
    implementation(projects.shared.features.exercise.data)
    implementation(projects.shared.features.exercise.domain)

    implementation(projects.shared.features.workout.data)
    implementation(projects.shared.features.workout.domain)
    implementation(projects.shared.features.workout.presentation)

    implementation(projects.shared.features.program.data)
    implementation(projects.shared.features.program.domain)
    implementation(projects.shared.features.program.presentation)
}

android {
    namespace = "dev.rafael.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.rafael.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        /**
         * Onde o app procura o servidor.
         *
         * Default `10.0.2.2` — o apelido de NAT do EMULADOR. Para rodar em aparelho físico, o
         * endereço tem que ser o IP da máquina na rede local; basta pôr no `gradle.properties`
         * (que é local e não vai para o git):
         *
         *     apiBaseUrl=http://192.168.0.10:8080
         *
         * Fica no build e não em código compartilhado de propósito: assim trocar de aparelho não
         * produz uma alteração em `HttpClientFactory` que entra num commit por engano.
         */
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${providers.gradleProperty("apiBaseUrl").getOrElse("http://10.0.2.2:8080")}\"",
        )
    }
    buildFeatures { buildConfig = true }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        // Acompanha o jvmTarget acima — os dois têm de andar juntos, senão o Kotlin gera 17 e
        // o Java do módulo continua em 11.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}