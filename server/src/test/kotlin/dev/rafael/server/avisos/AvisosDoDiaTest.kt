package dev.rafael.server.avisos

import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.group.services.AvisosDiarios
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
 * O laço diário do grupo (fatia F).
 *
 * ## O que este arquivo prova, e a `AvisosDiarios` não
 *
 * A política responde "cabe avisar?". Aqui se testa a **orquestração**: quem recebe o quê, quantas
 * vezes, e o que acontece quando o dia vira ou o servidor reinicia.
 *
 * ## O dublê tem a PK composta de verdade
 *
 * `registrarAviso` guarda por `(grupo, dia, tipo)` e devolve `false` na segunda chamada — como a
 * PK da V46. Um fake que sempre devolvesse `true` faria "no máximo uma por dia" passar por
 * construção, e o defeito só apareceria depois de um reinício em produção.
 */
class AvisosDoDiaTest {

    private val grupo = Uuid.random()
    private val admin = Uuid.random()
    private val membro = Uuid.random()

    private val saoPaulo = TimeZone.of("America/Sao_Paulo")

    /** Relógio que o teste move — é o que torna "3 dias parada" verificável em milissegundos. */
    private class RelogioFixo(var agora: Instant) : Clock {
        override fun now(): Instant = agora
        fun avancar(quanto: Duration) { agora += quanto }
    }

    /** Registra tudo que foi enviado, para o teste afirmar sem espiar o banco. */
    private class AvisosEnviados : AvisosDoDia.EnviarAviso {
        data class Entrada(
            val para: Uuid,
            val groupId: Uuid,
            val quantas: Int,
            val souOCriador: Boolean,
        )
        data class Fila(val para: Uuid, val groupId: Uuid, val casos: Int, val dias: Int)

        val entradas = mutableListOf<Entrada>()
        val filas = mutableListOf<Fila>()

        override suspend fun entradasDoDia(
            destinatario: Uuid,
            grupo: String,
            groupId: Uuid,
            quantas: Int,
            souOCriador: Boolean,
        ) {
            entradas += Entrada(destinatario, groupId, quantas, souOCriador)
        }

        override suspend fun filaParada(admin: Uuid, grupo: String, groupId: Uuid, casos: Int, dias: Int) {
            filas += Fila(admin, groupId, casos, dias)
        }
    }

    private inner class FakeRepo(
        var entradasHoje: Int = 0,
        var fila: FilaResumida = FilaResumida(0, Instant.DISTANT_FUTURE),
        var encerrado: Boolean = false,
    ) : AvisosDiariosRepository {

        /** A PK composta da V46, em memória. É o que torna o teste do reinício honesto. */
        val registrados = mutableSetOf<Triple<Uuid, LocalDate, AvisosDiarios.Tipo>>()
        var casosEncerrados = 0

        override suspend fun gruposVivos() = listOf(
            // O ADMIN é o criador neste cenário — é ele quem deve ver "no SEU desafio".
            GrupoParaAvisar(grupo, "Desafio", saoPaulo, encerrado, criadorId = admin),
        ).asSuccess()

        /** Guarda quem o serviço mandou excluir, para o teste afirmar que foi o criador. */
        var excluidoDaContagem: Uuid? = null

        override suspend fun entradasNoDia(
            groupId: Uuid,
            dia: LocalDate,
            fuso: TimeZone,
            exceto: Uuid?,
        ): AppResult<Int> {
            excluidoDaContagem = exceto
            return entradasHoje.asSuccess()
        }

        override suspend fun membros(groupId: Uuid) = listOf(admin, membro).asSuccess()
        override suspend fun admins(groupId: Uuid) = listOf(admin).asSuccess()
        override suspend fun filaAberta(groupId: Uuid) = fila.asSuccess()

        override suspend fun registrarAviso(
            groupId: Uuid,
            dia: LocalDate,
            tipo: AvisosDiarios.Tipo,
            quando: LocalDateTime,
        ): AppResult<Boolean> = registrados.add(Triple(groupId, dia, tipo)).asSuccess()

        override suspend fun encerrarCasosPendentes(groupId: Uuid, quando: LocalDateTime): AppResult<Int> {
            val n = fila.casos
            casosEncerrados += n
            fila = FilaResumida(0, Instant.DISTANT_FUTURE)
            return n.asSuccess()
        }
    }

    /** Meio-dia em São Paulo, para o dia civil não estar em cima de uma virada. */
    private fun meioDia() = LocalDateTime(2026, 12, 10, 15, 0).toInstant(TimeZone.UTC)

    // ---- 10.6: entradas do dia, agregadas ----

