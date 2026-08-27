package dev.rafael.server.features.checkin.services

import dev.rafael.contract.group.RankingEntryDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.group.services.FakeGroupRepository
import dev.rafael.server.features.group.services.FakeUserRepository
import dev.rafael.server.features.group.services.usuario
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * O RANKING do grupo (7.2, fatia C).
 *
 * [REGRA] #18: grupo pontua por **contagem de check-ins**, nunca por XP. Este arquivo afirma as
 * regras que decidem a ORDEM — e a que mais se erra é o desempate.
 *
 * A exclusão de quem saiu (2.15) não está aqui: ela é o `LEFT JOIN` a partir de `group_members`,
 * e só o Postgres prova. Ver `CheckInIntegrationTest`.
 */
class RankingTest {

    private val ana = usuario("ana")
    private val bruno = usuario("bruno")
    private val caio = usuario("caio")

    private var agora = Instant.parse("2026-08-25T18:00:00Z")
    private val relogio = object : Clock { override fun now() = agora }

    private class Cenario(
        val checkIns: FakeCheckInRepository,
        val service: CheckInService,
        val grupo: String,
    )

    private fun cenario(vararg pessoas: User): Cenario {
        val grupos = FakeGroupRepository()
        val checkIns = FakeCheckInRepository().apply {
            // A ordem de entrada é o TERCEIRO critério — a que os argumentos foram passados.
            pessoas.forEachIndexed { i, p ->
                nomes[p.id] = p.displayName
                entradas[p.id] = LocalDateTime(2026, 7, 1 + i, 12, 0)
            }
        }
        val users = UserService(FakeUserRepository(pessoas.toList()))
        val id = grupos.semear(
            admin = pessoas.first().id,
            outros = pessoas.drop(1).map { it.id },
            inicio = LocalDate(2026, 8, 1),
            fim = LocalDate(2026, 9, 30),
        )
        return Cenario(
            checkIns,
            CheckInService(users, grupos, checkIns, FakeArmazenamento(), relogio),
            id.toString(),
        )
    }

    /** Faz check-in em nome de alguém num dia específico, avançando o relógio. */
    private suspend fun Cenario.checkIn(quem: User, dia: Int) {
        agora = Instant.parse("2026-08-%02dT18:00:00Z".format(dia))
        service.criar(quem.firebaseUid, quem.email, grupo, PedidoDeCheckIn(null, null, null, null))
    }

    private suspend fun Cenario.ranking(quem: User) =
        assertIs<AppResult.Success<List<RankingEntryDto>>>(
            service.ranking(quem.firebaseUid, quem.email, grupo),
        ).value

    // ---- ordem por contagem ----

    @Test
    fun `ordena por contagem de check-ins, decrescente`(): Unit = runBlocking {
        val c = cenario(ana, bruno, caio)
        c.checkIn(ana, 10)
        c.checkIn(bruno, 10); c.checkIn(bruno, 11); c.checkIn(bruno, 12)
        c.checkIn(caio, 10); c.checkIn(caio, 11)

        val r = c.ranking(ana)

        assertEquals(listOf("bruno", "caio", "ana"), r.map { it.displayName })
        assertEquals(listOf(1, 2, 3), r.map { it.position })
        assertEquals(listOf(3, 2, 1), r.map { it.checkIns })
    }

    @Test
    fun `quem ainda nao treinou aparece com zero, e nao some da lista`(): Unit = runBlocking {
        // O `LEFT JOIN` existe para isto. Saber que ainda não começou é informação útil —
        // inclusive para a própria pessoa, que abre o ranking e se vê no fim.
        val c = cenario(ana, bruno)
        c.checkIn(ana, 10)

        val r = c.ranking(ana)

        assertEquals(2, r.size)
        assertEquals(0, r.last().checkIns)
        assertEquals("bruno", r.last().displayName)
    }

    // ---- o DESEMPATE, que é a parte que se erra ----

    @Test
    fun `empate e resolvido por quem atingiu a pontuacao PRIMEIRO`(): Unit = runBlocking {
        // Duas pessoas com 2 check-ins. Ana terminou os dela no dia 11; Bruno, no dia 20. Ana
        // chegou aos 2 primeiro, então lidera.
        //
        // O desempate sai da MESMA consulta que conta: com contagens iguais, quem tem o último
        // check-in mais ANTIGO chegou lá antes.
        val c = cenario(ana, bruno)
        c.checkIn(ana, 10); c.checkIn(ana, 11)
        c.checkIn(bruno, 19); c.checkIn(bruno, 20)

        val r = c.ranking(ana)

        assertEquals(listOf("ana", "bruno"), r.map { it.displayName })
        assertEquals(listOf(2, 2), r.map { it.checkIns }, "a contagem é a mesma")
        assertEquals(listOf(1, 2), r.map { it.position }, "e mesmo assim não há posição repetida")
    }

