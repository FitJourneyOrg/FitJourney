package dev.rafael.server.features.profile.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.profile.models.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class ProfileRepositoryImpl : ProfileRepository {

    override suspend fun findByUserId(userId: Uuid): AppResult<Profile?> =
        dbQuery {
            ProfilesTable.selectAll()
                .where { ProfilesTable.userId eq userId }
                .singleOrNull()
                ?.toProfile()
        }

    override suspend fun upsert(profile: Profile): AppResult<Profile> =
        dbQuery {
            val exists = ProfilesTable.selectAll()
                .where { ProfilesTable.userId eq profile.userId }
                .singleOrNull() != null

            if (exists) {
                ProfilesTable.update({ ProfilesTable.userId eq profile.userId }) {
                    it[goal] = profile.goal.name
                    it[level] = profile.level.name
                    it[daysPerWeek] = profile.daysPerWeek
                    it[focusAreas] = profile.focusAreas.toJson()
                    it[weightKg] = profile.weightKg
                    it[heightCm] = profile.heightCm
                    it[environment] = profile.environment?.name
                    it[healthScreening] = profile.health?.toJson()
                    it[onboardingCompleted] = profile.onboardingCompleted
                }
            } else {
                ProfilesTable.insert {
                    it[userId] = profile.userId
                    it[goal] = profile.goal.name
                    it[level] = profile.level.name
                    it[daysPerWeek] = profile.daysPerWeek
                    it[focusAreas] = profile.focusAreas.toJson()
                    it[weightKg] = profile.weightKg
                    it[heightCm] = profile.heightCm
                    it[environment] = profile.environment?.name
                    it[healthScreening] = profile.health?.toJson()
                    it[onboardingCompleted] = profile.onboardingCompleted
                }
            }
            profile
        }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { transaction { block() } }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}

private fun ResultRow.toProfile(): Profile = Profile(
    userId = this[ProfilesTable.userId],
    goal = Goal.valueOf(this[ProfilesTable.goal]),
    level = Level.valueOf(this[ProfilesTable.level]),
    daysPerWeek = this[ProfilesTable.daysPerWeek],
    focusAreas = this[ProfilesTable.focusAreas].toMuscleGroups(),
    weightKg = this[ProfilesTable.weightKg],
    heightCm = this[ProfilesTable.heightCm],
    environment = this[ProfilesTable.environment]?.let { TrainingEnvironment.valueOf(it) },
    health = this[ProfilesTable.healthScreening]?.toHealthScreening(),
    onboardingCompleted = this[ProfilesTable.onboardingCompleted],
)