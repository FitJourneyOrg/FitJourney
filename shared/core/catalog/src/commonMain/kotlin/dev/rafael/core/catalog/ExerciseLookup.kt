package dev.rafael.core.catalog

interface ExerciseLookup {
    suspend fun byIds(ids: List<String>): Map<String, ExerciseRef>
}