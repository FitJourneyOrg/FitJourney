package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class ExerciseRepositoryImpl(
    private val remote: ExerciseRemoteDataSource,
    private val local: ExerciseLocalDataSource,
) : ExerciseRepository {

    override fun observeExercises(category: ExerciseCategory?): Flow<List<Exercise>> {
        val rows = if (category == null) local.observeAll()
        else local.observeByCategory(category.name)
        return rows.map { list -> list.mapNotNull { it.toDomainOrNull() } }
    }

    // Último download bem-sucedido do catálogo. Singleton no Koin → sobrevive à navegação.
    private var sincronizadoEm: Long? = null

    private val fresco: Boolean
        get() = sincronizadoEm?.let { Clock.System.now().toEpochMilliseconds() - it < TTL_MS } == true

    /**
     * Baixa o catálogo inteiro e substitui o local.
     *
     * TTL de 24h (não 5 min como o resto): o catálogo é SEMIESTÁTICO — vem de migration no
     * servidor, então só muda em deploy. Antes não havia TTL e a lista chamava `refresh()` no
     * `init`, ou seja, 965 exercícios baixados a cada entrada na aba Exercícios, mais uma vez
     * no boot pela Splash.
     *
     * Catálogo local vazio ignora o TTL: sem dado não há o que preservar.
     *
     * @param forcar pull-to-refresh do usuário — ele pediu, então vai.
     */
    override suspend fun refresh(forcar: Boolean): AppResult<Unit> {
        if (!forcar && fresco && !local.isEmpty()) return AppResult.Success(Unit)
        return httpResult { local.replaceAll(remote.getExercises(category = null)) }
            .also { if (it is AppResult.Success) sincronizadoEm = Clock.System.now().toEpochMilliseconds() }
    }

    override suspend fun alternatives(exerciseId: String): AppResult<List<Exercise>> =
        httpResult { remote.getAlternatives(exerciseId).map { it.toDomain() } }

    /**
     * CACHE-FIRST ([REGRA] ARCH #30). Antes isto baixava o CATÁLOGO INTEIRO (965 exercícios)
     * para filtrar um id em memória — a cada abertura de tela de detalhe, e sem nem olhar o
     * banco local, que já tinha o mesmo dado desde o boot.
     *
     * Só vai à rede se o exercício não estiver local (catálogo desatualizado após um deploy
     * que adicionou exercícios novos).
     */
    override suspend fun getDetail(exerciseId: String): AppResult<Exercise> {
        local.readById(exerciseId)?.toDomainOrNull()?.let { return AppResult.Success(it) }

        return when (val r = refresh(forcar = true)) {
            is AppResult.Success ->
                local.readById(exerciseId)?.toDomainOrNull()
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(AppError.NotFound("Exercício não encontrado"))
            is AppResult.Failure -> AppResult.Failure(r.error)
        }
    }

    private companion object {
        /** 24h: catálogo vem de migration, muda só em deploy. */
        const val TTL_MS = 24 * 60 * 60 * 1000L
    }
}