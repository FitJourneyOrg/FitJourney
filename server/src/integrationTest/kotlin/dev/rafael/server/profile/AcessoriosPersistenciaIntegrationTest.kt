package dev.rafael.server.profile

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.ProfileDto
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.Semear
import dev.rafael.server.features.profile.db.ProfileRepositoryImpl
import dev.rafael.server.features.profile.models.Profile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Trava a distinção `NULL` × `[]` em `profiles.accessories` (V66) contra Postgres REAL -- a
 * pergunta 1 do plano `acessorios-no-onboarding.md`, resolvida pela opção (a).
 *
 * ## Por que isto não cabe no `AcessoriosIntegrationTest`
 *
 * Aquele arquivo trava o CATÁLOGO (que `requires_bench` são as linhas certas). Este trava a
 * COLUNA: que `NULL` (nunca perguntado) e `[]` (perguntado, nada declarado) sobrevivem ao
 * round-trip sem se confundir -- e que um cliente antigo, que não manda a chave no JSON, não
 * apaga sem querer a resposta de quem já respondeu.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcessoriosPersistenciaIntegrationTest {

    private val repo = ProfileRepositoryImpl()

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun perfil(userId: kotlin.uuid.Uuid, accessories: List<Accessory>?) = Profile(
        userId = userId,
        goal = Goal.GAIN_MUSCLE,
        level = Level.INTERMEDIATE,
        daysPerWeek = 3,
        splitPreference = null,
        unavailableDays = emptyList(),
        focusAreas = emptyList(),
        weightKg = null,
        heightCm = null,
        age = null,
        minorSupervised = false,
        environment = TrainingEnvironment.CASA,
        health = null,
        limitations = emptyList(),
        accessories = accessories,
        onboardingCompleted = true,
    )

    @Test
    fun `salvar perfil com tres acessorios e reler devolve os tres`(): Unit = runBlocking {
        val uid = Semear.usuario()
        val declarados = listOf(Accessory.BENCH, Accessory.STEP, Accessory.PULLUP_BAR)

        ok(repo.upsert(perfil(uid, declarados)))

        assertEquals(declarados, ok(repo.findByUserId(uid))!!.accessories)
    }

    /**
     * `[]` não é "esqueceu de preencher" -- é "perguntei e a pessoa não tem nada". Se o
     * `toProfile()` do repositório ainda colapsasse em `emptyList()` por fora do `null`, este
     * teste não distinguiria o caso de baixo, e é exatamente aí que o cliente perderia a resposta.
     */
    @Test
    fun `accessories vazia (perguntado, nada declarado) sobrevive distinta de nula`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, emptyList())))

        assertEquals(emptyList<Accessory>(), ok(repo.findByUserId(uid))!!.accessories)
    }

    @Test
    fun `accessories nula (nunca perguntado) sobrevive ao insert`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, null)))

        assertNull(ok(repo.findByUserId(uid))!!.accessories)
    }

    /**
     * ⭐ O teste que a pergunta 1 inteira existe para provar.
     *
     * Um cliente sem a feature (versão antiga) reenvia o perfil inteiro sem o campo `accessories`
     * -- no Kotlin isso vira `null` pelo default do DTO. Antes da opção (a), o repositório gravava
     * esse `null` sempre, e a segunda chamada apagava a resposta real da primeira. Aqui simulamos
     * as DUAS chamadas na ordem em que aconteceriam.
     */
    @Test
    fun `update com accessories nula nao apaga resposta anterior (cliente antigo)`(): Unit = runBlocking {
        val uid = Semear.usuario()
        val declarados = listOf(Accessory.BENCH, Accessory.ELEVATED_SURFACE)

        ok(repo.upsert(perfil(uid, declarados)))     // cliente novo, respondeu
        ok(repo.upsert(perfil(uid, null)))            // cliente antigo, resalva sem a chave

        assertEquals(declarados, ok(repo.findByUserId(uid))!!.accessories, "o update com null apagou a resposta")
    }

    /**
     * A outra metade: o `null` do UPDATE realmente significa "não mudei nada" e não "limpei a
     * lista" -- se o usuário quer mesmo desmarcar tudo, o cliente manda `[]`, não omite o campo.
     */
    @Test
    fun `update com accessories vazia explicita apaga para lista vazia`(): Unit = runBlocking {
        val uid = Semear.usuario()

        ok(repo.upsert(perfil(uid, listOf(Accessory.BENCH))))
        ok(repo.upsert(perfil(uid, emptyList())))

        assertEquals(emptyList<Accessory>(), ok(repo.findByUserId(uid))!!.accessories)
    }

    /**
     * Caminho de falha do par NULL/[]: um cliente de verdade, sem a feature, nunca manda a chave
     * `accessories` no JSON -- não dá pra simular isso construindo um `ProfileDto` em Kotlin (o
     * campo sempre existe no objeto). Decodifica o JSON cru e prova que o default (`null`) cobre
     * a ausência sem lançar exceção de desserialização.
     */
    @Test
    fun `JSON sem a chave accessories nao quebra a desserializacao (cliente antigo)`() {
        val semAChave = """
            {"goal":"GAIN_MUSCLE","level":"INTERMEDIATE","daysPerWeek":3}
        """.trimIndent()

        val dto = Json.decodeFromString<ProfileDto>(semAChave)

        assertNull(dto.accessories)
    }
}
