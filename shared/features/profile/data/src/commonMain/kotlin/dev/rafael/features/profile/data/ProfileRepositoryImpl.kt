package dev.rafael.features.profile.data

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.profile.domain.model.Profile
import dev.rafael.features.profile.domain.repository.ProfileRepository

class ProfileRepositoryImpl(
    private val dataSource: ProfileDataSource,
) : ProfileRepository {

    override suspend fun getProfile(): AppResult<Profile> =
        runCatching { dataSource.getProfile().toDomain() }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { AppError.Unexpected("Falha ao buscar perfil", it).asFailure() },
        )

    override suspend fun saveProfile(profile: Profile): AppResult<Profile> =
        runCatching { dataSource.saveProfile(profile.toDto()).toDomain() }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { AppError.Unexpected("Falha ao salvar perfil", it).asFailure() },
        )
}