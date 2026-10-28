package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.models.Ambiente
import dev.rafael.server.features.exercise.models.Exercise
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Motor v3, Fatia 9 — a varredura dos 720 perfis contra o oráculo, SEM Docker.
 *
 * É o critério de pronto registrado ao fechar a Fatia 6 (2026-10-23): o pipeline Kotlin inteiro
 * (`MotorV3Pipeline.gerar`) contra o mesmo `catalogo_banco.csv` do oráculo, comparado com
 * `referencia_720.txt` — que é a SAÍDA de `motor_v3.py::gerar()` para os 720 perfis, gerada por
 * `gerar_referencia_720.py` e mantida em dia por `ReferenciaDos720` no lado Python.
 *
 * Compara, por perfil: divisão, capacidade, iterações do ponto fixo, **cada vaga** (dia, alvo,
 * papel, séries, exercício, RIR), **cada músculo** (alocado, entregue, compostos, indireto e os
 * campos da auditoria da etapa [7], inclusive `pisoDiretoNegado`) e **os disparos**. Os dois
 * testes do revisor (contagem de disparos por regra; diferencial oráculo × motor) caem aqui: o
 * diferencial é zero por construção quando esta comparação passa.
 *
 * ## Onde o banco entra
 *
 * Aqui o catálogo vem do CSV. Que o BANCO bata com o CSV é o que o
 * `MotorV3FidelidadeCatalogoRealIntegrationTest` afirma (precisa de Docker). Os dois juntos fecham
 * oráculo == Kotlin sobre o catálogo real por transitividade, e cada um aponta a causa quando
 * quebra: este diz "o motor divergiu", o outro diz "o banco divergiu".
 *
 * ## Entradas do Gradle
 *
 * Este teste lê dois arquivos de fora de `src/test/kotlin`. Se o Gradle não souber disso,
 * `:server:test` dá `UP-TO-DATE` e **verde sem rodar** depois de uma mudança no oráculo — o mesmo
 * defeito de `:server:integrationTest` (débito pago em 2026-09-22). Os dois estão declarados em
 * `server/build.gradle.kts`.
 */
class MotorV3Varredura720Test {

    @Test
    fun `os 720 perfis batem com o oraculo vaga a vaga, musculo a musculo e disparo a disparo`() {
        val catalogo = Oraculo.catalogo()
        val referencia = Oraculo.referencia()

        val divergentes = mutableListOf<Pair<RefPerfil, List<String>>>()
        for (ref in referencia) {
            val resultado = MotorV3Pipeline.gerar(Oraculo.entradaDe(ref.id), catalogo)
            val dif = compara(ref, resultado)
            if (dif.isNotEmpty()) divergentes += ref to dif
        }

        if (divergentes.isNotEmpty()) {
            val porCategoria = divergentes.flatMap { (_, d) -> d.map { it.substringBefore(':').substringBefore(' ') } }
                .groupingBy { it }.eachCount().toSortedMap()
            fail(
                buildString {
                    appendLine("${divergentes.size}/${referencia.size} perfis divergiram do oráculo. Por categoria: $porCategoria")
                    appendLine("Se a mudança foi DE PROPÓSITO no oráculo, regenere (`python3 gerar_referencia_720.py`) e revise o diff.")
                    appendLine("Se foi só no Kotlin, o Kotlin está errado: o oráculo é a fonte.")
                    divergentes.take(6).forEach { (ref, d) ->
                        appendLine("--- ${ref.id}")
                        d.take(5).forEach { appendLine("    $it") }
                        if (d.size > 5) appendLine("    ... +${d.size - 5}")
                    }
                },
            )
        }
    }

    @Test
    fun `os invariantes valem nos 720 perfis sem olhar para o oraculo`() {
        // Independente da referência: se oráculo e Kotlin errarem JUNTOS, a comparação acima passa e
        // isto acusa. É a lição de 2026-10-27 (E10): dois lados podem concordar e estar errados.
        val catalogo = Oraculo.catalogo()
        val disparos = mutableMapOf<String, Int>()
        val violacoes = mutableListOf<String>()
        for (ref in Oraculo.referencia()) {
            val r = MotorV3Pipeline.gerar(Oraculo.entradaDe(ref.id), catalogo)
            violacoes += invariantes(r).map { "${ref.id}: $it" }
            r.disparos.forEach { (k, n) -> disparos.merge(k, n, Int::plus) }
        }
        if (violacoes.isNotEmpty()) {
            fail("${violacoes.size} violação(ões) de invariante:\n" + violacoes.take(10).joinToString("\n") { "  $it" })
        }
        // Regra de segurança que nunca dispara é código morto com cara de proteção.
        val mortas = REGRAS_DE_SEGURANCA.filter { (disparos[it] ?: 0) == 0 }
        assertTrue(mortas.isEmpty(), "regras de segurança sem nenhum disparo nos 720: $mortas (total: $disparos)")
        // Reauditoria da Fatia 7: inalcançável sem foco. Quando o foco (Fatia 10) entrar e disparar,
        // este assert fica vermelho -- é o sinal para mover a regra para REGRAS_DE_SEGURANCA.
        assertEquals(0, disparos["ombro_vira_isolamento"] ?: 0)
        assertEquals(0, disparos["ponto_fixo_nao_convergiu"] ?: 0)
    }

