package dev.rafael.app.push

import dev.rafael.app.navigation.AppRoute
import dev.rafael.app.screens.grupos.AbasDoGrupo
import kotlin.test.Test
import kotlin.test.assertEquals

class PilhaDoPushTest {

    private fun comentario(g: String? = "g1", c: String? = "c1") =
        DestinoDePush("COMENTARIO_NO_CHECKIN", groupId = g, checkInId = c)

    @Test
    fun `comentario com o app fora do grupo empilha o grupo e depois a conversa`() {
        assertEquals(
            listOf(AppRoute.GrupoDetalhe("g1", AbasDoGrupo.POSTS), AppRoute.Comentarios("g1", "c1")),
            pilhaDoPush(comentario(), grupoAberto = null),
        )
    }

    @Test
    fun `comentario com outro grupo aberto empilha o grupo do comentario`() {
        assertEquals(
            listOf(AppRoute.GrupoDetalhe("g1", AbasDoGrupo.POSTS), AppRoute.Comentarios("g1", "c1")),
            pilhaDoPush(comentario(), grupoAberto = "g2"),
        )
    }

    @Test
    fun `comentario com o mesmo grupo ja aberto empilha so a conversa`() {
        assertEquals(
            listOf(AppRoute.Comentarios("g1", "c1")),
            pilhaDoPush(comentario(), grupoAberto = "g1"),
        )
    }

    @Test
    fun `comentario sem groupId cai na central`() {
        assertEquals(listOf(AppRoute.Notificacoes), pilhaDoPush(comentario(g = null), null))
    }

    @Test
    fun `comentario sem checkInId cai na central mesmo com o grupo aberto`() {
        assertEquals(listOf(AppRoute.Notificacoes), pilhaDoPush(comentario(c = null), "g1"))
    }

    @Test
    fun `tipo desconhecido cai na central`() {
        assertEquals(listOf(AppRoute.Notificacoes), pilhaDoPush(DestinoDePush("TIPO_DO_FUTURO"), null))
    }

    @Test
    fun `tipos de grupo sem groupId caem na central`() {
        listOf("DENUNCIA_NO_GRUPO", "FILA_PARADA", "DENUNCIA_CONTRA_MIM", "CHECK_IN_INVALIDADO", "ENTRADAS_DO_DIA")
            .forEach { assertEquals(listOf(AppRoute.Notificacoes), pilhaDoPush(DestinoDePush(it), null), it) }
    }

    @Test
    fun `os demais tipos mantem a rota que tinham`() {
        fun d(t: String) = DestinoDePush(t, groupId = "g1", achievementId = "a1")
        assertEquals(listOf(AppRoute.Amigos), pilhaDoPush(d("PEDIDO_DE_AMIZADE"), null))
        assertEquals(listOf(AppRoute.Moderacao("g1")), pilhaDoPush(d("DENUNCIA_NO_GRUPO"), null))
        assertEquals(listOf(AppRoute.Moderacao("g1")), pilhaDoPush(d("FILA_PARADA"), null))
        assertEquals(listOf(AppRoute.GrupoDetalhe("g1", AbasDoGrupo.POSTS)), pilhaDoPush(d("DENUNCIA_CONTRA_MIM"), null))
        assertEquals(listOf(AppRoute.GrupoDetalhe("g1", AbasDoGrupo.POSTS)), pilhaDoPush(d("CHECK_IN_INVALIDADO"), null))
        assertEquals(listOf(AppRoute.GrupoDetalhe("g1", AbasDoGrupo.MEMBROS)), pilhaDoPush(d("ENTRADAS_DO_DIA"), null))
        assertEquals(listOf(AppRoute.Conquistas("a1")), pilhaDoPush(d("CONQUISTA_DESBLOQUEADA"), null))
    }
}
