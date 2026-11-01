package dev.rafael.server.features.program.db

import dev.rafael.contract.program.ProgramAvisoDto
import kotlinx.serialization.json.Json

private val json = Json

/**
 * Lista de avisos <-> JSON (coluna `programs.avisos`, V73). Mesmo padrão de
 * `FocusAreasJson.kt` (profile) -- vazio em branco, nunca erro em linha legada.
 */
fun List<ProgramAvisoDto>.toJson(): String = json.encodeToString(this)

fun String.toAvisos(): List<ProgramAvisoDto> =
    if (isBlank()) emptyList() else json.decodeFromString(this)

/** Chaves de `TargetMuscle` <-> JSON (coluna `programs.grupamentos_reduzidos_pelo_foco`, V73). */
fun List<String>.gruposReduzidosToJson(): String = json.encodeToString(this)

fun String.toGruposReduzidos(): List<String> =
    if (isBlank()) emptyList() else json.decodeFromString(this)
