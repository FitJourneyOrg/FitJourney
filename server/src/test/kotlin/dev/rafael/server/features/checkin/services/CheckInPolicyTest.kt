package dev.rafael.server.features.checkin.services

import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.GroupState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CheckInPolicyTest {

    private val saoPaulo = TimeZone.of("America/Sao_Paulo")   // UTC-3
    private val toquio = TimeZone.of("Asia/Tokyo")            // UTC+9

    // ---- o dia é o do GRUPO (4.6) ----

    @Test
    fun `quem treina a noite em Sao Paulo nao cai no dia seguinte`() {
        // 2026-08-24 22h em São Paulo é 2026-08-25 01h em UTC. Se o dia saísse do relógio do
        // servidor, este check-in cairia em 25 — e a pessoa poderia fazer OUTRO no dia 25 à tarde,
        // furando o "um por pessoa/dia/grupo" sem trapacear, só treinando tarde.
        val agora = Instant.parse("2026-08-25T01:00:00Z")

        assertEquals(LocalDate(2026, 8, 24), CheckInPolicy.diaDoGrupo(agora, saoPaulo))
        assertEquals(LocalDate(2026, 8, 25), CheckInPolicy.diaDoGrupo(agora, TimeZone.UTC))
    }

    @Test
    fun `fuso a frente do UTC tambem e respeitado`() {
        // O espelho do caso anterior: em Tóquio já é dia 25 quando em UTC ainda é 24.
        val agora = Instant.parse("2026-08-24T23:00:00Z")
        assertEquals(LocalDate(2026, 8, 25), CheckInPolicy.diaDoGrupo(agora, toquio))
        assertEquals(LocalDate(2026, 8, 24), CheckInPolicy.diaDoGrupo(agora, TimeZone.UTC))
    }

    // ---- só com o grupo ATIVO ----

    @Test
    fun `check-in so vale com o grupo ATIVO`() {
        assertNull(CheckInPolicy.impedimento(GroupState.ATIVO))
        assertEquals(CheckInBlock.NAO_COMECOU, CheckInPolicy.impedimento(GroupState.AGENDADO))
        assertEquals(CheckInBlock.ENCERRADO, CheckInPolicy.impedimento(GroupState.ENCERRADO))
    }

    // ---- recusa ESTRUTURAL das regras (5.3) ----

    @Test
    fun `grupo sem regras aceita check-in pelado`() {
        assertTrue(CheckInPolicy.regrasNaoCumpridas(emptySet(), temFoto = false, temLocal = false).isEmpty())
    }

    @Test
    fun `grupo que exige foto e local recusa quem manda so um`() {
        val regras = setOf(GroupRule.FOTO, GroupRule.LOCALIZACAO)

        assertEquals(
            setOf(GroupRule.FOTO, GroupRule.LOCALIZACAO),
            CheckInPolicy.regrasNaoCumpridas(regras, temFoto = false, temLocal = false),
        )
        assertEquals(
            setOf(GroupRule.LOCALIZACAO),
            CheckInPolicy.regrasNaoCumpridas(regras, temFoto = true, temLocal = false),
        )
        assertTrue(
            CheckInPolicy.regrasNaoCumpridas(regras, temFoto = true, temLocal = true).isEmpty(),
        )
    }

    @Test
    fun `EMOJI_DO_DIA exige FOTO mesmo sem FOTO na lista`() {
        // [INV] "Grupo com EMOJI_DO_DIA obrigatoriamente tem FOTO". A criação já garante isso, mas
        // uma linha antiga ou uma edição futura podem chegar sem — e aí exigir o emoji sem exigir
        // a foto pediria um emoji que não tem onde aparecer.
        val faltando = CheckInPolicy.regrasNaoCumpridas(
            setOf(GroupRule.EMOJI_DO_DIA), temFoto = false, temLocal = false,
        )
        assertEquals(setOf(GroupRule.FOTO), faltando)
    }

    @Test
    fun `GYM_PASS nao bloqueia ninguem`() {
        // Está declarado no contrato e indisponível (depende de contrato comercial). Recusar por
        // uma regra que ninguém consegue cumprir travaria o grupo inteiro.
        assertTrue(
            CheckInPolicy.regrasNaoCumpridas(setOf(GroupRule.GYM_PASS), temFoto = false, temLocal = false).isEmpty(),
        )
    }

    @Test
    fun `local sozinho nao supre a exigencia de foto`() {
        assertEquals(
            setOf(GroupRule.FOTO),
            CheckInPolicy.regrasNaoCumpridas(setOf(GroupRule.FOTO), temFoto = false, temLocal = true),
        )
    }

    // ---- apagar só no mesmo dia (4.11) ----

    @Test
    fun `apagar vale ate a virada do dia NO FUSO DO GRUPO`() {
        val feitoEm = LocalDate(2026, 8, 24)

        // 23h59 em São Paulo (= 02h59 UTC do dia 25): ainda é o dia 24 para o grupo.
        assertTrue(CheckInPolicy.podeApagar(feitoEm, Instant.parse("2026-08-25T02:59:00Z"), saoPaulo))

        // 00h01 em São Paulo: virou. Acabou a janela.
        assertFalse(CheckInPolicy.podeApagar(feitoEm, Instant.parse("2026-08-25T03:01:00Z"), saoPaulo))
    }

    @Test
    fun `check-in de ontem nao se apaga`() {
        assertFalse(
            CheckInPolicy.podeApagar(LocalDate(2026, 8, 20), Instant.parse("2026-08-24T12:00:00Z"), saoPaulo),
        )
    }

    // ---- coordenada arredondada NA ESCRITA ----

    @Test
    fun `a coordenada e arredondada a 2 casas`() {
        // [INV] "A coordenada exata nunca é gravada". Arredondar na escrita é o que torna o
        // invariante verificável com um SELECT — mascarar na leitura deixaria o dado cru no banco.
        assertEquals(-23.55, CheckInPolicy.arredondar(-23.5505199))
        assertEquals(-46.63, CheckInPolicy.arredondar(-46.6333094))
        assertEquals(0.0, CheckInPolicy.arredondar(0.0001))
    }

    @Test
    fun `2 casas sao cerca de 1 km — precisao suficiente para um mapa, nao para achar a casa`() {
        // 0.01 grau de latitude ≈ 1,1 km. O que sobra localiza o bairro, não o endereço.
        val cru = -23.5505199
        val arredondado = CheckInPolicy.arredondar(cru)
        assertTrue(kotlin.math.abs(cru - arredondado) < 0.01)
    }
}
