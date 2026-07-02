package dev.rafael.server.features.workout.db

import org.jetbrains.exposed.v1.core.Table

object WorkoutSetsTable : Table("workout_sets") {
    val id = uuid("id")
    val workoutExerciseId = uuid("workout_exercise_id")
    val reps = integer("reps")
    val orderIndex = integer("order_index")
    override val primaryKey = PrimaryKey(id)
}