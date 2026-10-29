package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Motor v3, Fatia 2 (`docs/trabalho/motor-v3-fatia-2.md`) — prova a tabela de cobertura por
 * faixa de tempo da especificação (`propostas/motor-v3-ESPECIFICACAO.md`, "Cobertura por faixa
 * de tempo") célula a célula, e os fatores de objetivo/idade isoladamente.
 */
class CapacityModelTest {

    // Demanda direta total por nível (especificação, "O princípio central: a capacidade é
    // escassa" — mesmos totais provados por músculo na Fatia 1 para intermediário/avançado).
    private val demandaDireta = mapOf(
        Level.BEGINNER to 44.0,
        Level.INTERMEDIATE to 86.0,
        Level.ADVANCED to 113.0,
    )

    /** linha da tabela: nível × minutos -> cobertura esperada (%) por dias 2..6. */
    private val tabelaDeCobertura = listOf(
        Triple(Level.BEGINNER, 45, listOf(44, 66, 103, 129, 155)),
        Triple(Level.BEGINNER, 60, listOf(67, 100, 145, 182, 218)),
        Triple(Level.BEGINNER, 75, listOf(73, 109, 145, 182, 218)),
        Triple(Level.INTERMEDIATE, 45, listOf(22, 34, 53, 66, 79)),
        Triple(Level.INTERMEDIATE, 60, listOf(34, 51, 76, 95, 114)),
        Triple(Level.INTERMEDIATE, 75, listOf(42, 63, 84, 105, 126)),
        Triple(Level.ADVANCED, 45, listOf(17, 26, 40, 50, 60)),
        Triple(Level.ADVANCED, 60, listOf(26, 39, 58, 72, 87)),
        Triple(Level.ADVANCED, 75, listOf(32, 48, 64, 80, 96)),
    )

    @Test
    fun `cobertura bate com a especificacao em todas as 27 celulas`() {
        for ((nivel, minutos, coberturasPorDias) in tabelaDeCobertura) {
            for ((indice, diasEsperado) in (2..6).withIndex()) {
                val capacidade = CapacityModel.capacidadeSemanal(
                    minutos = minutos,
                    dias = diasEsperado,
                    nivel = nivel,
                    goal = Goal.GAIN_MUSCLE,
                    idade = null,
                    mesocicloAtual = null,
                )
                val coberturaCalculada = (capacidade / demandaDireta.getValue(nivel)) * 100
                val esperado = coberturasPorDias[indice]
                assertTrue(
                    kotlin.math.abs(coberturaCalculada - esperado) <= 1.0,
                    "nível=$nivel minutos=$minutos dias=$diasEsperado: " +
                        "esperado ~$esperado%, calculado ${"%.1f".format(coberturaCalculada)}%",
                )
            }
        }
    }

    @Test
    fun `75 minutos so rende mais que 60 a partir de 4 dias`() {
        // A especificação é explícita: o teto por nível empata 60 e 75 min a partir de 4 dias;
        // em 2 e 3 dias o overhead maior deixa 75 acima de 60.
        val teto60em2dias = CapacityModel.tetoDaSessao(60, 2, Level.BEGINNER, Goal.GAIN_MUSCLE, null, null)
        val teto75em2dias = CapacityModel.tetoDaSessao(75, 2, Level.BEGINNER, Goal.GAIN_MUSCLE, null, null)
        assertTrue(teto75em2dias > teto60em2dias, "em 2 dias, 75min deveria render mais que 60min")

        val teto60em4dias = CapacityModel.tetoDaSessao(60, 4, Level.BEGINNER, Goal.GAIN_MUSCLE, null, null)
        val teto75em4dias = CapacityModel.tetoDaSessao(75, 4, Level.BEGINNER, Goal.GAIN_MUSCLE, null, null)
        assertEquals(teto60em4dias, teto75em4dias, "em 4 dias, 75min deveria empatar com 60min (teto por nível manda)")
    }

    @Test
    fun `overhead vem de dias, nao de split`() {
        assertEquals(16, CapacityModel.overheadMinutos(2))
        assertEquals(16, CapacityModel.overheadMinutos(3))
        assertEquals(11, CapacityModel.overheadMinutos(4))
        assertEquals(11, CapacityModel.overheadMinutos(5))
        assertEquals(11, CapacityModel.overheadMinutos(6))
    }

    @Test
    fun `dias fora de 2 a 6 falha alto, nao recebe overhead em silencio`() {
        assertFailsWith<IllegalArgumentException> { CapacityModel.overheadMinutos(1) }
        assertFailsWith<IllegalArgumentException> { CapacityModel.overheadMinutos(7) }
        assertFailsWith<IllegalArgumentException> { CapacityModel.overheadMinutos(0) }
    }

    @Test
    fun `fator de objetivo e 0,6 so para manter`() {
        assertEquals(0.6, CapacityModel.fatorObjetivo(Goal.MAINTAIN))
        assertEquals(1.0, CapacityModel.fatorObjetivo(Goal.GAIN_MUSCLE))
        assertEquals(1.0, CapacityModel.fatorObjetivo(Goal.LOSE_FAT))
        assertEquals(1.0, CapacityModel.fatorObjetivo(Goal.GENERAL_HEALTH))
    }

    @Test
    fun `fator de idade reduz todo 60 mais, mesociclo nulo conta como primeiro`() {
        assertEquals(0.75, CapacityModel.fatorIdade(idade = 65, Level.INTERMEDIATE, mesocicloAtual = null))
        assertEquals(0.75, CapacityModel.fatorIdade(idade = 65, Level.ADVANCED, mesocicloAtual = null))
        assertEquals(0.75, CapacityModel.fatorIdade(idade = 65, Level.BEGINNER, mesocicloAtual = 3))
    }

    @Test
    fun `fator de idade nao reduz 60 mais depois do primeiro mesociclo, se nao for iniciante`() {
        assertEquals(1.0, CapacityModel.fatorIdade(idade = 65, Level.INTERMEDIATE, mesocicloAtual = 2))
    }

    @Test
    fun `fator de idade nao se aplica abaixo de 60 ou sem idade informada`() {
        assertEquals(1.0, CapacityModel.fatorIdade(idade = 59, Level.BEGINNER, mesocicloAtual = null))
        assertEquals(1.0, CapacityModel.fatorIdade(idade = null, Level.BEGINNER, mesocicloAtual = null))
    }
}
