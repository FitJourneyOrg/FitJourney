package dev.rafael.features.exercise.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.rafael.contract.exercise.ExerciseDto
import dev.rafael.core.database.Exercise
import dev.rafael.core.database.FitJourneyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class ExerciseLocalDataSource(private val db: FitJourneyDatabase) {
    private val queries = db.exerciseQueries

    fun observeAll(): Flow<List<Exercise>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default)

    fun observeByCategory(category: String): Flow<List<Exercise>> =
        queries.selectByCategory(category).asFlow().mapToList(Dispatchers.Default)

    fun replaceAll(dtos: List<ExerciseDto>) {
        queries.transaction {
            queries.deleteAll()
            dtos.forEach { d ->
                queries.insertOrReplace(
                    id = d.id, name = d.name, category = d.category.name,
                    description = d.description, videoRef = d.videoRef, thumbRef = d.thumbRef,
                )
            }
        }
    }
}