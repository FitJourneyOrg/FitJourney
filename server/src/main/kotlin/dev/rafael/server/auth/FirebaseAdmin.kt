package dev.rafael.server.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.config.ApplicationConfig

object FirebaseAdmin {
    @Volatile private var initialized = false

    fun init(config: ApplicationConfig) {
        if (initialized) return
        val path = System.getenv("FIREBASE_CREDENTIALS_PATH")
            ?: config.property("firebase.credentialsPath").getString()
        val file = java.io.File(path)
        require(file.exists() && file.length() > 0) {
            "Credencial Firebase ausente ou vazia em ${file.absolutePath} (cwd=${System.getProperty("user.dir")})"
        }
        val credentials = file.inputStream().use { GoogleCredentials.fromStream(it) }
        FirebaseOptions.builder().setCredentials(credentials).build().let(FirebaseApp::initializeApp)
        initialized = true
    }
}