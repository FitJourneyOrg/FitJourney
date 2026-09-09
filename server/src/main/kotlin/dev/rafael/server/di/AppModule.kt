package dev.rafael.server.di

import com.google.firebase.auth.FirebaseAuth
import dev.rafael.server.auth.FirebaseTokenDecoder
import dev.rafael.server.auth.TokenDecoder
import dev.rafael.server.features.profile.db.ProfileRepository
import dev.rafael.server.features.profile.db.ProfileRepositoryImpl
import dev.rafael.server.features.profile.services.ProfileService
import dev.rafael.server.auth.TokenVerifier
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.db.UserRepositoryImpl
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.features.exercise.db.ExerciseRepository
import dev.rafael.server.features.exercise.db.ExerciseRepositoryImpl
import dev.rafael.server.features.exercise.engine.DeterministicWorkoutGenerator
import dev.rafael.server.features.exercise.engine.ExercisePreFilter
import dev.rafael.server.features.exercise.engine.StructureEngine
import dev.rafael.server.features.exercise.engine.WorkoutGenerator
import dev.rafael.server.features.exercise.services.ExerciseService
import dev.rafael.server.features.program.db.ProgramRepository
import dev.rafael.server.features.program.db.ProgramRepositoryImpl
import dev.rafael.server.features.program.services.ProgramService
import dev.rafael.server.features.workout.db.WorkoutRepository
import dev.rafael.server.features.workout.db.WorkoutRepositoryImpl
import dev.rafael.server.features.workout.services.WorkoutService
import dev.rafael.server.features.session.db.SessionRepository
import dev.rafael.server.features.session.db.SessionRepositoryImpl
import dev.rafael.server.features.session.services.SessionService
import dev.rafael.server.features.stats.AchievementService
import dev.rafael.server.features.user.services.PublicProfileService
import dev.rafael.core.result.map
import dev.rafael.server.features.friendship.db.FriendshipRepository
import dev.rafael.server.features.friendship.db.FriendshipRepositoryImpl
import dev.rafael.server.features.friendship.services.FriendshipService
import dev.rafael.server.features.friendship.services.LimitadorDeResgate
import dev.rafael.server.features.notificacao.db.DeviceTokenRepository
import dev.rafael.server.features.notificacao.db.DeviceTokenRepositoryImpl
import dev.rafael.server.features.notificacao.db.NotificationRepository
import dev.rafael.server.features.notificacao.db.NotificationRepositoryImpl
import dev.rafael.server.features.notificacao.services.NotificacaoService
import dev.rafael.server.features.notificacao.services.Aviso
import dev.rafael.server.features.notificacao.services.Notificador
import dev.rafael.server.features.notificacao.services.NotificadorFcm
import dev.rafael.server.features.stats.StatsService
import dev.rafael.server.features.stats.db.AchievementRepository
import dev.rafael.server.features.stats.db.AchievementRepositoryImpl
import org.koin.dsl.module
import kotlin.uuid.Uuid