    @Test
    fun `o verificador de invariantes acusa capacidade estourada e status mentiroso`() {
        val ref = Oraculo.referencia().first()
        val r = MotorV3Pipeline.gerar(Oraculo.entradaDe(ref.id), Oraculo.catalogo())
        assertEquals(emptyList<String>(), invariantes(r))

        val estourado = r.copy(capacidade = 0.5)
        assertTrue(invariantes(estourado).any { it.startsWith("entregue") })

        val alvoAlto = r.auditoria.mapValues { (_, a) ->
            a.copy(status = ProgramAudit.Status.ATENDIDO, alvo = a.efetivo + 5.0, atingiuMinimo = false)
        }
        assertTrue(invariantes(r.copy(auditoria = alvoAlto)).any { it.startsWith("ATENDIDO") })
    }

    /** Os `[INV]` de `regras/01` que são afirmáveis sobre UM resultado, sem referência. */
    private fun invariantes(r: MotorV3Pipeline.Resultado): List<String> {
        val v = mutableListOf<String>()
        val entregue = r.estado.entregue.values.sum()
        if (entregue > r.capacidade + 1e-6) v += "entregue $entregue > capacidade ${r.capacidade}"
        if (r.auditoria.size != TargetMuscle.entries.size) v += "auditoria sem todos os músculos: ${r.auditoria.size}"
        for ((m, a) in r.auditoria) {
            if (a.status == ProgramAudit.Status.ATENDIDO && a.alvo > 0.0 && a.efetivo < a.alvo - 1e-6) {
                v += "ATENDIDO com efetivo ${a.efetivo} < alvo ${a.alvo} em $m"
            }
            if (a.status == ProgramAudit.Status.MANUTENCAO && a.efetivo < a.minimo - 1e-6) {
                v += "MANUTENCAO com efetivo ${a.efetivo} < piso ${a.minimo} em $m"
            }
            if (a.efetivo + 1e-6 < a.direto) v += "efetivo ${a.efetivo} < direto ${a.direto} em $m"
        }
        return v
    }

    private companion object {
        /** Espelha `REGRAS_DE_SEGURANCA` de `test_motor_v3.py`: as duas que PRECISAM disparar na matriz. */
        val REGRAS_DE_SEGURANCA = listOf("ombro_teto_do_desenvolvimento", "um_empurrar_com_barra_por_sessao")
    }

    @Test
    fun `a referencia traz 720 perfis unicos e nenhum comparador vazio, e o catalogo tem as 147 linhas`() {
        // Caminho de falha da própria varredura: uma referência truncada ou um catálogo vazio
        // passariam o teste acima com "0 divergências" sem ter comparado nada.
        val referencia = Oraculo.referencia()
        assertEquals(720, referencia.size)
        assertEquals(720, referencia.map { it.id }.toSet().size, "perfil duplicado na referência")
        assertEquals(720 * TargetMuscle.entries.size, referencia.sumOf { it.musculos.size })
        assertTrue(referencia.sumOf { it.vagas.size } > 10_000, "a referência quase não tem vagas")
        assertEquals(147, Oraculo.catalogo().size)
    }

