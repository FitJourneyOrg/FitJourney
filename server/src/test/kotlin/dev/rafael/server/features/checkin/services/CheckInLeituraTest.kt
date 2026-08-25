package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.contract.group.GroupRule
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.group.services.FakeGroupRepository
import dev.rafael.server.features.group.services.FakeUserRepository
import dev.rafael.server.features.group.services.usuario
import dev.rafael.server.features.user.services.UserService
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Apagar (B.2), feed (B.3) e a foto atrás de autenticação.
 *
 * O teste que mais importa deste arquivo é o da foto: ele afirma que a permissão é reavaliada a
 * cada leitura. Foi a decisão que escolhemos pagar mais caro (toda foto atravessa o Ktor) para que
 * "saiu do grupo, não vê mais" fosse verdade — se alguém trocar por URL estática, ele cai.
 */
class CheckInLeituraTest {

    private val eu = usuario("eu")
    private val outro = usuario("outro")

    private var agora = Instant.parse("2026-08-25T18:00:00Z")           // 15h em São Paulo
    private val relogio = object : Clock { override fun now() = agora }

    private class Cenario(
        val grupos: FakeGroupRepository,
        val checkIns: FakeCheckInRepository,
        val disco: FakeArmazenamento,
        val service: CheckInService,
        val grupo: String,
    )

    private fun cenario(regras: Set<GroupRule> = emptySet()): Cenario {
        val grupos = FakeGroupRepository()
        val checkIns = FakeCheckInRepository().apply {
            nomes[eu.id] = eu.displayName
            nomes[outro.id] = outro.displayName
        }
        val disco = FakeArmazenamento()
        val users = UserService(FakeUserRepository(listOf(eu, outro)))
        val id = grupos.semear(
            admin = eu.id,
            outros = listOf(outro.id),
            inicio = LocalDate(2026, 8, 1),
            fim = LocalDate(2026, 9, 30),
            regras = regras,
        )
        return Cenario(grupos, checkIns, disco, CheckInService(users, grupos, checkIns, disco, relogio), id.toString())
    }

    private suspend fun Cenario.checkInDe(quem: dev.rafael.server.features.user.models.User, comFoto: Boolean = false) =
        (service.criar(quem.firebaseUid, quem.email, grupo, PedidoDeCheckIn(if (comFoto) jpeg() else null, null, null, null))
            as AppResult.Success).value

    // ---- apagar (4.11) ----

    @Test
    fun `o dono apaga o proprio check-in no mesmo dia e libera o slot`(): Unit = runBlocking {
        // Liberar o slot é o que faz a regra conversar com o índice único. Sem isso, apagar a foto
        // tremida deixaria a pessoa sem poder refazer — arrependimento viraria armadilha.
        val c = cenario()
        val feito = c.checkInDe(eu)

        assertIs<AppResult.Success<Unit>>(c.service.apagar(eu.firebaseUid, eu.email, c.grupo, feito.id))

        assertTrue(c.checkIns.guardados.isEmpty())
        assertIs<AppResult.Success<CheckInDto>>(
            c.service.criar(eu.firebaseUid, eu.email, c.grupo, PedidoDeCheckIn(null, null, null, null)),
        )
    }

    @Test
    fun `apagar leva a foto junto`(): Unit = runBlocking {
        val c = cenario(regras = setOf(GroupRule.FOTO))
        val feito = c.checkInDe(eu, comFoto = true)
        assertEquals(1, c.disco.refsVivas.size)

        c.service.apagar(eu.firebaseUid, eu.email, c.grupo, feito.id)

        assertTrue(c.disco.refsVivas.isEmpty(), "a foto ficou órfã no disco")
    }

    @Test
    fun `passada a meia-noite do grupo, nao apaga mais`(): Unit = runBlocking {
        val c = cenario()
        val feito = c.checkInDe(eu)
        assertTrue(feito.canDelete)

        // 03h01 UTC do dia 26 = 00h01 em São Paulo. Virou o dia DO GRUPO.
        agora = Instant.parse("2026-08-26T03:01:00Z")

        val erro = assertIs<AppResult.Failure>(c.service.apagar(eu.firebaseUid, eu.email, c.grupo, feito.id)).error
        assertEquals("PRAZO_DE_EXCLUSAO", assertIs<AppError.Conflict>(erro).code)
        assertEquals(1, c.checkIns.guardados.size)
    }

