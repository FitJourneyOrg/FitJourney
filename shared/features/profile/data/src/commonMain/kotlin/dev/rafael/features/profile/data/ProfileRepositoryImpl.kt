package dev.rafael.features.profile.data

import dev.rafael.core.network.TokenProvider
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.profile.domain.model.Profile
import dev.rafael.features.profile.domain.repository.ProfileRepository

class ProfileRepositoryImpl(
    private val remote: ProfileDataSource,
    private val local: ProfileLocalDataSource,
    private val tokenProvider: TokenProvider,   // p/ chavear o cache por uid (vem do core, sem dep de feature)
) : ProfileRepository {

    override suspend fun getProfile(): AppResult<Profile> {
        val r = httpResult { remote.getProfile().toDomain() }
        when (r) {
            is AppResult.Success -> cacheOnboarding(r.value.onboardingCompleted)   // flag do usuário ATUAL
            is AppResult.Failure -> if (r.error is AppError.NotFound) cacheOnboarding(false)   // sem perfil = não onboardou
        }
        return r
    }

    override suspend fun saveProfile(profile: Profile): AppResult<Profile> {
        val r = httpResult { remote.saveProfile(profile.toDto()).toDomain() }
        if (r is AppResult.Success) cacheOnboarding(r.value.onboardingCompleted)   // true pós-quiz
        return r
    }

    override suspend fun cachedOnboardingCompleted(): Boolean? =
        local.cachedOnboarding(tokenProvider.currentUid())

    // Logout: apaga o cache. Como é chaveado por uid, o próximo usuário já não herdaria; higiene extra.
    override suspend fun clearOnboardingCache() = local.clear()

    private suspend fun cacheOnboarding(completed: Boolean) {
        val uid = tokenProvider.currentUid() ?: return   // sem uid não dá p/ chavear — não cacheia
        local.saveOnboarding(uid, completed)
    }
}