    @Test
    fun `o comparador acusa uma vaga trocada e um disparo a mais`() {
        // Sem isto, um `compara()` que devolvesse sempre vazio faria a varredura inteira passar.
        val ref = Oraculo.referencia().first()
        val resultado = MotorV3Pipeline.gerar(Oraculo.entradaDe(ref.id), Oraculo.catalogo())
        assertEquals(emptyList(), compara(ref, resultado), "linha de base: o primeiro perfil tem de bater")

        val vagaTrocada = ref.copy(vagas = ref.vagas.mapIndexed { i, v -> if (i == 0) v.copy(exercicio = "Exercício que não existe") else v })
        assertTrue(compara(vagaTrocada, resultado).any { it.startsWith("vaga") })

        val disparoExtra = ref.copy(disparos = ref.disparos + ("regra_inventada" to 1))
        assertTrue(compara(disparoExtra, resultado).any { it.startsWith("disparos") })

        val musculoErrado = ref.copy(musculos = ref.musculos.mapIndexed { i, m -> if (i == 0) m.copy(entregue = m.entregue + 1) else m })
        assertTrue(compara(musculoErrado, resultado).any { it.startsWith("musculo") })
    }

    @Test
    fun `o divisor de CSV trata virgula dentro de aspas como um campo so, e recusa aspas sem fechar`() {
        assertEquals(listOf("a", "CHEST,BACK", "c"), Oraculo.dividirCsv("a,\"CHEST,BACK\",c"))
        assertEquals(listOf("x", "ele disse \"oi\"", "z"), Oraculo.dividirCsv("x,\"ele disse \"\"oi\"\"\",z"))
        assertEquals(listOf("", "b", ""), Oraculo.dividirCsv(",b,"))
        assertFailsWith<IllegalStateException> { Oraculo.dividirCsv("a,\"sem fechar,c") }
        // E o caso real que motivou: as 14 linhas aspeadas leem o mesmo nº de colunas do cabeçalho.
        val linhas = Oraculo.arquivo("catalogo_banco.csv").readText().lines().filter { it.isNotBlank() }
        val colunas = Oraculo.dividirCsv(linhas.first()).size
        assertEquals(emptyList(), linhas.drop(1).filter { Oraculo.dividirCsv(it).size != colunas })
    }

    @Test
    fun `o leitor da referencia recusa registro desconhecido, linha curta e vaga sem perfil`() {
        assertFailsWith<IllegalStateException> { Oraculo.lerReferencia("X|qualquer coisa") }
        assertFailsWith<IllegalStateException> { Oraculo.lerReferencia("P|id|FULL_BODY|9.0") }
        assertFailsWith<IllegalStateException> { Oraculo.lerReferencia("V|Dia|CHEST|COMPOSTO|3|Supino|3") }
        assertEquals(emptyList(), Oraculo.lerReferencia("# só comentário\n\n"))
    }

    // ------------------------------------------------------------------------------ comparação

    private fun compara(ref: RefPerfil, r: MotorV3Pipeline.Resultado): List<String> {
        val dif = mutableListOf<String>()
        if (ref.divisao != r.divisao) dif += "cabecalho: divisao ${ref.divisao} != ${r.divisao}"
        if (!perto(ref.capacidade, r.capacidade)) dif += "cabecalho: capacidade ${ref.capacidade} != ${r.capacidade}"
        if (ref.iteracoes != r.iteracoes) dif += "cabecalho: iteracoes ${ref.iteracoes} != ${r.iteracoes}"

        val produzidas = r.estado.sessoes.flatMap { (dia, vagas) ->
            vagas.map { vp ->
                RefVaga(dia, vp.vaga.alvo.name, vp.vaga.papel.name, vp.vaga.series, vp.exercicio?.name.orEmpty(), vp.rir)
            }
        }
        if (produzidas.size != ref.vagas.size) {
            dif += "vagas: ${ref.vagas.size} esperadas, ${produzidas.size} produzidas"
        }
        for (i in 0 until minOf(produzidas.size, ref.vagas.size)) {
            if (produzidas[i] != ref.vagas[i]) dif += "vaga[$i]: esperado ${ref.vagas[i]} produzido ${produzidas[i]}"
        }

        for (m in ref.musculos) {
            val alvo = TargetMuscle.valueOf(m.musculo)
            val a = r.auditoria.getValue(alvo)
            val obtido = RefMusculo(
                musculo = m.musculo, alvo = a.alvo,
                alocado = r.estado.alocado[alvo] ?: 0, entregue = r.estado.entregue[alvo] ?: 0,
                compostos = r.estado.compostosSemana[alvo] ?: 0, indireto = r.indireto[alvo] ?: 0.0,
                status = a.status.name, direto = a.direto, efetivo = a.efetivo, minimo = a.minimo,
                razao = a.razao, atingiuMinimo = a.atingiuMinimo, pisoDiretoNegado = a.pisoDiretoNegado,
            )
            if (!igual(m, obtido)) dif += "musculo ${m.musculo}: esperado $m obtido $obtido"
        }

        val obtidos = r.disparos.filterValues { it != 0 }
        if (obtidos != ref.disparos) {
            val soEsperado = ref.disparos.filter { (k, v) -> obtidos[k] != v }
            val soObtido = obtidos.filter { (k, v) -> ref.disparos[k] != v }
            dif += "disparos: oráculo $soEsperado Kotlin $soObtido"
        }
        return dif
    }

