package dev.rafael.server.program

import dev.rafael.contract.program.CausaAvisoDto
import dev.rafael.contract.program.ProgramAvisoDto
import dev.rafael.contract.workout.WorkoutOrigin
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.CodigoDeTeste
import dev.rafael.server.features.program.db.ProgramRepositoryImpl
import dev.rafael.server.features.program.models.Program
import dev.rafael.server.features.program.models.ProgramsTable
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.uuid.Uuid

/**
 * Programa contra Postgres REAL (V73, débito (c)/(d) de "o motor sabe, o app não mostra",
 * 2026-10-31) — o que só o banco prova: que `avisos`/`grupamentosReduzidosPeloFoco` sobrevivem
 * à releitura do MESMO programa (JSON-em-TEXT, mesmo padrão de `focusMuscles`/`focus_muscles`),
 * e que linha sem o dado (simulando legado pré-V73, coluna NULL) lê como lista vazia sem erro.
 *
 * **Lacuna pré-existente que esta fatia fecha**: zero testes de integração tocavam
 * `Program`/`ProgramRepository` antes desta classe (mesma classe de risco do débito já
 * registrado "Outbox sem teste de integração") — mas como esta fatia TOCA schema e
 * repositório, a C2 não deixa pular o Tier 3 aqui.
 *
 * Usa o [BancoDeTeste] — container compartilhado, limpo uma vez por CLASSE (ver KDoc do
 * objeto): afirma sobre o cenário do próprio teste, nunca sobre um `count()` global.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProgramIntegrationTest {

    private val programas = ProgramRepositoryImpl()

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun <T> ok(r: AppResult<T>): T = (r as AppResult.Success).value

    private fun novoUsuario(nome: String = "Atleta"): Uuid {
        val id = Uuid.random()
        transaction {
            UsersTable.insert {
                it[UsersTable.id] = id
                it[firebaseUid] = "uid-$id"
                it[email] = "$id@teste.local"
                it[displayName] = nome
                it[code] = CodigoDeTeste.de(id)
            }
        }
        return id
    }

    private fun programaBase(dono: Uuid, avisos: List<ProgramAvisoDto>, grupos: List<String>): Program {
        val ts = LocalDateTime(2026, 10, 31, 12, 0)
        return Program(
            id = Uuid.NIL, userId = dono, name = "P", origin = WorkoutOrigin.AI,
            daysPerWeek = 3, split = null, focusMuscles = emptyList(),
            avisos = avisos, grupamentosReduzidosPeloFoco = grupos,
            locked = false, workouts = emptyList(),
            createdAt = ts, updatedAt = ts, durationWeeks = 8, startedAt = ts,
        )
    }

    @Test
    fun `avisos e grupamentosReduzidosPeloFoco sobrevivem a releitura do mesmo programa`() = runBlocking {
        val dono = novoUsuario()
        val avisos = listOf(
            ProgramAvisoDto(musculo = "CHEST", causa = CausaAvisoDto.MANUTENCAO),
            ProgramAvisoDto(musculo = "BACK", causa = CausaAvisoDto.NAO_TREINADO),
        )
        val grupos = listOf("SHOULDERS", "BICEPS")
        val salvo = ok(programas.createForUser(dono, programaBase(dono, avisos, grupos)))

        val relido = ok(programas.findByIdForUser(dono, salvo.id))
        assertTrue(relido != null, "o programa recém-criado precisa ser lido de volta")
        assertEquals(avisos, relido!!.avisos)
        assertEquals(grupos, relido.grupamentosReduzidosPeloFoco)
    }

    @Test
    fun `sem avisos (motor atual, dormente) grava lista vazia, nao null, e le de volta vazio`() = runBlocking {
        // Caminho do DeterministicWorkoutGenerator hoje: ProgramDto.avisos nasce emptyList()
        // (default do contrato), nunca null -- ver V73, comentário da migration.
        val dono = novoUsuario()
        val salvo = ok(programas.createForUser(dono, programaBase(dono, emptyList(), emptyList())))

        val relido = ok(programas.findByIdForUser(dono, salvo.id))
        assertEquals(emptyList<ProgramAvisoDto>(), relido!!.avisos)
        assertEquals(emptyList<String>(), relido.grupamentosReduzidosPeloFoco)
    }

    @Test
    fun `linha com a coluna NULL (legado pre-V73) le como lista vazia, nao erro`() = runBlocking {
        // Caminho de FALHA: simula um programa gravado ANTES desta migration (coluna nunca
        // escrita, NULL de verdade -- não "[]"). A leitura não pode estourar nem confundir
        // "sem dado" com "lista vazia calculada".
        val dono = novoUsuario()
        val salvo = ok(programas.createForUser(dono, programaBase(dono, emptyList(), emptyList())))
        transaction {
            ProgramsTable.update({ ProgramsTable.id eq salvo.id }) {
                it[avisos] = null
                it[grupamentosReduzidosPeloFoco] = null
            }
        }

        val relido = ok(programas.findByIdForUser(dono, salvo.id))
        assertEquals(emptyList<ProgramAvisoDto>(), relido!!.avisos)
        assertEquals(emptyList<String>(), relido.grupamentosReduzidosPeloFoco)
    }
}
