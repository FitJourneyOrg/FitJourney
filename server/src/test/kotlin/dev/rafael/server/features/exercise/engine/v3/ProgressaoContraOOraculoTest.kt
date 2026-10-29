package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Evento
import dev.rafael.server.features.exercise.engine.v3.PromocaoDeNivel.Diagnostico
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.exercise.engine.v3.Progressao.Estado
import dev.rafael.server.features.exercise.engine.v3.Progressao.Serie
import dev.rafael.server.features.exercise.engine.v3.SessionComposition.Papel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Motor v3, Fatia 10 — o núcleo de progressão contra o oráculo, SEM Docker.
 *
 * Mesma ideia da varredura dos 720 aplicada a funções puras: `referencia_progressao.txt` é a SAÍDA
 * de `progressao_v3.py` sobre grades de entradas (cargas, RIRs, históricos, dias de pausa), gerada
 * por `gerar_referencia_progressao.py` e mantida em dia pelo lado Python. Este teste reproduz cada
 * linha com o Kotlin e compara: arredondamento, salto da faixa, calibração, redução no dia, 1RM,
 * as ~5 mil decisões entre sessões, o ciclo, a máquina do iniciante e o retorno após pausa.
 *
 * Os casos escritos à mão (que dizem o PORQUÊ de cada regra) estão em [ProgressaoTest],
 * [MesocicloTest] e [AncoraEPausaTest]. Os dois juntos fecham a lição da Fatia 9: dois lados
 * concordando não é prova — a grade acusa divergência, os casos à mão acusam erro comum.
 *
 * Lê um arquivo de fora de `src/test/kotlin`; está em `inputs.files` do Gradle
 * (`server/build.gradle.kts`, `oraculoDoMotor`) pelo mesmo motivo do `MotorV3Varredura720Test`.
 */
class ProgressaoContraOOraculoTest {

