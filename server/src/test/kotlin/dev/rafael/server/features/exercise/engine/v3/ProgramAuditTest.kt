package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.server.features.exercise.engine.TargetMuscle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Motor v3, etapa [7] — prova `ProgramAudit.auditar()` contra
 * `tools/motor_sim/motor_v3.py::auditar()`. Entradas construídas à mão (a função é pura: recebe
 * alvo/entregue/indireto/treináveis e classifica), cobrindo os cinco status e o caso que motivou
 * o campo `atingiuMinimo`.
 */
class ProgramAuditTest {

    // Nenhum alvo esta sem catalogo hoje (CORE, o unico, saiu do motor em 2026-10-27). O status
    // SEM_CATALOGO segue sendo a defesa para um alvo que PERCA cobertura, entao o teste simula
    // isso tirando um alvo qualquer de `treinaveis` -- o que exercita a regra, nao o acidente.
    private val semCatalogo = TargetMuscle.FOREARMS
    private val treinaveis = TargetMuscle.entries.filter { it != semCatalogo }

    private fun auditar(
        alvo: Map<TargetMuscle, Double>,
        entregue: Map<TargetMuscle, Int>,
        indireto: Map<TargetMuscle, Double> = emptyMap(),
        objetivo: Goal = Goal.GAIN_MUSCLE,
        pisoDiretoDe: Map<TargetMuscle, Int> = emptyMap(),
    ) = ProgramAudit.auditar(alvo, entregue, indireto, treinaveis, objetivo, pisoDiretoDe)

    @Test
    fun `ATENDIDO sem serie direta sai marcado pisoDiretoNegado - eixo separado do status`() {
        // ⭐ Asserção (b) do D-1: o alvo batido só pelo crédito indireto é ATENDIDO de verdade (o
        // volume efetivo chegou), mas o usuário não fez UM exercício de glúteo na semana. Os dois
        // fatos convivem em eixos separados; fundi-los num status novo esconderia um dos dois.
        val auditoria = auditar(
            alvo = mapOf(TargetMuscle.GLUTES to 10.0, TargetMuscle.TRICEPS to 10.0),
            entregue = mapOf(TargetMuscle.TRICEPS to 4),
            indireto = mapOf(TargetMuscle.GLUTES to 10.0, TargetMuscle.TRICEPS to 7.0),
            pisoDiretoDe = mapOf(TargetMuscle.GLUTES to 2, TargetMuscle.TRICEPS to 2),
        )
        val gluteo = auditoria.getValue(TargetMuscle.GLUTES)
        assertEquals(ProgramAudit.Status.ATENDIDO, gluteo.status)
        assertEquals(0, gluteo.direto)
        assertTrue(gluteo.pisoDiretoNegado, "0 série direta com piso de 2")

        val triceps = auditoria.getValue(TargetMuscle.TRICEPS)
        assertEquals(ProgramAudit.Status.ATENDIDO, triceps.status)
        assertFalse(triceps.pisoDiretoNegado, "4 séries diretas cobrem o piso de 2")
    }

    @Test
    fun `sem piso direto declarado ninguem e marcado - o iniciante e isento`() {
        // Caminho de FALHA do campo: `pisoDiretoDe` vazio é o iniciante (isento pela regra geral
        // do híbrido). Zero série direta ali não é negação de piso — é ausência de piso.
        val auditoria = auditar(
            alvo = mapOf(TargetMuscle.TRICEPS to 7.0),
            entregue = emptyMap(),
            indireto = mapOf(TargetMuscle.TRICEPS to 7.0),
        )
        val triceps = auditoria.getValue(TargetMuscle.TRICEPS)
        assertEquals(ProgramAudit.Status.ATENDIDO, triceps.status)
        assertEquals(0, triceps.direto)
        assertFalse(triceps.pisoDiretoNegado)
    }

    @Test
    fun `os cinco status, um caso de cada`() {
        val auditoria = auditar(
            alvo = mapOf(
                TargetMuscle.BACK to 16.0,    // atingido
                TargetMuscle.CHEST to 14.0,   // acima do piso (8), abaixo do alvo
                TargetMuscle.BICEPS to 12.0,  // abaixo do piso (6)
                TargetMuscle.TRICEPS to 12.0, // nada entregue, nada indireto
                semCatalogo to 0.0,           // fora de `treinaveis`
            ),
            entregue = mapOf(
                TargetMuscle.BACK to 16, TargetMuscle.CHEST to 12, TargetMuscle.BICEPS to 1,
            ),
            indireto = mapOf(TargetMuscle.BICEPS to 2.0),
        )

        assertEquals(ProgramAudit.Status.ATENDIDO, auditoria.getValue(TargetMuscle.BACK).status)
        assertEquals(ProgramAudit.Status.MANUTENCAO, auditoria.getValue(TargetMuscle.CHEST).status)
        assertEquals(ProgramAudit.Status.ABAIXO_DO_MINIMO, auditoria.getValue(TargetMuscle.BICEPS).status)
        assertEquals(ProgramAudit.Status.NAO_TREINADO, auditoria.getValue(TargetMuscle.TRICEPS).status)
        assertEquals(ProgramAudit.Status.SEM_CATALOGO, auditoria.getValue(semCatalogo).status)
    }

