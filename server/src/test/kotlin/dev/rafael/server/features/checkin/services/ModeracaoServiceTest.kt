package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.contract.checkin.ReportTarget
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.checkin.models.NovoCheckIn
import dev.rafael.server.features.checkin.models.TipoDeAcao
import dev.rafael.server.features.group.services.FakeGroupRepository
import dev.rafael.server.features.group.services.FakeUserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.media.ArmazenamentoDeMidia
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Denúncia e moderação (fatia E.2) — o serviço, suas guardas e os efeitos colaterais.
 *
 * ## O que este arquivo prova, e o `ModeracaoPolicyTest` não
 *
 * A política responde "cabe denunciar?". Aqui se testa o que só o serviço sabe: **quem** pede, **de
 * qual grupo**, se o alvo pertence a ele, e — o mais importante — **o que efetivamente mudou no
 * mundo** depois de cada ação: o estado do check-in, a fila, o ranking e o livro-razão.
 *
 * ## O relógio entra por parâmetro
 *
 * Um [Clock] fixo, avançável pelo teste. Sem isso, "denúncia fora do prazo" exigiria esperar sete
 * dias — e testes que dependem do dia em que rodam falham sozinhos numa segunda-feira qualquer.
 */
class ModeracaoServiceTest {

    private val admin = Uuid.random()
    private val autor = Uuid.random()
    private val membro = Uuid.random()
    private val estranho = Uuid.random()

    private val nascimento = LocalDateTime(2026, 12, 10, 12, 0)

    /** Relógio que o teste move. É o que torna o prazo de 7 dias verificável em milissegundos. */
    private class RelogioFixo(var agora: Instant) : Clock {
        override fun now(): Instant = agora
        fun avancar(quanto: Duration) { agora += quanto }
    }

    private class Cenario {
        val grupos = FakeGroupRepository()
        val checkIns = FakeCheckInRepository()
        val social = FakeSocialRepository()
        val moderacao = FakeModeracaoRepository()
        val relogio = RelogioFixo(LocalDateTime(2026, 12, 10, 12, 0).toInstant(TimeZone.UTC))

        lateinit var grupo: Uuid
        lateinit var outroGrupo: Uuid
        lateinit var doAutor: Uuid
        lateinit var deOutroGrupo: Uuid
        lateinit var servico: ModeracaoService

        /** O serviço de check-in, para provar que a invalidação some do RANKING de verdade. */
        lateinit var checkInService: CheckInService

        fun montar(admin: Uuid, membros: List<Uuid>, autor: Uuid): Cenario {
            grupo = grupos.semear(admin = admin, outros = membros)
            outroGrupo = grupos.semear(admin = Uuid.random(), code = "ZZZ999")

            doAutor = semear(grupo, autor)
            deOutroGrupo = semear(outroGrupo, Uuid.random())

            val users = FakeUserRepository((membros + admin + autor).distinct().map(::usuario))
            servico = ModeracaoService(
                UserService(users), grupos, checkIns, social, moderacao, relogio,
            )
            checkInService = CheckInService(
                UserService(users), grupos, checkIns, MidiaInerte, social, relogio,
            )
            return this
        }

        fun semear(g: Uuid, dono: Uuid, dia: LocalDate = LocalDate(2026, 12, 10)): Uuid {
            val id = Uuid.random()
            checkIns.semear(
                NovoCheckIn(
                    id = id,
                    groupId = g,
                    userId = dono,
                    localDate = dia,
                    createdAt = LocalDateTime(dia.year, dia.month, dia.day, 12, 0),
                    photoRef = null,
                    placeName = null,
                    placeLat = null,
                    placeLng = null,
                    emoji = null,
                ),
            )
            checkIns.entradas[dono] = LocalDateTime(2026, 12, 1, 8, 0)
            return id
        }

        private fun usuario(id: Uuid) = User(
            id = id,
            firebaseUid = "fb-$id",
            email = null,
            isPremium = false,
            displayName = "Atleta",
            code = id.toString().filter { it.isLetterOrDigit() }.take(8).uppercase(),
        )
    }

    /** A mídia não participa desta fatia. Um dublê que estoura denuncia uso indevido. */
    private object MidiaInerte : ArmazenamentoDeMidia {
        override suspend fun guardar(bytes: ByteArray, extensao: String) = error("não usa mídia")
        override suspend fun ler(ref: String) = error("não usa mídia")
        override suspend fun apagar(ref: String) = error("não usa mídia")
        override suspend fun listarRefs(anteriorA: Instant) = error("não usa mídia")
    }

