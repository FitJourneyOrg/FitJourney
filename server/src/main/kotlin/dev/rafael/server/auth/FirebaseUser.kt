package dev.rafael.server.auth

/** Identidade extraída de um ID Token válido. Model do server — não vai pro contrato. */
data class FirebaseUser(
    val uid: String,        // chave no Postgres (Fase 2) — sagrado
    val email: String?,
    val emailVerified: Boolean,
)