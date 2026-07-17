package dev.rafael.server.features.profile.models

import dev.rafael.contract.profile.ProfileDto

/** Profile (server) -> ProfileDto (fio). Estrutura idêntica; enums já são compartilhados. */
fun Profile.toDto(): ProfileDto = ProfileDto(
    goal = goal,
    level = level,
    daysPerWeek = daysPerWeek,
    focusAreas = focusAreas,
    weightKg = weightKg,
    heightCm = heightCm,
    environment = environment,
    health = health,
    limitations = limitations,
    onboardingCompleted = onboardingCompleted,
)