    @Test
    fun `avisa TODOS os membros, uma vez, com a contagem somada`(): Unit = runBlocking {
        val repo = FakeRepo(entradasHoje = 3)
        val enviados = AvisosEnviados()

        AvisosDoDia(repo, enviados, RelogioFixo(meioDia())).rodar()

        assertEquals(2, enviados.entradas.size, "os dois membros recebem")
        assertTrue(enviados.entradas.all { it.quantas == 3 }, "a contagem é do DIA, não por entrada")
    }

    /**
     * ⭐ Sem entrada nenhuma, ninguém é avisado.
     *
     * O laço roda todo dia para todo grupo, inclusive os parados. Sem esta guarda, cada desafio
     * mandaria "0 pessoas entraram no seu desafio hoje" para 50 pessoas, todo dia — e o gatilho que
     * existe para trazer gente de volta viraria o motivo de desinstalar o app.
     */
    @Test
    fun `zero entradas nao vira aviso`(): Unit = runBlocking {
        val repo = FakeRepo(entradasHoje = 0)
        val enviados = AvisosEnviados()

        AvisosDoDia(repo, enviados, RelogioFixo(meioDia())).rodar()

        assertTrue(enviados.entradas.isEmpty())
        assertTrue(repo.registrados.isEmpty(), "nem registra — o dia continua livre")
    }

    /**
     * ⭐ Duas passadas no mesmo dia mandam UMA vez.
     *
     * O laço acorda de hora em hora (o dia civil de cada grupo vira em instante diferente), então
     * isto acontece **24 vezes por dia em produção**. Quem garante o teto é a PK da V46, e este
     * teste só vale porque o dublê a reproduz.
     */
    @Test
    fun `duas passadas no mesmo dia avisam uma vez so`(): Unit = runBlocking {
        val repo = FakeRepo(entradasHoje = 2)
        val enviados = AvisosEnviados()
        val relogio = RelogioFixo(meioDia())
        val avisos = AvisosDoDia(repo, enviados, relogio)

        avisos.rodar()
        relogio.avancar(1.hours)
        avisos.rodar()

        assertEquals(2, enviados.entradas.size, "dois membros, uma rodada — não quatro")
    }

    /**
     * ⭐ **Criar não é entrar** — achado na bateria da F.
     *
     * O criador vira membro junto com o grupo (entra como `ADMIN` e ocupa uma vaga das 50), e a
     * primeira versão contava essa linha como adesão. Sintoma: criar um desafio e receber, segundos
     * depois, *"1 pessoa entrou no seu desafio hoje"* — sobre si mesmo. O aviso existe para trazer
     * a pessoa de volta, e a primeira coisa que fez foi contá-la.
     *
     * O teste afirma **que o serviço mandou excluir o criador** — não que a contagem deu zero. A
     * exclusão acontece no SQL, e o que este nível pode provar é que o parâmetro certo chegou lá.
     * Um teste que só olhasse o resultado passaria com o defeito de volta, bastando o fake
     * devolver zero por outra razão.
     */
    @Test
    fun `a contagem de entradas exclui o criador`(): Unit = runBlocking {
        val repo = FakeRepo(entradasHoje = 2)

        AvisosDoDia(repo, AvisosEnviados(), RelogioFixo(meioDia())).rodar()

        assertEquals(admin, repo.excluidoDaContagem, "o criador não conta como entrada")
    }

    /**
     * ⭐ "no **SEU** desafio" só para quem o criou (emenda de 2026-09-08).
     *
     * Para os outros 49 o possessivo é falso: eles participam, não são donos. Mesmo aviso, textos
     * diferentes — e o critério é **quem criou**, não quem é admin hoje: o cargo é transferível
     * (2.12) e o fundador não muda.
     */
    @Test
    // Sem aspas no nome: o Kotlin gera um `.class` por lambda usando o nome do teste, e `"` é
    // caractere ilegal em caminho no Windows — o compilador quebra com `InvalidPathException`.
    fun `so o criador ve o possessivo`(): Unit = runBlocking {
        val repo = FakeRepo(entradasHoje = 2)
        val enviados = AvisosEnviados()

        AvisosDoDia(repo, enviados, RelogioFixo(meioDia())).rodar()

        assertTrue(enviados.entradas.single { it.para == admin }.souOCriador)
        assertFalse(enviados.entradas.single { it.para == membro }.souOCriador)
    }

    /** No dia seguinte volta a avisar: a chave inclui o dia. */
    @Test
    fun `no dia seguinte avisa de novo`(): Unit = runBlocking {
        val repo = FakeRepo(entradasHoje = 1)
        val enviados = AvisosEnviados()
        val relogio = RelogioFixo(meioDia())
        val avisos = AvisosDoDia(repo, enviados, relogio)

        avisos.rodar()
        relogio.avancar(1.days)
        avisos.rodar()

        assertEquals(4, enviados.entradas.size, "dois membros × dois dias")
    }

    // ---- emenda: fila parada ----

