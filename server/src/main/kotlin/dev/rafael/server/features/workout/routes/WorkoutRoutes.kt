package dev.rafael.server.features.workout.routes

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.workout.services.WorkoutService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlin.uuid.Uuid

fun Route.workoutRoutes(service: WorkoutService) {
    authenticate(FIREBASE_AUTH) {

        post("/workouts") {
            val p = call.principal<FirebaseUser>()!!
            val dto = call.receive<WorkoutDto>()
            call.respondResult(service.create(p.uid, p.email, dto))
        }

        get("/workouts") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.list(p.uid, p.email))
        }

        get("/workouts/{id}") {
            val p = call.principal<FirebaseUser>()!!
            val id = call.workoutIdParam() ?: return@get call.respondResult(notFound<WorkoutDto>())
            call.respondResult(service.get(p.uid, p.email, id).notFoundIfNull())
        }

        put("/workouts/{id}") {
            val p = call.principal<FirebaseUser>()!!
            val id = call.workoutIdParam() ?: return@put call.respondResult(notFound<WorkoutDto>())
            val dto = call.receive<WorkoutDto>()
            call.respondResult(service.update(p.uid, p.email, id, dto).notFoundIfNull())
        }

        delete("/workouts/{id}") {
            val p = call.principal<FirebaseUser>()!!
            val id = call.workoutIdParam() ?: return@delete call.respondResult(notFound<Unit>())
            val result = service.delete(p.uid, p.email, id).flatMap { deleted ->
                if (deleted) Unit.asSuccess() else AppError.NotFound("Treino não encontrado").asFailure()
            }
            call.respondResult(result)
        }
    }
}

private fun ApplicationCall.workoutIdParam(): Uuid? =
    parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }

private fun <T> notFound(): AppResult<T> =
    AppError.NotFound("Treino não encontrado").asFailure()

private fun <T : Any> AppResult<T?>.notFoundIfNull(): AppResult<T> =
    flatMap { value ->
        if (value != null) value.asSuccess()
        else AppError.NotFound("Treino não encontrado").asFailure()
    }