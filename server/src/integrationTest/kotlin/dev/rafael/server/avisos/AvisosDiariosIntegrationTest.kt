package dev.rafael.server.avisos

import dev.rafael.contract.checkin.ReportTarget
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.Semear
import dev.rafael.server.features.checkin.models.NovaDenuncia
import dev.rafael.server.features.checkin.db.ModeracaoRepositoryImpl
import dev.rafael.server.features.group.services.AvisosDiarios
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import kotlin.uuid.Uuid

/**
 * O laço diário contra Postgres REAL (V46).
 *
 * ## A consulta mais delicada da fatia F
 *
 * `entradasNoDia` faz **duas coisas que só existem no SQL** e que o `AvisosDoDiaTest` não alcança —
 * lá o fake recebe o número pronto:
 *
 * 1. **Converte o dia civil do GRUPO para o intervalo UTC** de `joined_at`. Um erro aqui manda o
 *    aviso do dia errado para quem está noutro fuso, e ninguém percebe: o número é plausível.
 * 2. **Exclui o criador.** Um erro no `neq` volta a contar o fundador como entrada — o defeito que
 *    a bateria da F pegou, e que o teste de unidade só consegue afirmar pela *intenção*.
 *
 * > **Teste de unidade sobre algo que o banco decide só pode afirmar a intenção.** Este arquivo é o
 * > resto.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AvisosDiariosIntegrationTest {

    private val repo = AvisosDiariosRepositoryImpl()
    private val moderacao = ModeracaoRepositoryImpl()

    private val saoPaulo = TimeZone.of("America/Sao_Paulo")   // UTC-3
    private val toquio = TimeZone.of("Asia/Tokyo")            // UTC+9
    private val agora = LocalDateTime.parse("2026-09-09T12:00:00")

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    // ---- entradasNoDia: o CRIADOR não conta ----

    /**
     * ⭐ **Criar não é entrar** — agora provado no SQL.
     *
     * O criador vira membro junto com o grupo. A bateria pegou isso quando fundar um desafio
     * disparou *"1 pessoa entrou no seu desafio hoje"* sobre si mesmo; o teste de unidade só
     * consegue afirmar que o serviço **mandou** excluir. Aqui se vê o `neq` funcionando.
     */
    @Test
    fun `o criador nao conta como entrada`(): Unit = runBlocking {
        val criador = Semear.usuario()
        val dia = LocalDate.parse("2026-09-09")
        val grupo = Semear.grupo(criador, entrouEm = LocalDateTime.parse("2026-09-09T10:00:00"))

        assertEquals(0, ok(repo.entradasNoDia(grupo, dia, saoPaulo, exceto = criador)))
    }

    /** Sem o `exceto`, a mesma consulta conta o criador — é o que o defeito fazia. */
    @Test
    fun `sem o filtro o criador seria contado`(): Unit = runBlocking {
        val criador = Semear.usuario()
        val dia = LocalDate.parse("2026-09-09")
        val grupo = Semear.grupo(criador, entrouEm = LocalDateTime.parse("2026-09-09T10:00:00"))

        assertEquals(1, ok(repo.entradasNoDia(grupo, dia, saoPaulo, exceto = null)))
    }

    @Test
    fun `conta quem entrou de verdade, sem o criador`(): Unit = runBlocking {
        val criador = Semear.usuario()
        val dia = LocalDate.parse("2026-09-09")
        val grupo = Semear.grupo(criador, entrouEm = LocalDateTime.parse("2026-09-09T10:00:00"))
        Semear.membro(grupo, Semear.usuario(), LocalDateTime.parse("2026-09-09T11:00:00"))
        Semear.membro(grupo, Semear.usuario(), LocalDateTime.parse("2026-09-09T18:00:00"))

        assertEquals(2, ok(repo.entradasNoDia(grupo, dia, saoPaulo, exceto = criador)))
    }

    // ---- entradasNoDia: o FUSO ----

    /**
     * ⭐ O dia é o do GRUPO, e a conversão acontece no SQL.
     *
     * `joined_at` é UTC; o dia é o calendário do grupo. **23h50 em São Paulo do dia 9** é
     * `2026-09-10T02:50` em UTC — comparar `joined_at::date = dia` contaria como dia 10, e a pessoa
     * receberia "entrou hoje" no dia seguinte.
     *
     * É o mesmo erro que a V38 evita ao persistir `local_date`, e o mesmo que fez o comando de
     * teste da bateria E.2 acusar o código sem motivo.
     */
    @Test
    fun `entrada as 23h50 em Sao Paulo conta no dia de LA, nao no de UTC`(): Unit = runBlocking {
        val criador = Semear.usuario()
        val grupo = Semear.grupo(criador, entrouEm = LocalDateTime.parse("2026-09-01T10:00:00"))
        // 2026-09-10T02:50 UTC = 2026-09-09 23h50 em São Paulo
        Semear.membro(grupo, Semear.usuario(), LocalDateTime.parse("2026-09-10T02:50:00"))

        assertEquals(
            1,
            ok(repo.entradasNoDia(grupo, LocalDate.parse("2026-09-09"), saoPaulo, criador)),
            "o dia do GRUPO é 9",
        )
        assertEquals(
            0,
            ok(repo.entradasNoDia(grupo, LocalDate.parse("2026-09-10"), saoPaulo, criador)),
            "não é do dia 10, embora o UTC diga isso",
        )
    }

    /**
     * O outro lado do mundo, para o teste não passar por coincidência de sinal.
     *
     * Tóquio é UTC+9: **08h00 do dia 9 em Tóquio** é `2026-09-08T23:00` em UTC. Um bug que somasse
     * o fuso em vez de subtrair passaria no teste de São Paulo e falharia aqui.
     */
    @Test
    fun `o mesmo instante cai em dias diferentes conforme o fuso do grupo`(): Unit = runBlocking {
        val criador = Semear.usuario()
        val grupo = Semear.grupo(criador, fuso = "Asia/Tokyo", entrouEm = LocalDateTime.parse("2026-09-01T10:00:00"))
        Semear.membro(grupo, Semear.usuario(), LocalDateTime.parse("2026-09-08T23:00:00"))

        assertEquals(1, ok(repo.entradasNoDia(grupo, LocalDate.parse("2026-09-09"), toquio, criador)))
        assertEquals(0, ok(repo.entradasNoDia(grupo, LocalDate.parse("2026-09-08"), toquio, criador)))
    }

    // ---- V46: a PK tripla ----

    /**
     * ⭐ "Um aviso por grupo por dia por tipo" — **é a PK**, não o intervalo do laço.
     *
     * O laço acorda de hora em hora e passa por este grupo **24 vezes por dia**. Este índice é a
     * única coisa entre isso e 24 notificações.
     */
    @Test
    fun `o segundo aviso do mesmo tipo no mesmo dia e recusado`(): Unit = runBlocking {
        val grupo = Semear.grupo(Semear.usuario())
        val dia = LocalDate.parse("2026-09-09")

        assertTrue(ok(repo.registrarAviso(grupo, dia, AvisosDiarios.Tipo.ENTRADAS_DO_DIA, agora)))
        assertFalse(ok(repo.registrarAviso(grupo, dia, AvisosDiarios.Tipo.ENTRADAS_DO_DIA, agora)))
    }

    /** Tipos diferentes no mesmo dia convivem — a chave inclui o `kind`. */
    @Test
    fun `entradas e fila parada cabem no mesmo dia`(): Unit = runBlocking {
        val grupo = Semear.grupo(Semear.usuario())
        val dia = LocalDate.parse("2026-09-09")

        assertTrue(ok(repo.registrarAviso(grupo, dia, AvisosDiarios.Tipo.ENTRADAS_DO_DIA, agora)))
        assertTrue(ok(repo.registrarAviso(grupo, dia, AvisosDiarios.Tipo.FILA_PARADA, agora)))
    }

    /** No dia seguinte volta a valer — a chave inclui o dia. */
    @Test
    fun `o mesmo aviso passa no dia seguinte`(): Unit = runBlocking {
        val grupo = Semear.grupo(Semear.usuario())

        assertTrue(
            ok(repo.registrarAviso(grupo, LocalDate.parse("2026-09-09"), AvisosDiarios.Tipo.ENTRADAS_DO_DIA, agora)),
        )
        assertTrue(
            ok(repo.registrarAviso(grupo, LocalDate.parse("2026-09-10"), AvisosDiarios.Tipo.ENTRADAS_DO_DIA, agora)),
        )
    }

    /** Vocabulário fechado: um `kind` que o código não conhece não entra. */
    @Test
    fun `kind fora do vocabulario e recusado`() {
        val grupo = Semear.grupo(Semear.usuario())

        assertThrows<ExposedSQLException> {
            transaction {
                GroupDailyNoticesTable.insert {
                    it[groupId] = grupo
                    it[day] = LocalDate.parse("2026-09-09")
                    it[kind] = "AVISO_INVENTADO"
                    it[sentAt] = agora
                }
            }
        }
    }

    @Test
    fun `apagar o grupo leva os avisos do dia`(): Unit = runBlocking {
        val grupo = Semear.grupo(Semear.usuario())
        ok(repo.registrarAviso(grupo, LocalDate.parse("2026-09-09"), AvisosDiarios.Tipo.ENTRADAS_DO_DIA, agora))

        transaction { exec("delete from groups where id = '$grupo'") }

        assertEquals(
            0,
            transaction {
                GroupDailyNoticesTable.selectAll()
                    .where { GroupDailyNoticesTable.groupId eq grupo }.count().toInt()
            },
        )
    }

    // ---- gruposVivos e o estado derivado ----

    /**
     * `encerrado` é derivado no SQL a partir de `end_date` e do **fuso do grupo**.
     *
     * O dia do fim **conta**: um desafio que termina hoje ainda está ativo. Foi o que fez o comando
     * `end_date = current_date - 1` da bateria não encerrar nada — e o código estava certo.
     */
    @Test
    fun `o dia do fim ainda nao e encerrado`(): Unit = runBlocking {
        val criador = Semear.usuario()
        val hoje = AvisosDiarios.diaDoGrupo(kotlin.time.Clock.System.now(), saoPaulo)
        val grupo = Semear.grupo(criador, fim = hoje)

        val vivo = ok(repo.gruposVivos()).single { it.id == grupo }
        assertFalse(vivo.encerrado, "o dia do fim conta")
    }

    @Test
    fun `gruposVivos traz o criador junto`(): Unit = runBlocking {
        val criador = Semear.usuario()
        val grupo = Semear.grupo(criador)

        assertEquals(criador, ok(repo.gruposVivos()).single { it.id == grupo }.criadorId)
    }

    // ---- filaAberta ----

    /**
     * ⭐ A fila conta **CASOS**, não denúncias (6.11), e o agrupamento atravessa o arco exclusivo.
     *
     * Cinco pessoas denunciando o mesmo check-in são um item para o admin abrir. O
     * `AvisosDoDiaTest` recebe o número pronto; aqui ele sai do banco.
     */
    @Test
    fun `a fila conta casos e nao denuncias`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val grupo = Semear.grupo(dono)
        val checkIn = Semear.checkIn(grupo, dono)

        repeat(3) {
            ok(
                moderacao.denunciar(
                    NovaDenuncia(
                        Uuid.random(), grupo, ReportTarget.CHECK_IN, checkIn,
                        Semear.usuario(), "motivo", agora,
                    ),
                ),
            )
        }

        assertEquals(1, ok(repo.filaAberta(grupo)).casos)
    }

    /** Alvos diferentes são casos diferentes — mesmo com o mesmo denunciante. */
    @Test
    fun `alvos diferentes sao casos diferentes`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val grupo = Semear.grupo(dono)
        val quemDenuncia = Semear.usuario()
        val um = Semear.checkIn(grupo, dono, dia = LocalDate.parse("2026-09-08"))
        val outro = Semear.checkIn(grupo, dono, dia = LocalDate.parse("2026-09-09"))

        listOf(um, outro).forEach { alvo ->
            ok(
                moderacao.denunciar(
                    NovaDenuncia(
                        Uuid.random(), grupo, ReportTarget.CHECK_IN, alvo,
                        quemDenuncia, "motivo", agora,
                    ),
                ),
            )
        }

        assertEquals(2, ok(repo.filaAberta(grupo)).casos)
    }

    /**
     * O cronômetro é do caso **mais antigo** — e é o SQL que ordena.
     *
     * Pelo mais recente, um grupo com denúncias frequentes nunca geraria lembrete: exatamente o que
     * mais precisa.
     */
    @Test
    fun `a fila reporta a data do caso mais antigo`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val grupo = Semear.grupo(dono)
        val checkIn = Semear.checkIn(grupo, dono)
        val velha = LocalDateTime.parse("2026-09-01T08:00:00")

        ok(
            moderacao.denunciar(
                NovaDenuncia(Uuid.random(), grupo, ReportTarget.CHECK_IN, checkIn, Semear.usuario(), "a", velha),
            ),
        )
        ok(
            moderacao.denunciar(
                NovaDenuncia(Uuid.random(), grupo, ReportTarget.CHECK_IN, checkIn, Semear.usuario(), "b", agora),
            ),
        )

        assertEquals(
            velha.toInstant(TimeZone.UTC),
            ok(repo.filaAberta(grupo)).maisAntigoEm,
            "a mais antiga manda — a mais nova não zera o cronômetro",
        )
    }

    /** Resolvida sai da fila — o `WHERE resolved_at IS NULL`. */
    @Test
    fun `caso resolvido sai da fila`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val grupo = Semear.grupo(dono)
        val checkIn = Semear.checkIn(grupo, dono)
        ok(
            moderacao.denunciar(
                NovaDenuncia(Uuid.random(), grupo, ReportTarget.CHECK_IN, checkIn, Semear.usuario(), "x", agora),
            ),
        )

        ok(moderacao.resolver(checkIn, agora))

        assertEquals(0, ok(repo.filaAberta(grupo)).casos)
    }

    // ---- encerrarCasosPendentes ----

    /**
     * ⭐ Fecha os casos e grava a auditoria com **`admin_id` nulo** — a emenda de 2026-09-08.
     *
     * É o único caminho do sistema que grava decisão sem autor, e é esse nulo que distingue "o
     * admin manteve" de "o desafio acabou antes".
     */
    @Test
    fun `encerrar grava auditoria sem autor e esvazia a fila`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val grupo = Semear.grupo(dono)
        val checkIn = Semear.checkIn(grupo, dono)
        ok(
            moderacao.denunciar(
                NovaDenuncia(Uuid.random(), grupo, ReportTarget.CHECK_IN, checkIn, Semear.usuario(), "x", agora),
            ),
        )

        assertEquals(1, ok(repo.encerrarCasosPendentes(grupo, agora)))

        assertEquals(0, ok(repo.filaAberta(grupo)).casos)
        val acao = transaction {
            dev.rafael.server.features.checkin.db.ModerationActionsTable.selectAll()
                .where { dev.rafael.server.features.checkin.db.ModerationActionsTable.targetId eq checkIn }
                .single()
        }
        assertNull(acao[dev.rafael.server.features.checkin.db.ModerationActionsTable.adminId])
        assertEquals(
            "ENCERRADO_SEM_JULGAMENTO",
            acao[dev.rafael.server.features.checkin.db.ModerationActionsTable.action],
        )
    }

    /**
     * ⭐ **Idempotente.** O laço passa por todo grupo encerrado a cada ciclo.
     *
     * Sem isto, um desafio encerrado com denúncia gravaria uma linha de auditoria **por hora, para
     * sempre** — e a tabela append-only viraria lixo.
     */
    @Test
    fun `encerrar de novo nao grava nada`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val grupo = Semear.grupo(dono)
        val checkIn = Semear.checkIn(grupo, dono)
        ok(
            moderacao.denunciar(
                NovaDenuncia(Uuid.random(), grupo, ReportTarget.CHECK_IN, checkIn, Semear.usuario(), "x", agora),
            ),
        )
        ok(repo.encerrarCasosPendentes(grupo, agora))

        assertEquals(0, ok(repo.encerrarCasosPendentes(grupo, agora)))
        assertEquals(
            1,
            transaction {
                dev.rafael.server.features.checkin.db.ModerationActionsTable.selectAll()
                    .where { dev.rafael.server.features.checkin.db.ModerationActionsTable.targetId eq checkIn }
                    .count().toInt()
            },
        )
    }

    /** Uma linha por ALVO, não por denúncia: três pessoas no mesmo check-in dão uma decisão. */
    @Test
    fun `encerrar grava uma linha por alvo`(): Unit = runBlocking {
        val dono = Semear.usuario()
        val grupo = Semear.grupo(dono)
        val checkIn = Semear.checkIn(grupo, dono)
        repeat(3) {
            ok(
                moderacao.denunciar(
                    NovaDenuncia(
                        Uuid.random(), grupo, ReportTarget.CHECK_IN, checkIn,
                        Semear.usuario(), "motivo", agora,
                    ),
                ),
            )
        }

        assertEquals(1, ok(repo.encerrarCasosPendentes(grupo, agora)))
    }
}
