package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Estado
import dev.rafael.server.features.exercise.engine.v3.Progressao.Serie
import dev.rafael.server.features.exercise.engine.v3.SessionComposition.Papel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * Motor v3, Fatia 10 — o ciclo inteiro de UM exercício contra o repositório (fake à mão):
 * calibra → grava → decide → grava → pausa → grava. É o que o serviço da Fatia 11 vai fazer; aqui
 * prova-se que o contrato do repositório (chave por exercício, upsert, falha explícita) basta.
 */
class ProgressaoFluxoTest {

    private val eu = Uuid.random()
    private val supino = Uuid.random()
    private val remada = Uuid.random()

    private fun <T> AppResult<T>.valor(): T = (assertIs<AppResult.Success<T>>(this)).value

    @Test
    fun `calibra, grava, progride, grava e volta de uma pausa de tres semanas`(): Unit = runBlocking {
        val repo = FakeProgressaoRepository()

        assertNull(repo.buscar(eu, supino).valor(), "nunca fez: a calibração é obrigatória")

        // 1ª exposição: 20 kg -> 55 kg em 3 séries de calibração
        val calibrado = Progressao.calibrar(20.0, listOf(9, 7, 5), 2, false, 2.5)
        repo.salvar(eu, supino, calibrado).valor()
        assertEquals(Estado(carga = 55.0, exposicoes = 1), repo.buscar(eu, supino).valor())

        // 2ª exposição: topo da faixa com RIR 3 -> salto de 10% (60 kg)
        val (depois, decisao) = Progressao.decidir(
            repo.buscar(eu, supino).valor()!!, List(3) { Serie(9, 3) }, Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, true,
        )
        assertEquals(Decisao.SOBE, decisao)
        repo.salvar(eu, supino, depois).valor()
        assertEquals(60.0, repo.buscar(eu, supino).valor()!!.carga)

        // pausa de 20 dias: mesma semana 1 do mesmo mesociclo, carga 10% menor (55 kg)
        val retorno = RetornoAposPausa.apos(20, Mesociclo(1, 3))
        assertEquals(Mesociclo(1, 1), retorno.mesociclo)
        val voltou = RetornoAposPausa.aplicar(repo.buscar(eu, supino).valor()!!, retorno, 2.5)
        repo.salvar(eu, supino, voltou).valor()
        assertEquals(55.0, repo.buscar(eu, supino).valor()!!.carga)

        // pausa de 40 dias: recalibra — a carga some e o 1RM histórico fica
        val longa = RetornoAposPausa.apos(40, Mesociclo(1, 3))
        repo.salvar(eu, supino, RetornoAposPausa.aplicar(repo.buscar(eu, supino).valor()!!, longa, 2.5)).valor()
        val recalibrar = repo.buscar(eu, supino).valor()!!
        assertNull(recalibrar.carga)
        assertEquals(0, recalibrar.exposicoes)
    }

    @Test
    fun `o estado e por exercicio, nao por vaga nem por pessoa`(): Unit = runBlocking {
        val repo = FakeProgressaoRepository()
        val outra = Uuid.random()
        repo.salvar(eu, supino, Estado(carga = 60.0, exposicoes = 5)).valor()
        repo.salvar(eu, remada, Estado(carga = 40.0, exposicoes = 2)).valor()
        repo.salvar(outra, supino, Estado(carga = 100.0, exposicoes = 9)).valor()

        assertEquals(60.0, repo.buscar(eu, supino).valor()!!.carga)
        assertEquals(setOf(supino, remada), repo.todos(eu).valor().keys)
        assertEquals(100.0, repo.buscar(outra, supino).valor()!!.carga, "a carga de outra pessoa não vaza")
        assertNull(repo.buscar(outra, remada).valor())
    }

    @Test
    fun `gravar duas vezes o mesmo estado nao duplica a linha`(): Unit = runBlocking {
        // É o que permite ao cliente reenviar a sessão depois de um timeout.
        val repo = FakeProgressaoRepository()
        val estado = Estado(carga = 60.0, exposicoes = 5)
        repo.salvar(eu, supino, estado).valor()
        repo.salvar(eu, supino, estado).valor()
        assertEquals(1, repo.linhas)
    }

