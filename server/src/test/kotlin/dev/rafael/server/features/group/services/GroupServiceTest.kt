package dev.rafael.server.features.group.services

import dev.rafael.contract.group.GroupDto
import dev.rafael.contract.group.MemberRole
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Leitura de grupos (fatia A.1) — e a **contagem de consultas** da lista.
 *
 * O teste que justifica este arquivo não afirma um valor de tela: afirma **quantas vezes o
 * repositório é chamado**. É a única forma de impedir um N+1 de voltar, e ele volta — código novo
 * imita o que já está no arquivo, e a versão anterior de `meusGrupos` chamava `roleOf` dentro de
 * um `map`.
 *
 * Defeito de desempenho que ninguém afirma é defeito que reaparece na próxima fatia.
 */
class GroupServiceTest {

    private val eu = usuario("eu")

    private fun cenario(): Pair<FakeGroupRepository, GroupService> {
        val repo = FakeGroupRepository()
        return repo to GroupService(UserService(FakeUserRepository(listOf(eu))), repo)
    }

    @Test
    fun `a lista NAO faz uma consulta de papel por grupo`(): Unit = runBlocking {
        // Com vinte grupos, a versão anterior montava a tela com vinte e uma consultas. Não
        // aparece com dois grupos de teste; aparece com vinte, e aí já está copiado em três
        // lugares.
        val (repo, service) = cenario()
        repeat(20) { i -> repo.semear(admin = eu.id, code = "CODE%02d".format(i).take(6)) }

        val lista = assertIs<AppResult.Success<List<GroupDto>>>(
            service.meusGrupos(eu.firebaseUid, eu.email),
        ).value

        assertEquals(20, lista.size)
        assertEquals(1, repo.chamadasDeRolesOf, "uma consulta de papéis para a lista inteira")
    }

    @Test
    fun `a lista traz o papel certo de cada grupo`(): Unit = runBlocking {
        // Trocar N+1 por lote não pode perder a informação: em lote é fácil devolver o papel
        // errado para o grupo errado, e o sintoma seria o escudo de admin no grupo do vizinho.
        val (repo, service) = cenario()
        val outro = usuario("outro")
        val meuComoAdmin = repo.semear(admin = eu.id, code = "ADM123")
        val meuComoMembro = repo.semear(admin = outro.id, outros = listOf(eu.id), code = "MEM123")

        val lista = assertIs<AppResult.Success<List<GroupDto>>>(
            service.meusGrupos(eu.firebaseUid, eu.email),
        ).value.associateBy { it.id }

        assertEquals(MemberRole.ADMIN, lista[meuComoAdmin.toString()]?.myRole)
        assertEquals(MemberRole.MEMBRO, lista[meuComoMembro.toString()]?.myRole)
    }

    @Test
    fun `sem grupo nenhum, nem consulta de papel acontece`(): Unit = runBlocking {
        // `rolesOf` com lista vazia curto-circuita: `IN ()` é SQL inválido em alguns bancos e
        // desperdício em todos.
        val (repo, service) = cenario()

        val lista = assertIs<AppResult.Success<List<GroupDto>>>(
            service.meusGrupos(eu.firebaseUid, eu.email),
        ).value

        assertEquals(emptyList(), lista)
    }

    @Test
    fun `a lista NAO consulta o check-in de hoje`(): Unit = runBlocking {
        // `myCheckInToday` é do DETALHE, onde existe o botão de check-in. Na lista seria mais uma
        // consulta por grupo — o mesmo N+1 que acabamos de tirar, entrando pela porta da frente.
        val (repo, service) = cenario()
        repo.semear(admin = eu.id)

        val grupo = assertIs<AppResult.Success<List<GroupDto>>>(
            service.meusGrupos(eu.firebaseUid, eu.email),
        ).value.single()

        assertNull(grupo.myCheckInToday)
    }
}
