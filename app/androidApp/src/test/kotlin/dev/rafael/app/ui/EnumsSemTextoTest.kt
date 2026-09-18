package dev.rafael.app.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * ⭐ **Nenhum enum do cliente guarda texto de UI numa propriedade `String`** (G.4, ARCH #37).
 *
 * ## A família de defeitos que este teste fecha
 *
 * Quatro vezes nesta base o mesmo defeito apareceu com quatro caras, e as quatro escaparam de
 * varreduras diferentes:
 *
 * | onde | como aparecia | como escapou |
 * |---|---|---|
 * | `MuscleGroup` (G.3) | `enum.name` cru na tela | não é literal |
 * | `Environment` (G.3) | `env.name` como fallback | não é literal |
 * | `ExerciseCategory` (2026-09-11) | `Text(cat.name)` em duas telas | não é literal |
 * | `BottomTab` (2026-09-11) | `val label: String` com as 4 abas em português | o literal não estava na tela, e a tela não tinha literal |
 *
 * O último é o mais instrutivo: a **barra de baixo**, o componente mais visível do app, ficou em
 * português depois de uma fatia que declarou *zero literais em posição forte* — e a declaração
 * estava tecnicamente correta.
 *
 * > **Texto guardado como propriedade de enum é invisível para quem procura texto em posição de**
 * > **UI: o literal não está na tela, e a tela não tem literal.**
 *
 * ## Por que a regra é "enum do app não tem `String`", e não algo mais fino
 *
 * Porque enum do MÓDULO DE APRESENTAÇÃO com propriedade `String` quase sempre é rótulo. O que
 * legitimamente carrega `String` é enum de CONTRATO — o `Idioma.tag` é `pt-BR`, uma chave que
 * viaja na API — e contrato não mora aqui.
 *
 * A alternativa fina seria detectar "String que chega a um `Text(`", e ela exige o TIPO do
 * receptor, que regex não enxerga. Um teste por regex fingindo esse alcance daria confiança sem
 * base, que é pior que não ter teste. Esta regra é grossa, é do arquivo inteiro, e por isso é
 * verdadeira.
 *
 * > **Invariante grosseiro e honesto vale mais que invariante fino e fingido.**
 *
 * A saída, quando um enum precisar de rótulo, é a mesma do `ui/Rotulos.kt`: `@StringRes val`, que
 * mantém o enum Kotlin puro — sem `Context`, sem `@Composable`, resolvível em teste de unidade.
 */
class EnumsSemTextoTest {

    private val fonte: File by lazy {
        generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(5)
            .map { File(it, "src/main/kotlin") }
            .firstOrNull { it.isDirectory }
            ?: error("não achei src/main/kotlin a partir de ${File(".").absolutePath}")
    }

    /** O cabeçalho de um `enum class`, com a lista de parâmetros do construtor. */
    private val cabecalho = Regex("""enum class\s+(\w+)\s*\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
    private val propriedadeString = Regex(""":\s*String\b""")

    @Test
    fun `nenhum enum do app carrega texto numa propriedade String`() {
        val culpados = fonte.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { arquivo ->
                cabecalho.findAll(arquivo.readText())
                    .filter { propriedadeString.containsMatchIn(it.groupValues[2]) }
                    .map { "${arquivo.name}: enum ${it.groupValues[1]}" }
            }
            .toList()

        assertTrue(
            culpados.isEmpty(),
            "enum de apresentação com propriedade String é rótulo disfarçado de dado, e nenhuma " +
                "varredura de literais o enxerga. Use `@StringRes val`, como em ui/Rotulos.kt: $culpados",
        )
    }
}
