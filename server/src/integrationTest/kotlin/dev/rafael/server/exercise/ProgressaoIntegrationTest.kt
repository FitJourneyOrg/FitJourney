package dev.rafael.server.exercise

import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.Semear
import dev.rafael.server.features.exercise.db.MesocicloRepositoryImpl
import dev.rafael.server.features.exercise.db.ProgressaoRepositoryImpl
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Evento
import dev.rafael.server.features.exercise.engine.v3.Mesociclo
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Estado
import dev.rafael.server.features.exercise.engine.v3.ProgressaoPorRepeticao
import dev.rafael.server.features.exercise.db.ExerciseProgressionTable
import dev.rafael.server.features.session.db.SessionSetLogsTable
import dev.rafael.server.features.session.db.WorkoutSessionsTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

/**
 * Trava a **V69** (progressão por exercício, mesociclo por pessoa, RIR da série) contra Postgres REAL.
 *
 * O que só o banco prova: a PK que torna o reenvio da sessão inofensivo (D-2), os CHECKs, o FK sem
 * `ON DELETE` do exercício, o `ON DELETE CASCADE` da sessão e a coluna `rir` nullable.
 */
class ProgressaoIntegrationTest {

    private val progressao = ProgressaoRepositoryImpl()
    private val mesociclo = MesocicloRepositoryImpl()

    @BeforeEach
    fun limpa() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> AppResult<T>.valor(): T = (this as AppResult.Success<T>).value

    private fun sessao(usuario: Uuid, dia: Int = 1): Uuid =
        Semear.sessao(usuario, "Treino", LocalDate(2026, 10, dia), emptyList())

    // ------------------------------------------------------------------------------ D-2

    @Test
    fun `registrar a mesma sessao duas vezes aplica uma so e nao regrava o estado`() = runBlocking {
        val eu = Semear.usuario()
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        val s = sessao(eu)

        val primeira = progressao.registrar(eu, supino, s, Estado(carga = 60.0, exposicoes = 2), Evento.Carga(Decisao.SOBE), mesociclo = 1).valor()
        // o cliente reenviou depois de um timeout, e o serviço (por erro) decidiu de novo: estado diferente
        val segunda = progressao.registrar(eu, supino, s, Estado(carga = 65.0, exposicoes = 3), Evento.Carga(Decisao.SOBE), mesociclo = 1).valor()

        assertTrue(primeira)
        assertFalse(segunda, "a sessão já estava registrada")
        assertEquals(Estado(carga = 60.0, exposicoes = 2), progressao.buscar(eu, supino).valor(), "a 2ª tentativa não pode regravar")
        assertEquals(1, progressao.historico(eu, supino).valor().size)
    }

    @Test
    fun `sessoes diferentes do mesmo exercicio avancam o estado`() = runBlocking {
        val eu = Semear.usuario()
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        progressao.registrar(eu, supino, sessao(eu, 1), Estado(carga = 55.0, exposicoes = 1), Evento.Carga(Decisao.MANTEM), mesociclo = 1).valor()
        progressao.registrar(eu, supino, sessao(eu, 2), Estado(carga = 60.0, exposicoes = 2, posSalto = true), Evento.Carga(Decisao.SOBE), mesociclo = 1).valor()
        assertEquals(Estado(carga = 60.0, exposicoes = 2, posSalto = true), progressao.buscar(eu, supino).valor())
    }

    @Test
    fun `o historico vem do mais antigo ao mais novo, respeita o limite e distingue o deload`() = runBlocking {
        val eu = Semear.usuario()
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        val eventos = listOf(
            Evento.Carga(Decisao.SOBE), Evento.Carga(Decisao.MANTEM), Evento.Carga(Decisao.DESCE), Evento.Deload, Evento.Carga(Decisao.MANTEM),
        )
        eventos.forEachIndexed { i, e ->
            progressao.registrar(eu, supino, sessao(eu, i + 1), Estado(carga = 50.0, exposicoes = i), e, mesociclo = 1).valor()
        }
        assertEquals(eventos, progressao.historico(eu, supino).valor())
        assertEquals(eventos.takeLast(2), progressao.historico(eu, supino, limite = 2).valor(), "o limite corta os MAIS ANTIGOS")
        assertTrue(progressao.historico(eu, supino, limite = 0) is AppResult.Failure, "limite inválido falha alto")
    }

