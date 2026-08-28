package dev.rafael.server.features.friendship.services

import dev.rafael.contract.friendship.FriendStatus
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.friendship.db.FriendshipRepository
import dev.rafael.server.features.friendship.models.Amizade
import dev.rafael.server.features.friendship.models.PedidoRecebido
import dev.rafael.server.features.friendship.models.Pessoa
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * O serviço do grafo (ARCH #35).
 *
 * ## O fake implementa a REGRA, não devolve constante
 *
 * O [FakeGrafo] guarda as amizades num mapa chaveado pelo **par canônico** — a mesma chave que a
 * PK do banco usa. Isso é deliberado: um fake que guardasse por `(quemPede, alvo)` deixaria o
 * pedido cruzado passar aqui e falhar só em produção, e o teste estaria dando uma garantia que
 * não tem. Foi a lição que a fatia B já cobrou uma vez, com o `photoRef` constante.
 */
class FriendshipServiceTest {

    private val eu = User(Uuid.random(), "fb-eu", "eu@x.com", false, "Eu", "AAAA2222")
    private val outro = User(Uuid.random(), "fb-outro", "outro@x.com", false, "Fulano", "BBBB3333")

    private inner class FakeUsers : UserRepository {
        override suspend fun findByFirebaseUid(firebaseUid: String) =
            listOf(eu, outro).firstOrNull { it.firebaseUid == firebaseUid }.asSuccess()

        override suspend fun findById(userId: Uuid) =
            listOf(eu, outro).firstOrNull { it.id == userId }.asSuccess()

        override suspend fun findByCode(code: String) =
            listOf(eu, outro).firstOrNull { it.code == code }.asSuccess()

        override suspend fun updateCode(userId: Uuid, code: String) = error("não usado")
        override suspend fun create(
            id: Uuid, firebaseUid: String, email: String?, displayName: String, code: String,
        ) = error("não usado")
        override suspend fun setPremium(userId: Uuid, premium: Boolean) = error("não usado")
        override suspend fun updateDisplayName(userId: Uuid, displayName: String) = error("não usado")
    }

    /** Chaveado pelo PAR CANÔNICO, como o banco. É o que faz o fake não mentir. */
    private class FakeGrafo : FriendshipRepository {
        val amizades = mutableMapOf<Pair<Uuid, Uuid>, Amizade>()
        val bloqueios = mutableSetOf<Pair<Uuid, Uuid>>()   // (bloqueador, bloqueado)

        override suspend fun entre(um: Uuid, outro: Uuid) =
            amizades[FriendshipPolicy.par(um, outro)].asSuccess()

        override suspend fun pedir(quemPede: Uuid, alvo: Uuid, quando: LocalDateTime): AppResult<Boolean> {
            val chave = FriendshipPolicy.par(quemPede, alvo)
            // insertIgnore: a chave já existir NÃO sobrescreve — é o comportamento do banco.
            if (amizades.containsKey(chave)) return false.asSuccess()
            amizades[chave] = Amizade(
                userA = chave.first,
                userB = chave.second,
                requestedBy = quemPede,
                status = FriendshipPolicy.Estado.PENDENTE,
                createdAt = quando,
                respondedAt = null,
            )
            return true.asSuccess()
        }

        override suspend fun responder(
            um: Uuid, outro: Uuid, novo: FriendshipPolicy.Estado, quando: LocalDateTime,
        ): AppResult<Boolean> {
            val chave = FriendshipPolicy.par(um, outro)
            val atual = amizades[chave] ?: return false.asSuccess()
            // O `WHERE status = PENDENTE` do update, reproduzido: resposta em relação já decidida
            // não muda nada e devolve false.
            if (atual.status != FriendshipPolicy.Estado.PENDENTE) return false.asSuccess()
            amizades[chave] = atual.copy(status = novo, respondedAt = quando)
            return true.asSuccess()
        }

        override suspend fun apagar(um: Uuid, outro: Uuid) =
            (amizades.remove(FriendshipPolicy.par(um, outro)) != null).asSuccess()

        override suspend fun contarAmizades(userId: Uuid) =
            amizades.values.count {
                (it.userA == userId || it.userB == userId) &&
                    it.status == FriendshipPolicy.Estado.ACEITA
            }.asSuccess()

        override suspend fun amigos(userId: Uuid) = emptyList<Pessoa>().asSuccess()
        override suspend fun pedidosRecebidos(userId: Uuid) = emptyList<PedidoRecebido>().asSuccess()

        override suspend fun haBloqueioEntre(um: Uuid, outro: Uuid) =
            (bloqueios.contains(um to outro) || bloqueios.contains(outro to um)).asSuccess()

        override suspend fun bloqueouMe(alvo: Uuid, quemPergunta: Uuid) =
            bloqueios.contains(alvo to quemPergunta).asSuccess()

        override suspend fun bloquear(bloqueador: Uuid, bloqueado: Uuid, quando: LocalDateTime): AppResult<Unit> {
            // Os dois efeitos juntos, como na transação real.
            amizades.remove(FriendshipPolicy.par(bloqueador, bloqueado))
            bloqueios.add(bloqueador to bloqueado)
            return Unit.asSuccess()
        }

        override suspend fun desbloquear(bloqueador: Uuid, bloqueado: Uuid) =
            bloqueios.remove(bloqueador to bloqueado).asSuccess()

        override suspend fun bloqueados(userId: Uuid) = emptyList<Pessoa>().asSuccess()
    }

    private fun servico(grafo: FakeGrafo = FakeGrafo()): Pair<FriendshipService, FakeGrafo> {
        val users = FakeUsers()
        return FriendshipService(UserService(users), users, grafo) to grafo
    }

    @Test
    fun `pedir cria o pedido pendente com a direcao certa`(): Unit = runBlocking {
        val (s, grafo) = servico()

        val r = s.pedir("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(r is AppResult.Success)
        val amizade = grafo.amizades.values.single()
        assertEquals(FriendshipPolicy.Estado.PENDENTE, amizade.status)
        assertEquals(eu.id, amizade.requestedBy, "requestedBy é quem MANDOU, não o menor uuid")
    }

    /**
     * PEDIDO CRUZADO VIRA AMIZADE (decisão de 2026-08-27).
     *
     * Os dois manifestaram a mesma intenção — exigir que um deles refaça o gesto com outro nome
     * seria burocracia. Também é o que resolve a corrida de dois toques simultâneos sem erro.
     *
     * Esta regra nasceu de um teste que eu escrevi errado: assumi `Success` para o segundo
     * pedido, o código respondia `JA_EXISTE_PEDIDO`, e a discrepância revelou uma decisão de
     * produto que ninguém tinha tomado.
     */
    @Test
    fun `pedir a quem ja me pediu vira amizade, sem erro`(): Unit = runBlocking {
        val (s, grafo) = servico()

        val r1 = s.pedir("fb-eu", "eu@x.com", outro.id.toString())
        val r2 = s.pedir("fb-outro", "outro@x.com", eu.id.toString())

        assertTrue(r1 is AppResult.Success)
        assertTrue(r2 is AppResult.Success, "a intenção mútua não é conflito")
        assertEquals(1, grafo.amizades.size, "um par, não dois — a PK canônica é o que garante")
        assertEquals(
            FriendshipPolicy.Estado.ACEITA,
            grafo.amizades.values.single().status,
            "os dois quiseram: são amigos",
        )
    }

    /** Mas o pedido do MESMO lado, repetido, continua sendo conflito. */
    @Test
    fun `pedir duas vezes eu mesmo continua sendo conflito`(): Unit = runBlocking {
        val (s, grafo) = servico()

        s.pedir("fb-eu", "eu@x.com", outro.id.toString())
        val segundo = s.pedir("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(segundo is AppResult.Failure)
        assertEquals(
            FriendshipPolicy.Impedimento.JA_EXISTE_PEDIDO.name,
            (segundo.error as AppError.Conflict).code,
        )
        assertEquals(FriendshipPolicy.Estado.PENDENTE, grafo.amizades.values.single().status)
    }

    @Test
    fun `quem mandou nao consegue aceitar o proprio pedido`(): Unit = runBlocking {
        val (s, grafo) = servico()
        s.pedir("fb-eu", "eu@x.com", outro.id.toString())

        val r = s.aceitar("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
        assertEquals(
            FriendshipPolicy.Estado.PENDENTE,
            grafo.amizades.values.single().status,
            "continua pendente",
        )
    }

    @Test
    fun `o destinatario aceita e vira amizade`(): Unit = runBlocking {
        val (s, grafo) = servico()
        s.pedir("fb-eu", "eu@x.com", outro.id.toString())

        val r = s.aceitar("fb-outro", "outro@x.com", eu.id.toString())

        assertTrue(r is AppResult.Success)
        val amizade = grafo.amizades.values.single()
        assertEquals(FriendshipPolicy.Estado.ACEITA, amizade.status)
        assertTrue(amizade.respondedAt != null, "a V40 exige responded_at quando não é PENDENTE")
    }

    @Test
    fun `recusar deixa a linha, cancelar apaga`(): Unit = runBlocking {
        val (s1, g1) = servico()
        s1.pedir("fb-eu", "eu@x.com", outro.id.toString())
        s1.recusar("fb-outro", "outro@x.com", eu.id.toString())
        assertEquals(1, g1.amizades.size, "recusado FICA — senão o pedido reaparece sozinho")

        val (s2, g2) = servico()
        s2.pedir("fb-eu", "eu@x.com", outro.id.toString())
        s2.remover("fb-eu", "eu@x.com", outro.id.toString())
        assertEquals(0, g2.amizades.size, "cancelado SOME — desfazer o próprio ato não deixa rastro")
    }

    @Test
    fun `depois de recusado da para pedir de novo`(): Unit = runBlocking {
        val (s, grafo) = servico()
        s.pedir("fb-eu", "eu@x.com", outro.id.toString())
        s.recusar("fb-outro", "outro@x.com", eu.id.toString())

        val denovo = s.pedir("fb-eu", "eu@x.com", outro.id.toString())

        assertTrue(denovo is AppResult.Success, "recusar não é banir — para banir existe o bloqueio")
        assertEquals(FriendshipPolicy.Estado.PENDENTE, grafo.amizades.values.single().status)
    }

    /** `remover` decide pelo ESTADO: serve para cancelar pedido e para desfazer amizade. */
    @Test
    fun `remover desfaz amizade aceita`(): Unit = runBlocking {
        val (s, grafo) = servico()
        s.pedir("fb-eu", "eu@x.com", outro.id.toString())
        s.aceitar("fb-outro", "outro@x.com", eu.id.toString())

        val r = s.remover("fb-outro", "outro@x.com", eu.id.toString())

        assertTrue(r is AppResult.Success)
        assertTrue(grafo.amizades.isEmpty())
    }

    /**
     * Bloquear **apaga o pedido pendente**, na mesma transação (decisão de 2026-08-27).
     *
     * Sem isso a pessoa bloqueada ficaria com um "aguardando resposta" que nunca seria respondido.
     */
    @Test
    fun `bloquear some com o pedido pendente`(): Unit = runBlocking {
        val (s, grafo) = servico()
        s.pedir("fb-eu", "eu@x.com", outro.id.toString())

        s.bloquear("fb-outro", "outro@x.com", eu.id.toString())

        assertTrue(grafo.amizades.isEmpty(), "o pedido some junto com o bloqueio")
        assertTrue(grafo.bloqueios.contains(outro.id to eu.id))
    }

    @Test
    fun `com bloqueio em qualquer sentido ninguem consegue pedir`(): Unit = runBlocking {
        val (s, _) = servico()
        s.bloquear("fb-outro", "outro@x.com", eu.id.toString())

        val doBloqueado = s.pedir("fb-eu", "eu@x.com", outro.id.toString())
        val doBloqueador = s.pedir("fb-outro", "outro@x.com", eu.id.toString())

        listOf(doBloqueado, doBloqueador).forEach {
            assertTrue(it is AppResult.Failure)
            assertEquals(FriendshipPolicy.Impedimento.BLOQUEIO.name, (it.error as AppError.Conflict).code)
        }
    }

    @Test
    fun `desbloquear NAO restaura a amizade apagada`(): Unit = runBlocking {
        val (s, grafo) = servico()
        s.pedir("fb-eu", "eu@x.com", outro.id.toString())
        s.aceitar("fb-outro", "outro@x.com", eu.id.toString())

        s.bloquear("fb-outro", "outro@x.com", eu.id.toString())
        s.desbloquear("fb-outro", "outro@x.com", eu.id.toString())

        assertTrue(grafo.amizades.isEmpty(), "refazer a amizade é ato deliberado dos dois")
    }

    @Test
    fun `bloquear a si mesmo e recusado`(): Unit = runBlocking {
        val (s, grafo) = servico()

        val r = s.bloquear("fb-eu", "eu@x.com", eu.id.toString())

        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
        assertTrue(grafo.bloqueios.isEmpty(), "o CHECK blocks_nao_a_si_mesmo diz o mesmo no banco")
    }

    @Test
    fun `id inexistente e id malformado respondem os dois 404`(): Unit = runBlocking {
        val (s, _) = servico()

        val fantasma = s.pedir("fb-eu", "eu@x.com", Uuid.random().toString())
        val lixo = s.pedir("fb-eu", "eu@x.com", "nao-e-uuid")

        assertTrue(fantasma is AppResult.Failure && fantasma.error is AppError.NotFound)
        assertTrue(lixo is AppResult.Failure && lixo.error is AppError.NotFound)
    }

    // ---- relação, que é o que decide o botão no perfil ----

    @Test
    fun `a relacao muda de acordo com quem pergunta`(): Unit = runBlocking {
        val (s, _) = servico()
        s.pedir("fb-eu", "eu@x.com", outro.id.toString())

        val paraQuemMandou = s.relacaoEntre(dono = outro.id, quemPergunta = eu.id)
        val paraQuemRecebeu = s.relacaoEntre(dono = eu.id, quemPergunta = outro.id)

        assertTrue(paraQuemMandou is AppResult.Success)
        assertEquals(FriendStatus.PEDIDO_ENVIADO, paraQuemMandou.value.status)
        assertTrue(paraQuemRecebeu is AppResult.Success)
        assertEquals(FriendStatus.PEDIDO_RECEBIDO, paraQuemRecebeu.value.status)
    }

    @Test
    fun `relacao com quem me bloqueou marca meBloqueou`(): Unit = runBlocking {
        val (s, _) = servico()
        s.bloquear("fb-outro", "outro@x.com", eu.id.toString())

        val euOlhando = s.relacaoEntre(dono = outro.id, quemPergunta = eu.id)
        val eleOlhando = s.relacaoEntre(dono = eu.id, quemPergunta = outro.id)

        assertTrue(euOlhando is AppResult.Success)
        assertTrue(euOlhando.value.meBloqueou, "eu vejo perfil indisponível")

        assertTrue(eleOlhando is AppResult.Success)
        assertFalse(eleOlhando.value.meBloqueou, "quem bloqueou CONTINUA vendo — é assimétrico")
        assertEquals(FriendStatus.BLOQUEADO_POR_MIM, eleOlhando.value.status, "e vê o botão Desbloquear")
    }

    @Test
    fun `recusada aparece como NENHUMA para quem olha o perfil`(): Unit = runBlocking {
        val (s, _) = servico()
        s.pedir("fb-eu", "eu@x.com", outro.id.toString())
        s.recusar("fb-outro", "outro@x.com", eu.id.toString())

        val r = s.relacaoEntre(dono = outro.id, quemPergunta = eu.id)

        assertTrue(r is AppResult.Success)
        assertEquals(
            FriendStatus.NENHUMA,
            r.value.status,
            "recusado e nunca-pedido oferecem a MESMA ação; um estado a mais só contaria a recusa",
        )
    }

    @Test
    fun `meu proprio perfil nao tem relacao`(): Unit = runBlocking {
        val (s, _) = servico()

        val r = s.relacaoEntre(dono = eu.id, quemPergunta = eu.id)

        assertTrue(r is AppResult.Success)
        assertEquals(FriendStatus.NENHUMA, r.value.status)
        assertFalse(r.value.meBloqueou)
    }
}
