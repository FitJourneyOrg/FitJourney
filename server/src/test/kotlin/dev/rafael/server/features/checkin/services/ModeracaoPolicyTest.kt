package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.contract.group.MemberRole
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Denúncia e moderação (fatia E.2) — as regras que decidem, sem banco e sem relógio próprio. */
class ModeracaoPolicyTest {

    private val criacao = LocalDateTime(2026, 12, 10, 12, 0)
    private val instante = criacao.toInstant(TimeZone.UTC)

    // ---- prazo (6.9) ----

    @Test
    fun `dentro dos sete dias pode denunciar`() {
        assertNull(
            ModeracaoPolicy.podeDenunciarCheckIn(
                status = CheckInStatus.VALIDO,
                criadoEm = criacao,
                agora = instante + 6.days,
                souOAutor = false,
            ),
        )
    }

    /**
     * A borda EXATA: no instante em que completa sete dias, ainda vale.
     *
     * `>` e não `>=` na comparação. Parece detalhe, e é o tipo de coisa que a pessoa descobre ao
     * receber "fora do prazo" numa denúncia feita no sétimo dia — quando o teste que a protegeria
     * teria custado três linhas.
     */
    @Test
    fun `no instante exato do sétimo dia ainda vale`() {
        assertNull(
            ModeracaoPolicy.podeDenunciarCheckIn(
                status = CheckInStatus.VALIDO,
                criadoEm = criacao,
                agora = instante + 7.days,
                souOAutor = false,
            ),
        )
    }

    @Test
    fun `um segundo depois do sétimo dia ja nao vale`() {
        assertEquals(
            DenunciaBlock.PRAZO,
            ModeracaoPolicy.podeDenunciarCheckIn(
                status = CheckInStatus.VALIDO,
                criadoEm = criacao,
                agora = instante + 7.days + 1.seconds,
                souOAutor = false,
            ),
        )
    }

    // ---- ordem das recusas ----

    /**
     * ⭐ "É seu" vem ANTES do prazo, e a ordem é a regra.
     *
     * Um check-in de duas semanas atrás, visto pelo dono: as duas recusas se aplicam. Se o prazo
     * viesse primeiro, a tela diria "o prazo acabou" — sugerindo que ele poderia ter denunciado o
     * próprio check-in se tivesse sido mais rápido. **A mensagem errada ensina a regra errada.**
     */
    @Test
    fun `o dono recebe E_SEU, nao PRAZO, mesmo com o prazo vencido`() {
        assertEquals(
            DenunciaBlock.E_SEU,
            ModeracaoPolicy.podeDenunciarCheckIn(
                status = CheckInStatus.VALIDO,
                criadoEm = criacao,
                agora = instante + 30.days,
                souOAutor = true,
            ),
        )
    }

    /**
     * Denunciar o próprio check-in é recusado dentro do prazo também.
     *
     * Quem quer desfazer o próprio check-in tem a 4.11 (apagar no mesmo dia). Oferecer a denúncia
     * como segundo caminho criaria uma forma de "apagar" fora do prazo, passando pelo admin —
     * **dois caminhos para o mesmo efeito é como uma regra de prazo deixa de valer**.
     */
    @Test
    fun `ninguem denuncia o proprio check-in`() {
        assertEquals(
            DenunciaBlock.E_SEU,
            ModeracaoPolicy.podeDenunciarCheckIn(
                status = CheckInStatus.VALIDO,
                criadoEm = criacao,
                agora = instante + 1.hours,
                souOAutor = true,
            ),
        )
    }

    @Test
    fun `o ja invalidado nao se denuncia de novo`() {
        assertEquals(
            DenunciaBlock.JA_JULGADO,
            ModeracaoPolicy.podeDenunciarCheckIn(
                status = CheckInStatus.INVALIDADO,
                criadoEm = criacao,
                agora = instante + 1.hours,
                souOAutor = false,
            ),
        )
    }