    @Test
    fun `fila parada ha menos de tres dias nao lembra ninguem`(): Unit = runBlocking {
        val agora = meioDia()
        val repo = FakeRepo(fila = FilaResumida(casos = 1, maisAntigoEm = agora - 2.days))
        val enviados = AvisosEnviados()

        AvisosDoDia(repo, enviados, RelogioFixo(agora)).rodar()

        assertTrue(enviados.filas.isEmpty())
    }

    /** ⭐ Só o ADMIN é lembrado — o membro comum não pode julgar (6.2). */
    @Test
    fun `fila parada ha tres dias lembra so o admin`(): Unit = runBlocking {
        val agora = meioDia()
        val repo = FakeRepo(fila = FilaResumida(casos = 2, maisAntigoEm = agora - 3.days))
        val enviados = AvisosEnviados()

        AvisosDoDia(repo, enviados, RelogioFixo(agora)).rodar()

        assertEquals(1, enviados.filas.size)
        assertEquals(admin, enviados.filas.single().para)
        assertEquals(2, enviados.filas.single().casos)
        assertEquals(3, enviados.filas.single().dias)
    }

    /**
     * ⭐ O relógio conta do caso MAIS ANTIGO, não do mais recente.
     *
     * Fosse pelo mais recente, uma denúncia nova zeraria o cronômetro — e o grupo com denúncias
     * frequentes nunca geraria lembrete. **Exatamente o que mais precisa.**
     *
     * Encenado com um caso de 10 dias: se o teste passasse a olhar o mais novo, o `maisAntigoEm`
     * deixaria de ser o campo consultado e isto quebraria.
     */
    @Test
    fun `o cronometro e do caso mais antigo`(): Unit = runBlocking {
        val agora = meioDia()
        val repo = FakeRepo(fila = FilaResumida(casos = 5, maisAntigoEm = agora - 10.days))
        val enviados = AvisosEnviados()

        AvisosDoDia(repo, enviados, RelogioFixo(agora)).rodar()

        assertEquals(10, enviados.filas.single().dias)
    }

    @Test
    fun `lembrete tambem sai uma vez por dia`(): Unit = runBlocking {
        val agora = meioDia()
        val repo = FakeRepo(fila = FilaResumida(casos = 1, maisAntigoEm = agora - 5.days))
        val enviados = AvisosEnviados()
        val relogio = RelogioFixo(agora)
        val avisos = AvisosDoDia(repo, enviados, relogio)

        avisos.rodar()
        relogio.avancar(3.hours)
        avisos.rodar()

        assertEquals(1, enviados.filas.size)
    }

    // ---- emenda: desafio encerrado ----

    /**
     * ⭐ Desafio encerrado FECHA os casos e **não avisa ninguém**.
     *
     * Não houve julgamento; avisar "sua denúncia foi arquivada" contaria ao denunciante algo que
     * ele nem sabe que estava esperando — e a decisão foi que ele não recebe retorno.
     *
     * O contraste com a expiração por tempo (recusada) é o ponto: lá o caso ainda podia produzir
     * efeito, e sumir seria perdoar por inércia do admin. Aqui o ranking congelou.
     */
    @Test
    fun `desafio encerrado fecha os casos sem avisar ninguem`(): Unit = runBlocking {
        val agora = meioDia()
        val repo = FakeRepo(
            entradasHoje = 4,
            fila = FilaResumida(casos = 2, maisAntigoEm = agora - 10.days),
            encerrado = true,
        )
        val enviados = AvisosEnviados()

        val r = AvisosDoDia(repo, enviados, RelogioFixo(agora)).rodar()

        assertEquals(2, r.casosEncerrados)
        assertEquals(2, repo.casosEncerrados)
        assertTrue(enviados.filas.isEmpty(), "não lembra o admin de um desafio que acabou")
        assertTrue(enviados.entradas.isEmpty(), "e ninguém entra num desafio encerrado")
    }

    /**
     * O encerramento é IDEMPOTENTE: a segunda passada não tem o que fechar.
     *
     * O laço roda de hora em hora e passa por todo grupo encerrado que ainda tenha caso. Sem a
     * fila esvaziar, ele gravaria uma linha de auditoria por hora, para sempre.
     */
    @Test
    fun `encerrar de novo nao grava auditoria duplicada`(): Unit = runBlocking {
        val agora = meioDia()
        val repo = FakeRepo(fila = FilaResumida(casos = 3, maisAntigoEm = agora - 1.days), encerrado = true)
        val avisos = AvisosDoDia(repo, AvisosEnviados(), RelogioFixo(agora))

        avisos.rodar()
        val segunda = avisos.rodar()

        assertEquals(0, segunda.casosEncerrados)
        assertEquals(3, repo.casosEncerrados, "só a primeira passada fechou")
    }
}
