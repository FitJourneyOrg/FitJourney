package dev.rafael.server.features.profile.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.profile.models.Profile
import kotlin.uuid.Uuid

interface ProfileRepository {
    suspend fun findByUserId(userId: Uuid): AppResult<Profile?>
    suspend fun upsert(profile: Profile): AppResult<Profile>
}