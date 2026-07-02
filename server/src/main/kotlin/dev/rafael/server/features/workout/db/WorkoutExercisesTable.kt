package dev.rafael.server.features.workout.db

import org.jetbrains.exposed.v1.core.Table

object WorkoutExercisesTable : Table("workout_exercises") {
    val id = uuid("id")
    val workoutId = uuid("workout_id")
    val exerciseId = uuid("exercise_id")
    val orderIndex = integer("order_index")
    override val primaryKey = PrimaryKey(id)
}