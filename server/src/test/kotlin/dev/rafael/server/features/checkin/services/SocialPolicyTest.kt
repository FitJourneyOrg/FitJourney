package dev.rafael.server.features.checkin.services

import dev.rafael.contract.group.MemberRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Comentário e reação (fatia E.1) — as regras que decidem, sem banco. */
class SocialPolicyTest {

    // ---- comentário ----

    @Test
    fun `texto normal passa e volta aparado`() {
        assertEquals("Boa!", SocialPolicy.comentarioValido("  Boa!  "))
    }

    /**
     * O espaço some ANTES de medir.
     *
     * 500 caracteres mais um espaço não é um comentário grande demais — é o mesmo comentário. Se a
     * medição viesse primeiro, a pessoa perderia o texto por causa de um toque acidental no fim.
     */
    @Test
    fun `500 caracteres com espaco em volta continua valido`() {
        val limite = "a".repeat(SocialPolicy.MAX_COMENTARIO)

        assertEquals(limite, SocialPolicy.comentarioValido("  $limite  "))
    }

    @Test
    fun `501 caracteres nao passa`() {
        assertNull(SocialPolicy.comentarioValido("a".repeat(SocialPolicy.MAX_COMENTARIO + 1)))
    }

    @Test
    fun `so espacos vira nulo`() {
        // Comentário vazio é ruído no feed de 49 pessoas: se não há o que dizer, não há o que
        // publicar. E o CHECK do banco recusaria de qualquer forma — isto evita o 500.
        assertNull(SocialPolicy.comentarioValido("     "))
        assertNull(SocialPolicy.comentarioValido(""))
        assertNull(SocialPolicy.comentarioValido("\n\t "))
    }

    /**
     * Devolve o TEXTO, não um booleano.
     *
     * É o que impede o serviço de validar uma coisa e gravar outra — a lição do `display_name`,
     * onde o servidor normalizava e a tela exibia o que a pessoa tinha digitado, com dois espaços.
     */
    @Test
    fun `o valor devolvido e o que deve ser gravado`() {
        val tratado = SocialPolicy.comentarioValido("  boa   demais  ")

        assertEquals("boa   demais", tratado, "só as pontas são aparadas — o miolo é do autor")
    }

    // ---- reação ----

    @Test
    fun `as seis reacoes sao aceitas`() {
        SocialPolicy.REACOES.forEach {
            assertTrue(SocialPolicy.reacaoValida(it), "'$it' deveria ser aceita")
        }
    }

    @Test
    fun `emoji fora do conjunto e recusado`() {
        // A 8.2 dizia "qualquer emoji" e foi emendada: com 50 membros escolhendo livremente, o
        // agrupamento perde sentido e "12 👍" nunca acontece.
        assertFalse(SocialPolicy.reacaoValida("🏦"))
        assertFalse(SocialPolicy.reacaoValida("🫡"))
        assertFalse(SocialPolicy.reacaoValida(""))
        assertFalse(SocialPolicy.reacaoValida("👍👍"))
    }

    @Test
    fun `nenhuma reacao e recente demais para o Android que suportamos`() {
        // Mesmo guarda da lista de emojis do dia (V43): Unicode 14/15 vira caixa vazia antes do
        // Android 14, e uma reação invisível é um botão que não diz nada.
        val recentes = 0x1FAE0..0x1FAFF

        SocialPolicy.REACOES.forEach { emoji ->
            val muitoNovo = emoji.codePoints().anyMatch { it in recentes }
            assertFalse(muitoNovo, "'$emoji' é Unicode 14+ e vira caixa vazia antes do Android 14")
        }
    }

    @Test
    fun `a lista de reacoes nao tem repetidos`() {
        assertEquals(SocialPolicy.REACOES.size, SocialPolicy.REACOES.toSet().size)
    }

    // ---- quem apaga comentário ----

    @Test
    fun `o autor apaga o proprio comentario`() {
        assertTrue(
            SocialPolicy.podeApagarComentario(
                souOAutor = true,
                souDonoDoCheckIn = false,
                meuPapel = MemberRole.MEMBRO,
            ),
        )
    }

    @Test
    fun `o admin apaga o comentario de qualquer um`() {
        // É a autoridade única do grupo (6.7), e a 6.4 diz que comentário pode ser removido.
        assertTrue(
            SocialPolicy.podeApagarComentario(
                souOAutor = false,
                souDonoDoCheckIn = false,
                meuPapel = MemberRole.ADMIN,
            ),
        )
    }

    /**
     * O dono do check-in apaga comentário alheio na PRÓPRIA foto (emenda de 2026-09-07).
     *
     * Repare no papel: `MEMBRO`. Sem esta regra, quem publicou dependeria de o admin acordar para
     * tirar um comentário do próprio conteúdo — e o admin pode ser a pessoa que comentou.
     */
    @Test
    fun `o dono do check-in apaga comentario alheio no proprio check-in`() {
        assertTrue(
            SocialPolicy.podeApagarComentario(
                souOAutor = false,
                souDonoDoCheckIn = true,
                meuPapel = MemberRole.MEMBRO,
            ),
        )
    }

    @Test
    fun `membro comum nao apaga comentario alheio`() {
        assertFalse(
            SocialPolicy.podeApagarComentario(
                souOAutor = false,
                souDonoDoCheckIn = false,
                meuPapel = MemberRole.MEMBRO,
            ),
        )
    }

    @Test
    fun `quem nao e membro e nao e autor nao apaga nada`() {
        assertFalse(
            SocialPolicy.podeApagarComentario(
                souOAutor = false,
                souDonoDoCheckIn = false,
                meuPapel = null,
            ),
        )
    }

    /**
     * A FILIAÇÃO não é decidida aqui, e isso é deliberado.
     *
     * `souOAutor = true` com papel nulo devolve `true` — e está certo: esta função responde
     * "**este papel** permite apagar?", não "esta pessoa pode acessar o grupo?". Quem barra o
     * ex-membro é a guarda de filiação do serviço, antes de chegar aqui, como já acontece em todo
     * o resto da fatia B.
     *
     * Misturar as duas perguntas numa função só faria a política precisar saber de sessão e
     * pertencimento — e ela deixaria de ser pura.
     */
    @Test
    fun `a politica responde pelo PAPEL, nao pela filiacao`() {
        assertTrue(
            SocialPolicy.podeApagarComentario(
                souOAutor = true,
                souDonoDoCheckIn = false,
                meuPapel = null,
            ),
        )
    }

    /**
     * Não há prazo, ao contrário do check-in (4.11).
     *
     * Lá o prazo existe porque apagar libera o slot do dia e mexeria no ranking. Comentário não
     * vale ponto, então apagar não move nada — e comentário ofensivo antigo precisa poder sair.
     *
     * O "teste" é a **assinatura**: não há parâmetro de tempo. Se um dia alguém acrescentar um
     * prazo, este arquivo para de compilar e a decisão volta à mesa.
     */
    @Test
    fun `apagar comentario nao depende de quando ele foi escrito`() {
        assertTrue(
            SocialPolicy.podeApagarComentario(
                souOAutor = true,
                souDonoDoCheckIn = false,
                meuPapel = MemberRole.MEMBRO,
            ),
        )
    }
}
