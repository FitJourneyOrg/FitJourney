package dev.rafael.features.profile.data

import dev.rafael.core.network.TokenProvider
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
    private val local: ProfileLocalDataSource,
    private val tokenProvider: TokenProvider,   // p/ chavear o cache por uid (vem do core, sem dep de feature)
) : ProfileRepository {

    override suspend fun getProfile(): AppResult<Profile> =
        runCatching { remote.getProfile().toDomain() }.fold(
            onSuccess = { profile ->
                cacheOnboarding(profile.onboardingCompleted)   // grava o flag do usuário ATUAL
                profile.asSuccess()
            },
            onFailure = { e ->
                when {
                    e is ClientRequestException && e.response.status == HttpStatusCode.NotFound -> {
                        cacheOnboarding(false)   // sem perfil = não onboardou (p/ o uid atual)
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
                cacheOnboarding(saved.onboardingCompleted)   // true pós-quiz (p/ o uid atual)
                saved.asSuccess()
            },
            onFailure = { AppError.Unexpected("Falha ao salvar perfil", it).asFailure() },
        )

    override suspend fun cachedOnboardingCompleted(): Boolean? =
        local.cachedOnboarding(tokenProvider.currentUid())

    // Logout: apaga o cache. Como o cache é chaveado por uid, o próximo usuário já não
    // herdaria o flag; apagar é só higiene extra.
    override suspend fun clearOnboardingCache() = local.clear()

    private suspend fun cacheOnboarding(completed: Boolean) {
        val uid = tokenProvider.currentUid() ?: return   // sem uid não dá p/ chavear — não cacheia
        local.saveOnboarding(uid, completed)
    }
}
