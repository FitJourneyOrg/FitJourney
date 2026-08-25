package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CheckInDto
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Criação de check-in (fatia B.1).
 *
 * O relógio é fixo: check-in é a operação mais dependente de data e fuso da fase, e teste que usa
 * o relógio da máquina falha sozinho às 21h de um dia qualquer.
 */
class CheckInServiceTest {

    private val eu = usuario("eu")
    private val agora = Instant.parse("2026-08-25T18:00:00Z")   // 15h em São Paulo
    private val relogio = object : Clock { override fun now() = agora }

    private class Cenario(
        val grupos: FakeGroupRepository,
        val checkIns: FakeCheckInRepository,
        val disco: FakeArmazenamento,
        val service: CheckInService,
    )

    private fun cenario(
        regras: Set<GroupRule> = emptySet(),
        inicio: LocalDate = LocalDate(2026, 8, 1),
        fim: LocalDate = LocalDate(2026, 9, 30),
    ): Pair<Cenario, String> {
        val grupos = FakeGroupRepository()
        val checkIns = FakeCheckInRepository()
        val disco = FakeArmazenamento()
        val users = UserService(FakeUserRepository(listOf(eu)))
        val id = grupos.semear(admin = eu.id, inicio = inicio, fim = fim, regras = regras)
        return Cenario(
            grupos, checkIns, disco,
            CheckInService(users, grupos, checkIns, disco, relogio),
        ) to id.toString()
    }

    private fun pedido(
        comFoto: Boolean = false,
        local: String? = null,
        lat: Double? = null,
        lng: Double? = null,
    ) = PedidoDeCheckIn(if (comFoto) jpeg() else null, local, lat, lng)

    private suspend fun Cenario.criar(grupo: String, pedido: PedidoDeCheckIn) =
        service.criar(eu.firebaseUid, eu.email, grupo, pedido)

    // ---- caminho feliz ----

    @Test
    fun `check-in em grupo sem regras nasce VALIDO e no dia do GRUPO`(): Unit = runBlocking {
        val (c, grupo) = cenario()

        val dto = assertIs<AppResult.Success<CheckInDto>>(c.criar(grupo, pedido())).value

        // 18h UTC é 15h em São Paulo, mesmo dia — mas quem manda é o fuso do grupo, não o do servidor.
        assertEquals("2026-08-25", dto.localDate)
        assertTrue(dto.mine)
        assertTrue(dto.canDelete, "feito hoje: ainda dá para apagar (4.11)")
        assertNull(dto.photoUrl, "grupo sem regra de foto")
    }

    @Test
    fun `com foto, o DTO aponta para a rota AUTENTICADA`(): Unit = runBlocking {
        val (c, grupo) = cenario(regras = setOf(GroupRule.FOTO))

        val dto = assertIs<AppResult.Success<CheckInDto>>(c.criar(grupo, pedido(comFoto = true))).value

        assertEquals("/checkins/${dto.id}/foto", dto.photoUrl)
        assertEquals(1, c.disco.gravacoes)
    }

    @Test
    fun `a coordenada e arredondada antes de chegar ao banco`(): Unit = runBlocking {
        // [INV] "A coordenada exata nunca é gravada". Aqui se prova no ponto de escrita.
        val (c, grupo) = cenario(regras = setOf(GroupRule.LOCALIZACAO))

        c.criar(grupo, pedido(local = "Smart Fit", lat = -23.5505199, lng = -46.6333094))

        val gravado = c.checkIns.guardados.single()
        assertEquals(-23.55, gravado.placeLat)
        assertEquals(-46.63, gravado.placeLng)
        assertEquals("Smart Fit", gravado.placeName)
    }

    // ---- recusa estrutural (5.3) ----

    @Test
    fun `grupo que exige foto recusa check-in sem foto`(): Unit = runBlocking {
        val (c, grupo) = cenario(regras = setOf(GroupRule.FOTO))

        val erro = assertIs<AppResult.Failure>(c.criar(grupo, pedido())).error
        assertIs<AppError.Validation>(erro)
        assertTrue("foto" in erro.fieldErrors)
        assertTrue(c.checkIns.guardados.isEmpty(), "nada pode ter sido gravado")
    }

    @Test
    fun `grupo que exige local recusa quem mandou so a foto`(): Unit = runBlocking {
        val (c, grupo) = cenario(regras = setOf(GroupRule.FOTO, GroupRule.LOCALIZACAO))

        val erro = assertIs<AppResult.Failure>(c.criar(grupo, pedido(comFoto = true))).error
        assertIs<AppError.Validation>(erro)
        assertTrue("localizacao" in erro.fieldErrors)
    }

