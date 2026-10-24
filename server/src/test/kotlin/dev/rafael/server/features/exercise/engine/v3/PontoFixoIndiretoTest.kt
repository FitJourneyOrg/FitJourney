package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Motor v3, Fatia 6 — prova a MECÂNICA do laço (`PontoFixoIndireto`), com `passo` falso: o
 * oráculo nunca esgota as 30 iterações nos 720 perfis reais (máximo observado: 5), então esse
 * caminho só é alcançável com um `passo` construído, não com o pipeline de verdade.
 */
class PontoFixoIndiretoTest {

    private val zero = TargetMuscle.entries.associateWith { 0.0 }
    private fun chest(x: Double) = zero + (TargetMuscle.CHEST to x)

    @Test
    fun `converge na primeira iteracao quando o passo ja devolve o mesmo indireto`() {
        val resultado = PontoFixoIndireto.iterar<String> { indireto -> Triple("estado", indireto, mutableMapOf()) }
        assertEquals("estado", resultado.estado)
        assertEquals(zero, resultado.indireto)
        assertEquals(emptyMap(), resultado.disparos)
        assertEquals(1, resultado.iteracoes)
    }

    @Test
    fun `converge depois de N iteracoes quando o indireto estabiliza`() {
        val sequencia = listOf(chest(1.0), chest(2.0), chest(2.0)) // 3a chamada repete a 2a -> converge
        var i = 0
        val resultado = PontoFixoIndireto.iterar<Int> { _ ->
            val novo = sequencia[i]
            i += 1
            Triple(i, novo, mutableMapOf())
        }
        assertEquals(3, resultado.estado)
        assertEquals(chest(2.0), resultado.indireto)
        assertEquals(3, resultado.iteracoes)
        assertEquals(emptyMap(), resultado.disparos)
    }

    @Test
    fun `oscila entre dois estados - fica com o de menor indireto total, disparo medido`() {
        val map0 = zero
        val map1 = chest(1.0)
        val map2 = chest(2.0)
        // passo(map0)->map1, passo(map1)->map2, passo(map2)->map1 (ciclo de volta pro map1)
        val resultado = PontoFixoIndireto.iterar<String> { indireto ->
            when (indireto) {
                map0 -> Triple("e0", map1, mutableMapOf())
                map1 -> Triple("e1", map2, mutableMapOf())
                map2 -> Triple("e2", map1, mutableMapOf())
                else -> error("indireto inesperado: $indireto")
            }
        }
        // ciclo = [e1(novo=map2, soma=2.0), e2(novo=map1, soma=1.0)] -> e2 tem o menor indireto total
        assertEquals("e2", resultado.estado)
        assertEquals(map1, resultado.indireto)
        assertEquals(mapOf("ponto_fixo_oscilou" to 1), resultado.disparos)
        assertEquals(3, resultado.iteracoes)
    }

    @Test
    fun `esgota as 30 iteracoes sem convergir - disparo ponto_fixo_nao_convergiu`() {
        var contador = 0
        val resultado = PontoFixoIndireto.iterar<Int> { _ ->
            contador += 1
            Triple(contador, chest(contador.toDouble()), mutableMapOf())
        }
        assertEquals(PontoFixoIndireto.LIMITE_ITERACOES, resultado.iteracoes)
        assertEquals(30, resultado.estado)
        assertEquals(mapOf("ponto_fixo_nao_convergiu" to 1), resultado.disparos)
    }

    @Test
    fun `disparos da entrada persistem quando o estado escolhido no ciclo nao e o ultimo`() {
        // confirma que o disparo de oscilacao vai pro disparos do estado ESCOLHIDO (e1), nao do
        // ultimo calculado (e2) -- sao MutableMap distintos, por isso precisa dessa checagem.
        val map0 = zero
        val map1 = chest(5.0) // maior soma -> nao sera escolhido
        val map2 = chest(1.0) // menor soma -> sera escolhido, ciclando de volta pra map1... 
        // ajuste: forcamos o ciclo a devolver pro valor de ENTRADA map1, cujo "e1" tem a maior soma
        val resultado = PontoFixoIndireto.iterar<String> { indireto ->
            when (indireto) {
                map0 -> Triple("e0", map1, mutableMapOf("algo_de_e0" to 1))
                map1 -> Triple("e1", map2, mutableMapOf("algo_de_e1" to 1))
                map2 -> Triple("e2", map1, mutableMapOf("algo_de_e2" to 1))
                else -> error("indireto inesperado: $indireto")
            }
        }
        // ciclo = [e1(novo=map2,soma=1.0), e2(novo=map1,soma=5.0)] -> e1 tem a menor soma -> escolhido
        assertEquals("e1", resultado.estado)
        assertEquals(mapOf("algo_de_e1" to 1, "ponto_fixo_oscilou" to 1), resultado.disparos)
    }
}
