package dev.rafael.features.stats.data

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.stats.ExercicioDetalheDto
import dev.rafael.core.result.AppResult
import dev.rafael.features.stats.domain.DetalheDeExercicio

/** Repassa pra API, so trocando o idioma atual pela tag — sem cache, ver KDoc da interface. */
class DetalheDeExercicioRepository(
    private val api: DetalheDeExercicioApi,
    private val idiomaAtual: () -> Idioma,
) : DetalheDeExercicio {
    override suspend fun buscar(programId: String, exercicioId: String): AppResult<ExercicioDetalheDto> =
        api.get(idiomaAtual().tag, programId, exercicioId)
}