    @Test
    fun `recusa por regra NAO deixa foto orfa no disco`(): Unit = runBlocking {
        // A validação roda ANTES de gravar. Se a ordem se inverter, cada tentativa inválida
        // deixaria um arquivo em disco que ninguém mais referencia — e nada avisaria.
        val (c, grupo) = cenario(regras = setOf(GroupRule.FOTO, GroupRule.LOCALIZACAO))

        c.criar(grupo, pedido(comFoto = true))

        assertEquals(0, c.disco.gravacoes, "gravou a foto antes de validar as regras")
    }

    // ---- estado do grupo ----

    @Test
    fun `desafio que ainda nao comecou recusa check-in`(): Unit = runBlocking {
        val (c, grupo) = cenario(inicio = LocalDate(2026, 12, 1), fim = LocalDate(2026, 12, 31))

        val erro = assertIs<AppResult.Failure>(c.criar(grupo, pedido())).error
        assertEquals(CheckInBlock.NAO_COMECOU.name, assertIs<AppError.Conflict>(erro).code)
    }

    @Test
    fun `desafio encerrado recusa check-in`(): Unit = runBlocking {
        // Aceitar aqui mudaria o resultado de um desafio que já acabou (2-B.4).
        val (c, grupo) = cenario(inicio = LocalDate(2026, 1, 1), fim = LocalDate(2026, 1, 31))

        val erro = assertIs<AppResult.Failure>(c.criar(grupo, pedido())).error
        assertEquals(CheckInBlock.ENCERRADO.name, assertIs<AppError.Conflict>(erro).code)
    }

    // ---- um por dia (4.3) ----

    @Test
    fun `o segundo check-in do dia e recusado`(): Unit = runBlocking {
        val (c, grupo) = cenario()
        c.criar(grupo, pedido())

        val erro = assertIs<AppResult.Failure>(c.criar(grupo, pedido())).error
        assertEquals(CheckInBlock.JA_FEZ_HOJE.name, assertIs<AppError.Conflict>(erro).code)
        assertEquals(1, c.checkIns.guardados.size)
    }

    @Test
    fun `perder a corrida do indice APAGA a foto ja gravada`(): Unit = runBlocking {
        // O toque duplo com rede lenta: as duas requisições passam pela validação, as duas gravam
        // a foto, e só uma vence o índice. Sem a limpeza, cada toque duplo deixaria uma foto sem
        // dono em disco — invisível, e crescendo.
        val (c, grupo) = cenario(regras = setOf(GroupRule.FOTO))
        val outro = c.checkIns   // encena: alguém insere a linha do dia bem antes do meu INSERT
        c.criar(grupo, pedido(comFoto = true))
        val refsAntes = c.disco.refsVivas

        val r = c.criar(grupo, pedido(comFoto = true))

        assertIs<AppResult.Failure>(r)
        assertEquals(2, c.disco.gravacoes, "a segunda foto chegou a ser gravada")
        assertEquals(refsAntes, c.disco.refsVivas, "e tinha que ter sido apagada")
        assertEquals(1, outro.guardados.size)
    }

    // ---- fronteiras ----

    @Test
    // `: Unit` explícito nos três testes que terminam em `assertIs`/`assertNotNull`: os dois
    // DEVOLVEM valor, e num corpo de expressão isso vira o tipo de retorno do método — o JUnit4
    // recusa a classe inteira com `InvalidTestClassError`, sem dizer qual método.
    fun `quem nao e membro recebe 404, nunca 403`(): Unit = runBlocking {
        // Responder "sem permissão" contaria que o grupo existe.
        val forasteiro = usuario("forasteiro")
        val grupos = FakeGroupRepository()
        val users = UserService(FakeUserRepository(listOf(eu, forasteiro)))
        val grupo = grupos.semear(admin = eu.id)
        val service = CheckInService(users, grupos, FakeCheckInRepository(), FakeArmazenamento(), relogio)

        val r = service.criar(forasteiro.firebaseUid, forasteiro.email, grupo.toString(), pedido())

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(r).error)
    }

    @Test
    fun `nome de local acima de 60 caracteres e recusado`(): Unit = runBlocking {
        val (c, grupo) = cenario(regras = setOf(GroupRule.LOCALIZACAO))

        val r = c.criar(grupo, pedido(local = "x".repeat(61), lat = -23.5, lng = -46.6))

        val erro = assertIs<AppError.Validation>(assertIs<AppResult.Failure>(r).error)
        assertNotNull(erro.fieldErrors["nomeDoLocal"])
    }

    @Test
    fun `nome de local sem coordenada e recusado`(): Unit = runBlocking {
        // O CHECK do banco recusaria com 500; melhor recusar aqui, dizendo o que houve.
        val (c, grupo) = cenario(regras = setOf(GroupRule.LOCALIZACAO))

        val r = c.criar(grupo, pedido(local = "Academia"))

        assertIs<AppError.Validation>(assertIs<AppResult.Failure>(r).error)
    }

    private fun jpeg(): ByteArray {
        val img = BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB)
        val saida = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", saida)
        return saida.toByteArray()
    }
}
