package dev.rafael.server

import dev.rafael.server.db.DatabaseFactory
import dev.rafael.server.features.exercise.routes.exerciseRoutes
import dev.rafael.server.features.exercise.services.ExerciseService
import dev.rafael.server.features.checkin.routes.checkInRoutes
import dev.rafael.server.features.checkin.services.CheckInService
import dev.rafael.server.features.group.routes.groupMembershipRoutes
import dev.rafael.server.features.group.routes.groupRoutes
import dev.rafael.server.features.group.services.GroupMembershipService
import dev.rafael.server.features.group.services.GroupService
import dev.rafael.server.features.profile.routes.profileRoutes
import dev.rafael.server.features.profile.services.ProfileService
import dev.rafael.server.features.program.routes.programRoutes
import dev.rafael.server.features.program.services.ProgramService
import dev.rafael.server.features.session.routes.sessionRoutes
import dev.rafael.server.features.session.services.SessionService
import dev.rafael.server.features.stats.StatsService
import dev.rafael.server.features.stats.AchievementService
import dev.rafael.server.features.stats.statsRoutes
import dev.rafael.server.features.user.routes.userRoutes
import dev.rafael.server.features.friendship.routes.friendshipRoutes
import dev.rafael.server.features.friendship.services.FriendshipService
import dev.rafael.server.features.friendship.services.LimitadorDeResgate
import dev.rafael.server.features.notificacao.db.DeviceTokenRepository
import dev.rafael.server.features.notificacao.routes.deviceRoutes
import dev.rafael.server.features.notificacao.routes.notificacaoRoutes
import dev.rafael.server.features.notificacao.services.NotificacaoService
import dev.rafael.server.features.user.services.PublicProfileService
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.features.workout.routes.workoutRoutes
import dev.rafael.server.features.workout.services.WorkoutService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.get
import org.koin.ktor.ext.inject     // ou get



@Serializable
data class HealthResponse(
    val status: String,
    val service: String = "fitjourney-server",
    val db: String,
)



fun Application.configureRouting() {
    val userService = get<UserService>()
    val publicProfileService = get<PublicProfileService>()
    val friendshipService = get<FriendshipService>()
    val limitadorDeResgate = get<LimitadorDeResgate>()
    val deviceTokens = get<DeviceTokenRepository>()
    val notificacoes = get<NotificacaoService>()
    val profileService = get<ProfileService>()
    val exerciseService = get<ExerciseService>()
    val workoutService = get<WorkoutService>()
    val programService = get<ProgramService>()
    val sessionService = get<SessionService>()
    val statsService = get<StatsService>()
    val achievementService = get<AchievementService>()
    val groupService = get<GroupService>()
    val groupMembershipService = get<GroupMembershipService>()
    val checkInService = get<CheckInService>()
    val mediaDir = resolveMediaDir(
        environment.config.propertyOrNull("media.dir")?.getString() ?: "gifs_exercicios",
    )


    routing {
        // Mídia dos exercícios (png/mp4), pública, SÓ DEV. Ver MediaRoutes.kt.
        staticFiles("/media", mediaDir)

        userRoutes(userService, publicProfileService, limitadorDeResgate)
        friendshipRoutes(friendshipService)
        deviceRoutes(userService, deviceTokens)
        notificacaoRoutes(notificacoes)
        profileRoutes(profileService)
        exerciseRoutes(exerciseService, profileService)
        workoutRoutes(workoutService, userService, profileService, programService)
        programRoutes(userService, profileService, programService)
        sessionRoutes(sessionService)
        statsRoutes(statsService, achievementService)
        groupRoutes(groupService)
        groupMembershipRoutes(groupMembershipService)
        checkInRoutes(checkInService)

        get("/health") {
            val dbOk = DatabaseFactory.isHealthy()
            call.respond(
                status = if (dbOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                message = HealthResponse(
                    status = if (dbOk) "UP" else "DEGRADED",
                    db = if (dbOk) "UP" else "DOWN",
                ),
            )
        }
    }
}