package dev.rafael.core.database

import dev.rafael.core.catalog.ExerciseRef
import dev.rafael.core.catalog.ExerciseLookup

class ExerciseLookupImpl(private val db: FitJourneyDatabase) : ExerciseLookup {
    override suspend fun byIds(ids: List<String>): Map<String, ExerciseRef> {
        if (ids.isEmpty()) return emptyMap()          // <- guard
        return db.exerciseQueries.selectByIds(ids)
            .executeAsList()
            .associate { it.id to ExerciseRef(it.id, it.name, it.thumbRef) }
    }
}

