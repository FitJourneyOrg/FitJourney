package dev.rafael.server.features.profile.services

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.models.Profile
import dev.rafael.server.features.profile.models.toDto
import dev.rafael.server.features.user.services.UserService

class ProfileService(
    private val userService: UserService,
    private val repository: ProfileRepository,
) {
    /** Perfil do usuário logado. 404 (NotFound) se ainda não fez onboarding. */
    suspend fun getProfile(firebaseUid: String, email: String?): AppResult<ProfileDto> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            when (val found = repository.findByUserId(user.id)) {
                is AppResult.Failure -> found
                is AppResult.Success ->
                    found.value?.toDto()?.asSuccess()
                        ?: AppError.NotFound("Perfil não encontrado").asFailure()
            }
        }

    /** Cria/atualiza o perfil. Validação autoritativa do servidor. */
    suspend fun saveProfile(firebaseUid: String, email: String?, dto: ProfileDto): AppResult<ProfileDto> {
        if (dto.daysPerWeek !in 2..6) {
            return AppError.Validation("daysPerWeek deve estar entre 2 e 6").asFailure()
        }
        if (dto.focusAreas.size > 2) {
            return AppError.Validation("focusAreas aceita no máximo 2 grupos").asFailure()
        }
        if (dto.age != null && dto.age !in 5..120) {
            return AppError.Validation("Idade fora do intervalo válido").asFailure()
        }
        // Estágio 2 (descanso): dias off válidos e sobra dia p/ treinar.
        if (dto.unavailableDays.any { it !in 1..7 } || dto.unavailableDays.toSet().size != dto.unavailableDays.size) {
            return AppError.Validation("Dias indisponíveis inválidos (1..7, sem repetir)").asFailure()
        }
        if (7 - dto.unavailableDays.size < dto.daysPerWeek) {
            return AppError.Validation("Dias livres insuficientes para treinar ${dto.daysPerWeek}x na semana").asFailure()
        }
        return userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val profile = Profile(
                userId = user.id,
                goal = dto.goal,
                level = dto.level,
                daysPerWeek = dto.daysPerWeek,
                splitPreference = dto.splitPreference,
                unavailableDays = dto.unavailableDays,
                focusAreas = dto.focusAreas,
                weightKg = dto.weightKg,
                heightCm = dto.heightCm,
                age = dto.age,
                minorSupervised = dto.minorSupervised,
                environment = dto.environment,
                health = dto.health,
                limitations = dto.limitations,
                onboardingCompleted = true,
            )
            repository.upsert(profile).map { it.toDto() }
        }
    }
}