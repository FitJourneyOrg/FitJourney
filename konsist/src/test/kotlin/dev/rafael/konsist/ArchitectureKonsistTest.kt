package dev.rafael.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

private val production = Konsist.scopeFromProduction()

private fun KoFileDeclaration.pkg(): String = packagee?.name.orEmpty()

private fun KoFileDeclaration.importsAnyOf(vararg prefixes: String): Boolean =
    imports.any { imp -> prefixes.any { imp.name.startsWith(it) } }

private fun List<KoFileDeclaration>.violationsImporting(vararg prefixes: String): List<String> =
    filter { it.importsAnyOf(*prefixes) }.map { it.path }

/** dev.rafael.feature.<FEATURE>... -> retorna <FEATURE> (4o segmento). */
private fun featureSegment(fqName: String): String? = fqName.split('.').getOrNull(3)

class ArchitectureKonsistTest {

    // ===== Camadas com código real hoje (server, shared-contract, core:result) =====

    @Test
    fun `shared-contract e Kotlin puro`() {
        val v = production.files
            .filter { it.pkg().startsWith("dev.rafael.contract") }
            .violationsImporting(
                "io.ktor", "org.jetbrains.exposed", "app.cash.sqldelight",
                "org.jetbrains.compose", "androidx", "com.google.firebase",
                "com.zaxxer.hikari", "dev.rafael.core", "dev.rafael.feature",
                "dev.rafael.server", "dev.rafael.app",
            )
        assertTrue("shared-contract deve ser Kotlin puro (DTOs/rotas + kotlinx.serialization).\n${v.joinToString("\n")}", v.isEmpty(), )
    }

    @Test
    fun `core nao sobe para feature, server nem app`() {
        val v = production.files
            .filter { it.pkg().startsWith("dev.rafael.core") }
            .violationsImporting("dev.rafael.feature", "dev.rafael.server", "dev.rafael.app")
        assertTrue("core nao pode depender de feature/server/app.\n${v.joinToString("\n")}",v.isEmpty())
    }

    @Test
    fun `server nao depende de modulos de cliente`() {
        val v = production.files
            .filter { it.pkg().startsWith("dev.rafael.server") }
            .violationsImporting(
                "dev.rafael.core.network", "dev.rafael.core.database", "dev.rafael.core.designsystem",
                "dev.rafael.feature", "dev.rafael.app",
                "org.jetbrains.compose", "androidx", "app.cash.sqldelight",
            )
        assertTrue("server so ve contract/core.domain/core.result (ARCH #3) + libs de server.\n${v.joinToString("\n")}",v.isEmpty())
    }

    // ===== Regras de feature: prontas, ativam quando auth ganhar código (Fase 2) =====

    @Test
    fun `feature domain e puro e nao olha data, presentation nem contract`() {
        val domain = production.files.filter {
            it.pkg().startsWith("dev.rafael.feature") && it.pkg().contains(".domain")
        }
        if (domain.isEmpty()) return // Grupo B ainda em estado wizard (débito Fase 0)
        val v = domain.filter { f ->
            f.importsAnyOf(
                "org.jetbrains.compose", "androidx", "io.ktor",
                "app.cash.sqldelight", "org.jetbrains.exposed", "dev.rafael.contract",
            ) || f.imports.any { i ->
                i.name.startsWith("dev.rafael.feature") &&
                        (i.name.contains(".data.") || i.name.contains(".presentation."))
            }
        }.map { it.path }
        assertTrue("feature/domain: Kotlin puro, sem data/presentation/contract.\n${v.joinToString("\n")}",v.isEmpty())
    }

    @Test
    fun `feature presentation nao acessa data direto`() {
        val pres = production.files.filter {
            it.pkg().startsWith("dev.rafael.feature") && it.pkg().contains(".presentation")
        }
        if (pres.isEmpty()) return
        val v = pres.filter { f ->
            f.imports.any { it.name.startsWith("dev.rafael.feature") && it.name.contains(".data.") }
        }.map { it.path }
        assertTrue("presentation passa pelo domain, nao importa data direto.\n${v.joinToString("\n")}",v.isEmpty())
    }

    @Test
    fun `feature nunca depende de outra feature`() {
        val featureFiles = production.files.filter { it.pkg().startsWith("dev.rafael.feature.") }
        if (featureFiles.isEmpty()) return
        val v = featureFiles.flatMap { f ->
            val own = featureSegment(f.pkg()) ?: return@flatMap emptyList<String>()
            f.imports
                .filter { it.name.startsWith("dev.rafael.feature.") }
                .mapNotNull { imp ->
                    val other = featureSegment(imp.name)
                    if (other != null && other != own) "${f.path} -> ${imp.name}" else null
                }
        }
        assertTrue("feature NUNCA depende de feature (o comum sobe p/ core).\n${v.joinToString("\n")}",v.isEmpty())
    }

    @Test
    fun `todo arquivo de producao reside sob dev rafael`() {
        val v = production.files
            .filterNot { it.pkg().startsWith("dev.rafael") }
            .map { "${it.path} (pacote: '${it.pkg().ifEmpty { "<sem package>" }}')" }
        assertTrue(
            "Todo arquivo de producao deve residir sob dev.rafael (cores com .core).\n${v.joinToString("\n")}",
            v.isEmpty(),
        )
    }
}