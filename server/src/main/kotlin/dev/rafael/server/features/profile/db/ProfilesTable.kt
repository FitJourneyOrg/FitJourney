package dev.rafael.server.features.profile.db

import org.jetbrains.exposed.v1.core.Table

/** Espelha V2__create_profiles.sql. 1:1 com users (user_id = PK + FK). focus_areas = JSON em TEXT. */
object ProfilesTable : Table("profiles") {
    val userId = uuid("user_id")
    val goal = varchar("goal", 32)
    val level = varchar("level", 32)
    val daysPerWeek = integer("days_per_week")
    val focusAreas = text("focus_areas")
    val weightKg = double("weight_kg").nullable()
    val heightCm = double("height_cm").nullable()
    val environment = varchar("environment", 32).nullable()   // <- novo
    val healthScreening = text("health_screening").nullable()  // <- novo (JSON)
    val limitations = text("limitations").nullable()
    val onboardingCompleted = bool("onboarding_completed")
    override val primaryKey = PrimaryKey(userId)
}