    @Test
    fun `comecar antes NAO basta — o que vale e quando se ATINGE a pontuacao`(): Unit = runBlocking {
        // Ana começou primeiro (dia 1) e parou. Bruno começou depois e alcançou. Com 2 a 2, quem
        // fechou os 2 antes foi Ana — mas se o critério fosse "quem começou primeiro", Ana
        // lideraria mesmo tendo demorado um mês para fazer o segundo.
        val c = cenario(ana, bruno)
        c.checkIn(ana, 1); c.checkIn(ana, 28)
        c.checkIn(bruno, 5); c.checkIn(bruno, 6)

        val r = c.ranking(ana)

        assertEquals(listOf("bruno", "ana"), r.map { it.displayName })
    }

    @Test
    fun `no DIA 1 ninguem treinou, e a ordem ainda assim e ESTAVEL`(): Unit = runBlocking {
        // O empate não é caso de borda: é o estado inicial de TODO desafio. Cinquenta pessoas com
        // zero check-ins empatam nos dois primeiros critérios, e sem um terceiro o banco devolve
        // em ordem arbitrária — que muda entre consultas. Com o polling de 10s, a lista se
        // reembaralharia sozinha na tela de quem está olhando.
        val c = cenario(ana, bruno, caio)

        val primeira = c.ranking(ana)
        val segunda = c.ranking(ana)
        val terceira = c.ranking(bruno)

        assertEquals(listOf(0, 0, 0), primeira.map { it.checkIns })
        assertEquals(primeira.map { it.userId }, segunda.map { it.userId }, "a ordem mudou entre consultas")
        assertEquals(primeira.map { it.userId }, terceira.map { it.userId }, "a ordem muda conforme quem pergunta")
        // Quem entrou antes fica na frente — critério explicável, ao contrário de um id.
        assertEquals(listOf("ana", "bruno", "caio"), primeira.map { it.displayName })
    }

    @Test
    fun `empate em pontuacao MAIOR que zero tambem cai no criterio de entrada`(): Unit = runBlocking {
        // Mesmo com check-ins, dá para empatar nos dois primeiros: mesma contagem e mesmo instante
        // do último. Raro, mas o `joinedAt` fecha antes de sobrar id.
        val c = cenario(ana, bruno)
        agora = Instant.parse("2026-08-10T18:00:00Z")
        c.service.criar(ana.firebaseUid, ana.email, c.grupo, PedidoDeCheckIn(null, null, null, null))
        c.service.criar(bruno.firebaseUid, bruno.email, c.grupo, PedidoDeCheckIn(null, null, null, null))

        val r = c.ranking(ana)

        assertEquals(listOf(1, 1), r.map { it.checkIns })
        assertEquals(listOf("ana", "bruno"), r.map { it.displayName }, "ana entrou antes")
    }

    // ---- fronteiras ----

    @Test
    fun `o ranking marca quem sou eu, e a tela nao compara ids`(): Unit = runBlocking {
        val c = cenario(ana, bruno)
        c.checkIn(bruno, 10)

        val r = c.ranking(ana)

        assertEquals(1, r.count { it.mine })
        assertEquals("ana", r.single { it.mine }.displayName)
    }

    @Test
    fun `o ranking NAO expoe e-mail nem XP`(): Unit = runBlocking {
        // [REGRA] #18 e 9.3. A fronteira é o DTO: não há campo de XP a esquecer de omitir, porque
        // ele não existe. Este teste existe para que acrescentar um campo exija olhar para aqui.
        val c = cenario(ana, bruno)
        c.checkIn(ana, 10)

        val serializado = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(RankingEntryDto.serializer()),
            c.ranking(ana),
        )

        assertTrue(!serializado.contains("@"), "e-mail vazou no ranking")
        assertTrue(!serializado.contains("xp", ignoreCase = true), "XP vazou no ranking")
    }

    @Test
    fun `quem nao e membro recebe 404, nunca 403`(): Unit = runBlocking {
        val forasteiro = usuario("forasteiro")
        val grupos = FakeGroupRepository()
        val users = UserService(FakeUserRepository(listOf(ana, forasteiro)))
        val grupo = grupos.semear(admin = ana.id, inicio = LocalDate(2026, 8, 1), fim = LocalDate(2026, 9, 30))
        val service = CheckInService(users, grupos, FakeCheckInRepository(), FakeArmazenamento(), relogio)

        val r = service.ranking(forasteiro.firebaseUid, forasteiro.email, grupo.toString())

        assertIs<AppError.NotFound>(assertIs<AppResult.Failure>(r).error)
    }
}
