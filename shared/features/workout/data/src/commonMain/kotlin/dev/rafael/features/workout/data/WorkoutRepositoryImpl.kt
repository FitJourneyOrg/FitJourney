package dev.rafael.features.workout.data

import dev.rafael.core.network.TokenProvider
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.features.workout.domain.model.Workout
import dev.rafael.features.workout.domain.model.WorkoutSummary
import dev.rafael.features.workout.domain.repository.WorkoutRepository
import kotlin.time.Clock

class WorkoutRepositoryImpl(
    private val remote: WorkoutDataSource,
    private val local: WorkoutLocalDataSource,
    private val tokenProvider: TokenProvider,
) : WorkoutRepository {

    // Momento do último sync bem-sucedido, POR TREINO. Singleton no Koin, então sobrevive à
    // navegação. Ausente = cache sujo/inexistente → próximo get() vai à rede.
    private val sincronizadoEm = mutableMapOf<String, Long>()

    // Dono do cache. Trocar de conta descarta tudo: sem isto o usuário novo reaproveitaria os
    // carimbos do anterior e leria o treino dele do banco como se estivesse fresco.
    private var donoDoCache: String? = null

    private fun fresco(id: String): Boolean =
        sincronizadoEm[id]?.let { Clock.System.now().toEpochMilliseconds() - it < TTL_MS } == true

    override suspend fun list(): AppResult<List<WorkoutSummary>> =
        httpResult { remote.list().map { it.toDomain() } }

    /**
     * CACHE-FIRST ([REGRA] ARCH #30: a tela lê do banco, sync é de fundo).
     *
     * Antes isto era network-first — apesar do comentário dizer "offline-first", ia à rede em
     * TODA abertura e só usava o cache se a rede falhasse. O efeito no log do servidor: um
     * `GET /workouts/{id}` por navegação, mais um 403 a cada vez que a Home (viva no back
     * stack) reagia ao Flow e buscava o treino do dia trancado.
     */
    override suspend fun get(id: String): AppResult<Workout> {
        val dono = tokenProvider.currentUid()
        if (dono != donoDoCache) {
            sincronizadoEm.clear()
            donoDoCache = dono
        }
        if (fresco(id)) {
            local.read(id)?.let { return it.toDomain().asSuccess() }
        }
        return refresh(id)
    }

    /** Vai à rede e regrava o local. Falha de TRANSPORTE cai no cache (p/ treinar offline). */
    private suspend fun refresh(id: String): AppResult<Workout> =
        when (val net = httpResult { remote.get(id) }) {
            is AppResult.Success -> {
                local.save(id, net.value)
                sincronizadoEm[id] = Clock.System.now().toEpochMilliseconds()
                donoDoCache = tokenProvider.currentUid()
                net.value.toDomain().asSuccess()
            }
            is AppResult.Failure -> {
                // Ver ProgramRepositoryImpl: só transporte cai no cache; resposta do servidor, não.
                val cached = if (net.error is AppError.Connection) local.read(id) else null
                cached?.toDomain()?.asSuccess() ?: net.error.asFailure()
            }
        }

    /** Muda o treino → o carimbo dele morre, e o próximo get() busca o estado novo. */
    private fun invalidar(id: String) {
        sincronizadoEm.remove(id)
    }

    override suspend fun create(workout: Workout): AppResult<Workout> =
        httpResult { remote.create(workout.toDto()).toDomain() }

    override suspend fun update(id: String, workout: Workout): AppResult<Workout> =
        httpResult { remote.update(id, workout.toDto()).toDomain() }.also { invalidar(id) }

    override suspend fun delete(id: String): AppResult<Unit> =
        httpResult { remote.delete(id) }.also { invalidar(id) }

    private companion object {
        /** Mesma janela do ProgramRepositoryImpl — política de frescor única no app. */
        const val TTL_MS = 5 * 60 * 1000L
    }
}
