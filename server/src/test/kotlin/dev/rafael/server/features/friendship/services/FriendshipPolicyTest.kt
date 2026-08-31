package dev.rafael.server.features.friendship.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * As regras puras de amizade (ARCH #35).
 *
 * O teste que carrega o peso é o do **par canônico**: ele é o que faz a chave primária impedir
 * pedido cruzado, e se a ordenação deixar de ser estável a tabela ganha pares duplicados que a PK
 * deveria ter impedido — e o defeito aparece como "somos amigos numa tela e não em outra".
 */
class FriendshipPolicyTest {

    private val a = Uuid.parse("00000000-0000-0000-0000-00000000000a")
    private val b = Uuid.parse("00000000-0000-0000-0000-00000000000b")

    @Test
    fun `o par e o MESMO nas duas direcoes`() {
        assertEquals(
            FriendshipPolicy.par(a, b),
            FriendshipPolicy.par(b, a),
            "A→B e B→A precisam produzir a mesma chave, senão a PK não impede pedido cruzado",
        )
    }

    @Test
    fun `o par poe o MENOR uuid primeiro`() {
        val (primeiro, segundo) = FriendshipPolicy.par(b, a)
        assertEquals(a, primeiro)
        assertEquals(b, segundo)
        assertTrue(
            primeiro.toString() < segundo.toString(),
            "a ordem é a mesma do CHECK friendships_ordem_canonica no banco",
        )
    }

    @Test
    fun `nao da para adicionar a si mesmo`() {
        val i = FriendshipPolicy.impedimentoParaPedir(a, a, null, euPedi = false, haBloqueio = false, amizadesDeQuemPede = 0)
        assertEquals(FriendshipPolicy.Impedimento.A_SI_MESMO, i)
    }

    /**
     * BLOQUEIO vence tudo — e é verificado ANTES de "já são amigos".
     *
     * A ordem importa para não vazar: se o impedimento mudasse conforme a relação existente, quem
     * bloqueou poderia deduzir pelo texto do erro em que estado a relação estava.
     */
    @Test
    fun `bloqueio vence qualquer outro impedimento`() {
        val comAmizade = FriendshipPolicy.impedimentoParaPedir(
            a, b, FriendshipPolicy.Estado.ACEITA, euPedi = false, haBloqueio = true, amizadesDeQuemPede = 0,
        )
        val noTeto = FriendshipPolicy.impedimentoParaPedir(
            a, b, null, euPedi = false, haBloqueio = true, amizadesDeQuemPede = 999,
        )
        assertEquals(FriendshipPolicy.Impedimento.BLOQUEIO, comAmizade)
        assertEquals(FriendshipPolicy.Impedimento.BLOQUEIO, noTeto, "a mesma resposta nos dois casos")
    }

    @Test
    fun `recusado pode pedir de novo`() {
        val i = FriendshipPolicy.impedimentoParaPedir(
            a, b, FriendshipPolicy.Estado.RECUSADA, euPedi = false, haBloqueio = false, amizadesDeQuemPede = 0,
        )
        assertNull(i, "recusar NÃO é banir — para banir existe o bloqueio, que é explícito")
    }

    /**
     * PEDIDO CRUZADO não é impedimento (decisão de 2026-08-27).
     *
     * Quem já foi pedido e pede de volta não recebe erro: o serviço transforma isso em ACEITE,
     * porque os dois manifestaram a mesma intenção. `euPedi` é o que separa os dois casos.
     */
    @Test
    fun `pendente so impede quando fui EU que pedi`() {
        val euPedi = FriendshipPolicy.impedimentoParaPedir(
            a, b, FriendshipPolicy.Estado.PENDENTE, euPedi = true, haBloqueio = false, amizadesDeQuemPede = 0,
        )
        val elePediu = FriendshipPolicy.impedimentoParaPedir(
            a, b, FriendshipPolicy.Estado.PENDENTE, euPedi = false, haBloqueio = false, amizadesDeQuemPede = 0,
        )
        assertEquals(FriendshipPolicy.Impedimento.JA_EXISTE_PEDIDO, euPedi)
        assertNull(elePediu, "pedido do outro lado vira aceite no serviço, não erro aqui")
    }

    @Test
    fun `teto de 500 barra quem pede`() {
        val no499 = FriendshipPolicy.impedimentoParaPedir(a, b, null, false, false, 499)
        val no500 = FriendshipPolicy.impedimentoParaPedir(a, b, null, false, false, 500)
        assertNull(no499)
        assertEquals(FriendshipPolicy.Impedimento.TETO_ATINGIDO, no500)
    }

    /**
     * A falha mais óbvia de um fluxo com aceite, e a mais fácil de deixar passar: as duas pessoas
     * estão na MESMA linha da tabela, então nada no schema impede quem pediu de responder.
     */
    @Test
    fun `quem MANDOU o pedido nao pode aceita-lo`() {
        assertFalse(
            FriendshipPolicy.podeResponder(quemAge = a, requestedBy = a, FriendshipPolicy.Estado.PENDENTE),
        )
        assertTrue(
            FriendshipPolicy.podeResponder(quemAge = b, requestedBy = a, FriendshipPolicy.Estado.PENDENTE),
        )
    }

    @Test
    fun `so quem mandou cancela, e so o outro recusa`() {
        assertTrue(FriendshipPolicy.podeCancelar(a, requestedBy = a, FriendshipPolicy.Estado.PENDENTE))
        assertFalse(FriendshipPolicy.podeCancelar(b, requestedBy = a, FriendshipPolicy.Estado.PENDENTE))
    }

    @Test
    fun `nao se responde nem se cancela relacao ja decidida`() {
        listOf(FriendshipPolicy.Estado.ACEITA, FriendshipPolicy.Estado.RECUSADA).forEach { estado ->
            assertFalse(FriendshipPolicy.podeResponder(b, a, estado), "responder $estado")
            assertFalse(FriendshipPolicy.podeCancelar(a, a, estado), "cancelar $estado")
        }
    }

    @Test
    fun `desfazer so vale para amizade aceita`() {
        assertTrue(FriendshipPolicy.podeDesfazer(FriendshipPolicy.Estado.ACEITA))
        assertFalse(FriendshipPolicy.podeDesfazer(FriendshipPolicy.Estado.PENDENTE))
    }

    /**
     * [INVARIANTE] O `.name` do enum **nunca** chega ao usuário.
     *
     * Isto já aconteceu: o `JoinBlock` da fatia A.2 mostrou "TETO_ATINGIDO" na tela. O teste
     * verifica a forma da frase, não o texto exato — texto muda, "não parece constante de código"
     * é o que precisa continuar valendo.
     */
    @Test
    fun `toda frase de impedimento e legivel, nunca o nome do enum`() {
        FriendshipPolicy.Impedimento.entries.forEach { i ->
            val frase = i.frase()
            assertFalse(frase.contains("_"), "`$frase` parece nome de enum")
            assertTrue(frase.first().isUpperCase(), "`$frase` deveria começar com maiúscula")
            assertTrue(frase.endsWith("."), "`$frase` deveria terminar com ponto")
        }
    }

    /**
     * A frase do BLOQUEIO é vaga DE PROPÓSITO (decisão de 2026-08-27).
     *
     * Dizer "esta pessoa bloqueou você" transformaria o bloqueio num recado, e quem bloqueia quer
     * sumir, não avisar. Quem esqueceu que bloqueou alguém descobre em Configurações →
     * Bloqueados, que é onde a informação pertence.
     */
    @Test
    fun `a frase do bloqueio nao revela que houve bloqueio`() {
        val frase = FriendshipPolicy.Impedimento.BLOQUEIO.frase().lowercase()
        listOf("bloque", "blocked", "banid").forEach {
            assertFalse(frase.contains(it), "a frase do bloqueio não pode conter `$it`: $frase")
        }
    }
}

/** O código de usuário (35.5) — regra de IDENTIDADE, mora em `user`. */
class UserCodePolicyTest {

    @Test
    fun `gera sempre 8 caracteres do alfabeto sem ambiguidade`() {
        repeat(200) {
            val c = dev.rafael.server.features.user.services.UserCodePolicy.gerar()
            assertEquals(8, c.length)
            assertTrue(
                c.all { it in dev.rafael.server.features.user.services.UserCodePolicy.ALFABETO },
                "`$c` saiu do alfabeto",
            )
        }
    }

    /**
     * O alfabeto NÃO pode ter os pares ambíguos — o código é ditado por voz e digitado à mão.
     *
     * Verificado por CARACTERE e não comparando com uma constante: comparar a constante com ela
     * mesma provaria apenas que ela não mudou, e não que ela está certa.
     */
    @Test
    fun `o alfabeto nao tem O zero I nem um`() {
        val alfabeto = dev.rafael.server.features.user.services.UserCodePolicy.ALFABETO
        listOf('O', '0', 'I', '1').forEach {
            assertFalse(it in alfabeto, "`$it` é ambíguo e não pode estar no alfabeto")
        }
        assertEquals(32, alfabeto.length, "32 símbolos: 8 posições dão ~1 trilhão de códigos")
        assertEquals(alfabeto.length, alfabeto.toSet().size, "sem repetição — enviesaria o sorteio")
    }

    /**
     * Gente digita com espaço e em minúscula. Recusar por isso seria recusar o usuário por causa
     * do teclado dele.
     */
    @Test
    fun `normalizar aceita minuscula e espaco`() {
        val p = dev.rafael.server.features.user.services.UserCodePolicy
        assertEquals("ABCD2345", p.normalizar("abcd2345"))
        assertEquals("ABCD2345", p.normalizar("  ABCD 2345 "))
    }

    @Test
    fun `normalizar recusa o que nao pode ser codigo`() {
        val p = dev.rafael.server.features.user.services.UserCodePolicy
        assertNull(p.normalizar("ABC"), "curto demais")
        assertNull(p.normalizar("ABCD23456"), "longo demais")
        assertNull(p.normalizar("ABCD234O"), "contém O, que não está no alfabeto")
        assertNull(p.normalizar("ABCD-234"), "contém caractere fora do alfabeto")
        assertNotNull(p.normalizar("ABCD2345"))
    }
}
