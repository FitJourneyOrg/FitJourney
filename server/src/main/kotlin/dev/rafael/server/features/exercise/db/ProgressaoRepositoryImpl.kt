package dev.rafael.server.features.exercise.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Evento
import dev.rafael.server.features.exercise.engine.v3.Mesociclo
import dev.rafael.server.features.exercise.engine.v3.Progressao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorRepeticao
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorNivel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.uuid.Uuid

private fun agora() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private const val DELOAD = "DELOAD"

private const val SUFIXO_REP = "_REP"
private const val SUFIXO_NIVEL = "_NIVEL"

internal fun Evento.nome(): String = when (this) {
    is Evento.Carga -> decisao.name
    is Evento.Repeticao -> decisao.name + SUFIXO_REP
    is Evento.Nivel -> decisao.name + SUFIXO_NIVEL
    Evento.Deload -> DELOAD
}

internal fun eventoDe(nome: String): Evento = when {
    nome == DELOAD -> Evento.Deload
    nome.endsWith(SUFIXO_NIVEL) -> Evento.Nivel(ProgressaoPorNivel.Decisao.valueOf(nome.removeSuffix(SUFIXO_NIVEL)))
    nome.endsWith(SUFIXO_REP) -> Evento.Repeticao(ProgressaoPorRepeticao.Decisao.valueOf(nome.removeSuffix(SUFIXO_REP)))
    else -> Evento.Carga(Decisao.valueOf(nome))
}

class ProgressaoRepositoryImpl : ProgressaoRepository {

    override suspend fun buscar(usuarioId: Uuid, exercicioId: Uuid): AppResult<Progressao.Estado?> = dbQuery {
        transaction {
            ExerciseProgressionTable.selectAll()
                .where { (ExerciseProgressionTable.userId eq usuarioId) and (ExerciseProgressionTable.exerciseId eq exercicioId) }
                .singleOrNull()?.let(::estadoDe)
        }
    }

    override suspend fun salvar(usuarioId: Uuid, exercicioId: Uuid, estado: Progressao.Estado): AppResult<Unit> = dbQuery {
        transaction { gravar(usuarioId, exercicioId, estado) }
    }

    override suspend fun buscarPorRepeticao(usuarioId: Uuid, exercicioId: Uuid): AppResult<ProgressaoPorRepeticao.Estado?> = dbQuery {
        transaction {
            ExerciseProgressionTable.selectAll()
                .where { (ExerciseProgressionTable.userId eq usuarioId) and (ExerciseProgressionTable.exerciseId eq exercicioId) }
                .singleOrNull()?.let(::estadoRepeticaoDe)
        }
    }

    override suspend fun buscarPorNivel(usuarioId: Uuid, exercicioId: Uuid): AppResult<ProgressaoPorNivel.Estado?> = dbQuery {
        transaction {
            ExerciseProgressionTable.selectAll()
                .where { (ExerciseProgressionTable.userId eq usuarioId) and (ExerciseProgressionTable.exerciseId eq exercicioId) }
                .singleOrNull()?.let(::estadoNivelDe)
        }
    }

    override suspend fun registrar(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: Progressao.Estado,
        evento: Evento,
        mesociclo: Int,
    ): AppResult<Boolean> = dbQuery {
        transaction {
            // `insertIgnore` + contagem, em vez de "SELECT e depois INSERT": quem decide o empate é a
            // PK (usuário, exercício, sessão), então duas requisições simultâneas da mesma sessão não
            // passam as duas. Evento e estado na MESMA transação: ou os dois, ou nenhum.
            val inseridos = ProgressionEventTable.insertIgnore {
                it[userId] = usuarioId
                it[exerciseId] = exercicioId
                it[sessionId] = sessaoId
                it[tipo] = evento.nome()
                it[createdAt] = agora()
                it[ProgressionEventTable.mesociclo] = mesociclo
                it[ProgressionEventTable.umRmKg] = estado.umRm
            }.insertedCount
            if (inseridos == 0) {
                false
            } else {
                gravar(usuarioId, exercicioId, estado)
                true
            }
        }
    }

