package dev.rafael.server.features.exercise.routes

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.exercise.services.ExerciseService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.exerciseRoutes(service: ExerciseService) {
    authenticate(FIREBASE_AUTH) {
        get("/exercises") {
            val categoryParam = call.queryParameters["category"]
            val result = if (categoryParam != null) {
                val category = runCatching { ExerciseCategory.valueOf(categoryParam) }.getOrNull()
                if (category == null) {
                    service.listAll()   // categoria inválida -> retorna tudo (ou poderia dar 400)
                } else {
                    service.listByCategory(category)
                }
            } else {
                service.listAll()
            }
            call.respondResult(result)
        }
    }
}