    @Test
    fun `efetivo soma o indireto, e e sobre ele que o piso e medido`() {
        // BICEPS: 1 série direta + 2 de crédito indireto = 3 efetivas, contra um piso de 6.
        val biceps = auditar(
            alvo = mapOf(TargetMuscle.BICEPS to 12.0),
            entregue = mapOf(TargetMuscle.BICEPS to 1),
            indireto = mapOf(TargetMuscle.BICEPS to 2.0),
        ).getValue(TargetMuscle.BICEPS)

        assertEquals(3.0, biceps.efetivo)
        assertEquals(6.0, biceps.minimo)
        assertEquals(0.5, biceps.razao)
        assertFalse(biceps.atingiuMinimo)
    }

    @Test
    fun `quem mantem e medido contra o MV, e por isso aprova`() {
        // O caso que motivou o limiar por objetivo (E9): MAINTAIN/BEGINNER tem alvo 4,8 no peito
        // contra uma coluna de iniciante de 8. Medido contra o MEV, reprovava por design — 660
        // pares nos 720. Medido contra o MV (4,8), aprova: prescrever abaixo do MEV não é erro
        // em manutenção, é o que manutenção significa.
        val peito = auditar(
            alvo = mapOf(TargetMuscle.CHEST to 4.8),
            entregue = mapOf(TargetMuscle.CHEST to 5),
            objetivo = Goal.MAINTAIN,
        ).getValue(TargetMuscle.CHEST)

        assertEquals(4.8, peito.minimo, absoluteTolerance = 1e-9)
        assertEquals(ProgramAudit.Status.ATENDIDO, peito.status)
        assertTrue(peito.atingiuMinimo)
    }

    @Test
    fun `o mesmo programa, pedido como ganho de massa, reprova`() {
        // caminho de falha do teste acima: o limiar é a única coisa que muda. 5 séries efetivas
        // passam no MV (4,8) e reprovam no MEV (8).
        val peito = auditar(
            alvo = mapOf(TargetMuscle.CHEST to 14.0),
            entregue = mapOf(TargetMuscle.CHEST to 5),
            objetivo = Goal.GAIN_MUSCLE,
        ).getValue(TargetMuscle.CHEST)

        assertEquals(8.0, peito.minimo)
        assertEquals(ProgramAudit.Status.ABAIXO_DO_MINIMO, peito.status)
        assertFalse(peito.atingiuMinimo)
    }

    @Test
    fun `o gatilho do aviso usa o limiar, nao o status`() {
        val auditoria = auditar(
            alvo = mapOf(TargetMuscle.CHEST to 14.0, TargetMuscle.BACK to 16.0),
            entregue = mapOf(TargetMuscle.CHEST to 5, TargetMuscle.BACK to 16),
        )
        val reprovados = ProgramAudit.abaixoDoMinimo(auditoria).map { it.musculo }

        assertTrue(TargetMuscle.CHEST in reprovados)  // 5 efetivas contra MEV 8
        assertTrue(TargetMuscle.BACK !in reprovados)
        assertTrue(semCatalogo !in reprovados)  // SEM_CATALOGO nunca reprova
    }

    @Test
    fun `auditar nao tem efeito colateral`() {
        val alvo = mapOf(TargetMuscle.CHEST to 14.0)
        val entregue = mapOf(TargetMuscle.CHEST to 12)
        val primeira = auditar(alvo, entregue)
        val segunda = auditar(alvo, entregue)

        assertEquals(primeira, segunda)
        assertEquals(mapOf(TargetMuscle.CHEST to 12), entregue)
    }

    @Test
    fun `todo musculo sai com status, inclusive os que nao receberam nada`() {
        val auditoria = auditar(alvo = emptyMap(), entregue = emptyMap())

        assertEquals(TargetMuscle.entries.size, auditoria.size)
        assertTrue(auditoria.values.all { it.status != ProgramAudit.Status.MANUTENCAO })
    }
}
