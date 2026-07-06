package dev.rafael.features.profile.data

import dev.rafael.core.database.FitJourneyDatabase

class ProfileLocalDataSource(private val db: FitJourneyDatabase) {
    private val queries = db.profileQueries

    fun cachedOnboarding(): Boolean? =
        queries.selectOnboarding().executeAsOneOrNull()?.let { it == 1L }

    fun saveOnboarding(completed: Boolean) {
        queries.upsertOnboarding(if (completed) 1L else 0L)
    }
}