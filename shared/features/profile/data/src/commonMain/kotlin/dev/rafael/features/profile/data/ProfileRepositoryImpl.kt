package dev.rafael.features.profile.data

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.profile.domain.model.Profile
import dev.rafael.features.profile.domain.repository.ProfileRepository
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

class ProfileRepositoryImpl(
    private val remote: ProfileDataSource,
    private val local: ProfileLocalDataSource,   // <- novo
) : ProfileRepository {

    override suspend fun getProfile(): AppResult<Profile> =
        runCatching { remote.getProfile().toDomain() }.fold(
            onSuccess = { profile ->
                local.saveOnboarding(profile.onboardingCompleted)   // <- grava cache
                profile.asSuccess()
            },
            onFailure = { e ->
                when {
                    e is ClientRequestException && e.response.status == HttpStatusCode.NotFound -> {
                        // Sem perfil = não onboardou. Corrige o cache stale (single-row por device):
                        // sem isso, um usuário novo herda o 'true' de um usuário anterior no fallback.
                        local.saveOnboarding(false)
                        AppError.NotFound("Perfil não encontrado").asFailure()
                    }
                    else ->
                        AppError.Unexpected("Falha ao buscar perfil", e).asFailure()
                }
            },
        )

    override suspend fun saveProfile(profile: Profile): AppResult<Profile> =
        runCatching { remote.saveProfile(profile.toDto()).toDomain() }.fold(
            onSuccess = { saved ->
                local.saveOnboarding(saved.onboardingCompleted)     // <- grava cache (true pós-quiz)
                saved.asSuccess()
            },
            onFailure = { AppError.Unexpected("Falha ao salvar perfil", it).asFailure() },
        )

    override suspend fun cachedOnboardingCompleted(): Boolean? = local.cachedOnboarding()

    // Marca "não onboardado" no logout. Assim, se o próximo usuário (novo cadastro) cair no
    // fallback (getProfile lento/offline), o gate manda p/ o Quiz — e não herda o 'true' do
    // usuário anterior mandando pra Home. (Cache é single-row por device — ver débito uid.)
    override suspend fun clearOnboardingCache() = local.saveOnboarding(false)
}