    @Test
    fun `apagar a sessao leva o evento mas nao desfaz o estado`() = runBlocking {
        val eu = Semear.usuario()
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        val s = sessao(eu)
        progressao.registrar(eu, supino, s, Estado(carga = 60.0, exposicoes = 2), Evento.Carga(Decisao.SOBE), mesociclo = 1).valor()
        transaction { WorkoutSessionsTable.deleteWhere { WorkoutSessionsTable.id eq s } }
        assertTrue(progressao.historico(eu, supino).valor().isEmpty())
        assertEquals(60.0, progressao.buscar(eu, supino).valor()!!.carga)
    }

    // ------------------------------------------------------------------------------ D-1

    @Test
    fun `o estado e por pessoa e por exercicio`() = runBlocking {
        val eu = Semear.usuario("Eu")
        val outra = Semear.usuario("Outra")
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        val remada = Semear.exercicio("Remada", listOf("BACK"))
        progressao.salvar(eu, supino, Estado(carga = 60.0, exposicoes = 5)).valor()
        progressao.salvar(eu, remada, Estado(carga = 40.0, exposicoes = 2)).valor()
        progressao.salvar(outra, supino, Estado(carga = 100.0, exposicoes = 9)).valor()

        assertEquals(setOf(supino, remada), progressao.todos(eu).valor().keys)
        assertEquals(100.0, progressao.buscar(outra, supino).valor()!!.carga)
        assertNull(progressao.buscar(outra, remada).valor())
        assertNull(progressao.buscar(eu, Semear.exercicio("Outro", null)).valor(), "nunca fez: calibração obrigatória")
    }

    @Test
    fun `salvar substitui e a calibracao pendente volta nula do banco`() = runBlocking {
        val eu = Semear.usuario()
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        progressao.salvar(eu, supino, Estado(carga = 80.0, exposicoes = 7, posSalto = true, umRm = 110.0, semSubir = 2)).valor()
        val recal = Estado(carga = null, exposicoes = 0, posSalto = false, umRm = 110.0, semSubir = 0)
        progressao.salvar(eu, supino, recal).valor()
        assertEquals(recal, progressao.buscar(eu, supino).valor())
        assertEquals(1, progressao.todos(eu).valor().size, "upsert, não segunda linha")
    }

    // ------------------------------------------------------------------------------ CHECKs e FKs

    @Test
    fun `o banco recusa carga zero, exposicao negativa e exercicio inexistente`() = runBlocking {
        val eu = Semear.usuario()
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        // o repositório devolve Failure em vez de lançar
        assertTrue(progressao.salvar(eu, supino, Estado(carga = 0.0, exposicoes = 1)) is AppResult.Failure)
        assertTrue(progressao.salvar(eu, supino, Estado(carga = 50.0, exposicoes = 1, umRm = -1.0)) is AppResult.Failure)
        assertTrue(progressao.salvar(eu, Uuid.random(), Estado(carga = 50.0, exposicoes = 1)) is AppResult.Failure, "FK do exercício")
        assertTrue(progressao.salvar(Uuid.random(), supino, Estado(carga = 50.0, exposicoes = 1)) is AppResult.Failure, "FK do usuário")
        assertTrue(progressao.todos(eu).valor().isEmpty(), "nada vazou")
    }

    @Test
    fun `falha ao registrar nao deixa evento sem estado`() = runBlocking {
        val eu = Semear.usuario()
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        val s = sessao(eu)
        // estado inválido (carga <= 0) -> a transação inteira volta, inclusive o evento
        assertTrue(progressao.registrar(eu, supino, s, Estado(carga = -5.0, exposicoes = 1), Evento.Carga(Decisao.SOBE), mesociclo = 1) is AppResult.Failure)
        assertTrue(progressao.historico(eu, supino).valor().isEmpty(), "evento e estado entram juntos ou nenhum")
        // e a sessão pode ser registrada depois, com estado válido
        assertTrue(progressao.registrar(eu, supino, s, Estado(carga = 50.0, exposicoes = 1), Evento.Carga(Decisao.SOBE), mesociclo = 1).valor())
    }

    // ------------------------------------------------------------------------------ D-4

