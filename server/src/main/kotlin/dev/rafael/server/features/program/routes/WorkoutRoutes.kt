package dev.rafael.server.features.program.routes

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.contract.program.CreateManualProgramRequest
import dev.rafael.contract.program.RenameProgramRequest
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.features.profile.services.ProfileService
import dev.rafael.server.features.program.services.ProgramLimits
import dev.rafael.server.features.program.services.ProgramService
import dev.rafael.server.features.user.services.UserService
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

// Tetos por plano: ver ProgramLimits (ARCH #26).

fun Route.programRoutes(
    userService: UserService,
    profileService: ProfileService,
    programService: ProgramService,
) {
    authenticate(FIREBASE_AUTH) {
        post("/programs/generate") {
            val principal = call.principal<FirebaseUser>()!!

            val result = userService.findOrCreate(principal.uid, principal.email).flatMap { user ->
                // 1. GATE DE SAÚDE (§3.2 [INV]) — pré-condição pra gerar, vale p/ todos.
                when (val pr = profileService.getProfile(principal.uid, principal.email)) {
                    is AppResult.Failure ->
                        if (pr.error is AppError.NotFound)
                            AppError.Forbidden(
                                "Complete a avaliação de saúde antes de gerar treinos.",
                                ErrorCodes.HEALTH_GATE_REQUIRED,
                            ).asFailure()
                        else pr
                    is AppResult.Success -> {
                        val profile = pr.value
                        if (profile.health?.gateSatisfied != true) {
                            AppError.Forbidden(
                                "Complete a avaliação de saúde antes de gerar treinos.",
                                ErrorCodes.HEALTH_GATE_REQUIRED,
                            ).asFailure()
                        } else {
                            // 2. GATE POR TETO (ARCH #26) — política pura (ProgramLimits).
                            programService.counts(user.id).flatMap { c ->
                                ProgramLimits.gate(c, user.isPremium, ProgramLimits.Kind.AI)
                                    .flatMap { programService.generate(user.id, profile) }
                            }
                        }
                    }
                }
            }
            call.respondResult(result)   // 200 OK
        }

        get("/programs") {
            val principal = call.principal<FirebaseUser>()!!
            val result = userService.findOrCreate(principal.uid, principal.email)
                .flatMap { user -> programService.listForUser(user.id) }
            call.respondResult(result)
        }

        post("/programs") {
            val principal = call.principal<FirebaseUser>()!!
            val body = call.receive<CreateManualProgramRequest>()
            val result = userService.findOrCreate(principal.uid, principal.email).flatMap { user ->
                // GATE POR TETO (ARCH #26) — política pura (ProgramLimits).
                programService.counts(user.id).flatMap { c ->
                    ProgramLimits.gate(c, user.isPremium, ProgramLimits.Kind.MANUAL)
                        .flatMap { programService.createManual(user.id, body.name) }
                }
            }
            call.respondResult(result)
        }

        put("/programs/{id}") {
            val principal = call.principal<FirebaseUser>()!!
            val programId = call.programIdParam()
            val body = call.receive<RenameProgramRequest>()
            val result = if (programId == null) {
                AppError.Validation("id de programa inválido").asFailure()
            } else {
                userService.findOrCreate(principal.uid, principal.email)
                    .flatMap { user -> programService.rename(user.id, programId, body.name) }
            }
            call.respondResult(result)
        }

        delete("/programs/{id}") {
            val principal = call.principal<FirebaseUser>()!!
            val programId = call.programIdParam()
            val result = if (programId == null) {
                AppError.Validation("id de programa inválido").asFailure()
            } else {
                userService.findOrCreate(principal.uid, principal.email).flatMap { user ->
                    programService.delete(user.id, programId).flatMap { deleted ->
                        if (deleted) Unit.asSuccess()
                        else AppError.NotFound("Programa não encontrado").asFailure()
                    }
                }
            }
            call.respondResult(result)
        }
    }
}

private fun ApplicationCall.programIdParam(): Uuid? =
    parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
