package dev.rafael.server.features.exercise.routes

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.result.AppError
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.flatMap
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.exercise.services.ExerciseService
import dev.rafael.server.features.profile.services.ProfileService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.uuid.Uuid

fun Route.exerciseRoutes(service: ExerciseService, profileService: ProfileService) {
    authenticate(FIREBASE_AUTH) {
        get("/exercises") {
            val categoryParam = call.queryParameters["category"]
            val result = if (categoryParam != null) {
                val category = runCatching { ExerciseCategory.valueOf(categoryParam) }.getOrNull()
                if (category == null) service.listAll() else service.listByCategory(category)
            } else {
                service.listAll()
            }
            call.respondResult(result)
        }

        // Alternativas de mesmo tipo pra troca (usa ambiente/nível/limitações do perfil).
        get("/exercises/{id}/alternatives") {
            val principal = call.principal<FirebaseUser>()!!
            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            val result = if (id == null) {
                AppError.Validation("id de exercício inválido").asFailure()
            } else {
                profileService.getProfile(principal.uid, principal.email).flatMap { p ->
                    val env = p.environment
                    if (env == null) AppError.Validation("Ambiente de treino não definido").asFailure()
                    else service.alternatives(id, env, p.level, p.limitations)
                }
            }
            call.respondResult(result)
        }
    }
}