val appModule = module {
    single<UserRepository> { UserRepositoryImpl() }
    single { UserService(get()) }
    // userService + userRepo + statsService + achievementRepo (C.1, #34 + emenda 9.3-A)
    single {
        PublicProfileService(
            userService = get(),
            users = get(),
            // Duas portas estreitas: o perfil pede XP+nível e a relação social, nunca as classes
            // inteiras. É o que mantém `user` sem importar `stats` nem `friendship` (ver KDoc).
            gamificacaoDe = { userId -> get<StatsService>().gamificacaoDe(userId) },
            achievements = get(),
            relacaoCom = { dono, quemPede ->
                get<FriendshipService>().relacaoEntre(dono, quemPede).map { r ->
                    PublicProfileService.Relacao(r.status, r.meBloqueou)
                }
            },
        )
    }

    // ---- Amizades (ARCH #35) ----
    single<FriendshipRepository> { FriendshipRepositoryImpl() }
    single {
        FriendshipService(
            userService = get(),
            users = get(),
            repository = get(),
            // Porta estreita para o push: `friendship` não importa `notificacao`, recebe uma
            // função. O default não faz nada — o grafo funciona sem notificação (ver KDoc).
            // Passa pelo NotificacaoService, não pelo Notificador direto: ele GRAVA antes de
            // despachar, e é a gravação que faz a notificação sobreviver a push que não chega.
            avisar = { destinatario, nome, quemPediu ->
                get<NotificacaoService>().avisar(destinatario, Aviso.pedidoDeAmizade(nome, quemPediu))
            },
        )
    }
    // Singleton de propósito: a contagem de tentativas vive em MEMÓRIA (ver KDoc do limitador).
    single { LimitadorDeResgate() }

    // ---- Notificação (F.1) ----
    single<DeviceTokenRepository> { DeviceTokenRepositoryImpl() }
    single<NotificationRepository> { NotificationRepositoryImpl() }
    single<Notificador> { NotificadorFcm(get()) }
    single { NotificacaoService(get(), get(), get()) }

    // ---- laço diário do grupo (fatia F: 10.6 + as duas emendas de 2026-09-07) ----
    //
    // Mora em `server.avisos`, fora de `features/`, porque atravessa três delas: lê membros
    // (`group`), lê denúncias (`checkin`) e escreve notificação. Mesma escolha do `PurgaDeMidia`.
    single<dev.rafael.server.avisos.AvisosDiariosRepository> {
        dev.rafael.server.avisos.AvisosDiariosRepositoryImpl()
    }
    single {
        dev.rafael.server.avisos.AvisosDoDia(
            repository = get(),
            avisar = object : dev.rafael.server.avisos.AvisosDoDia.EnviarAviso {
                override suspend fun entradasDoDia(
                    destinatario: Uuid,
                    grupo: String,
                    groupId: Uuid,
                    quantas: Int,
                    souOCriador: Boolean,
                ) = get<NotificacaoService>()
                    .avisar(destinatario, Aviso.entradasDoDia(grupo, groupId, quantas, souOCriador))

                override suspend fun filaParada(
                    admin: Uuid,
                    grupo: String,
                    groupId: Uuid,
                    casos: Int,
                    dias: Int,
                ) = get<NotificacaoService>()
                    .avisar(admin, Aviso.filaParada(grupo, groupId, casos, dias))
            },
        )
    }

    // Auth: FirebaseAuth.getInstance() só é válido após FirebaseAdmin.init() (roda no boot, antes).
    single { FirebaseAuth.getInstance() }
    single<TokenDecoder> { FirebaseTokenDecoder(get()) }
    single { TokenVerifier(get()) }

    // Profile (Fase 3)
    single<ProfileRepository> { ProfileRepositoryImpl() }
    single { ProfileService(get(), get()) }


    single<ExerciseRepository> { ExerciseRepositoryImpl() }
    single { ExerciseService(get(), get()) }   // repo + ExercisePreFilter (registrado abaixo)


    single<WorkoutRepository> { WorkoutRepositoryImpl() }        // <- ESTA linha sumiu
    single { WorkoutService(get(), get(), get(), get()) }


    // Motor (Fatia F) — as três peças + a interface.
    single { StructureEngine() }
    single { ExercisePreFilter() }
    single<WorkoutGenerator> { DeterministicWorkoutGenerator(get(), get()) }

    // Persistência + orquestração (G.1).
    single<ProgramRepository> { ProgramRepositoryImpl() }
    single { ProgramService(get(), get()) }

    // Sessão de treino (Fase 5 — execução).
    single<SessionRepository> { SessionRepositoryImpl() }
    single { SessionService(get(), get()) }   // userService + repo
    single { StatsService(get(), get(), get()) }   // userService + sessionRepo + programService (ARCH #16)

    // Conquistas (ARCH #16). Reusa o StatsService em vez de recalcular sessoes/streak/nivel:
    // duas contas do mesmo numero acabariam divergindo.
    single<AchievementRepository> { AchievementRepositoryImpl() }
    single { AchievementService(get(), get(), get()) }   // userService + statsService + repo

    // Grupos (Fase 6, ARCH #33). Sem coluna `status`: o estado sai do GroupPolicy a cada leitura.
    single<dev.rafael.server.features.group.db.GroupRepository> {
        dev.rafael.server.features.group.db.GroupRepositoryImpl()
    }
    // A porta estreita entre grupo e check-in: o detalhe do grupo precisa saber se JÁ FIZ hoje
    // para não oferecer um botão que vai ser recusado. Só esta pergunta atravessa a fronteira.
    single<dev.rafael.server.features.group.services.CheckInDeHoje> {
        val checkIns = get<dev.rafael.server.features.checkin.db.CheckInRepository>()
        dev.rafael.server.features.group.services.CheckInDeHoje { grupo, usuario, dia ->
            (checkIns.doDia(grupo, usuario, dia) as? dev.rafael.core.result.AppResult.Success)?.value
        }
    }
    single { dev.rafael.server.features.group.services.GroupService(get(), get(), get()) }
    single { dev.rafael.server.features.group.services.GroupMembershipService(get(), get()) }

    // Check-in (fatia B). O `ArmazenamentoDeMidia` vem do `midiaModule`, que é separado porque
    // precisa da pasta resolvida a partir da configuração do Ktor.
    // Comentários e reações (E.1). Repositório próprio: o CheckInRepository já tem nove
    // operações, e um repositório que cresce sem parar vira o lugar onde nada é encontrável.
    single<dev.rafael.server.features.checkin.db.SocialRepository> {
        dev.rafael.server.features.checkin.db.SocialRepositoryImpl()
    }
    single {
        dev.rafael.server.features.checkin.services.SocialService(
            get(), get(), get(), get(),
            clock = kotlin.time.Clock.System,
            // Porta estreita para o push (fatia F): `checkin` não importa `notificacao`. Passa pelo
            // NotificacaoService e não pelo Notificador direto — é ele que GRAVA antes de
            // despachar, e a gravação é o que faz a notificação sobreviver a push que não chega.
            avisarComentario = { dono, nome, texto, groupId, checkInId ->
                get<NotificacaoService>().avisar(
                    dono,
                    Aviso.comentarioNoMeuCheckIn(nome, texto, groupId, checkInId),
                )
            },
        )
    }

    // Denúncia e moderação (E.2). Repositório próprio pelo mesmo motivo do social — e porque as
    // duas tabelas dele têm políticas de integridade OPOSTAS (ver a V45).
    single<dev.rafael.server.features.checkin.db.ModeracaoRepository> {
        dev.rafael.server.features.checkin.db.ModeracaoRepositoryImpl()
    }
    single {
        dev.rafael.server.features.checkin.services.ModeracaoService(
            get(), get(), get(), get(), get(),
            clock = kotlin.time.Clock.System,
            avisos = dev.rafael.server.features.checkin.services.ModeracaoService.AvisosDeModeracao(
                denunciaContraMim = { dono, groupId, ehComentario ->
                    get<NotificacaoService>().avisar(dono, Aviso.denunciaContraMim(groupId, ehComentario))
                },
                novaDenunciaNoGrupo = { admin, nome, groupId ->
                    get<NotificacaoService>().avisar(admin, Aviso.novaDenunciaNoGrupo(nome, groupId))
                },
                checkInInvalidado = { dono, nome, groupId ->
                    get<NotificacaoService>().avisar(dono, Aviso.checkInInvalidado(nome, groupId))
                },
            ),
        )
    }

    single<dev.rafael.server.features.checkin.db.CheckInRepository> {
        dev.rafael.server.features.checkin.db.CheckInRepositoryImpl()
    }
    single {
        // userService + groupRepository + checkInRepository + armazenamento
        dev.rafael.server.features.checkin.services.CheckInService(get(), get(), get(), get(), get())
    }
}