    private fun perto(a: Double, b: Double) = abs(a - b) < 1e-5

    private fun igual(e: RefMusculo, o: RefMusculo): Boolean =
        perto(e.alvo, o.alvo) && e.alocado == o.alocado && e.entregue == o.entregue &&
            e.compostos == o.compostos && perto(e.indireto, o.indireto) && e.status == o.status &&
            e.direto == o.direto && perto(e.efetivo, o.efetivo) && perto(e.minimo, o.minimo) &&
            (e.razao == null) == (o.razao == null) &&
            (e.razao == null || perto(e.razao, o.razao!!)) &&
            e.atingiuMinimo == o.atingiuMinimo && e.pisoDiretoNegado == o.pisoDiretoNegado
}

// ---------------------------------------------------------------------------------------- dados

internal data class RefVaga(
    val dia: String, val alvo: String, val papel: String, val series: Int,
    val exercicio: String, val rir: Int?,
)

internal data class RefMusculo(
    val musculo: String, val alvo: Double, val alocado: Int, val entregue: Int, val compostos: Int,
    val indireto: Double, val status: String, val direto: Int, val efetivo: Double, val minimo: Double,
    val razao: Double?, val atingiuMinimo: Boolean, val pisoDiretoNegado: Boolean,
)

internal data class RefPerfil(
    val id: String, val divisao: String, val teto: Double, val capacidade: Double, val iteracoes: Int,
    val vagas: List<RefVaga>, val musculos: List<RefMusculo>, val disparos: Map<String, Int>,
)

/**
 * Onde mora o que o oráculo produz, e como lê. Gradle roda o teste com o diretório do módulo
 * (`server/`) como cwd; a IDE às vezes roda da raiz — por isso os dois candidatos.
 */
internal object Oraculo {
    private val pastas = listOf("src/test/oracle", "server/src/test/oracle")

    fun arquivo(nome: String): File =
        pastas.map { File(it, nome) }.firstOrNull { it.isFile }
            ?: error("$nome não encontrado em ${pastas.joinToString()} (cwd=${File(".").absoluteFile.normalize()})")

    /**
     * Divide uma linha de CSV respeitando aspas (`"CHEST,BACK"` é UM campo). O exportador usa
     * `FORMAT csv`, que aspeia o campo que contém vírgula — 14 das 147 linhas
     * (`primary_muscles` com mais de um músculo). Medido em 2026-10-27: o `split(",")` ingênuo lia
     * colunas erradas nessas 14.
     */
    fun dividirCsv(linha: String): List<String> {
        val campos = mutableListOf<String>()
        val atual = StringBuilder()
        var entreAspas = false
        var i = 0
        while (i < linha.length) {
            val ch = linha[i]
            when {
                ch == '"' && entreAspas && i + 1 < linha.length && linha[i + 1] == '"' -> { atual.append('"'); i++ }
                ch == '"' -> entreAspas = !entreAspas
                ch == ',' && !entreAspas -> { campos += atual.toString(); atual.clear() }
                else -> atual.append(ch)
            }
            i++
        }
        check(!entreAspas) { "CSV: aspas sem fechar em: $linha" }
        campos += atual.toString()
        return campos
    }