    @Test
    fun `mesociclo - ida e volta com conservador, substituicao e usuario sem linha`() = runBlocking {
        val eu = Semear.usuario()
        assertNull(mesociclo.buscar(eu).valor(), "sem linha = 1º mesociclo")
        mesociclo.salvar(eu, Mesociclo(3, 2, conservador = true)).valor()
        assertEquals(Mesociclo(3, 2, true), mesociclo.buscar(eu).valor())
        mesociclo.salvar(eu, Mesociclo(3, 3)).valor()
        assertEquals(Mesociclo(3, 3, false), mesociclo.buscar(eu).valor())
    }

    @Test
    fun `mesociclo - semana 5, numero zero e usuario inexistente sao recusados`() = runBlocking {
        val eu = Semear.usuario()
        assertTrue(mesociclo.salvar(eu, Mesociclo(1, 5)) is AppResult.Failure)
        assertTrue(mesociclo.salvar(eu, Mesociclo(0, 1)) is AppResult.Failure)
        assertTrue(mesociclo.salvar(Uuid.random(), Mesociclo(1, 1)) is AppResult.Failure)
        assertNull(mesociclo.buscar(eu).valor())
    }

    // ------------------------------------------------------------------------------ D-3

    @Test
    fun `rir da serie - nulo por padrao, aceita 0 a 10 e o banco recusa 11`() {
        val eu = Semear.usuario()
        val supino = Semear.exercicio("Supino", listOf("CHEST"))
        val s = Semear.sessao(eu, "Treino", LocalDate(2026, 10, 1), listOf(Semear.Quadrupla(supino, 8, 60.0)))
        val rirDaSerie = transaction { SessionSetLogsTable.selectAll().where { SessionSetLogsTable.sessionId eq s }.single()[SessionSetLogsTable.rir] }
        assertNull(rirDaSerie, "sem backfill: quem não informou fica nulo")

        for (rir in listOf(0, 10)) escreveRir(s, rir)
        assertThrows<ExposedSQLException> { escreveRir(s, 11) }
        assertThrows<ExposedSQLException> { escreveRir(s, -1) }
    }

    private fun escreveRir(sessao: Uuid, valor: Int) = transaction {
        SessionSetLogsTable.update({ SessionSetLogsTable.sessionId eq sessao }) { it[SessionSetLogsTable.rir] = valor.toShort() }
    }

    // ------------------------------------------------------------------------------ V76, eixo de repeticao

    @Test
    fun `repeticao - registrar a mesma sessao duas vezes aplica uma so e nao regrava o estado`() = runBlocking {
        val eu = Semear.usuario()
        val flexao = Semear.exercicio("Flexao", listOf("CHEST"))
        val s = sessao(eu)
        val estadoRep = ProgressaoPorRepeticao.Estado(repsMin = 13, repsMax = 17, exposicoes = 1)

        val primeira = progressao.registrarPorRepeticao(eu, flexao, s, estadoRep, Evento.Repeticao(ProgressaoPorRepeticao.Decisao.SOBE), mesociclo = 1).valor()
        val segunda = progressao.registrarPorRepeticao(
            eu, flexao, s, estadoRep.copy(repsMin = 14, repsMax = 18), Evento.Repeticao(ProgressaoPorRepeticao.Decisao.SOBE), mesociclo = 1,
        ).valor()

        assertTrue(primeira)
        assertFalse(segunda, "a sessão já estava registrada")
        assertEquals(estadoRep, progressao.buscarPorRepeticao(eu, flexao).valor(), "a 2ª tentativa não pode regravar")
    }

    @Test
    fun `repeticao - historico guarda SOBE_REP junto de eventos de carga, e decodifica de volta certo`() = runBlocking {
        val eu = Semear.usuario()
        val flexao = Semear.exercicio("Flexao", listOf("CHEST"))
        val eventos = listOf(
            Evento.Repeticao(ProgressaoPorRepeticao.Decisao.SOBE),
            Evento.Repeticao(ProgressaoPorRepeticao.Decisao.MANTEM),
            Evento.Deload,
            Evento.Repeticao(ProgressaoPorRepeticao.Decisao.DESCE),
        )
        eventos.forEachIndexed { i, e ->
            val estado = ProgressaoPorRepeticao.Estado(repsMin = 12, repsMax = 16, exposicoes = i)
            progressao.registrarPorRepeticao(eu, flexao, sessao(eu, i + 1), estado, e, mesociclo = 1).valor()
        }
        assertEquals(eventos, progressao.historico(eu, flexao).valor(), "SOBE_REP/MANTEM_REP/DESCE_REP nao colidem com o enum de carga")
    }

