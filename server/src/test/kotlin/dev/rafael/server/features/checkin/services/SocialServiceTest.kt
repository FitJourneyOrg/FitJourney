package dev.rafael.server.features.checkin.services

import dev.rafael.core.result.AppError
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Comentários e reações (fatia E.1) — o serviço e suas DUAS guardas.
 *
 * ## O que este arquivo prova, e o `SocialPolicyTest` não
 *
 * A política responde "este papel permite?". Aqui se testa o que só o serviço sabe: **quem** está
 * pedindo, **de qual grupo**, e se o alvo pertence a ele. São as duas perguntas que, juntas,
 * impedem alguém de comentar num check-in de um grupo do qual não faz parte.
 *
 * ## Todos os dublês são os que já existem
 *
 * `FakeGroupRepository`, `FakeCheckInRepository`, `FakeUserRepository` e `FakeSocialRepository` —
 * nenhum criado aqui. Duplicá-los criaria duas versões da mesma mentira, que é como um teste
 * começa a passar por engano.
 *
 * O `FakeSocialRepository` guarda as reações num mapa chaveado por `(checkInId, userId)` — a mesma
 * chave que a PK da V44. Um fake que acumulasse numa lista deixaria a segunda reação da mesma
 * pessoa passar aqui e falhar só em produção. É a lição que a fatia B cobrou com o `photoRef`
 * constante.
 */
class SocialServiceTest {

    private val autor = Uuid.random()
    private val admin = Uuid.random()
    private val membro = Uuid.random()
    private val estranho = Uuid.random()

    /** Grupo com os três dentro, um check-in dele, e um check-in de OUTRO grupo. */
    private class Cenario {
        val grupos = FakeGroupRepository()
        val checkIns = FakeCheckInRepository()

        /**
         * O mesmo dublê que os testes de check-in usam — **as reações chaveadas por
         * `(checkInId, userId)`**, como a PK da V44. Um fake que acumulasse numa lista deixaria a
         * segunda reação da mesma pessoa passar aqui e falhar só em produção.
         *
         * Compartilhado de propósito: duas versões do mesmo dublê divergem, e a que mente é a que
         * faz o teste passar.
         */
        val social = FakeSocialRepository()

        lateinit var grupo: Uuid
        lateinit var outroGrupo: Uuid
        lateinit var checkInDoGrupo: Uuid
        lateinit var checkInDeOutroGrupo: Uuid
        lateinit var servico: SocialService

        fun montar(admin: Uuid, membros: List<Uuid>): Cenario {
            grupo = grupos.semear(admin = admin, outros = membros)
            outroGrupo = grupos.semear(admin = Uuid.random())

            checkInDoGrupo = semearCheckIn(grupo, admin)
            checkInDeOutroGrupo = semearCheckIn(outroGrupo, Uuid.random())

            val users = FakeUserRepository((membros + admin).map(::usuario))
            servico = SocialService(UserService(users), grupos, checkIns, social)
            return this
        }

        /**
         * Mais um check-in NESTE grupo, com o dono que o teste escolher.
         *
         * Existe por causa da emenda de 2026-09-07: o dono do check-in também apaga comentário. Com
         * um único check-in — o do admin — o teste "o admin apaga o alheio" passaria por DOIS
         * motivos ao mesmo tempo, e continuaria verde se a regra do admin sumisse.
         */
        fun checkInDe(dono: Uuid): Uuid = semearCheckIn(grupo, dono)

        private fun semearCheckIn(g: Uuid, dono: Uuid): Uuid {
            val id = Uuid.random()
            checkIns.semear(
                NovoCheckIn(
                    id = id,
                    groupId = g,
                    userId = dono,
                    localDate = LocalDate(2026, 12, 10),
                    createdAt = LocalDateTime(2026, 12, 10, 12, 0),
                    photoRef = null,
                    placeName = null,
                    placeLat = null,
                    placeLng = null,
                    emoji = null,
                ),
            )
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

    private fun cenario() = Cenario().montar(admin = admin, membros = listOf(autor, membro))

    private fun uid(u: Uuid) = "fb-$u"

    // ---- comentar ----

    @Test
    fun `membro comenta e o texto volta aparado`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.comentar(
            uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "  Boa!  ",
        )

        assertTrue(r is AppResult.Success)
        assertEquals("Boa!", c.social.comentarios.values.single().body)
    }

    @Test
    fun `comentario vazio e recusado antes de chegar ao banco`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.comentar(
            uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "   ",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
        assertTrue(c.social.comentarios.isEmpty(), "nada pode ter sido gravado")
    }

