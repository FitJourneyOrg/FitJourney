package dev.rafael.features.profile.domain.repository

import dev.rafael.core.result.AppResult
import dev.rafael.features.profile.domain.model.Profile

/** Contrato do perfil. Implementado na camada data (Ktor, GET/PUT /me/profile). */
interface ProfileRepository {
    suspend fun getProfile(): AppResult<Profile>
    suspend fun saveProfile(profile: Profile): AppResult<Profile>
}