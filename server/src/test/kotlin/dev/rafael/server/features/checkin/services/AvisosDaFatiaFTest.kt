package dev.rafael.server.features.checkin.services

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.checkin.models.NovoCheckIn
import dev.rafael.server.features.group.services.FakeGroupRepository
import dev.rafael.server.features.group.services.FakeUserRepository
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Os quatro gatilhos de notificação que nascem de uma AÇÃO (fatia F, decisão 10.4).
 *
 * ## Por que num arquivo próprio
 *
 * Os testes de `SocialService` e `ModeracaoService` afirmam sobre o **efeito no banco**: o
 * comentário gravado, o estado do check-in, a fila. Estes afirmam sobre **quem foi avisado** — outra
 * pergunta, com outro dublê, e que quebra por outras razões. Misturá-los faria cada arquivo ter
 * dois assuntos, e o segundo sempre acaba menos cuidado.
 *
 * ## O que os dublês registram
 *
 * As portas estreitas viram listas. É o único jeito de afirmar sobre push sem rede — e é
 * exatamente o que o `Notificador` como interface (F.1) foi desenhado para permitir.
 */
class AvisosDaFatiaFTest {

    private val admin = Uuid.random()
    private val dono = Uuid.random()
    private val outro = Uuid.random()

    /** Cada aviso enviado, com destinatário. O teste afirma sobre a LISTA, não sobre um booleano. */
    private class Registro {
        val comentarios = mutableListOf<Pair<Uuid, String>>()
        val denunciasContraMim = mutableListOf<Uuid>()
        val denunciasNoGrupo = mutableListOf<Uuid>()
        val invalidacoes = mutableListOf<Uuid>()
    }

    private inner class Cenario {
        val grupos = FakeGroupRepository()
        val checkIns = FakeCheckInRepository()
        val social = FakeSocialRepository()
        val moderacao = FakeModeracaoRepository()
        val registro = Registro()

        lateinit var grupo: Uuid
        lateinit var doDono: Uuid
        lateinit var socialService: SocialService
        lateinit var moderacaoService: ModeracaoService

        /** Exposto para o teste do push quebrado montar OUTRO serviço com os mesmos usuários. */
        lateinit var userService: UserService

        fun montar(): Cenario {
            grupo = grupos.semear(admin = admin, outros = listOf(dono, outro))
            doDono = semear(dono)

            val users = FakeUserRepository(listOf(admin, dono, outro).map(::usuario))
            userService = UserService(users)

            socialService = SocialService(
                userService, grupos, checkIns, social,
                avisarComentario = { paraQuem, _, texto, _, _ ->
                    registro.comentarios += paraQuem to texto
                },
            )
            moderacaoService = ModeracaoService(
                userService, grupos, checkIns, social, moderacao,
                avisos = ModeracaoService.AvisosDeModeracao(
                    denunciaContraMim = { d, _, _ -> registro.denunciasContraMim += d },
                    novaDenunciaNoGrupo = { a, _, _ -> registro.denunciasNoGrupo += a },
                    checkInInvalidado = { d, _, _ -> registro.invalidacoes += d },
                ),
            )
            return this
        }

        fun semear(deQuem: Uuid): Uuid {
            val id = Uuid.random()
            checkIns.semear(
                NovoCheckIn(
                    id = id,
                    groupId = grupo,
                    userId = deQuem,
                    localDate = LocalDate(2026, 12, 10),
                    createdAt = LocalDateTime(2026, 12, 10, 12, 0),
                    photoRef = null, placeName = null, placeLat = null, placeLng = null, emoji = null,
                ),
            )
            return id
        }

        private fun usuario(id: Uuid) = User(
            id = id,
            firebaseUid = "fb-$id",
            email = null,
            isPremium = false,
            displayName = "Atleta $id",
            code = id.toString().filter { it.isLetterOrDigit() }.take(8).uppercase(),
        )
    }

    private fun cenario() = Cenario().montar()
    private fun uid(u: Uuid) = "fb-$u"

