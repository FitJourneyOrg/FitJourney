package dev.rafael.server.media

import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.checkin.db.CheckInRepository
import dev.rafael.server.features.checkin.db.FotoExpirada
import dev.rafael.server.features.checkin.models.CheckInComAutor
import dev.rafael.server.features.checkin.models.NovoCheckIn
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * O varredor de mídia (4.8, emendada).
 *
 * Isto **apaga foto de gente**, e de forma irreversível. Os testes que mais importam aqui não são
 * os do caminho feliz — são os das GUARDAS: que arquivo recém-gravado não é confundido com órfão,
 * e que a marca no banco nunca entra antes de o arquivo sair.
 */
class PurgaDeMidiaTest {

    private val agora = Instant.parse("2026-08-25T12:00:00Z")
    private val relogio = object : Clock { override fun now() = agora }

    /** Dublê com as duas consultas da purga. O resto da interface não é usado aqui. */
    private class FakeCheckIns(
        val expiradas: MutableList<FotoExpirada> = mutableListOf(),
        val vivas: MutableSet<String> = mutableSetOf(),
    ) : CheckInRepository {
        var marcados: List<Uuid> = emptyList()
            private set
        var marcouEm: LocalDateTime? = null
            private set

        override suspend fun comFotoExpirada(carenciaEmDias: Int, limite: Int) =
            expiradas.take(limite).asSuccess()

        override suspend fun marcarPurgados(ids: List<Uuid>, agora: LocalDateTime): AppResult<Unit> {
            marcados = ids
            marcouEm = agora
            return Unit.asSuccess()
        }

        override suspend fun refsVivas(): AppResult<Set<String>> = vivas.toSet().asSuccess()

        override suspend fun criar(novo: NovoCheckIn) = naoUsado()
        override suspend fun porId(id: Uuid) = naoUsado()
        override suspend fun doDia(groupId: Uuid, userId: Uuid, dia: LocalDate) = naoUsado()
        override suspend fun doGrupo(groupId: Uuid, limite: Int, antesDe: LocalDateTime?) = naoUsado()
        override suspend fun apagar(id: Uuid) = naoUsado()
        private fun naoUsado(): Nothing = error("a purga não chama isto")
    }

    /** Armazenamento em memória que REGISTRA a ordem — é o que prova a sequência arquivo→marca. */
    private class FakeMidia(inicial: Map<String, Instant> = emptyMap()) : ArmazenamentoDeMidia {
        val arquivos = inicial.toMutableMap()   // ref -> instante de gravação
        val apagados = mutableListOf<String>()

        override suspend fun guardar(bytes: ByteArray, extensao: String) = naoUsado()
        override suspend fun ler(ref: String) = naoUsado()

        override suspend fun apagar(ref: String): AppResult<Unit> {
            apagados += ref
            arquivos.remove(ref)
            return Unit.asSuccess()
        }

        override suspend fun listarRefs(anteriorA: Instant): AppResult<List<String>> =
            arquivos.filterValues { it < anteriorA }.keys.toList().asSuccess()

        private fun naoUsado(): Nothing = error("a purga não chama isto")
    }

    private fun ref(n: Int) = "aa/bb/${"%032x".format(n)}.jpg"

    // ---- foto expirada ----

    @Test
    fun `apaga a foto de desafio encerrado e marca a linha`(): Unit = runBlocking {
        val id = Uuid.random()
        val checkIns = FakeCheckIns(expiradas = mutableListOf(FotoExpirada(id, ref(1))))
        val midia = FakeMidia(mapOf(ref(1) to agora - 100.hours))

        val r = PurgaDeMidia(checkIns, midia, relogio).rodar()

        assertEquals(1, r.fotosExpiradas)
        assertEquals(listOf(ref(1)), midia.apagados)
        assertEquals(listOf(id), checkIns.marcados)
    }

