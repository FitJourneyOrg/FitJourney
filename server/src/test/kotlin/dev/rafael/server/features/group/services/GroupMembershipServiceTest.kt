package dev.rafael.server.features.group.services

import dev.rafael.contract.group.MemberRole
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * SAIR e mandar no grupo — as regras 2.5, 2.5-A e a transferência de admin.
 *
 * Estas são as operações que **destroem** coisa: alguém perde o vínculo, alguém perde o cargo, e
 * numa delas o desafio inteiro desaparece. Até aqui só a `GroupPolicy` (lógica pura) tinha teste,
 * e a prova de que a 2.5-A funcionava era eu ter clicado no emulador uma vez.
 */
class GroupMembershipServiceTest {

    private val admin = usuario("admin")
    private val outro = usuario("outro")

    private fun cenario(vararg pessoas: User) =
        FakeGroupRepository() to UserService(FakeUserRepository(pessoas.toList()))

    // ---- 2.5-A: o admin sozinho sai, e o desafio vai junto ----

    @Test
    fun `admin sozinho sai e o desafio e EXCLUIDO`() = runBlocking {
        // Sem esta regra a 2.5 era um beco sem saída, e não num caso raro: TODO desafio nasce com
        // uma pessoa só. Quem criasse um e desistisse antes de convidar alguém não podia sair
        // (precisa transferir) nem transferir (não há para quem).
        val (repo, users) = cenario(admin)
        val service = GroupMembershipService(users, repo)
        val grupo = repo.semear(admin = admin.id)

        val r = service.sair(admin.firebaseUid, admin.email, grupo.toString())

        assertIs<AppResult.Success<Unit>>(r)
        assertFalse(repo.existe(grupo), "o desafio tinha que ter sido apagado junto")
        assertEquals(0, repo.quantidadeDeMembros(grupo), "o cascade leva os vínculos")
    }

    @Test
    fun `admin com outro membro NAO sai e nao apaga nada`() = runBlocking {
        val (repo, users) = cenario(admin, outro)
        val service = GroupMembershipService(users, repo)
        val grupo = repo.semear(admin = admin.id, outros = listOf(outro.id))

        val r = service.sair(admin.firebaseUid, admin.email, grupo.toString())

        val erro = assertIs<AppResult.Failure>(r).error
        assertIs<AppError.Conflict>(erro)
        assertEquals("Transfira o cargo de admin antes de sair do grupo.", erro.message)
        assertTrue(repo.existe(grupo), "recusar não pode apagar")
        assertEquals(2, repo.quantidadeDeMembros(grupo))
    }

    @Test
    fun `se alguem entrar entre o pedido e a exclusao, a recusa de 2-5 volta`() = runBlocking {
        // A CORRIDA. O grupo pode estar AGENDADO, com o código circulando: entre o admin tocar em
        // "Excluir" e o banco decidir, alguém entra. Apagar aqui tiraria o desafio de baixo de
        // quem acabou de entrar. Agora existe para quem transferir, então a 2.5 volta a valer.
        val (repo, users) = cenario(admin, outro)
        val service = GroupMembershipService(users, repo)
        val grupo = repo.semear(admin = admin.id)
        repo.antesDeExcluir = { runBlocking { repo.join(grupo, outro.id) } }

        val r = service.sair(admin.firebaseUid, admin.email, grupo.toString())

        assertIs<AppError.Conflict>(assertIs<AppResult.Failure>(r).error)
        assertTrue(repo.existe(grupo), "o desafio de quem acabou de entrar não pode sumir")
    }

    @Test
    fun `membro comum sai e o desafio continua de pe`() = runBlocking {
        val (repo, users) = cenario(admin, outro)
        val service = GroupMembershipService(users, repo)
        val grupo = repo.semear(admin = admin.id, outros = listOf(outro.id))

        val r = service.sair(outro.firebaseUid, outro.email, grupo.toString())

        assertIs<AppResult.Success<Unit>>(r)
        assertTrue(repo.existe(grupo))
        assertEquals(1, repo.quantidadeDeMembros(grupo))
        assertEquals(MemberRole.ADMIN.name, repo.papel(grupo, admin.id), "o admin não foi tocado")
    }

    // ---- transferência de admin (o passo 9 da bateria manual, agora automatizado) ----

    @Test
    fun `transferir admin REBAIXA quem transferiu na mesma operacao`() = runBlocking {
        // Se o escudo aparecesse nos dois, o grupo teria dois admins — e nada na regra 2.5
        // impediria os dois de saírem, um de cada vez, deixando o desafio sem dono.
        val (repo, users) = cenario(admin, outro)
        val service = GroupMembershipService(users, repo)
        val grupo = repo.semear(admin = admin.id, outros = listOf(outro.id))

        val r = service.transferirAdmin(admin.firebaseUid, admin.email, grupo.toString(), outro.id.toString())

        assertIs<AppResult.Success<Unit>>(r)
        assertEquals(MemberRole.ADMIN.name, repo.papel(grupo, outro.id))
        assertEquals(MemberRole.MEMBRO.name, repo.papel(grupo, admin.id), "quem transferiu tem que cair")
    }

    @Test
    fun `quem NAO e admin nao transfere`() = runBlocking {
        val (repo, users) = cenario(admin, outro)
        val service = GroupMembershipService(users, repo)
        val grupo = repo.semear(admin = admin.id, outros = listOf(outro.id))

        val r = service.transferirAdmin(outro.firebaseUid, outro.email, grupo.toString(), outro.id.toString())

        assertIs<AppError.Forbidden>(assertIs<AppResult.Failure>(r).error)
        assertEquals(MemberRole.ADMIN.name, repo.papel(grupo, admin.id), "nada mudou")
    }

    // ---- expulsar ----

    @Test
    fun `admin nao expulsa a si mesmo — para isso existe o sair`() = runBlocking {
        // Sem esta guarda, "expulsar a si mesmo" seria um caminho paralelo para sair que pularia
        // inteira a regra 2.5 — e deixaria o grupo sem admin.
        val (repo, users) = cenario(admin, outro)
        val service = GroupMembershipService(users, repo)
        val grupo = repo.semear(admin = admin.id, outros = listOf(outro.id))

        val r = service.expulsar(admin.firebaseUid, admin.email, grupo.toString(), admin.id.toString())

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(r).error)
        assertEquals(2, repo.quantidadeDeMembros(grupo))
    }

    @Test
    fun `quem nao e membro recebe 404, nunca 403`() = runBlocking {
        // Responder "sem permissão" contaria que o grupo existe. Para quem está fora, ele não
        // existe — mesma escolha do `GET /groups/{id}` de grupo alheio.
        val forasteiro = usuario("forasteiro")
        val (repo, users) = cenario(admin, forasteiro)
        val service = GroupMembershipService(users, repo)
        val grupo = repo.semear(admin = admin.id)

        val r = service.sair(forasteiro.firebaseUid, forasteiro.email, grupo.toString())

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(r).error)
        assertTrue(repo.existe(grupo))
    }
}
