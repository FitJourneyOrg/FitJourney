package dev.rafael.server.features.profile.services

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.error.ErrorFields
import dev.rafael.contract.limites.Limites
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
import dev.rafael.contract.error.ErrorCodes

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
                        // Desmembrado do "Perfil não encontrado" na G.2. É o PRÓPRIO perfil, e ele
                        // não existe porque o questionário ainda não foi feito. Não é erro: é um
                        // passo que falta, e com código próprio a tela pode oferecer o onboarding
                        // em vez de mostrar cara de erro.
                        ?: AppError.NotFound(
                            "Você ainda não completou o questionário.",
                            code = ErrorCodes.MEU_PERFIL_INCOMPLETO,
                        ).asFailure()
            }
        }

    /**
     * Cria/atualiza o perfil. Validação autoritativa do servidor.
     *
     * Os tetos (dias/semana, grupos de foco, idade) vêm de [Limites.Profile] desde a correção do
     * débito "12 frases cravam constante do servidor" — antes eram literais soltos aqui (`2..6`,
     * `> 2`, `5..120`), sem nome nem no servidor, e a frase do `strings.xml` cravava os mesmos
     * números por coincidência, não por contrato.
     */
    suspend fun saveProfile(firebaseUid: String, email: String?, dto: ProfileDto): AppResult<ProfileDto> {
        if (dto.daysPerWeek !in Limites.Profile.DAYS_PER_WEEK_MIN..Limites.Profile.DAYS_PER_WEEK_MAX) {
            val msg = "Escolha entre ${Limites.Profile.DAYS_PER_WEEK_MIN} e ${Limites.Profile.DAYS_PER_WEEK_MAX} dias por semana"
            return AppError.Validation(
                msg,
                mapOf(ErrorFields.DAYS_PER_WEEK to ErrorCodes.DIAS_POR_SEMANA_INVALIDO),
                code = ErrorCodes.DIAS_POR_SEMANA_INVALIDO,
            ).asFailure()
        }
        if (dto.focusAreas.size > Limites.Profile.FOCUS_AREAS_MAX) {
            val msg = "Escolha no máximo ${Limites.Profile.FOCUS_AREAS_MAX} grupos de foco"
            return AppError.Validation(
                msg,
                mapOf(ErrorFields.FOCUS_AREAS to ErrorCodes.FOCO_ALEM_DO_LIMITE),
                code = ErrorCodes.FOCO_ALEM_DO_LIMITE,
            ).asFailure()
        }
        if (dto.age != null && dto.age !in Limites.Profile.AGE_MIN..Limites.Profile.AGE_MAX) {
            val msg = "Digite uma idade entre ${Limites.Profile.AGE_MIN} e ${Limites.Profile.AGE_MAX} anos."
            return AppError.Validation(
                msg,
                mapOf(ErrorFields.AGE to ErrorCodes.IDADE_INVALIDA),
                code = ErrorCodes.IDADE_INVALIDA,
            ).asFailure()
        }
        // Estágio 2 (descanso): dias off válidos e sobra dia p/ treinar.
        if (dto.unavailableDays.any { it !in 1..7 } || dto.unavailableDays.toSet().size != dto.unavailableDays.size) {
            return AppError.Validation(
                "Dias indisponíveis inválidos",
                mapOf(ErrorFields.UNAVAILABLE_DAYS to ErrorCodes.DIAS_INDISPONIVEIS_INVALIDOS),
                code = ErrorCodes.DIAS_INDISPONIVEIS_INVALIDOS,
            ).asFailure()
        }
        if (7 - dto.unavailableDays.size < dto.daysPerWeek) {
            return AppError.Validation(
                "Dias livres insuficientes para treinar ${dto.daysPerWeek}x na semana",
                mapOf(ErrorFields.UNAVAILABLE_DAYS to ErrorCodes.DIAS_LIVRES_INSUFICIENTES),
                code = ErrorCodes.DIAS_LIVRES_INSUFICIENTES,
            ).asFailure()
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