    /** `catalogo_banco.csv` → catálogo, pela MESMA fábrica dos demais testes do motor. */
    fun catalogo(): List<Exercise> {
        val linhas = arquivo("catalogo_banco.csv").readText().lines().filter { it.isNotBlank() }
        val cab = dividirCsv(linhas.first())
        return linhas.drop(1).map { l ->
            val c = dividirCsv(l)
            check(c.size == cab.size) { "catalogo_banco.csv: ${c.size} colunas, o cabeçalho tem ${cab.size}: $l" }
            val r = cab.zip(c).toMap()
            fun flag(k: String) = r.getValue(k) == "t"
            exercicioV3(
                name = r.getValue("name"),
                alvo = TargetMuscle.valueOf(r.getValue("volume_target")),
                composto = flag("is_compound"),
                equipamento = r.getValue("equipment"),
                familia = r.getValue("familia"),
                prioridade = r.getValue("prioridade").toInt(),
                relevancia = r.getValue("relevancia").toInt(),
                nivel = Level.valueOf(r.getValue("level")),
                nivelMaximo = Level.valueOf(r.getValue("nivel_maximo")),
                ambiente = Ambiente.valueOf(r.getValue("ambiente")),
                acessorios = buildSet {
                    if (flag("requires_bench")) add(Accessory.BENCH)
                    if (flag("requires_elevated_surface")) add(Accessory.ELEVATED_SURFACE)
                    if (flag("requires_pullup_bar")) add(Accessory.PULLUP_BAR)
                    if (flag("requires_dip_station")) add(Accessory.DIP_STATION)
                    if (flag("requires_step")) add(Accessory.STEP)
                    if (flag("requires_anchor_point")) add(Accessory.ANCHOR_POINT)
                },
                rirMinimoSeguranca = flag("rir_minimo_seguranca"),
            )
        }
    }

    fun referencia(): List<RefPerfil> = lerReferencia(arquivo("referencia_720.txt").readText())

    /** `OBJETIVO-NIVEL-4d-60m-30a-AMBIENTE` → [MotorV3Pipeline.Entrada]. */
    fun entradaDe(id: String): MotorV3Pipeline.Entrada {
        val p = id.split("-")
        check(p.size == 6) { "id de perfil malformado: $id" }
        val ambiente = TrainingEnvironment.valueOf(p[5])
        return MotorV3Pipeline.Entrada(
            objetivo = Goal.valueOf(p[0]), nivel = Level.valueOf(p[1]),
            dias = p[2].removeSuffix("d").toInt(), minutos = p[3].removeSuffix("m").toInt(),
            idade = p[4].removeSuffix("a").toInt(), mesocicloAtual = null,
            ambiente = ambiente,
            // Mesma regra do produto: academia tem tudo, casa vale o que foi declarado (nada).
            acessorios = Accessory.paraAmbiente(ambiente, emptyList()),
        )
    }

    fun lerReferencia(texto: String): List<RefPerfil> {
        class Parcial(
            val id: String, val divisao: String, val teto: Double, val capacidade: Double, val iteracoes: Int,
        ) {
            val vagas = mutableListOf<RefVaga>()
            val musculos = mutableListOf<RefMusculo>()
            var disparos: Map<String, Int> = emptyMap()
            fun pronto() = RefPerfil(id, divisao, teto, capacidade, iteracoes, vagas, musculos, disparos)
        }

        val perfis = mutableListOf<Parcial>()
        for ((n, linha) in texto.lines().withIndex()) {
            if (linha.isBlank() || linha.startsWith("#")) continue
            val c = linha.split("|")
            fun exige(campos: Int) = check(c.size == campos) { "referência linha ${n + 1}: ${c.size} campos, ${c[0]} pede $campos: $linha" }
            val atual = perfis.lastOrNull()
            when (c[0]) {
                "P" -> {
                    exige(6)
                    perfis += Parcial(c[1], c[2], c[3].toDouble(), c[4].toDouble(), c[5].toInt())
                }
                "V" -> {
                    exige(7)
                    checkNotNull(atual) { "referência linha ${n + 1}: vaga antes de qualquer perfil" }
                        .vagas += RefVaga(c[1], c[2], c[3], c[4].toInt(), c[5], c[6].takeIf { it.isNotEmpty() }?.toInt())
                }
                "M" -> {
                    exige(14)
                    checkNotNull(atual) { "referência linha ${n + 1}: músculo antes de qualquer perfil" }
                        .musculos += RefMusculo(
                            musculo = c[1], alvo = c[2].toDouble(), alocado = c[3].toInt(), entregue = c[4].toInt(),
                            compostos = c[5].toInt(), indireto = c[6].toDouble(), status = c[7], direto = c[8].toInt(),
                            efetivo = c[9].toDouble(), minimo = c[10].toDouble(), razao = c[11].takeIf { it.isNotEmpty() }?.toDouble(),
                            atingiuMinimo = c[12] == "1", pisoDiretoNegado = c[13] == "1",
                        )
                }
                "D" -> {
                    exige(2)
                    checkNotNull(atual) { "referência linha ${n + 1}: disparos antes de qualquer perfil" }.disparos =
                        c[1].split(";").filter { it.isNotEmpty() }.associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toInt() }
                }
                else -> error("referência linha ${n + 1}: registro desconhecido '${c[0]}'")
            }
        }
        return perfis.map { it.pronto() }
    }
}
