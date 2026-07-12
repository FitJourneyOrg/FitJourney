package dev.rafael.features.profile.data

import dev.rafael.contract.profile.ProfileDto
import dev.rafael.features.profile.domain.model.Profile

/** DTO (fio) -> Profile (domínio). O DTO não passa daqui. */
fun ProfileDto.toDomain(): Profile = Profile(
    goal = goal,
    level = level,
    daysPerWeek = daysPerWeek,
    focusAreas = focusAreas,
    weightKg = weightKg,
    heightCm = heightCm,
    environment = environment,
    health = health,
    onboardingCompleted = onboardingCompleted,
)

/** Profile (domínio) -> DTO (fio). onboardingCompleted é derivado no server; aqui vai false. */
fun Profile.toDto(): ProfileDto = ProfileDto(
    goal = goal,
    level = level,
    daysPerWeek = daysPerWeek,
    focusAreas = focusAreas,
    weightKg = weightKg,
    heightCm = heightCm,
    environment = environment,   // <- novo
    health = health,             // <- novo
    onboardingCompleted = onboardingCompleted,
)