    /**
     * O que já está EM_ANALISE **continua denunciável**.
     *
     * Cada denúncia nova engrossa o contador da 6.11, e é isso que diz ao admin que o caso é sério.
     * Bloquear a segunda transformaria "5 pessoas reclamaram" em "1 pessoa reclamou" — e o número
     * é metade do que ele tem para julgar.
     */
    @Test
    fun `o que esta em analise continua aceitando denuncia`() {
        assertNull(
            ModeracaoPolicy.podeDenunciarCheckIn(
                status = CheckInStatus.EM_ANALISE,
                criadoEm = criacao,
                agora = instante + 1.hours,
                souOAutor = false,
            ),
        )
    }

    // ---- comentário ----

    @Test
    fun `comentario segue o mesmo prazo, contado dele mesmo`() {
        assertNull(ModeracaoPolicy.podeDenunciarComentario(criacao, instante + 3.days, false))
        assertEquals(
            DenunciaBlock.PRAZO,
            ModeracaoPolicy.podeDenunciarComentario(criacao, instante + 8.days, false),
        )
    }

    @Test
    fun `ninguem denuncia o proprio comentario`() {
        assertEquals(
            DenunciaBlock.E_SEU,
            ModeracaoPolicy.podeDenunciarComentario(criacao, instante, true),
        )
    }

    // ---- máquina de estados ----

    @Test
    fun `a primeira denuncia leva de VALIDO para EM_ANALISE`() {
        assertEquals(CheckInStatus.EM_ANALISE, ModeracaoPolicy.aposDenuncia(CheckInStatus.VALIDO))
    }

    /**
     * A segunda denúncia não mexe em nada — é a 6.11 valendo também para o ESTADO.
     *
     * "Várias denúncias viram uma solicitação" costuma ser lido como regra de tela. Aqui ela é
     * regra de máquina: o que já está em análise permanece, e o que já foi invalidado não volta.
     */
    @Test
    fun `denuncia sobre o que ja esta em analise nao muda o estado`() {
        assertEquals(CheckInStatus.EM_ANALISE, ModeracaoPolicy.aposDenuncia(CheckInStatus.EM_ANALISE))
    }

    /** [INV] "check-in invalidado nunca volta a contar" — `INVALIDADO` é terminal. */
    @Test
    fun `nada tira um check-in de INVALIDADO`() {
        assertEquals(CheckInStatus.INVALIDADO, ModeracaoPolicy.aposDenuncia(CheckInStatus.INVALIDADO))
    }

    @Test
    fun `acatar invalida, recusar devolve a VALIDO`() {
        assertEquals(CheckInStatus.INVALIDADO, ModeracaoPolicy.aposJulgamento(acatou = true))
        // Devolve a VALIDO puro, e não a "válido com marca": quem foi absolvido é indistinguível de
        // quem nunca foi denunciado. Guardar a suspeita seria punir a absolvição.
        assertEquals(CheckInStatus.VALIDO, ModeracaoPolicy.aposJulgamento(acatou = false))
    }

    // ---- quem modera ----

    @Test
    fun `so o admin modera`() {
        assertTrue(ModeracaoPolicy.podeModerar(MemberRole.ADMIN))
        assertFalse(ModeracaoPolicy.podeModerar(MemberRole.MEMBRO))
        assertFalse(ModeracaoPolicy.podeModerar(null))
    }

    // ---- motivo ----

    @Test
    fun `motivo volta aparado`() {
        assertEquals("Foto de outro dia", ModeracaoPolicy.motivoValido("  Foto de outro dia  "))
    }

    @Test
    fun `motivo vazio, so espacos ou ausente nao serve`() {
        assertNull(ModeracaoPolicy.motivoValido(null))
        assertNull(ModeracaoPolicy.motivoValido(""))
        assertNull(ModeracaoPolicy.motivoValido("   \n\t "))
    }

    /**
     * Texto longo é CORTADO, não recusado.
     *
     * Escolha diferente da do comentário, que devolve `null` acima de 500 — e a diferença tem
     * motivo. Lá o campo da tela é o produto e o contador avisa; aqui o texto é acessório ao ato de
     * denunciar, e perder a denúncia inteira por excesso de explicação seria punir quem se esforçou.
     */
    @Test
    fun `motivo longo demais e cortado, nao recusado`() {
        val longo = "a".repeat(ModeracaoPolicy.MAX_MOTIVO + 50)

        val tratado = ModeracaoPolicy.motivoValido(longo)

        assertEquals(ModeracaoPolicy.MAX_MOTIVO, tratado?.length)
    }
}
