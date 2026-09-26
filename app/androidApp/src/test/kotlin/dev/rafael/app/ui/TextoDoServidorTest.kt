package dev.rafael.app.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ⚠️ **Onde texto do SERVIDOR, em português, ainda chega à tela** (fatia G.3, ARCH #37).
 *
 * ## Por que este arquivo existe
 *
 * A G.2 tirou 103 frases do servidor porque **ele não sabe em que idioma a tela está**. A G.3
 * tirou os literais do cliente pelo mesmo motivo. Mas alguns pontos continuam recebendo a frase
 * pronta do servidor, e todos por decisão registrada:
 *
 * | Onde | O quê | Por quê |
 * |---|---|---|
 * | `ErrorUi` | `AppError.message` | código desconhecido, ou um dos nove de `SEM_TEXTO_PROPRIO` |
 * | `ProgramGenerate` | `GenerateError.Other.message` | falha sem código próprio |
 * | `WorkoutDetail` | erro ao buscar alternativas | idem |
 *
 * Quando o inglês entrar, **estes pontos aparecerão em português**. Isso é sabido e é o que este
 * teste mantém contável.
 *
 * `ProgramDetail` e `ProgramReveal` saíram desta lista na fatia "rationale derivado"
 * (2026-09-22): o `rationale` parou de vir pronto do servidor -- quem monta a frase agora é o
 * cliente, via `rationaleDoPrograma()`.
 *
 * > **Débito que ninguém consegue enumerar não é débito, é surpresa.**
 *
 * ## Por que contar chamadas em vez de testar comportamento
 *
 * Porque o defeito não é de comportamento: cada um destes pontos funciona. O risco é
 * **crescerem sem ninguém notar** — mais uma tela renderizando `message` cru, e a lista que o
 * Rafael valida no portão do pt-BR passa a mentir por omissão.
 *
 * O [Frase.DoServidor] é o que torna isso verificável: a fronteira virou tipo, e tipo dá para
 * procurar. Um ponto novo quebra o build e obriga a decisão a ficar escrita; um ponto resolvido
 * quebra também, e a lista encolhe junto.
 *
 * ## O que este teste NÃO cobre, e por que deixou de precisar
 *
 * O `erroDoCampo` chegou a ser a mesma classe de débito — `fieldErrors` era um mapa aberto, sem
 * código, e a tela mostrava a frase crua do servidor. Fechado em 2026-09-23 (P2): o valor de
 * `fieldErrors` virou CÓDIGO, igual ao `code` de nível superior, e `erroDoCampo` resolve via
 * `TextosDeErro`, como todo o resto do catálogo — ver o KDoc dele em `ErrorUi.kt`. Continua fora
 * desta lista porque já não constrói `Frase.DoServidor` nenhuma: o fallback dele (código
 * desconhecido) devolve o código cru, não uma frase do servidor.
 */
class TextoDoServidorTest {

    /**
     * Cada arquivo do app que constrói uma [Frase.DoServidor], e quantas vezes.
     *
     * Mexeu num destes? Confira se o número ainda bate. Ponto novo? Ou ele tem código de erro
     * (e aí vira `Frase.Recurso`), ou entra aqui com a razão escrita.
     */
    private val esperado = mapOf(
        // A política de apresentação de erro: os quatro ramos que usam `message` do servidor,
        // mais o fallback do 404 sem mensagem útil.
        "ui/ErrorUi.kt" to 5,
        // Falhas que chegam sem código próprio.
        "screens/program/ProgramGenerateScreen.kt" to 1,
        "screens/workout/WorkoutDetailScreen.kt" to 1,
    )

    private val raiz: File by lazy {
        generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(5)
            .map { File(it, "src/main/kotlin/dev/rafael/app") }
            .firstOrNull { it.isDirectory }
            ?: error("não achei as fontes do app a partir de ${File(".").absolutePath}")
    }

    @Test
    fun `os pontos de texto do servidor sao exatamente os declarados`() {
        val encontrado = raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { arquivo ->
                val n = Regex("Frase\\.DoServidor\\(").findAll(arquivo.readText()).count()
                if (n == 0) null else arquivo.relativeTo(raiz).invariantSeparatorsPath to n
            }
            .toMap()

        assertTrue(encontrado.isNotEmpty(), "nenhum arquivo lido de ${raiz.absolutePath}")

        assertEquals(
            esperado,
            encontrado,
            "a lista de pontos onde o texto do servidor chega à tela mudou. " +
                "Ponto novo entra em `esperado` com o motivo; ponto resolvido sai dela.",
        )
    }
}
