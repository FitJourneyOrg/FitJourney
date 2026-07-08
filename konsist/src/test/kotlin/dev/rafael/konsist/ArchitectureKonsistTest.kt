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
}