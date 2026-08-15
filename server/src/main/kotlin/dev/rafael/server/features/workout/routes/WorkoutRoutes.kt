package dev.rafael.server.features.workout.routes

import dev.rafael.contract.workout.WorkoutDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.profile.services.ProfileService
import dev.rafael.server.features.program.services.ProgramService
import dev.rafael.server.features.user.services.UserService
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

fun Route.workoutRoutes(
    service: WorkoutService,
    userService: UserService,
    profileService: ProfileService,
    programService: ProgramService,
) {
    authenticate(FIREBASE_AUTH) {

        post("/workouts") {
            val p = call.principal<FirebaseUser>()!!
            val dto = call.receive<WorkoutDto>()
            // ARCH #27: todo treino vive dentro de um programa — gate composto aqui
            // (na rota), não no WorkoutService, pra não criar ciclo workout→program.
            val programId = dto.programId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            val result = if (programId == null) {
                AppError.Validation("Treino precisa pertencer a um programa (programId inválido ou ausente)").asFailure()
            } else {
                userService.findOrCreate(p.uid, p.email).flatMap { user ->
                    // GATE PREMIUM (ARCH #25): adicionar treino a programa IA exige premium.
                    programService.requireEditable(user.id, programId, user.isPremium).flatMap {
                        // G.2: usa o dia escolhido (dto.dayOfWeek) validando colisão, ou 1º dia livre.
                        programService.resolveNewWorkoutDay(user.id, programId, dto.dayOfWeek).flatMap { day ->
                            service.create(p.uid, p.email, dto, programId, dayOfWeek = day)
                        }
                    }
                }
            }
            call.respondResult(result)
        }

        get("/workouts") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.list(p.uid, p.email))
        }

        get("/workouts/{id}") {
            val p = call.principal<FirebaseUser>()!!
            val id = call.workoutIdParam() ?: return@get call.respondResult(notFound<WorkoutDto>())
            // GATE PREMIUM DE LEITURA (ARCH #23): sem isto, o blur do GET /programs escondia os
            // dias trancados na lista mas este endpoint entregava o conteúdo inteiro a quem
            // pedisse pelo id — inclusive à própria Home, que busca o treino do dia só para
            // estimar a duração e acabava gravando `locked = false` no banco local.
            val result = userService.findOrCreate(p.uid, p.email).flatMap { user ->
                service.get(p.uid, p.email, id).notFoundIfNull().flatMap { treino ->
                    val pid = treino.programId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                    // Treino avulso (sem programa) não tem trava de entitlement.
                    if (pid == null) treino.asSuccess()
                    else programService.requireReadable(user.id, pid, id, user.isPremium)
                        .flatMap { treino.asSuccess() }
                }
            }
            call.respondResult(result)
        }

        put("/workouts/{id}") {
            val p = call.principal<FirebaseUser>()!!
            val id = call.workoutIdParam() ?: return@put call.respondResult(notFound<WorkoutDto>())
            val dto = call.receive<WorkoutDto>()
            // GATE PREMIUM (ARCH #25): editar workout de programa origin=AI exige premium.
            // Programa manual (origin=MANUAL) é livre. Gate na rota (ARCH #18).
            val result = userService.findOrCreate(p.uid, p.email).flatMap { user ->
                service.get(p.uid, p.email, id).notFoundIfNull().flatMap { existing ->
                    val pid = existing.programId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                    if (pid == null) {
                        service.update(p.uid, p.email, id, dto).notFoundIfNull()
                    } else {
                        programService.requireEditable(user.id, pid, user.isPremium).flatMap {
                            service.update(p.uid, p.email, id, dto).notFoundIfNull()
                        }
                    }
                }
            }
            call.respondResult(result)
        }

        delete("/workouts/{id}") {
            val p = call.principal<FirebaseUser>()!!
            val id = call.workoutIdParam() ?: return@delete call.respondResult(notFound<Unit>())
            // GATE PREMIUM (ARCH #25): remover treino de programa IA exige premium.
            val result = userService.findOrCreate(p.uid, p.email).flatMap { user ->
                service.get(p.uid, p.email, id).notFoundIfNull().flatMap { existing ->
                    val pid = existing.programId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                    val gate: AppResult<Unit> =
                        if (pid == null) Unit.asSuccess()
                        else programService.requireEditable(user.id, pid, user.isPremium)
                    gate.flatMap {
                        service.delete(p.uid, p.email, id).flatMap { deleted ->
                            if (deleted) Unit.asSuccess()
                            else AppError.NotFound("Treino não encontrado").asFailure()
                        }
                    }
                }
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