    // ---- comentário no meu check-in ----

    @Test
    fun `comentar avisa o dono do check-in`(): Unit = runBlocking {
        val c = cenario()

        c.socialService.comentar(uid(outro), null, c.grupo.toString(), c.doDono.toString(), "boa!")

        assertEquals(listOf(dono to "boa!"), c.registro.comentarios)
    }

    /**
     * ⭐ **Não aviso a mim mesmo.**
     *
     * Comentar no próprio check-in é o caso comum — a pessoa responde a quem comentou nela. Um push
     * dizendo "você comentou no seu check-in" é ruído puro, e ruído ensina a ignorar a notificação
     * inteira.
     */
    @Test
    fun `o dono comentando no proprio check-in nao gera aviso`(): Unit = runBlocking {
        val c = cenario()

        c.socialService.comentar(uid(dono), null, c.grupo.toString(), c.doDono.toString(), "obrigado")

        assertTrue(c.registro.comentarios.isEmpty())
    }

    /** Comentário recusado não avisa: o aviso sai de dentro do `map`, depois de gravar. */
    @Test
    fun `comentario vazio nao avisa ninguem`(): Unit = runBlocking {
        val c = cenario()

        c.socialService.comentar(uid(outro), null, c.grupo.toString(), c.doDono.toString(), "   ")

        assertTrue(c.registro.comentarios.isEmpty())
    }

    // ---- denúncia ----

    @Test
    fun `denunciar avisa o dono e o admin`(): Unit = runBlocking {
        val c = cenario()

        c.moderacaoService.denunciarCheckIn(uid(outro), null, c.grupo.toString(), c.doDono.toString(), "falso")

        assertEquals(listOf(dono), c.registro.denunciasContraMim)
        assertEquals(listOf(admin), c.registro.denunciasNoGrupo)
    }

    /**
     * ⭐ 6.11 no push: a SEGUNDA denúncia avisa o dono, mas **não o admin**.
     *
     * O admin já sabe que há algo ali — a fila mostra um item só, e o badge conta casos. Cinco
     * pessoas denunciando o mesmo check-in gerariam cinco pushes para um item de fila.
     *
     * O dono continua sendo avisado porque, para ele, é um fato novo: outra pessoa reclamou.
     */
    @Test
    fun `segunda denuncia no mesmo alvo nao avisa o admin de novo`(): Unit = runBlocking {
        val c = cenario()
        c.moderacaoService.denunciarCheckIn(uid(outro), null, c.grupo.toString(), c.doDono.toString(), "a")

        c.moderacaoService.denunciarCheckIn(uid(admin), null, c.grupo.toString(), c.doDono.toString(), "b")

        assertEquals(2, c.registro.denunciasContraMim.size, "o dono soube das duas")
        assertEquals(1, c.registro.denunciasNoGrupo.size, "o admin, de uma — é um caso só (6.11)")
    }

    @Test
    fun `denuncia recusada por prazo nao avisa ninguem`(): Unit = runBlocking {
        val c = cenario()

        // Denunciar o PRÓPRIO check-in é recusado antes de qualquer gravação.
        c.moderacaoService.denunciarCheckIn(uid(dono), null, c.grupo.toString(), c.doDono.toString(), "x")

        assertTrue(c.registro.denunciasContraMim.isEmpty())
        assertTrue(c.registro.denunciasNoGrupo.isEmpty())
    }

    // ---- invalidação ----

    @Test
    fun `acatar a denuncia avisa o dono do check-in`(): Unit = runBlocking {
        val c = cenario()
        c.moderacaoService.denunciarCheckIn(uid(outro), null, c.grupo.toString(), c.doDono.toString(), "x")

        c.moderacaoService.julgar(uid(admin), null, c.grupo.toString(), c.doDono.toString(), acatar = true)

        assertEquals(listOf(dono), c.registro.invalidacoes)
    }