    @Test
    fun `ninguem apaga o check-in de outra pessoa — e recebe 404, nao 403`(): Unit = runBlocking {
        // 403 contaria que aquele check-in existe. Para quem não é dono, ele não existe.
        val c = cenario()
        val doOutro = c.checkInDe(outro)

        val r = c.service.apagar(eu.firebaseUid, eu.email, c.grupo, doOutro.id)

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(r).error)
        assertEquals(1, c.checkIns.guardados.size)
    }

    @Test
    fun `check-in EM ANALISE nao pode ser apagado`(): Unit = runBlocking {
        // [PROPOSTA — ratificar na fatia E] sem esta guarda, apagar-e-refazer desfaria uma
        // invalidação do admin, e "decisões do admin são imutáveis".
        val c = cenario()
        val feito = c.checkInDe(eu)
        c.checkIns.status[kotlin.uuid.Uuid.parse(feito.id)] = CheckInStatus.EM_ANALISE

        val erro = assertIs<AppResult.Failure>(c.service.apagar(eu.firebaseUid, eu.email, c.grupo, feito.id)).error
        assertEquals("EM_ANALISE", assertIs<AppError.Conflict>(erro).code)
    }

    // ---- feed (8.0) ----

    @Test
    fun `o feed traz os check-ins do grupo, mais recente primeiro`(): Unit = runBlocking {
        val c = cenario()
        c.checkInDe(outro)
        agora = Instant.parse("2026-08-25T19:00:00Z")
        val meu = c.checkInDe(eu)

        val feed = assertIs<AppResult.Success<List<CheckInDto>>>(
            c.service.feed(eu.firebaseUid, eu.email, c.grupo, null, null),
        ).value

        assertEquals(2, feed.size)
        assertEquals(meu.id, feed.first().id, "ordem cronológica invertida (8.0.4)")
    }

    @Test
    fun `o feed marca o que e MEU e so ai oferece apagar`(): Unit = runBlocking {
        val c = cenario()
        c.checkInDe(outro)
        c.checkInDe(eu)

        val feed = assertIs<AppResult.Success<List<CheckInDto>>>(
            c.service.feed(eu.firebaseUid, eu.email, c.grupo, null, null),
        ).value

        val meu = feed.single { it.mine }
        val dele = feed.single { !it.mine }
        assertTrue(meu.canDelete)
        assertFalse(dele.canDelete, "não se apaga o check-in dos outros")
    }

    @Test
    fun `o feed nunca expoe e-mail nem coordenada`(): Unit = runBlocking {
        // [REGRA] #33 e 5.2. A fronteira é o DTO: o que não está lá não atravessa. Este teste
        // existe para que acrescentar um campo ao DTO exija olhar para esta linha.
        val c = cenario(regras = setOf(GroupRule.LOCALIZACAO))
        c.service.criar(eu.firebaseUid, eu.email, c.grupo, PedidoDeCheckIn(null, "Smart Fit", -23.5505, -46.6333))

        val item = assertIs<AppResult.Success<List<CheckInDto>>>(
            c.service.feed(eu.firebaseUid, eu.email, c.grupo, null, null),
        ).value.single()

        assertEquals("Smart Fit", item.placeName)
        assertEquals(eu.displayName, item.displayName)
        val serializado = kotlinx.serialization.json.Json.encodeToString(CheckInDto.serializer(), item)
        assertFalse(serializado.contains("@"), "e-mail vazou no DTO")
        assertFalse(serializado.contains("23.55"), "coordenada vazou no DTO")
    }

    @Test
    fun `quem nao e membro nao le o feed`(): Unit = runBlocking {
        val forasteiro = usuario("forasteiro")
        val grupos = FakeGroupRepository()
        val users = UserService(FakeUserRepository(listOf(eu, forasteiro)))
        val grupo = grupos.semear(admin = eu.id, inicio = LocalDate(2026, 8, 1), fim = LocalDate(2026, 9, 30))
        val service = CheckInService(users, grupos, FakeCheckInRepository(), FakeArmazenamento(), relogio)

        val r = service.feed(forasteiro.firebaseUid, forasteiro.email, grupo.toString(), null, null)

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(r).error)
    }

    // ---- a foto, atrás de autenticação ----

    @Test
    fun `membro do grupo enxerga a foto de outro membro`(): Unit = runBlocking {
        val c = cenario(regras = setOf(GroupRule.FOTO))
        val dele = c.checkInDe(outro, comFoto = true)

        val bytes = assertIs<AppResult.Success<ByteArray>>(c.service.foto(eu.firebaseUid, eu.email, dele.id)).value

        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `quem NAO e do grupo nao enxerga a foto, mesmo com o id em maos`(): Unit = runBlocking {
        // ESTA é a razão de a rota ser autenticada em vez de estática (decisão desta fatia). Se um
        // dia isto virar URL aleatória servida como arquivo, este teste cai — e é para cair.
        val c = cenario(regras = setOf(GroupRule.FOTO))
        val meu = c.checkInDe(eu, comFoto = true)
        val forasteiro = usuario("forasteiro")
        val service = CheckInService(
            UserService(FakeUserRepository(listOf(forasteiro))), c.grupos, c.checkIns, c.disco, relogio,
        )

        val r = service.foto(forasteiro.firebaseUid, forasteiro.email, meu.id)

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(r).error)
    }

    @Test
    fun `check-in sem foto responde 404 na rota da foto`(): Unit = runBlocking {
        val c = cenario()
        val meu = c.checkInDe(eu)

        val r = c.service.foto(eu.firebaseUid, eu.email, meu.id)

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(r).error)
        assertNull(meu.photoUrl)
    }

    private fun jpeg(): ByteArray {
        val saida = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB), "jpeg", saida)
        return saida.toByteArray()
    }
}