    /**
     * ⭐ A SEGUNDA GUARDA.
     *
     * Sou membro do grupo A e conheço um id de check-in do grupo B. A guarda de filiação passa —
     * eu **sou** membro do grupo que declarei — e é a conferência do check-in que barra.
     *
     * Sem ela, bastaria descobrir um `Uuid` para comentar em qualquer check-in do app. Ids não se
     * adivinham, mas **segurança que depende de o atacante não descobrir um identificador é
     * segurança emprestada**.
     */
    @Test
    fun `nao comento em check-in de OUTRO grupo`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.comentar(
            uid(membro), null, c.grupo.toString(), c.checkInDeOutroGrupo.toString(), "oi",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound, "404, nunca 403")
        assertTrue(c.social.comentarios.isEmpty())
    }

    @Test
    fun `quem nao e membro leva 404, nao 403`(): Unit = runBlocking {
        // 403 confirmaria que o grupo e o check-in existem. Mesma escolha do perfil público (C.1).
        val c = cenario()

        val r = c.servico.comentar(
            uid(estranho), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "oi",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
    }

    // ---- apagar comentário ----

    @Test
    fun `o autor apaga o proprio comentario`(): Unit = runBlocking {
        val c = cenario()
        c.servico.comentar(uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "erro")
        val id = c.social.comentarios.keys.single()

        val r = c.servico.apagarComentario(uid(membro), null, c.grupo.toString(), id.toString())

        assertTrue(r is AppResult.Success)
        assertTrue(c.social.comentarios.isEmpty())
    }

    @Test
    fun `o admin apaga comentario alheio`(): Unit = runBlocking {
        val c = cenario()
        // Num check-in que NÃO é dele: senão o teste passaria também pela regra do dono, e
        // continuaria verde se a autoridade do admin (6.7) fosse removida por engano.
        val foto = c.checkInDe(membro)
        c.servico.comentar(uid(autor), null, c.grupo.toString(), foto.toString(), "ofensa")
        val id = c.social.comentarios.keys.single()

        val r = c.servico.apagarComentario(uid(admin), null, c.grupo.toString(), id.toString())

        assertTrue(r is AppResult.Success, "o admin é a autoridade única do grupo (6.7)")
        assertTrue(c.social.comentarios.isEmpty())
    }

    /**
     * ⭐ A EMENDA DE 2026-09-07: quem publicou apaga o que penduraram na foto dele.
     *
     * Repare no papel de quem apaga: **MEMBRO comum**. Sem esta regra, tirar um comentário do
     * próprio conteúdo dependeria de o admin acordar — e o admin pode ser justamente quem comentou.
     *
     * O serviço precisa de uma ida a mais ao banco para saber o dono, e é por isso que o
     * `comCheckIn` passou a devolver `(id, dono)`: a busca já tinha acontecido na guarda.
     */
    @Test
    fun `o dono do check-in apaga comentario alheio na propria foto`(): Unit = runBlocking {
        val c = cenario()
        val minhaFoto = c.checkInDe(membro)
        c.servico.comentar(uid(autor), null, c.grupo.toString(), minhaFoto.toString(), "ofensa")
        val id = c.social.comentarios.keys.single()

        val r = c.servico.apagarComentario(uid(membro), null, c.grupo.toString(), id.toString())

        assertTrue(r is AppResult.Success, "é a foto dele — quem publica decide o que fica pendurado")
        assertTrue(c.social.comentarios.isEmpty())
    }

    /**
     * Ser dono de UM check-in não dá poder sobre os comentários dos OUTROS.
     *
     * O par com o teste acima: a regra é "dono **deste** check-in", não "quem já publicou alguma
     * vez". Se o serviço comparasse o dono errado — por exemplo o do check-in que veio na rota em
     * vez do que o comentário aponta — só este teste pegaria.
     */
    @Test
    fun `dono de um check-in nao apaga comentario de OUTRO check-in`(): Unit = runBlocking {
        val c = cenario()
        c.checkInDe(membro)   // ele é dono de um check-in, mas não deste
        c.servico.comentar(uid(autor), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "meu")
        val id = c.social.comentarios.keys.single()

        val r = c.servico.apagarComentario(uid(membro), null, c.grupo.toString(), id.toString())

        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
        assertEquals(1, c.social.comentarios.size)
    }

    @Test
    fun `membro comum NAO apaga comentario alheio`(): Unit = runBlocking {
        val c = cenario()
        c.servico.comentar(uid(autor), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "meu texto")
        val id = c.social.comentarios.keys.single()

        val r = c.servico.apagarComentario(uid(membro), null, c.grupo.toString(), id.toString())

        assertTrue(r is AppResult.Failure && r.error is AppError.Forbidden)
        assertEquals(1, c.social.comentarios.size, "o comentário continua lá")
    }

    // ---- reagir ----

    /**
     * [INVARIANTE] Uma reação por pessoa (8.2) — reagir de novo TROCA.
     *
     * O fake guarda por `(checkInId, userId)`, como a PK. Se ele acumulasse numa lista, este teste
     * passaria com duas linhas e o defeito só apareceria no banco real.
     */
    @Test
    fun `reagir duas vezes TROCA, nao acumula`(): Unit = runBlocking {
        val c = cenario()

        c.servico.reagir(uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "👍")
        c.servico.reagir(uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "🔥")

        assertEquals(1, c.social.reacoes.size, "uma reação por pessoa")
        assertEquals("🔥", c.social.reacoes.values.single())
    }

    @Test
    fun `duas pessoas reagem no mesmo check-in`(): Unit = runBlocking {
        val c = cenario()

        c.servico.reagir(uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "👍")
        c.servico.reagir(uid(admin), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "👍")

        assertEquals(2, c.social.reacoes.size, "a chave inclui o usuário — não é uma por check-in")
    }

    @Test
    fun `emoji fora do conjunto e recusado`(): Unit = runBlocking {
        // O banco aceitaria (vocabulário aberto, de propósito). A recusa mora no serviço.
        val c = cenario()

        val r = c.servico.reagir(
            uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "🏦",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.Validation)
        assertTrue(c.social.reacoes.isEmpty())
    }

    @Test
    fun `desreagir some com a minha e nao com a dos outros`(): Unit = runBlocking {
        val c = cenario()
        c.servico.reagir(uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "👍")
        c.servico.reagir(uid(admin), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "💪")

        c.servico.desreagir(uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString())

        assertEquals(1, c.social.reacoes.size)
        assertEquals("💪", c.social.reacoes.values.single())
    }

    @Test
    fun `desreagir sem ter reagido nao e erro`(): Unit = runBlocking {
        // Idempotente: o toque duplo no botão não pode virar erro na cara do usuário.
        val c = cenario()

        val r = c.servico.desreagir(
            uid(membro), null, c.grupo.toString(), c.checkInDoGrupo.toString(),
        )

        assertTrue(r is AppResult.Success)
    }

    @Test
    fun `nao reajo em check-in de outro grupo`(): Unit = runBlocking {
        val c = cenario()

        val r = c.servico.reagir(
            uid(membro), null, c.grupo.toString(), c.checkInDeOutroGrupo.toString(), "👍",
        )

        assertTrue(r is AppResult.Failure && r.error is AppError.NotFound)
        assertTrue(c.social.reacoes.isEmpty())
    }

    // ---- canDelete resolvido no servidor ----

    @Test
    fun `o canDelete vem resolvido para cada leitor`(): Unit = runBlocking {
        // A tela não compara ids nem consulta papel: ela desenha o que o servidor decidiu.
        val c = cenario()
        c.servico.comentar(uid(autor), null, c.grupo.toString(), c.checkInDoGrupo.toString(), "meu")

        fun visto(por: Uuid) = (
            runBlocking {
                c.servico.comentarios(uid(por), null, c.grupo.toString(), c.checkInDoGrupo.toString())
            } as AppResult.Success
            ).value.single().canDelete

        assertTrue(visto(autor), "o autor apaga o próprio")
        assertTrue(visto(admin), "o admin apaga o de qualquer um")
        assertFalse(visto(membro), "membro comum não apaga o alheio")
    }

    /** O mesmo `canDelete`, agora pela regra do dono: mudou o check-in, mudou a resposta. */
    @Test
    fun `o dono do check-in ve a lixeira no comentario dos outros`(): Unit = runBlocking {
        val c = cenario()
        val minhaFoto = c.checkInDe(membro)
        c.servico.comentar(uid(autor), null, c.grupo.toString(), minhaFoto.toString(), "alheio")

        val lista = c.servico.comentarios(
            uid(membro), null, c.grupo.toString(), minhaFoto.toString(),
        )

        assertTrue(lista is AppResult.Success)
        assertTrue(lista.value.single().canDelete, "é a foto dele, mesmo sendo membro comum")
    }
}
