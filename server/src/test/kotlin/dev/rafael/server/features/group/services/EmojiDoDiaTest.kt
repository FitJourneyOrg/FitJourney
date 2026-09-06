package dev.rafael.server.features.group.services

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * O emoji do dia (fatia D).
 *
 * ## O que estes testes protegem
 *
 * A regra tem duas metades que se contradizem se uma for implementada sem a outra: o emoji precisa
 * ser **imprevisível** e **estável**. Um `Random.nextInt()` daria a primeira e destruiria a
 * segunda — e o defeito só apareceria com duas pessoas olhando a mesma tela ao mesmo tempo, que é
 * exatamente o que nenhum teste manual de uma pessoa só pega.
 */
class EmojiDoDiaTest {

    private val grupo = Uuid.parse("11111111-1111-1111-1111-111111111111")
    private val outroGrupo = Uuid.parse("22222222-2222-2222-2222-222222222222")
    private val hoje = LocalDate.parse("2026-09-10")

    /**
     * [INVARIANTE] O emoji é o MESMO para todo o grupo naquele dia.
     *
     * É a metade que um sorteio de verdade quebraria. Sem isto, cada recarga de tela mostraria um
     * emoji diferente e duas pessoas do mesmo grupo nunca concordariam sobre o que imitar.
     */
    @Test
    fun `o mesmo grupo no mesmo dia sempre da o mesmo emoji`() {
        val emoji = EmojiDoDia.de(grupo, hoje)

        repeat(50) {
            assertEquals(emoji, EmojiDoDia.de(grupo, hoje), "o emoji do dia não pode variar entre chamadas")
        }
    }

    @Test
    fun `dias diferentes dao emojis diferentes ao longo da semana`() {
        // Não afirmo que dois dias QUAISQUER diferem — com 20 emojis, colisão em dias vizinhos é
        // esperada e não é defeito. O que se afirma é que a sequência VARIA: um sorteio preso num
        // valor só passaria no teste de estabilidade acima e falharia aqui.
        val semana = (10..16).map { EmojiDoDia.de(grupo, LocalDate.parse("2026-09-$it")) }

        assertTrue(semana.toSet().size > 1, "sete dias seguidos com o mesmo emoji não é sorteio: $semana")
    }

    @Test
    fun `grupos diferentes no mesmo dia nao ficam sincronizados`() {
        // Se o emoji dependesse só do dia, o app inteiro imitaria o mesmo gesto — e a regra
        // deixaria de ser "do seu grupo".
        val dias = (1..28).map { LocalDate.parse("2026-09-%02d".format(it)) }
        val iguais = dias.count { EmojiDoDia.de(grupo, it) == EmojiDoDia.de(outroGrupo, it) }

        assertTrue(iguais < dias.size, "dois grupos com a MESMA sequência num mês significa que o grupo não entra no cálculo")
    }

    /**
     * [INVARIANTE] "Sorteia de uma lista curada, nunca de todo o Unicode."
     *
     * O índice é `floorMod` justamente para isto: `hashCode` devolve negativo com frequência, e
     * `absoluteValue` não resolve — `Int.MIN_VALUE.absoluteValue` continua negativo, e o índice
     * estouraria a lista uma vez a cada dois bilhões de combinações. Raro o bastante para passar
     * despercebido em teste manual, e certo o bastante para acontecer em produção.
     */
    @Test
    fun `o emoji sai SEMPRE da lista curada, para milhares de combinacoes`() {
        val grupos = List(200) { Uuid.random() }
        val dias = (1..30).map { LocalDate.parse("2026-09-%02d".format(it)) }

        grupos.forEach { g ->
            dias.forEach { d ->
                val emoji = EmojiDoDia.de(g, d)
                assertTrue(emoji in EmojiDoDia.LISTA, "'$emoji' não pertence à lista curada")
            }
        }
    }

    @Test
    fun `a lista nao tem repetidos`() {
        // Um emoji duplicado dobraria a chance dele sair, e ninguém perceberia lendo a lista.
        assertEquals(EmojiDoDia.LISTA.size, EmojiDoDia.LISTA.toSet().size)
    }

    /**
     * Nenhum emoji de Unicode 14 ou 15 na lista.
     *
     * Eles aparecem como **caixa vazia** em aparelhos anteriores ao Android 14 — o `minSdk` do
     * projeto é 24, e a bancada roda Android 12. Um emoji que a pessoa não enxerga é uma regra
     * impossível de cumprir, e nada no app detecta isso: o servidor sorteia normalmente, a tela
     * desenha o que a fonte devolve, e o resultado é um retângulo vazio pedindo imitação.
     *
     * A faixa `U+1FAF0..U+1FAFF` é a das "mãos" novas (🫰 🫱 🫲 🫳 🫴 🫵 🫶 🫸 🫷) e a
     * `U+1FAE0..U+1FAEF` a dos rostos novos (🫡 🫢 🫣 🫤 🫥 …). São as duas que a proposta de
     * lista de 2026-09-05 trouxe e que foram recusadas por isto.
     */
    @Test
    fun `nenhum emoji recente demais para o Android que suportamos`() {
        val recentes = 0x1FAE0..0x1FAFF

        EmojiDoDia.LISTA.forEach { emoji ->
            val muitoNovo = emoji.codePoints().anyMatch { it in recentes }
            assertTrue(
                !muitoNovo,
                "'$emoji' é Unicode 14+ e vira caixa vazia antes do Android 14",
            )
        }
    }

    /**
     * Nenhum emoji da lista carrega modificador de tom de pele (decisão de 2026-08-31).
     *
     * Duas versões do mesmo gesto no feed pareceriam emojis diferentes, e a comparação entre
     * check-ins de dias distintos perderia sentido. O intervalo `U+1F3FB..U+1F3FF` é a faixa dos
     * cinco modificadores Fitzpatrick.
     */
    @Test
    fun `nenhum emoji tem modificador de tom de pele`() {
        val modificadores = 0x1F3FB..0x1F3FF

        EmojiDoDia.LISTA.forEach { emoji ->
            val temModificador = emoji.codePoints().anyMatch { it in modificadores }
            assertTrue(!temModificador, "'$emoji' carrega modificador de tom de pele — use a forma neutra")
        }
    }
}
