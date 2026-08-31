package dev.rafael.server.features.user.services

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class UserServiceTest {

    /** `jaExiste = false` simula o PRIMEIRO acesso — o caminho em que o nome nasce. */
    private class FakeRepo(private val jaExiste: Boolean = true) : UserRepository {
        val user = User(
            id = Uuid.random(),
            firebaseUid = "fb-uid",
            email = null,
            isPremium = false,
            displayName = "Atleta-abc123",
            code = "ABCD2345",
        )
        var setPremiumCalledWith: Boolean? = null
        var criadoCom: String? = null
        var renomeadoPara: String? = null
        var criadoComCodigo: String? = null
        var codigoNovo: String? = null

        override suspend fun findByFirebaseUid(firebaseUid: String) =
            AppResult.Success(if (jaExiste) user else null)

        override suspend fun findById(userId: Uuid): AppResult<User?> =
            AppResult.Success(if (jaExiste && userId == user.id) user else null)

        override suspend fun findByCode(code: String): AppResult<User?> =
            AppResult.Success(if (jaExiste && code == user.code) user else null)

        override suspend fun updateCode(userId: Uuid, code: String): AppResult<User?> {
            codigoNovo = code
            return AppResult.Success(user.copy(code = code))
        }

        override suspend fun create(
            id: Uuid,
            firebaseUid: String,
            email: String?,
            displayName: String,
            code: String,
        ): AppResult<User> {
            criadoCom = displayName
            criadoComCodigo = code
            return AppResult.Success(user.copy(id = id, email = email, displayName = displayName, code = code))
        }

        override suspend fun setPremium(userId: Uuid, premium: Boolean): AppResult<User?> {
            setPremiumCalledWith = premium
            return AppResult.Success(user.copy(isPremium = premium))
        }

        override suspend fun updateDisplayName(userId: Uuid, displayName: String): AppResult<User?> {
            renomeadoPara = displayName
            return AppResult.Success(user.copy(displayName = displayName))
        }
    }

    @Test
    fun `activatePremium liga o premium do usuario`() = runBlocking {
        val repo = FakeRepo()
        val r = UserService(repo).activatePremium("fb-uid", null)

        assertTrue(r is AppResult.Success && r.value.isPremium, "usuário volta premium")
        assertEquals(true, repo.setPremiumCalledWith, "setPremium foi chamado com true")
    }

    @Test
    fun `usuario NOVO ja nasce com nome`() = runBlocking {
        // O ponto da fatia A.0: findOrCreate roda no GET /me do splash, ANTES do onboarding.
        // Se o nome esperasse o quiz, a coluna NOT NULL não teria valor no insert.
        val repo = FakeRepo(jaExiste = false)
        val r = UserService(repo).findOrCreate("fb-novo", "rafel0017@gmail.com")

        assertTrue(r is AppResult.Success)
        assertEquals("rafel0017", repo.criadoCom, "nome derivado da parte local do e-mail")
    }

    @Test
    fun `usuario novo SEM e-mail nasce com o fallback`() = runBlocking {
        val repo = FakeRepo(jaExiste = false)
        UserService(repo).findOrCreate("fb-novo", null)

        assertTrue(
            repo.criadoCom!!.startsWith("Atleta-"),
            "sem e-mail o nome vem do id — nunca vazio, senão o NOT NULL estoura",
        )
    }

    @Test
    fun `updateDisplayName normaliza antes de gravar`() = runBlocking {
        val repo = FakeRepo()
        val r = UserService(repo).updateDisplayName("fb-uid", null, "  Rafael   Souza ")

        assertTrue(r is AppResult.Success)
        assertEquals("Rafael Souza", repo.renomeadoPara, "grava o normalizado, não o cru")
    }

    @Test
    fun `nome invalido NAO chega ao banco`() = runBlocking {
        // A validação vem antes da consulta: recusa não deve custar ida ao banco, e o servidor
        // é quem decide validade ([REGRA]), não a UI.
        val repo = FakeRepo()
        val r = UserService(repo).updateDisplayName("fb-uid", null, "R")

        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
        assertNull(repo.renomeadoPara, "o repositório não pode ter sido tocado")
    }

    /**
     * O CÓDIGO nasce junto com a linha (V40, #35) — mesma lição do `display_name` na A.0.
     *
     * A coluna é NOT NULL; gerar "quando alguém precisar" exigiria que ela fosse nullable, e
     * nullable espalha `?:` por toda tela que a usa até alguém esquecer um.
     */
    @Test
    fun `o codigo nasce junto com a linha, no primeiro acesso`(): Unit = runBlocking {
        val repo = FakeRepo(jaExiste = false)

        UserService(repo).findOrCreate("fb-novo", "novo@x.com")

        val codigo = repo.criadoComCodigo
        assertNotNull(codigo, "create tem que receber um código — a coluna é NOT NULL")
        assertEquals(8, codigo.length)
        assertTrue(
            codigo.all { it in UserCodePolicy.ALFABETO },
            "`$codigo` saiu do alfabeto sem ambiguidade",
        )
    }

    /**
     * Regenerar mata o código anterior (35.5) — a defesa que devolve CONTROLE a quem está sendo
     * importunado, em vez de depender de nós detectarmos o abuso.
     */
    @Test
    fun `regenerar troca o codigo por outro valido`(): Unit = runBlocking {
        val repo = FakeRepo()
        val anterior = repo.user.code

        val r = UserService(repo).regenerarCodigo("fb-uid", null)

        assertTrue(r is AppResult.Success)
        val novo = repo.codigoNovo
        assertNotNull(novo)
        assertEquals(8, novo.length)
        assertTrue(novo.all { it in UserCodePolicy.ALFABETO })
        assertEquals(novo, r.value.code, "a resposta traz o código NOVO, não o antigo")
        // Colisão com o anterior é possível (1 em 1 trilhão) mas seria loteria; se este teste
        // falhar por isso, compre um bilhete.
        assertTrue(novo != anterior)
    }
}
