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
    private val estadosRepeticao = mutableMapOf<Pair<Uuid, Uuid>, ProgressaoPorRepeticao.Estado>()
    private val estadosNivel = mutableMapOf<Pair<Uuid, Uuid>, ProgressaoPorNivel.Estado>()

    /** Liga o caminho de falha: toda chamada devolve `AppError.Unexpected`, e NADA é gravado. */
    var falhar: Boolean = false

    val linhas: Int get() = estados.size
    val linhasRepeticao: Int get() = estadosRepeticao.size
    val linhasNivel: Int get() = estadosNivel.size

    /** Eventos por `(usuário, exercício, sessão)` — a MESMA chave da PK de `progression_event` (D-2). */
    private val eventos = linkedMapOf<Triple<Uuid, Uuid, Uuid>, AncoraDoIniciante.Evento>()

    /** Mesociclo de cada evento (V71) — mesma chave de [eventos], mapa paralelo pra não reabrir a tupla. */
    private val mesociclos = mutableMapOf<Triple<Uuid, Uuid, Uuid>, Int>()

    /** Snapshot do 1RM de cada evento (V72) — mesma chave de [eventos], mesmo motivo do mapa paralelo. */
    private val umRms = mutableMapOf<Triple<Uuid, Uuid, Uuid>, Double?>()

    override suspend fun buscar(usuarioId: Uuid, exercicioId: Uuid): AppResult<Progressao.Estado?> {
        if (falhar) return caiu()
        return estados[usuarioId to exercicioId].asSuccess()
    }

    override suspend fun salvar(usuarioId: Uuid, exercicioId: Uuid, estado: Progressao.Estado): AppResult<Unit> {
        if (falhar) return caiu()
        estados[usuarioId to exercicioId] = estado   // upsert: a chave única substitui, não duplica
        return Unit.asSuccess()
    }

    override suspend fun buscarPorRepeticao(usuarioId: Uuid, exercicioId: Uuid): AppResult<ProgressaoPorRepeticao.Estado?> {
        if (falhar) return caiu()
        return estadosRepeticao[usuarioId to exercicioId].asSuccess()
    }

    override suspend fun buscarPorNivel(usuarioId: Uuid, exercicioId: Uuid): AppResult<ProgressaoPorNivel.Estado?> {
        if (falhar) return caiu()
        return estadosNivel[usuarioId to exercicioId].asSuccess()
    }

    override suspend fun registrar(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: Progressao.Estado,
        evento: AncoraDoIniciante.Evento,
        mesociclo: Int,
    ): AppResult<Boolean> {
        if (falhar) return caiu()
        val chave = Triple(usuarioId, exercicioId, sessaoId)
        if (chave in eventos) return false.asSuccess()   // sessão já registrada: NÃO regrava o estado
        eventos[chave] = evento
        mesociclos[chave] = mesociclo
        umRms[chave] = estado.umRm
        estados[usuarioId to exercicioId] = estado
        return true.asSuccess()
    }

    override suspend fun registrarPorRepeticao(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: ProgressaoPorRepeticao.Estado,
        evento: AncoraDoIniciante.Evento,
        mesociclo: Int,
    ): AppResult<Boolean> {
        if (falhar) return caiu()
        val chave = Triple(usuarioId, exercicioId, sessaoId)
        if (chave in eventos) return false.asSuccess()
        eventos[chave] = evento
        mesociclos[chave] = mesociclo
        umRms[chave] = estado.sinalForca   // ancoras-avancado-bodyweight: pode nao ser null
        estadosRepeticao[usuarioId to exercicioId] = estado
        return true.asSuccess()
    }

    override suspend fun registrarPorNivel(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: ProgressaoPorNivel.Estado,
        evento: AncoraDoIniciante.Evento,
        mesociclo: Int,
    ): AppResult<Boolean> {
        if (falhar) return caiu()
        val chave = Triple(usuarioId, exercicioId, sessaoId)
        if (chave in eventos) return false.asSuccess()
        eventos[chave] = evento
        mesociclos[chave] = mesociclo
        umRms[chave] = null   // eixo de nivel nao tem 1RM/sinal de forca -- fora de escopo
        estadosNivel[usuarioId to exercicioId] = estado
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

    override suspend fun exerciciosComEventoNoMesociclo(usuarioId: Uuid, mesociclo: Int): AppResult<List<Uuid>> {
        if (falhar) return caiu()
        return mesociclos.entries
            .filter { (chave, m) -> chave.first == usuarioId && m == mesociclo }
            .map { (chave, _) -> chave.second }
            .distinct()
            .asSuccess()
    }

    override suspend fun primeiroUmRmNoMesociclo(usuarioId: Uuid, exercicioId: Uuid, mesociclo: Int): AppResult<Double?> {
        if (falhar) return caiu()
        // `eventos` é LinkedHashMap: ordem de inserção == ordem de `seq` no banco real.
        val chave = eventos.keys.firstOrNull { chave ->
            chave.first == usuarioId && chave.second == exercicioId && mesociclos[chave] == mesociclo
        }
        return (chave?.let { umRms[it] }).asSuccess()
    }

    override suspend fun reducoesDesde(usuarioId: Uuid, exercicioId: Uuid, desde: Int): AppResult<Int> {
        if (falhar) return caiu()
        return eventos.entries.count { (chave, evento) ->
            chave.first == usuarioId &&
                chave.second == exercicioId &&
                evento is AncoraDoIniciante.Evento.Carga &&
                evento.decisao == Progressao.Decisao.DESCE &&
                (mesociclos[chave] ?: Int.MIN_VALUE) >= desde
        }.asSuccess()
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