    @Test
    fun `o ARQUIVO sai antes da MARCA`(): Unit = runBlocking {
        // Se a marca viesse primeiro e a exclusão falhasse, a linha diria "purgada" com o arquivo
        // ainda em disco — e ninguém voltaria a olhar, porque a consulta filtra por
        // `photo_purged_at IS NULL`. Vazamento permanente e silencioso.
        val id = Uuid.random()
        val ordem = mutableListOf<String>()
        val checkIns = object : CheckInRepository by FakeCheckIns() {
            override suspend fun comFotoExpirada(carenciaEmDias: Int, limite: Int) =
                listOf(FotoExpirada(id, ref(1))).asSuccess()

            override suspend fun marcarPurgados(ids: List<Uuid>, agora: LocalDateTime): AppResult<Unit> {
                ordem += "marca"
                return Unit.asSuccess()
            }

            override suspend fun refsVivas() = emptySet<String>().asSuccess()
        }
        val midia = object : ArmazenamentoDeMidia {
            override suspend fun guardar(bytes: ByteArray, extensao: String) = error("")
            override suspend fun ler(ref: String) = error("")
            override suspend fun apagar(ref: String): AppResult<Unit> {
                ordem += "arquivo"
                return Unit.asSuccess()
            }
            override suspend fun listarRefs(anteriorA: Instant) = emptyList<String>().asSuccess()
        }

        PurgaDeMidia(checkIns, midia, relogio).rodar()

        assertEquals(listOf("arquivo", "marca"), ordem)
    }

    @Test
    fun `sem nada a purgar, nao marca ninguem`(): Unit = runBlocking {
        val checkIns = FakeCheckIns()
        val r = PurgaDeMidia(checkIns, FakeMidia(), relogio).rodar()

        assertEquals(0, r.fotosExpiradas)
        assertTrue(checkIns.marcados.isEmpty())
        assertEquals(null, checkIns.marcouEm, "marcar lista vazia seria um UPDATE sem WHERE útil")
    }

    // ---- órfãos ----

    @Test
    fun `recolhe arquivo que nenhum check-in referencia`(): Unit = runBlocking {
        // O caso do grupo apagado: a cascata levou as linhas e deixou os arquivos.
        val checkIns = FakeCheckIns(vivas = mutableSetOf(ref(1)))
        val midia = FakeMidia(
            mapOf(ref(1) to agora - 100.hours, ref(2) to agora - 100.hours),
        )

        val r = PurgaDeMidia(checkIns, midia, relogio).rodar()

        assertEquals(1, r.orfaosRecolhidos)
        assertEquals(listOf(ref(2)), midia.apagados)
        assertTrue(ref(1) in midia.arquivos, "a foto viva não pode ser tocada")
    }

    @Test
    fun `arquivo recem-gravado NAO e confundido com orfao`(): Unit = runBlocking {
        // A GUARDA. O `CheckInService` grava a foto e só depois tenta o INSERT; entre as duas
        // coisas existe um arquivo legítimo sem linha nenhuma. Sem o corte de 24h, uma passada no
        // instante errado apagaria a foto de alguém no meio do próprio check-in.
        val checkIns = FakeCheckIns()   // nenhuma ref viva
        val midia = FakeMidia(mapOf(ref(9) to agora - 1.hours))

        val r = PurgaDeMidia(checkIns, midia, relogio).rodar()

        assertEquals(0, r.orfaosRecolhidos)
        assertTrue(midia.apagados.isEmpty(), "arquivo com 1 hora de vida foi apagado")
        assertTrue(ref(9) in midia.arquivos)
    }

    @Test
    fun `um arquivo com mais de 24h e sem linha e orfao`(): Unit = runBlocking {
        val checkIns = FakeCheckIns()
        val midia = FakeMidia(mapOf(ref(9) to agora - 25.hours))

        assertEquals(1, PurgaDeMidia(checkIns, midia, relogio).rodar().orfaosRecolhidos)
    }

    @Test
    fun `falha ao ler as refs vivas NAO apaga nada`(): Unit = runBlocking {
        // A pior falha imaginável: se um erro de banco devolvesse "nenhuma ref viva", o varredor
        // concluiria que TODO arquivo é órfão e apagaria a biblioteca inteira de fotos.
        val checkIns = object : CheckInRepository by FakeCheckIns() {
            override suspend fun comFotoExpirada(carenciaEmDias: Int, limite: Int) =
                emptyList<FotoExpirada>().asSuccess()

            override suspend fun refsVivas(): AppResult<Set<String>> =
                AppResult.Failure(dev.rafael.core.result.AppError.Unexpected("banco fora"))
        }
        val midia = FakeMidia(mapOf(ref(1) to agora - 100.hours))

        val r = PurgaDeMidia(checkIns, midia, relogio).rodar()

        assertEquals(0, r.orfaosRecolhidos)
        assertTrue(midia.apagados.isEmpty(), "erro de leitura virou exclusão em massa")
    }
}
