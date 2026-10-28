package dev.rafael.server.exercise

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExerciseRepositoryImpl
import dev.rafael.server.features.exercise.models.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File

/**
 * Motor v3, Fatia 9 — o BANCO bate com o catálogo que o oráculo usou.
 *
 * ## O que este teste era, e por que mudou
 *
 * Até 2026-10-27 ele rodava 12 perfis contra o catálogo real e comparava com um bloco de 245
 * linhas escrito dentro deste arquivo, gerado em 23/10. A Fatia 8 mudou o oráculo e os 12 perfis
 * passaram a divergir, todos — sem que nada acusasse, porque `:server:integrationTest` não rodou.
 * Referência embutida em teste envelhece em silêncio.
 *
 * ## O que ele afirma agora
 *
 * Uma coisa só, a que só o banco pode afirmar: **as linhas da lista principal do Postgres (as que
 * têm `volume_target`) são as 147 de `src/test/oracle/catalogo_banco.csv`, campo a campo** —
 * exatamente os campos que o motor lê. A varredura dos 720 perfis mora em
 * `MotorV3Varredura720Test` (`:server:test`, sem Docker) e roda o pipeline Kotlin sobre esse mesmo
 * CSV contra a saída do oráculo. Os dois juntos dão oráculo == Kotlin sobre o catálogo real por
 * transitividade, e cada um aponta a causa: lá "o motor divergiu", aqui "o banco divergiu".
 *
 * Por isso também passa pelo `ExerciseRepositoryImpl.findAll()` REAL: o que está sob teste inclui
 * o `toExercise()` (o mapeamento de coluna para campo, onde um enum mal casado some em silêncio).
 *
 * Quando falha, a pergunta é qual dos dois mudou: uma migration de curadoria nova pede regenerar o
 * CSV (`exportar_catalogo.sql`) e a referência (`gerar_referencia_720.py`) e revisar o diff.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MotorV3FidelidadeCatalogoRealIntegrationTest {

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    /** Os campos que o motor v3 lê — nem mais, nem menos. */
    private data class Linha(
        val nome: String,
        val alvo: String?,
        val composto: Boolean?,
        val equipamento: String?,
        val nivel: String?,
        val nivelMaximo: String?,
        val ambiente: String?,
        val familia: String?,
        val prioridade: Int?,
        val relevancia: Int?,
        val acessorios: Set<String>,
        val rirMinimoSeguranca: Boolean,
    )

    @Test
    fun `a lista principal do banco tem as mesmas 147 linhas do catalogo_banco_csv, campo a campo`() = runBlocking {
        val doBanco = (ExerciseRepositoryImpl().findAll(Idioma.PT_BR) as AppResult.Success).value
            .filter { it.volumeTarget != null }
            .map(::deBanco)
        val doCsv = lerCsv(arquivoDoOraculo("catalogo_banco.csv").readText())

        assertEquals(147, doCsv.size, "o CSV do oráculo mudou de tamanho: regenere a referência e revise")
        val dif = diferencas(esperado = doCsv, obtido = doBanco)
        assertTrue(dif.isEmpty()) {
            "O banco divergiu do catálogo que o oráculo usou (${dif.size} diferença(s)):\n" +
                dif.take(15).joinToString("\n") { "  $it" } +
                (if (dif.size > 15) "\n  ... +${dif.size - 15}" else "") +
                "\nSe a curadoria mudou de propósito: regenere catalogo_banco.csv (exportar_catalogo.sql) e " +
                "referencia_720.txt (gerar_referencia_720.py) e revise o diff."
        }
    }

    @Test
    fun `o comparador acusa linha faltando, linha sobrando e campo diferente`() {
        // Sem isto, um `diferencas()` que devolvesse sempre vazio passaria o teste acima.
        val base = listOf(linha("A", "CHEST"), linha("B", "BACK"))
        assertEquals(emptyList<String>(), diferencas(base, base))

        assertTrue(diferencas(base, base.take(1)).any { it.startsWith("falta no banco: B") })
        assertTrue(diferencas(base, base + linha("C", "QUADS")).any { it.startsWith("sobra no banco: C") })
        val campo = base.map { if (it.nome == "A") it.copy(prioridade = 99) else it }
        assertTrue(diferencas(base, campo).any { it.startsWith("A: prioridade") })
    }

    // ------------------------------------------------------------------------------ comparação

    private fun linha(nome: String, alvo: String) = Linha(
        nome, alvo, true, "MACHINE", "BEGINNER", "ADVANCED", "ACADEMIA", nome, 1, 1, emptySet(), false,
    )

    private fun diferencas(esperado: List<Linha>, obtido: List<Linha>): List<String> {
        val dif = mutableListOf<String>()
        val e = esperado.associateBy { it.nome }
        val o = obtido.associateBy { it.nome }
        (e.keys - o.keys).sorted().forEach { dif += "falta no banco: $it" }
        (o.keys - e.keys).sorted().forEach { dif += "sobra no banco: $it" }
        for (nome in (e.keys intersect o.keys).sorted()) {
            val a = e.getValue(nome)
            val b = o.getValue(nome)
            if (a == b) continue
            fun campo(rotulo: String, x: Any?, y: Any?) { if (x != y) dif += "$nome: $rotulo esperado=$x banco=$y" }
            campo("alvo", a.alvo, b.alvo)
            campo("composto", a.composto, b.composto)
            campo("equipamento", a.equipamento, b.equipamento)
            campo("nivel", a.nivel, b.nivel)
            campo("nivelMaximo", a.nivelMaximo, b.nivelMaximo)
            campo("ambiente", a.ambiente, b.ambiente)
            campo("familia", a.familia, b.familia)
            campo("prioridade", a.prioridade, b.prioridade)
            campo("relevancia", a.relevancia, b.relevancia)
            campo("acessorios", a.acessorios, b.acessorios)
            campo("rirMinimoSeguranca", a.rirMinimoSeguranca, b.rirMinimoSeguranca)
        }
        return dif
    }

    private fun deBanco(e: Exercise) = Linha(
        nome = e.name,
        alvo = e.volumeTarget,
        composto = e.isCompound,
        equipamento = e.equipment,
        nivel = e.level?.name,
        nivelMaximo = e.nivelMaximo?.name,
        ambiente = e.ambiente?.name,
        familia = e.familia,
        prioridade = e.prioridade,
        relevancia = e.relevancia,
        acessorios = e.acessorios.map { it.name }.toSet(),
        rirMinimoSeguranca = e.rirMinimoSeguranca,
    )

    /**
     * Divide uma linha de CSV respeitando aspas (`"CHEST,BACK"` é UM campo). O exportador usa
     * `FORMAT csv`, que aspeia o campo que contém vírgula — 14 das 147 linhas
     * (`primary_muscles` com mais de um músculo). Medido em 2026-10-27: o `split(",")` ingênuo lia
     * colunas erradas nessas 14.
     */
    private fun dividirCsv(linha: String): List<String> {
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

    /** Mesmas colunas e mesma tradução `requires_*` → acessório do `MotorV3Varredura720Test`. */
    private fun lerCsv(texto: String): List<Linha> {
        val linhas = texto.lines().filter { it.isNotBlank() }
        val cab = dividirCsv(linhas.first())
        return linhas.drop(1).map { l ->
            val c = dividirCsv(l)
            check(c.size == cab.size) { "catalogo_banco.csv: ${c.size} colunas, o cabeçalho tem ${cab.size}: $l" }
            val r = cab.zip(c).toMap()
            fun flag(k: String) = r.getValue(k) == "t"
            Linha(
                nome = r.getValue("name"),
                alvo = r.getValue("volume_target"),
                composto = flag("is_compound"),
                equipamento = r.getValue("equipment"),
                nivel = r.getValue("level"),
                nivelMaximo = r.getValue("nivel_maximo"),
                ambiente = r.getValue("ambiente"),
                familia = r.getValue("familia"),
                prioridade = r.getValue("prioridade").toInt(),
                relevancia = r.getValue("relevancia").toInt(),
                acessorios = buildSet {
                    if (flag("requires_bench")) add("BENCH")
                    if (flag("requires_elevated_surface")) add("ELEVATED_SURFACE")
                    if (flag("requires_pullup_bar")) add("PULLUP_BAR")
                    if (flag("requires_dip_station")) add("DIP_STATION")
                    if (flag("requires_step")) add("STEP")
                    if (flag("requires_anchor_point")) add("ANCHOR_POINT")
                },
                rirMinimoSeguranca = flag("rir_minimo_seguranca"),
            )
        }
    }

    /** Gradle roda com o módulo (`server/`) como cwd; a IDE às vezes roda da raiz. */
    private fun arquivoDoOraculo(nome: String): File =
        listOf("src/test/oracle", "server/src/test/oracle").map { File(it, nome) }.firstOrNull { it.isFile }
            ?: error("$nome não encontrado em src/test/oracle (cwd=${File(".").absoluteFile.normalize()})")
}