    /**
     * ⭐ **Manter não avisa ninguém.**
     *
     * O denunciado já foi avisado quando a denúncia chegou. Um segundo push dizendo "não deu em
     * nada" é pior que um: ensina que metade dos avisos não pede ação nenhuma.
     */
    @Test
    fun `manter nao avisa ninguem`(): Unit = runBlocking {
        val c = cenario()
        c.moderacaoService.denunciarCheckIn(uid(outro), null, c.grupo.toString(), c.doDono.toString(), "x")

        c.moderacaoService.julgar(uid(admin), null, c.grupo.toString(), c.doDono.toString(), acatar = false)

        assertTrue(c.registro.invalidacoes.isEmpty())
    }

    /**
     * ⭐ 6.10 avisa o MESMO que o julgamento acatado.
     *
     * Os dois caminhos levam ao mesmo lugar para quem recebe: o check-in dele parou de contar.
     * **Quem perde o ponto não precisa saber por qual rota o admin passou** — e se um dos caminhos
     * não avisasse, a pessoa descobriria pelo ranking, sem entender por quê.
     */
    @Test
    fun `invalidar direto avisa igual ao julgamento`(): Unit = runBlocking {
        val c = cenario()

        c.moderacaoService.invalidarDireto(uid(admin), null, c.grupo.toString(), c.doDono.toString())

        assertEquals(listOf(dono), c.registro.invalidacoes)
    }

    /**
     * Invalidar o que já está invalidado não avisa de novo.
     *
     * O serviço sai cedo (é idempotente), e o aviso está depois da guarda. Sem isso, um admin
     * tocando duas vezes mandaria dois pushes sobre um fato que aconteceu uma vez.
     */
    @Test
    fun `invalidar duas vezes avisa uma vez`(): Unit = runBlocking {
        val c = cenario()
        c.moderacaoService.invalidarDireto(uid(admin), null, c.grupo.toString(), c.doDono.toString())

        c.moderacaoService.invalidarDireto(uid(admin), null, c.grupo.toString(), c.doDono.toString())

        assertEquals(1, c.registro.invalidacoes.size)
    }

    /**
     * ⭐ A PORTA NÃO PROTEGE NINGUÉM — quem protege é o `NotificacaoService`.
     *
     * Este teste afirma um comportamento **negativo**, e de propósito: se o push estourar, a
     * exceção atravessa e derruba o comentário que já foi gravado. Não é defeito — é onde mora a
     * rede de proteção. O wiring real passa pelo `NotificacaoService`, que engole a falha (F.1,
     * "nunca falha para o chamador").
     *
     * Sem esta afirmação escrita, quem ligar uma porta nova direto no `Notificador` acharia que
     * está seguro porque "as outras estão". O teste existe para dizer onde a segurança está — não
     * na assinatura `Unit`.
     *
     * **Primeira versão deste teste passava por engano:** usava `FakeUserRepository(emptyList())`,
     * que cria um usuário fora do grupo — a guarda de filiação recusava com 404 antes de chegar ao
     * aviso. Ele afirmava sobre um caminho que nunca era percorrido.
     *
     * > **Teste de comportamento negativo precisa provar que chegou lá.** O `comentarios` vazio no
     * > fim é o que garante que a exceção veio do push, e não de uma recusa anterior.
     */
    @Test
    fun `push que estoura derruba a acao — por isso o wiring passa pelo NotificacaoService`(): Unit = runBlocking {
        val c = cenario()
        val comPushQuebrado = SocialService(
            c.userService, c.grupos, c.checkIns, c.social,
            avisarComentario = { _, _, _, _, _ -> error("FCM fora do ar") },
        )

        val r = runCatching {
            comPushQuebrado.comentar(uid(outro), null, c.grupo.toString(), c.doDono.toString(), "oi")
        }

        assertTrue(r.isFailure, "a porta crua não tem proteção própria")
        assertTrue(
            c.social.comentarios.values.any { it.body == "oi" },
            "e o comentário JÁ estava gravado — a exceção veio do aviso, não de uma recusa antes dele",
        )
    }
}
