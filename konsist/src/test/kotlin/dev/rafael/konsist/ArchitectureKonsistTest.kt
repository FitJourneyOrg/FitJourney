package dev.rafael.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.architecture
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.imports
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

class ArchitectureKonsistTest {

    private val production = Konsist.scopeFromProduction()

    // ===== Camadas com código real hoje =====

    @Test
    fun `shared-contract deve ser Kotlin puro`() {
        production
            .files
            .withPackage("dev.rafael.contract..")
            .imports
            .assertFalse(additionalMessage = "shared-contract não pode conter frameworks externos ou módulos internos complexos") {
                val name = it.name
                name.startsWith("io.ktor") || name.startsWith("org.jetbrains.exposed") ||
                        name.startsWith("app.cash.sqldelight") || name.startsWith("org.jetbrains.compose") ||
                        name.startsWith("androidx") || name.startsWith("dev.rafael.core") ||
                        name.startsWith("dev.rafael.features") || name.startsWith("dev.rafael.server")
            }
    }

    @Test
    fun `core nao sobe para feature, server nem app`() {
        production
            .files
            .withPackage("dev.rafael.core..")
            .imports
            .assertFalse(additionalMessage = "Core é base e não pode conhecer camadas superiores") {
                val name = it.name
                name.startsWith("dev.rafael.features") ||
                        name.startsWith("dev.rafael.server") ||
                        name.startsWith("dev.rafael.app")
            }
    }

    // ===== Regras de feature =====

    @Test
    fun `camadas internas da feature respeitam os limites da Clean Architecture`() {
        production.assertArchitecture {
            // A API nativa de arquitetura do Konsist mapeia e testa as relações automaticamente
            val domain = Layer("Domain", "dev.rafael.features..domain..")
            val presentation = Layer("Presentation", "dev.rafael.features..presentation..")
            val data = Layer("Data", "dev.rafael.features..data..")

            domain.dependsOnNothing()
            presentation.dependsOn(domain)
            data.dependsOn(domain)
        }
    }

    @Test
    fun `todo arquivo de feature mora no pacote dev-rafael-features-nome-camada`() {
        val regex = Regex(""".*[/\\]shared[/\\]features[/\\]([^/\\]+)[/\\](domain|data|presentation)[/\\]src[/\\].*""")

        val files = Konsist.scopeFromProject().files
            .filter { regex.matches(it.path) }

        // guard: se o filtro vier vazio, o teste falha (evita passar vacuamente)
        check(files.isNotEmpty()) { "Nenhum arquivo de feature encontrado — filtro/scope errado" }

        files.assertTrue { file ->
            val (feature, layer) = regex.find(file.path)!!.destructured
            val expected = "dev.rafael.features.$feature.$layer"
            file.packagee?.name?.startsWith(expected) == true
        }
    }
    @Test
    fun `diagnostico do filtro`() {
        val regex = Regex(""".*[/\\]shared[/\\]features[/\\]([^/\\]+)[/\\](domain|data|presentation)[/\\]src[/\\].*""")
        val all = Konsist.scopeFromProject().files
        val matched = all.filter { regex.matches(it.path) }
        println("TOTAL=${all.size}  MATCHED=${matched.size}")
        matched.forEach {
            val (f, l) = regex.find(it.path)!!.destructured
            println("  ${it.path}  PKG=${it.packagee?.name}  esperado=dev.rafael.features.$f.$l")
        }
    }

    @Test
    fun `todo arquivo de producao reside sob dev rafael`() {
        production
            .files
            .assertFalse(additionalMessage = "Todos os pacotes devem pertencer ao escopo dev.rafael") {
                !it.hasPackage("dev.rafael..")
            }
    }

    // ===== Regras de UI =====

    /**
     * ⭐ **Inset de barra do sistema é do `Scaffold`, nunca de um modificador solto.**
     *
     * O app tem `Scaffold` aninhado: um no `AppNavHost` (menu e abas) e um em cada uma das 24
     * telas. A [REGRA] de quem aplica o quê está escrita lá, e ela custou dois defeitos seguidos —
     * faixa preta em cima e embaixo de toda tela, e depois a status bar sobrando sem cor.
     *
     * O que esta regra impede é a terceira rodada. O `Scaffold` da tela já entrega no `padding`
     * tudo que precisa ser afastado das barras; um `systemBarsPadding()` escrito por cima **soma
     * a mesma distância outra vez**, e volta a faixa — agora numa tela só, que é o modo mais caro
     * de o defeito voltar.
     *
     * > **Modificador de inset dentro do conteúdo de um `Scaffold` está sempre duplicando algo: o
     * > `Scaffold` existe exatamente para não precisar dele.**
     *
     * O caminho que leva até aqui é sempre o mesmo: alguém vê um espaçamento errado, acrescenta
     * o modificador naquela tela, e o problema de verdade — que é de quem declarou os insets —
     * fica escondido atrás da tentativa.
     *
     * ⚠️ **`imePadding` NÃO entra na lista, de propósito.** Teclado não é barra fixa: ele aparece
     * e some, ninguém o consome no topo, e é responsabilidade de quem tem campo de texto.
     *
     * ## As exceções são três, e cada uma tem motivo
     *
     * - **`AppNavHost`** é o dono, e é onde a decisão mora.
     * - **`MainActivity`** chama o `enableEdgeToEdge`, que é o que faz tudo isto existir.
     * - **`MenuLateral`** é a gaveta, e a gaveta fica **fora** do `Scaffold`, dentro do
     *   `ModalNavigationDrawer`. O consumo do `AppNavHost` não a alcança, então ela é o único
     *   lugar do app onde um `statusBarsPadding` continua somando pixel de verdade.
     *
     * A terceira é a que importa: sem ela, esta regra proibiria o conserto legítimo da gaveta e
     * empurraria quem fosse arrumá-la para um contorno pior.
     *
     * > **Regra que bloqueia a correção certa ensina a contornar a regra.**
     */
    @Test
    fun `so o AppNavHost mexe em inset de barra do sistema`() {
        val insetsDeBarra = setOf(
            "androidx.compose.foundation.layout.statusBarsPadding",
            "androidx.compose.foundation.layout.navigationBarsPadding",
            "androidx.compose.foundation.layout.systemBarsPadding",
        )

        val donosLegitimos = setOf("AppNavHost", "MainActivity", "MenuLateral")

        production
            .files
            .withPackage("dev.rafael.app..")
            .filterNot { it.name in donosLegitimos }
            .imports
            .assertFalse(
                additionalMessage = "Quem afasta o conteúdo das barras do sistema é o Scaffold da " +
                    "tela, pelo padding que ele entrega. Um modificador de inset por cima soma a " +
                    "mesma distância duas vezes. Se o espaçamento está errado, a [REGRA] com a " +
                    "divisão de responsabilidade está no AppNavHost.",
            ) { it.name in insetsDeBarra }
    }
}