    @Test
    fun `repeticao - o estado e por pessoa e por exercicio, igual ao eixo de carga`() = runBlocking {
        val eu = Semear.usuario()
        val outra = Semear.usuario("Outra")
        val flexao = Semear.exercicio("Flexao", listOf("CHEST"))
        progressao.registrarPorRepeticao(eu, flexao, sessao(eu), ProgressaoPorRepeticao.Estado(13, 17, 1), Evento.Repeticao(ProgressaoPorRepeticao.Decisao.SOBE), mesociclo = 1).valor()
        progressao.registrarPorRepeticao(outra, flexao, sessao(outra), ProgressaoPorRepeticao.Estado(20, 20, 9), Evento.Repeticao(ProgressaoPorRepeticao.Decisao.MANTEM), mesociclo = 1).valor()

        assertEquals(13, progressao.buscarPorRepeticao(eu, flexao).valor()!!.repsMin)
        assertEquals(20, progressao.buscarPorRepeticao(outra, flexao).valor()!!.repsMax)
        assertNull(progressao.buscarPorRepeticao(eu, Semear.exercicio("Outro", null)).valor(), "nunca fez")
    }

    @Test
    fun `repeticao - sinalForca viaja pela mesma coluna umRmKg e alimenta buscar() e primeiroUmRmNoMesociclo`() = runBlocking {
        // fatia "ancoras-avancado-bodyweight" (2026-11-06): sem isto as 4 ancoras do avancado
        // BODYWEIGHT ficam em DADO INSUFICIENTE pra sempre, mesmo pra quem treina em academia.
        val eu = Semear.usuario()
        val barraFixa = Semear.exercicio("Barra Fixa", listOf("BACK"))
        val estado = ProgressaoPorRepeticao.Estado(repsMin = 6, repsMax = 9, exposicoes = 0, sinalForca = 8.0)

        progressao.registrarPorRepeticao(eu, barraFixa, sessao(eu), estado, Evento.Repeticao(ProgressaoPorRepeticao.Decisao.MANTEM), mesociclo = 1).valor()

        assertEquals(8.0, progressao.buscar(eu, barraFixa).valor()?.umRm, "buscar() le o sinal generico, sem saber de eixo -- o mesmo caminho que PromotionService usa")
        assertEquals(8.0, progressao.primeiroUmRmNoMesociclo(eu, barraFixa, mesociclo = 1).valor())
    }

    @Test
    fun `repeticao - sem RIR nenhuma serie, sinalForca fica null e nao finge dado que nao existe`() = runBlocking {
        val eu = Semear.usuario()
        val flexao = Semear.exercicio("Flexao", listOf("CHEST"))
        val estado = ProgressaoPorRepeticao.Estado(repsMin = 6, repsMax = 9, exposicoes = 0, sinalForca = null)

        progressao.registrarPorRepeticao(eu, flexao, sessao(eu), estado, Evento.Repeticao(ProgressaoPorRepeticao.Decisao.MANTEM), mesociclo = 1).valor()

        assertNull(progressao.buscar(eu, flexao).valor()?.umRm)
    }

    @Test
    fun `repeticao - o banco recusa reps_min sem reps_max (um dos dois nulo)`() = runBlocking {
        val eu = Semear.usuario()
        val flexao = Semear.exercicio("Flexao", listOf("CHEST"))
        assertThrows<ExposedSQLException> {
            transaction {
                ExerciseProgressionTable.insert {
                    it[userId] = eu
                    it[exerciseId] = flexao
                    it[repsMin] = 12
                    it[exposicoes] = 0
                    it[posSalto] = false
                    it[semSubir] = 0
                }
            }
        }
    }

    @Test
    fun `repeticao - o banco recusa reps_max menor que reps_min`() = runBlocking {
        val eu = Semear.usuario()
        val flexao = Semear.exercicio("Flexao", listOf("CHEST"))
        assertThrows<ExposedSQLException> {
            transaction {
                ExerciseProgressionTable.insert {
                    it[userId] = eu
                    it[exerciseId] = flexao
                    it[repsMin] = 16
                    it[repsMax] = 12
                    it[exposicoes] = 0
                    it[posSalto] = false
                    it[semSubir] = 0
                }
            }
        }
    }
}