    @Test
    fun `reenviar a mesma sessao nao decide de novo - a carga nao sobe em dobro`(): Unit = runBlocking {
        val repo = FakeProgressaoRepository()
        val sessao = Uuid.random()
        repo.salvar(eu, supino, Estado(carga = 55.0, exposicoes = 1)).valor()

        fun aplica(): Boolean {
            val atual = runBlocking { repo.buscar(eu, supino).valor()!! }
            val (depois, decisao) = Progressao.decidir(atual, List(3) { Serie(9, 3) }, Papel.COMPOSTO, Goal.GAIN_MUSCLE, 2.5, true)
            return runBlocking { repo.registrar(eu, supino, sessao, depois, AncoraDoIniciante.Evento.Carga(decisao), mesociclo = 1).valor() }
        }
        assertEquals(true, aplica())
        assertEquals(false, aplica(), "o reenvio é recusado pela chave (usuário, exercício, sessão)")
        assertEquals(60.0, repo.buscar(eu, supino).valor()!!.carga)
        assertEquals(2, repo.buscar(eu, supino).valor()!!.exposicoes)
        assertEquals(1, repo.historico(eu, supino).valor().size)
    }

    @Test
    fun `historico respeita o limite, e limite invalido ou banco fora falham`(): Unit = runBlocking {
        val repo = FakeProgressaoRepository()
        repeat(4) { repo.registrar(eu, supino, Uuid.random(), Estado(carga = 50.0, exposicoes = it), AncoraDoIniciante.Evento.Deload, mesociclo = 1).valor() }
        assertEquals(2, repo.historico(eu, supino, limite = 2).valor().size)
        assertIs<AppResult.Failure>(repo.historico(eu, supino, limite = 0))
        repo.falhar = true
        assertIs<AppResult.Failure>(repo.registrar(eu, supino, Uuid.random(), Estado(carga = 50.0), AncoraDoIniciante.Evento.Deload, mesociclo = 1))
    }

    @Test
    fun `falha do banco vira Unexpected e nao grava nada`(): Unit = runBlocking {
        val repo = FakeProgressaoRepository()
        repo.salvar(eu, supino, Estado(carga = 60.0, exposicoes = 5)).valor()

        repo.falhar = true
        assertIs<AppError.Unexpected>(assertIs<AppResult.Failure>(repo.salvar(eu, supino, Estado(carga = 99.0, exposicoes = 6))).error)
        assertIs<AppResult.Failure>(repo.buscar(eu, supino))
        assertIs<AppResult.Failure>(repo.todos(eu))

        repo.falhar = false
        assertEquals(60.0, repo.buscar(eu, supino).valor()!!.carga, "a gravação que falhou não pode ter vazado")
    }

    @Test
    fun `mesociclo sem estado e o primeiro, e concluir quatro semanas abre o segundo`(): Unit = runBlocking {
        val repo = FakeMesocicloRepository()
        assertEquals(1, CicloDoMesociclo.paraPerfil(repo.buscar(eu).valor()))

        var m = Mesociclo()
        repeat(CicloDoMesociclo.SEMANAS) { m = CicloDoMesociclo.concluirSemana(m) }
        repo.salvar(eu, m).valor()
        assertEquals(Mesociclo(2, 1), repo.buscar(eu).valor())
        assertEquals(2, CicloDoMesociclo.paraPerfil(repo.buscar(eu).valor()))
    }

    @Test
    fun `mesociclo com falha do banco nao devolve um numero inventado`(): Unit = runBlocking {
        val repo = FakeMesocicloRepository()
        repo.falhar = true
        assertIs<AppResult.Failure>(repo.buscar(eu))
        assertIs<AppResult.Failure>(repo.salvar(eu, Mesociclo(3, 2)))
        repo.falhar = false
        assertNull(repo.buscar(eu).valor(), "a gravação que falhou não pode ter vazado")
    }
}
