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
        jvmTarget = JvmTarget.JVM_11
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

    // Media3 / ExoPlayer — mp4 em loop mudo no detalhe do exercício
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // icones
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // Koin (DI no cliente)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(projects.shared.core.network)
    implementation(projects.shared.core.database)
    implementation(projects.shared.core.result)
    implementation(projects.shared.core.catalog)      // ExerciseLookup (execução da sessão)
    implementation(libs.kotlinx.datetime)             // timestamps da sessão (Fase 5)
    implementation(libs.ktor.client.core)             // SessionApi (data no app — extrair p/ session:data depois)
    implementation(libs.kotlinx.serialization.json)   // serializa o payload da outbox
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
    }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}