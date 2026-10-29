package dev.rafael.server.features.exercise.db

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Evento
import dev.rafael.server.features.exercise.engine.v3.Mesociclo
import dev.rafael.server.features.exercise.engine.v3.Progressao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.uuid.Uuid

private fun agora() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

private const val DELOAD = "DELOAD"

internal fun Evento.nome(): String = when (this) {
    is Evento.Carga -> decisao.name
    Evento.Deload -> DELOAD
}

internal fun eventoDe(nome: String): Evento =
    if (nome == DELOAD) Evento.Deload else Evento.Carga(Decisao.valueOf(nome))

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

    override suspend fun registrar(
        usuarioId: Uuid,
        exercicioId: Uuid,
        sessaoId: Uuid,
        estado: Progressao.Estado,
        evento: Evento,
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
            }.insertedCount
            if (inseridos == 0) {
                false
            } else {
                gravar(usuarioId, exercicioId, estado)
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