    override suspend fun registrarPorRepeticao(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: ProgressaoPorRepeticao.Estado,
        evento: Evento,
        mesociclo: Int,
    ): AppResult<Boolean> = dbQuery {
        transaction {
            val inseridos = ProgressionEventTable.insertIgnore {
                it[userId] = usuarioId
                it[exerciseId] = exercicioId
                it[sessionId] = sessaoId
                it[tipo] = evento.nome()
                it[createdAt] = agora()
                it[ProgressionEventTable.mesociclo] = mesociclo
                // Fatia "ancoras-avancado-bodyweight" (2026-11-06): sinal de forca (reps+RIR, nao
                // 1RM em kg) pras ancoras BODYWEIGHT -- mesma coluna do eixo de carga, PromotionService
                // le generico por .umRm sem saber de eixo. Fica null quando a sessao nao tem RIR.
                it[ProgressionEventTable.umRmKg] = estado.sinalForca
            }.insertedCount
            if (inseridos == 0) {
                false
            } else {
                gravarRepeticao(usuarioId, exercicioId, estado)
                true
            }
        }
    }

    override suspend fun registrarPorNivel(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: ProgressaoPorNivel.Estado,
        evento: Evento,
        mesociclo: Int,
    ): AppResult<Boolean> = dbQuery {
        transaction {
            val inseridos = ProgressionEventTable.insertIgnore {
                it[userId] = usuarioId
                it[exerciseId] = exercicioId
                it[sessionId] = sessaoId
                it[tipo] = evento.nome()
                it[createdAt] = agora()
                it[ProgressionEventTable.mesociclo] = mesociclo
                // sem 1RM/sinal de forca no eixo de nivel -- fora de escopo desta fatia
                // ("progressao-por-nivel", 2026-11-06), nenhum BAND e ancora do avancado hoje.
            }.insertedCount
            if (inseridos == 0) {
                false
            } else {
                gravarNivel(usuarioId, exercicioId, estado)
                true
            }
        }
    }

    override suspend fun historico(usuarioId: Uuid, exercicioId: Uuid, limite: Int): AppResult<List<Evento>> = dbQuery {
        require(limite > 0) { "limite deve ser positivo" }
        transaction {
            ProgressionEventTable.selectAll()
                .where { (ProgressionEventTable.userId eq usuarioId) and (ProgressionEventTable.exerciseId eq exercicioId) }
                .orderBy(ProgressionEventTable.seq to SortOrder.DESC)
                .limit(limite)
                .map { eventoDe(it[ProgressionEventTable.tipo]) }
                .asReversed()
        }
    }

    override suspend fun todos(usuarioId: Uuid): AppResult<Map<Uuid, Progressao.Estado>> = dbQuery {
        transaction {
            ExerciseProgressionTable.selectAll()
                .where { ExerciseProgressionTable.userId eq usuarioId }
                .associate { it[ExerciseProgressionTable.exerciseId] to estadoDe(it) }
        }
    }

    override suspend fun exerciciosComEventoNoMesociclo(usuarioId: Uuid, mesociclo: Int): AppResult<List<Uuid>> = dbQuery {
        transaction {
            ProgressionEventTable.selectAll()
                .where { (ProgressionEventTable.userId eq usuarioId) and (ProgressionEventTable.mesociclo eq mesociclo) }
                .map { it[ProgressionEventTable.exerciseId] }
                .distinct()
        }
    }