    @Test
    fun `cada caso da referencia da progressao bate com o oraculo`() {
        val divergencias = Oraculo.linhasDaProgressao().mapNotNull { confere(it) }
        if (divergencias.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("${divergencias.size} caso(s) divergiram do oráculo (de ${Oraculo.linhasDaProgressao().size}).")
                    appendLine("Se a mudança foi DE PROPÓSITO no oráculo, regenere (`python3 gerar_referencia_progressao.py`) e revise o diff.")
                    appendLine("Se foi só no Kotlin, o Kotlin está errado: o oráculo é a fonte.")
                    divergencias.take(8).forEach { appendLine("  $it") }
                },
            )
        }
    }

    @Test
    fun `a grade tem todos os tipos de caso e nenhuma linha vazia`() {
        // Caminho de falha da própria varredura: uma referência truncada passaria com "0 divergências".
        val tipos = Oraculo.linhasDaProgressao().groupingBy { it.substringBefore('|') }.eachCount()
        assertEquals(
            setOf("PI", "SF", "PC", "RI", "RM", "DE", "SD", "CS", "DP", "IN", "RE", "AR", "PD", "PP", "PA", "PN"),
            tipos.keys,
        )
        assertEquals(6912, tipos["DE"])
        assertEquals(2730, tipos["IN"])
        assertTrue(Oraculo.linhasDaProgressao().size > 10_000, "a referência da progressão quase não tem casos")
    }

    @Test
    fun `as tres decisoes aparecem na grade, senao ela nao exercita o desfecho`() {
        val decisoes = Oraculo.linhasDaProgressao().filter { it.startsWith("DE|") }.map { it.split("|")[10] }.toSet()
        assertEquals(Decisao.entries.map { it.name }.toSet(), decisoes)
    }

    @Test
    fun `o comparador acusa um valor trocado e uma linha desconhecida`() {
        // Sem isto, um `confere()` que devolvesse sempre null faria a grade inteira passar.
        val linha = Oraculo.linhasDaProgressao().first { it.startsWith("DE|") }
        assertEquals(null, confere(linha), "linha de base: o primeiro caso tem de bater")
        val campos = linha.split("|").toMutableList()
        campos[11] = "999.000000"
        assertTrue(confere(campos.joinToString("|"))!!.startsWith("DE"))

        val piErrado = Oraculo.linhasDaProgressao().first { it.startsWith("PI|") }.split("|").toMutableList()
        piErrado[3] = "123.000000"
        assertTrue(confere(piErrado.joinToString("|"))!!.startsWith("PI"))

        assertFailsWith<IllegalStateException> { confere("XX|qualquer coisa") }
        assertFailsWith<IllegalStateException> { confere("PI|1.0|2.0") }
    }

    // ------------------------------------------------------------------------------ reprodução

    private fun perto(a: Double?, b: Double?): Boolean =
        if (a == null || b == null) a == b else abs(a - b) < 1e-5

    private fun d(s: String): Double? = s.takeIf { it.isNotEmpty() }?.toDouble()

    private fun b(s: String) = s == "1"

    /** Diagnóstico de promoção opcional: `""` = `null`, senão o nome do enum. */
    private fun diag(s: String): Diagnostico? = s.takeIf { it.isNotEmpty() }?.let { Diagnostico.valueOf(it) }

    /** RIR do arquivo: `-` = não informado (D-3). */
    private fun r(s: String): Int? = s.toIntOrNull()

    private fun series(s: String): List<Serie> =
        s.split(";").map { par -> par.split(":").let { Serie(it[0].toInt(), it[1].toIntOrNull()) } }  // "-" = RIR nulo

    private fun eventos(letras: String): List<Evento> = letras.map {
        when (it) {
            'S' -> Evento.Carga(Decisao.SOBE)
            'M' -> Evento.Carga(Decisao.MANTEM)
            'D' -> Evento.Carga(Decisao.DESCE)
            'L' -> Evento.Deload
            else -> error("letra de histórico desconhecida: $it")
        }
    }

    /** `null` = bateu; senão, a descrição da divergência (começa pelo tipo do caso). */
    private fun confere(linha: String): String? {
        val c = linha.split("|")
        fun exige(n: Int) = check(c.size == n) { "${c[0]}: ${c.size} campos, esperava $n: $linha" }
        fun falha(vararg esperadoObtido: Pair<Any?, Any?>): String? {
            val dif = esperadoObtido.filter { (e, o) -> if (e is Double? && o is Double?) !perto(e, o) else e != o }
            return if (dif.isEmpty()) null else "${c[0]}: $linha -> Kotlin deu ${esperadoObtido.map { it.second }}"
        }
        return when (c[0]) {
            "PI" -> { exige(4); falha(d(c[3]) to Progressao.paraIncremento(d(c[1])!!, d(c[2])!!)) }
            "SF" -> { exige(4); falha(d(c[3]) to Progressao.saltoDaFaixa(c[1].toInt(), b(c[2]))) }
            "PC" -> {
                exige(9)
                val p = Progressao.passoCalibracao(d(c[1])!!, r(c[2]), c[3].toInt(), c[4].toInt(), b(c[5]), d(c[6])!!)
                falha(d(c[7]) to p.carga, b(c[8]) to p.terminou)
            }
            "RI" -> {
                exige(7)
                val (nova, anulada) = Progressao.reducaoIntrasessao(d(c[1])!!, r(c[2]), c[3].toInt(), d(c[4])!!)
                falha(d(c[5]) to nova, b(c[6]) to anulada)
            }
            "RM" -> {
                exige(6)
                val um = Progressao.estimarUmRm(d(c[1])!!, listOf(Serie(c[2].toInt(), r(c[3]))), Papel.valueOf(c[4]))
                falha(d(c[5]) to um)
            }
            "DE" -> {
                exige(16)
                val estado = Estado(d(c[5]), c[6].toInt(), b(c[7]), null, c[8].toInt())
                val (novo, decisao) = Progressao.decidir(
                    estado, series(c[9]), Papel.valueOf(c[2]), Goal.valueOf(c[1]), d(c[3])!!, b(c[4]),
                )
                falha(
                    c[10] to decisao.name, d(c[11]) to novo.carga, c[12].toInt() to novo.exposicoes,
                    b(c[13]) to novo.posSalto, d(c[14]) to novo.umRm, c[15].toInt() to novo.semSubir,
                )
            }
            "SD" -> { exige(4); falha(c[3].toInt() to CicloDoMesociclo.seriesDoDia(c[1].toInt(), b(c[2]))) }
            "CS" -> {
                exige(7)
                val m = CicloDoMesociclo.concluirSemana(Mesociclo(c[1].toInt(), c[2].toInt(), b(c[3])))
                falha(c[4].toInt() to m.numero, c[5].toInt() to m.semana, b(c[6]) to m.conservador)
            }
            "DP" -> {
                exige(5)
                falha(b(c[4]) to CicloDoMesociclo.deloadProgramado(Level.valueOf(c[1]), Mesociclo(c[2].toInt(), c[3].toInt())))
            }
            "IN" -> { exige(4); falha(c[3] to AncoraDoIniciante.situacao(eventos(c[1]), b(c[2])).name) }
            "RE" -> {
                exige(11)
                val r = RetornoAposPausa.apos(c[1].toInt(), Mesociclo(c[2].toInt(), c[3].toInt(), b(c[4])), c[5].toInt())
                falha(
                    c[6].toInt() to r.mesociclo.numero, c[7].toInt() to r.mesociclo.semana,
                    b(c[8]) to r.mesociclo.conservador, b(c[9]) to r.recalibra, d(c[10]) to r.fatorCarga,
                )
            }
            "AR" -> {
                exige(13)
                val e = Estado(d(c[1]), c[2].toInt(), b(c[3]), d(c[4]), c[5].toInt())
                val r = RetornoAposPausa.apos(c[7].toInt(), Mesociclo())
                val n = RetornoAposPausa.aplicar(e, r, d(c[6])!!)
                falha(d(c[8]) to n.carga, c[9].toInt() to n.exposicoes, b(c[10]) to n.posSalto, d(c[11]) to n.umRm, c[12].toInt() to n.semSubir)
            }
            "PD" -> { exige(3); falha(diag(c[2]) to PromocaoDeNivel.diagnosticoIniciante(eventos(c[1]))) }
            "PP" -> {
                exige(3)
                val letraParaDiag = mapOf('N' to null, 'P' to Diagnostico.PROMOVER, 'R' to Diagnostico.RECUPERACAO, 'S' to Diagnostico.RECUPERACAO_SEM_RETOMADA)
                val porAncora = c[1].map { letraParaDiag.getValue(it) }
                falha(diag(c[2]) to PromocaoDeNivel.diagnosticoDoPerfil(porAncora))
            }
            "PA" -> {
                exige(6)
                val r = PromocaoDeNivel.diagnosticoAvancado(d(c[1]), d(c[2]), c[3].toInt(), c[4].toInt())
                falha(diag(c[5]) to r)
            }
            "PN" -> {
                exige(4)
                falha(b(c[3]) to PromocaoDeNivel.podeSugerirDeNovo(c[1].toInt(), c[2].toIntOrNull()))
            }
            else -> error("referência da progressão: registro desconhecido '${c[0]}'")
        }
    }
}
