package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.db.MesocicloRepository
import dev.rafael.server.features.exercise.db.ProgressaoRepository
import kotlin.uuid.Uuid

/**
 * Repositório de progressão em memória, com a **chave única de verdade**.
 *
 * A chave do mapa é `(usuário, exercício)` — a mesma do `UNIQUE` proposto em
 * `docs/arch/motor-v3-progressao-schema.md`. Sem isso o teste de idempotência não provaria nada:
 * um dublê que acumula linhas faria "gravar duas vezes" parecer inofensivo por construção.
 *
 * Escrito à mão (sem biblioteca de mock): os targets iOS estão declarados e MockK é só JVM.
 */
class FakeProgressaoRepository : ProgressaoRepository {

    private val estados = mutableMapOf<Pair<Uuid, Uuid>, Progressao.Estado>()

    /** Liga o caminho de falha: toda chamada devolve `AppError.Unexpected`, e NADA é gravado. */
    var falhar: Boolean = false

    val linhas: Int get() = estados.size

    /** Eventos por `(usuário, exercício, sessão)` — a MESMA chave da PK de `progression_event` (D-2). */
    private val eventos = linkedMapOf<Triple<Uuid, Uuid, Uuid>, AncoraDoIniciante.Evento>()

    override suspend fun buscar(usuarioId: Uuid, exercicioId: Uuid): AppResult<Progressao.Estado?> {
        if (falhar) return caiu()
        return estados[usuarioId to exercicioId].asSuccess()
    }

    override suspend fun salvar(usuarioId: Uuid, exercicioId: Uuid, estado: Progressao.Estado): AppResult<Unit> {
        if (falhar) return caiu()
        estados[usuarioId to exercicioId] = estado   // upsert: a chave única substitui, não duplica
        return Unit.asSuccess()
    }

    override suspend fun registrar(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: Progressao.Estado,
        evento: AncoraDoIniciante.Evento,
    ): AppResult<Boolean> {
        if (falhar) return caiu()
        val chave = Triple(usuarioId, exercicioId, sessaoId)
        if (chave in eventos) return false.asSuccess()   // sessão já registrada: NÃO regrava o estado
        eventos[chave] = evento
        estados[usuarioId to exercicioId] = estado
        return true.asSuccess()
    }

    override suspend fun historico(usuarioId: Uuid, exercicioId: Uuid, limite: Int): AppResult<List<AncoraDoIniciante.Evento>> {
        if (falhar) return caiu()
        if (limite <= 0) return AppError.Unexpected("limite deve ser positivo", IllegalArgumentException("limite")).asFailure()
        return eventos.filterKeys { it.first == usuarioId && it.second == exercicioId }.values.toList().takeLast(limite).asSuccess()
    }

    override suspend fun todos(usuarioId: Uuid): AppResult<Map<Uuid, Progressao.Estado>> {
        if (falhar) return caiu()
        return estados.filterKeys { it.first == usuarioId }.mapKeys { it.key.second }.asSuccess()
    }

    private fun <T> caiu(): AppResult<T> =
        AppError.Unexpected("falha simulada do banco", RuntimeException("falha simulada")).asFailure()
}

/** Mesociclo por pessoa, em memória. Mesma convenção de falha de [FakeProgressaoRepository]. */
class FakeMesocicloRepository : MesocicloRepository {

    private val porUsuario = mutableMapOf<Uuid, Mesociclo>()
    var falhar: Boolean = false

    override suspend fun buscar(usuarioId: Uuid): AppResult<Mesociclo?> {
        if (falhar) return caiu()
        return porUsuario[usuarioId].asSuccess()
    }

    override suspend fun salvar(usuarioId: Uuid, mesociclo: Mesociclo): AppResult<Unit> {
        if (falhar) return caiu()
        porUsuario[usuarioId] = mesociclo
        return Unit.asSuccess()
    }

    private fun <T> caiu(): AppResult<T> =
        AppError.Unexpected("falha simulada do banco", RuntimeException("falha simulada")).asFailure()
}