    override suspend fun primeiroUmRmNoMesociclo(usuarioId: Uuid, exercicioId: Uuid, mesociclo: Int): AppResult<Double?> = dbQuery {
        transaction {
            ProgressionEventTable.selectAll()
                .where {
                    (ProgressionEventTable.userId eq usuarioId) and
                        (ProgressionEventTable.exerciseId eq exercicioId) and
                        (ProgressionEventTable.mesociclo eq mesociclo)
                }
                .orderBy(ProgressionEventTable.seq to SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.get(ProgressionEventTable.umRmKg)
        }
    }

    override suspend fun reducoesDesde(usuarioId: Uuid, exercicioId: Uuid, desde: Int): AppResult<Int> = dbQuery {
        transaction {
            ProgressionEventTable.selectAll()
                .where {
                    (ProgressionEventTable.userId eq usuarioId) and
                        (ProgressionEventTable.exerciseId eq exercicioId) and
                        (ProgressionEventTable.tipo eq Decisao.DESCE.name) and
                        (ProgressionEventTable.mesociclo greaterEq desde)
                }
                .count()
                .toInt()
        }
    }

    private fun gravar(usuarioId: Uuid, exercicioId: Uuid, estado: Progressao.Estado) {
        ExerciseProgressionTable.upsert {
            it[userId] = usuarioId
            it[exerciseId] = exercicioId
            it[cargaKg] = estado.carga
            it[exposicoes] = estado.exposicoes
            it[posSalto] = estado.posSalto
            it[umRmKg] = estado.umRm
            it[semSubir] = estado.semSubir
            it[updatedAt] = agora()
        }
    }

    private fun gravarRepeticao(usuarioId: Uuid, exercicioId: Uuid, estado: ProgressaoPorRepeticao.Estado) {
        ExerciseProgressionTable.upsert {
            it[userId] = usuarioId
            it[exerciseId] = exercicioId
            it[repsMin] = estado.repsMin
            it[repsMax] = estado.repsMax
            it[exposicoes] = estado.exposicoes
            it[semSubir] = estado.semSubir
            // Fatia "ancoras-avancado-bodyweight" (2026-11-06) -- ver nota em registrarPorRepeticao.
            it[umRmKg] = estado.sinalForca
            it[updatedAt] = agora()
        }
    }

    private fun gravarNivel(usuarioId: Uuid, exercicioId: Uuid, estado: ProgressaoPorNivel.Estado) {
        ExerciseProgressionTable.upsert {
            it[userId] = usuarioId
            it[exerciseId] = exercicioId
            it[nivelAtual] = estado.nivelAtual
            it[exposicoes] = estado.exposicoes
            it[semSubir] = estado.semSubir
            it[updatedAt] = agora()
        }
    }

    private fun estadoRepeticaoDe(linha: org.jetbrains.exposed.v1.core.ResultRow): ProgressaoPorRepeticao.Estado? {
        val min = linha[ExerciseProgressionTable.repsMin] ?: return null
        val max = linha[ExerciseProgressionTable.repsMax] ?: return null
        return ProgressaoPorRepeticao.Estado(
            repsMin = min,
            repsMax = max,
            exposicoes = linha[ExerciseProgressionTable.exposicoes],
            semSubir = linha[ExerciseProgressionTable.semSubir],
            sinalForca = linha[ExerciseProgressionTable.umRmKg],
        )
    }

    private fun estadoNivelDe(linha: org.jetbrains.exposed.v1.core.ResultRow): ProgressaoPorNivel.Estado? {
        val nivel = linha[ExerciseProgressionTable.nivelAtual] ?: return null
        return ProgressaoPorNivel.Estado(
            nivelAtual = nivel,
            exposicoes = linha[ExerciseProgressionTable.exposicoes],
            semSubir = linha[ExerciseProgressionTable.semSubir],
        )
    }

    private fun estadoDe(linha: org.jetbrains.exposed.v1.core.ResultRow) = Progressao.Estado(
        carga = linha[ExerciseProgressionTable.cargaKg],
        exposicoes = linha[ExerciseProgressionTable.exposicoes],
        posSalto = linha[ExerciseProgressionTable.posSalto],
        umRm = linha[ExerciseProgressionTable.umRmKg],
        semSubir = linha[ExerciseProgressionTable.semSubir],
    )
}

class MesocicloRepositoryImpl : MesocicloRepository {

    override suspend fun buscar(usuarioId: Uuid): AppResult<Mesociclo?> = dbQuery {
        transaction {
            UserMesocycleTable.selectAll().where { UserMesocycleTable.userId eq usuarioId }.singleOrNull()?.let {
                Mesociclo(it[UserMesocycleTable.numero], it[UserMesocycleTable.semana], it[UserMesocycleTable.conservador])
            }
        }
    }

    override suspend fun salvar(usuarioId: Uuid, mesociclo: Mesociclo): AppResult<Unit> = dbQuery {
        transaction {
            UserMesocycleTable.upsert {
                it[userId] = usuarioId
                it[numero] = mesociclo.numero
                it[semana] = mesociclo.semana
                it[conservador] = mesociclo.conservador
                it[updatedAt] = agora()
            }
        }
        Unit
    }
}

private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
    withContext(Dispatchers.IO) {
        runCatching { block() }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
        )
    }