    private fun cenario() =
        Cenario().montar(admin = admin, membros = listOf(autor, membro), autor = autor)

    private fun uid(u: Uuid) = "fb-$u"

    private fun Cenario.estadoDe(id: Uuid) = runBlocking {
        (checkIns.porId(id) as AppResult.Success).value!!.checkIn.status
    }

    // ---- denunciar check-in ----

    @Test
    fun `a primeira denuncia poe o check-in EM_ANALISE`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.denunciarCheckIn(
            uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "Foto de outro dia",
        )

        assertTrue(r is AppResult.Success)
        assertEquals(CheckInStatus.EM_ANALISE, c.estadoDe(c.doAutor))
    }

    /**
     * ⭐ 6.8: EM_ANALISE **continua contando ponto**.
     *
     * Testado pelo RANKING de verdade, não pelo status: a regra que interessa não é "o campo diz
     * EM_ANALISE", é "a pessoa não perdeu a posição". Se o ponto sumisse durante a análise, a
     * denúncia viraria arma — bastaria denunciar quem lidera na véspera do fim.
     */
    @Test
    fun `check-in em analise continua pontuando no ranking`(): Unit = runBlocking {
        val c = cenario()
        val antes = c.ranking()

        c.servico.denunciarCheckIn(
            uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "suspeito",
        )

        assertEquals(antes, c.ranking(), "presunção de boa-fé: o ponto fica até o admin decidir")
        assertEquals(1, c.ranking()[autor], "o autor mantém o check-in dele")
    }

    /**
     * 6.11: a segunda denúncia da mesma pessoa é RECUSADA e não engorda nada.
     *
     * Quem garante é o índice único da V45, reproduzido no dublê. Um fake que guardasse numa lista
     * deixaria isto passar contando 2 — e o defeito só apareceria em produção, no dia em que
     * alguém tocasse duas vezes no botão.
     */
    @Test
    fun `a mesma pessoa nao denuncia duas vezes`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "1")

        val segunda = c.servico.denunciarCheckIn(
            uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "2",
        )

        assertTrue(segunda is AppResult.Failure && segunda.error is AppError.Conflict)
        assertEquals(1, c.moderacao.denuncias.size)
    }

    /** 6.11: duas PESSOAS diferentes viram UM caso, com contador 2. */
    @Test
    fun `duas pessoas denunciando o mesmo check-in viram UM caso`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "a")
        c.servico.denunciarCheckIn(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), "b")

        val fila = c.servico.fila(uid(admin), null, c.grupo.toString())

        assertTrue(fila is AppResult.Success)
        assertEquals(1, fila.value.size, "um item na fila, não dois")
        assertEquals(2, fila.value.single().count)
        assertEquals(listOf("a", "b"), fila.value.single().reasons)
    }

    @Test
    fun `o dono nao denuncia o proprio check-in`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.denunciarCheckIn(
            uid(autor), null, c.grupo.toString(), c.doAutor.toString(), "me arrependi",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
        assertEquals(CheckInStatus.VALIDO, c.estadoDe(c.doAutor), "nada mudou")
    }

    @Test
    fun `fora dos sete dias a denuncia e recusada`(): Unit = runBlocking {
        val c = cenario()
        c.relogio.avancar(7.days + 1.hours)

        val r = c.servico.denunciarCheckIn(
            uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "tarde demais",
        )

        assertTrue(r is AppResult.Failure)
        assertEquals(CODE_PRAZO_DA_DENUNCIA, (r.error as AppError.Conflict).code)
        assertTrue(c.moderacao.denuncias.isEmpty())
    }

    @Test
    fun `motivo em branco e recusado antes de chegar ao banco`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.denunciarCheckIn(
            uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "   ",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
        assertTrue(c.moderacao.denuncias.isEmpty())
    }

    /**
     * ⭐ A SEGUNDA GUARDA, a mesma da E.1.
     *
     * Sou membro do grupo A e conheço um id de check-in do grupo B. A filiação passa — eu **sou**
     * membro do grupo que declarei — e é a conferência do alvo que barra. Sem ela, a fila de
     * qualquer admin encheria de conteúdo que ele não pode nem abrir.
     */
    @Test
    fun `nao denuncio check-in de OUTRO grupo`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.denunciarCheckIn(
            uid(membro), null, c.grupo.toString(), c.deOutroGrupo.toString(), "x",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound, "404, nunca 403")
        assertTrue(c.moderacao.denuncias.isEmpty())
    }

    @Test
    fun `quem nao e membro leva 404`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.denunciarCheckIn(
            uid(estranho), null, c.grupo.toString(), c.doAutor.toString(), "x",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
    }

    // ---- a fila ----

    /**
     * ⭐ **A fila não diz QUEM denunciou** (decisão de 2026-09-07).
     *
     * O teste olha o DTO inteiro, não um campo: se alguém acrescentar `reporterName` amanhã, este
     * teste continua verde — e é por isso que a garantia real está no contrato, onde o campo não
     * existe. O que se afirma aqui é que o dado **está no banco** e mesmo assim não sai.
     */
    @Test
    fun `a fila mostra contagem e motivos, e o banco guarda o denunciante`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "foto falsa")

        val fila = (c.servico.fila(uid(admin), null, c.grupo.toString()) as AppResult.Success).value

        assertEquals("foto falsa", fila.single().reasons.single())
        assertEquals(
            membro,
            c.moderacao.denuncias.values.single().denuncianteId,
            "o banco sabe quem foi — o DTO é que não conta",
        )
    }

    /** 6.2 + 6.7: membro comum leva **403**, não 404. Ele sabe que o grupo existe. */
    @Test
    fun `membro comum nao ve a fila e leva 403`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.fila(uid(membro), null, c.grupo.toString())

        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
    }

    /** O badge conta CASOS, não denúncias: cinco pessoas no mesmo check-in são um item para abrir. */
    @Test
    fun `o contador do badge conta casos, nao denuncias`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "a")
        c.servico.denunciarCheckIn(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), "b")

        val n = c.servico.pendentes(uid(admin), null, c.grupo.toString())

        assertTrue(n is AppResult.Success)
        assertEquals(1, n.value, "duas denúncias, um caso")
    }

    /** A fila carrega o CONTEÚDO junto — a tela do admin não faz uma busca por item. */
    @Test
    fun `a fila vem com o check-in hidratado`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "x")

        val item = (c.servico.fila(uid(admin), null, c.grupo.toString()) as AppResult.Success).value.single()

        assertEquals(ReportTarget.CHECK_IN, item.target)
        assertEquals(c.doAutor.toString(), item.checkIn?.id)
        assertEquals(null, item.comment)
    }

    // ---- julgar ----

    /**
     * ⭐ 6.3 + o [INV] "invalidado nunca volta a contar", ponta a ponta.
     *
     * Três afirmações no mesmo teste porque é UM fato: acatar a denúncia invalida o check-in, tira
     * o ponto do ranking e fecha o caso. Separá-las em três testes daria a impressão de que podem
     * acontecer independentemente — e o defeito perigoso é justamente o parcial.
     */
    @Test
    fun `acatar invalida, tira do ranking e esvazia a fila`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "falso")

        val r = c.servico.julgar(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), acatar = true)

        assertTrue(r is AppResult.Success)
        assertEquals(CheckInStatus.INVALIDADO, c.estadoDe(c.doAutor))
        assertEquals(0, c.ranking()[autor], "perdeu o ponto (6.3)")
        assertEquals(0, (c.servico.pendentes(uid(admin), null, c.grupo.toString()) as AppResult.Success).value)
    }

    @Test
    fun `recusar devolve a VALIDO e tambem fecha o caso`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "acho que")

        c.servico.julgar(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), acatar = false)

        assertEquals(CheckInStatus.VALIDO, c.estadoDe(c.doAutor), "absolvido é indistinguível de nunca acusado")
        assertEquals(1, c.ranking()[autor])
        assertEquals(0, (c.servico.pendentes(uid(admin), null, c.grupo.toString()) as AppResult.Success).value)
    }

    /** [INV] 6.6: **toda** decisão vira registro, inclusive a que não muda nada visível. */
    @Test
    fun `toda decisao vira registro append-only, inclusive a de manter`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "x")

        c.servico.julgar(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), acatar = false)

        val registro = c.moderacao.decisoesSobre(c.doAutor, ReportTarget.CHECK_IN).single()
        assertEquals(TipoDeAcao.MANTER_CHECK_IN, registro.acao)
        assertEquals(admin, registro.adminId)
    }

    @Test
    fun `membro comum nao julga`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), "x")

        val r = c.servico.julgar(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), acatar = true)

        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
        assertEquals(CheckInStatus.EM_ANALISE, c.estadoDe(c.doAutor), "continua esperando o admin")
    }

    @Test
    fun `julgar o que nao esta na fila e 404`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.julgar(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), acatar = true)

        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
        assertEquals(CheckInStatus.VALIDO, c.estadoDe(c.doAutor))
    }

    // ---- invalidação direta (6.10) ----

    @Test
    fun `o admin invalida direto, sem denuncia previa`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.invalidarDireto(uid(admin), null, c.grupo.toString(), c.doAutor.toString())

        assertTrue(r is AppResult.Success)
        assertEquals(CheckInStatus.INVALIDADO, c.estadoDe(c.doAutor))
        assertEquals(0, c.ranking()[autor])
    }

    /**
     * Os dois caminhos deixam a MESMA auditoria.
     *
     * É o que permite responder "o que aconteceu com este check-in?" sem perguntar antes por qual
     * rota o admin passou — e o que impede a 6.10 de virar um atalho sem rastro.
     */
    @Test
    fun `invalidar direto registra a mesma acao que acatar uma denuncia`(): Unit = runBlocking {
        val c = cenario()

        c.servico.invalidarDireto(uid(admin), null, c.grupo.toString(), c.doAutor.toString())

        assertEquals(
            TipoDeAcao.INVALIDAR_CHECK_IN,
            c.moderacao.decisoesSobre(c.doAutor, ReportTarget.CHECK_IN).single().acao,
        )
    }

    /** Invalidar direto o que já tinha denúncia aberta FECHA o caso — não se julga o já decidido. */
    @Test
    fun `invalidar direto tambem esvazia a fila daquele alvo`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "x")

        c.servico.invalidarDireto(uid(admin), null, c.grupo.toString(), c.doAutor.toString())

        assertEquals(0, (c.servico.pendentes(uid(admin), null, c.grupo.toString()) as AppResult.Success).value)
    }

    @Test
    fun `membro comum nao invalida direto`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.invalidarDireto(uid(membro), null, c.grupo.toString(), c.doAutor.toString())

        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
        assertEquals(CheckInStatus.VALIDO, c.estadoDe(c.doAutor))
    }

    // ---- comentários (6.4) ----

    @Test
    fun `acatar denuncia de comentario o REMOVE`(): Unit = runBlocking {
        val c = cenario()
        val comentario = c.comentar(de = autor, texto = "ofensa")
        c.servico.denunciarComentario(uid(membro), null, c.grupo.toString(), comentario.toString(), "grosseiro")

        c.servico.julgar(uid(admin), null, c.grupo.toString(), comentario.toString(), acatar = true)

        assertTrue(c.social.comentarios.isEmpty())
        assertEquals(
            TipoDeAcao.REMOVER_COMENTARIO,
            c.moderacao.decisoesSobre(comentario, ReportTarget.COMMENT).single().acao,
        )
    }

    @Test
    fun `recusar denuncia de comentario o mantem`(): Unit = runBlocking {
        val c = cenario()
        val comentario = c.comentar(de = autor, texto = "opinião")
        c.servico.denunciarComentario(uid(membro), null, c.grupo.toString(), comentario.toString(), "não gostei")

        c.servico.julgar(uid(admin), null, c.grupo.toString(), comentario.toString(), acatar = false)

        assertEquals(1, c.social.comentarios.size)
        assertEquals(
            TipoDeAcao.MANTER_COMENTARIO,
            c.moderacao.decisoesSobre(comentario, ReportTarget.COMMENT).single().acao,
        )
    }

    @Test
    fun `ninguem denuncia o proprio comentario`(): Unit = runBlocking {
        val c = cenario()
        val comentario = c.comentar(de = membro, texto = "meu")

        val r = c.servico.denunciarComentario(
            uid(membro), null, c.grupo.toString(), comentario.toString(), "x",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
    }

    /**
     * A fila mistura os dois tipos e cada item sabe o que é.
     *
     * O `target` não é decoração: é ele que diz à tela o que desenhar e ao serviço qual ação
     * executar no julgamento. Um caso de comentário tratado como check-in tentaria mudar um
     * `status` que não existe.
     */
    @Test
    fun `a fila carrega check-in e comentario juntos, cada um com seu tipo`(): Unit = runBlocking {
        val c = cenario()
        val comentario = c.comentar(de = autor, texto = "algo")
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "a")
        c.servico.denunciarComentario(uid(membro), null, c.grupo.toString(), comentario.toString(), "b")

        val fila = (c.servico.fila(uid(admin), null, c.grupo.toString()) as AppResult.Success).value

        assertEquals(2, fila.size)
        val doComentario = fila.single { it.target == ReportTarget.COMMENT }
        assertEquals("algo", doComentario.comment?.body)
        assertFalse(doComentario.comment!!.canDelete, "o admin age pela fila, não pelos botões do card")
        assertEquals(c.doAutor.toString(), fila.single { it.target == ReportTarget.CHECK_IN }.checkIn?.id)
    }

    // ---- a regra ratificada: check-in sob moderação não se apaga ----

    /**
     * ⭐ [REGRA ratificada em 2026-09-07] A brecha do apagar-e-refazer, fechada.
     *
     * A 4.11 diz que apagar libera o slot do dia. Sem esta guarda, quem fosse invalidado apagaria o
     * check-in e faria outro — desfazendo a decisão do admin com dois toques, num sistema onde
     * "decisões do admin são imutáveis".
     *
     * Vale para `EM_ANALISE` também, e é a metade que se esquece: bloquear só o invalidado deixaria
     * a saída pelo outro lado — apagar ANTES do julgamento, esvaziando a fila.
     */
    @Test
    fun `check-in sob moderacao nao pode ser apagado pelo dono`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "x")

        val emAnalise = c.checkInService.apagar(uid(autor), null, c.grupo.toString(), c.doAutor.toString())
        assertTrue(emAnalise is AppResult.Failure && emAnalise.error is AppError.Conflict)

        c.servico.julgar(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), acatar = true)
        val invalidado = c.checkInService.apagar(uid(autor), null, c.grupo.toString(), c.doAutor.toString())
        assertTrue(invalidado is AppResult.Failure && invalidado.error is AppError.Conflict)

        assertEquals(1, c.checkIns.guardados.count { it.id == c.doAutor }, "a linha continua lá")
    }

    /**
     * ⭐ O `canDelete` do FEED tem de concordar com a recusa do serviço.
     *
     * Este é o teste que faltava. A regra existia — o `apagar` recusava —, mas o `canDelete` do DTO
     * só olhava a data, e o menu do card oferecia "Apagar meu check-in" num check-in invalidado. O
     * servidor recusava depois do toque. Achado no passo 21 da bateria E.2.
     *
     * > **Duas fontes de verdade para a mesma pergunta divergem na primeira que alguém esquecer.**
     *
     * Afirma sobre o DTO, e não sobre a política: é o DTO que a tela lê, e era exatamente ali que a
     * divergência morava. Um teste só da `CheckInPolicy` teria passado com o defeito no lugar.
     */
    @Test
    fun `o canDelete do feed acompanha a moderacao`(): Unit = runBlocking {
        val c = cenario()
        fun meuCanDelete() = runBlocking {
            (c.checkInService.feed(uid(autor), null, c.grupo.toString(), null, null) as AppResult.Success)
                .value.single { it.id == c.doAutor.toString() }.canDelete
        }

        assertTrue(meuCanDelete(), "meu, de hoje, válido: dá para apagar")

        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "x")
        assertFalse(meuCanDelete(), "em análise: o botão some ANTES do toque")

        c.servico.julgar(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), acatar = true)
        assertFalse(meuCanDelete(), "invalidado: idem")
    }

    /**
     * E o caminho de volta: absolvido, o botão VOLTA.
     *
     * Sem isto, "some quando entra em análise" poderia ser implementado como um caminho só de ida —
     * e quem foi denunciado injustamente perderia para sempre o direito de refazer o check-in do
     * próprio dia.
     */
    @Test
    fun `manter devolve o canDelete ao dono`(): Unit = runBlocking {
        val c = cenario()
        c.servico.denunciarCheckIn(uid(membro), null, c.grupo.toString(), c.doAutor.toString(), "x")

        c.servico.julgar(uid(admin), null, c.grupo.toString(), c.doAutor.toString(), acatar = false)

        val meu = (c.checkInService.feed(uid(autor), null, c.grupo.toString(), null, null) as AppResult.Success)
            .value.single { it.id == c.doAutor.toString() }
        assertTrue(meu.canDelete, "absolvido é indistinguível de nunca acusado")
    }

    // ---- utilidades ----

    /** O ranking como mapa `usuário -> pontos`, para afirmar sobre pontuação sem depender de ordem. */
    private fun Cenario.ranking(): Map<Uuid, Int> = runBlocking {
        (checkInService.ranking(uid(admin), null, grupo.toString()) as AppResult.Success)
            .value
            .associate { Uuid.parse(it.userId) to it.checkIns }
    }

    private fun Cenario.comentar(de: Uuid, texto: String): Uuid = runBlocking {
        val id = Uuid.random()
        social.comentar(
            dev.rafael.server.features.checkin.models.NovoComentario(
                id = id,
                checkInId = doAutor,
                groupId = grupo,
                userId = de,
                body = texto,
                createdAt = nascimento,
            ),
        )
        id
    }
}
