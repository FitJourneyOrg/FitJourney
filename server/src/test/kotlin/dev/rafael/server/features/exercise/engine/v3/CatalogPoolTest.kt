package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.models.Ambiente
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Motor v3, Fatia 6 — prova `CatalogPool.pool()` contra `tools/motor_sim/motor_v3.py::pool()`. */
class CatalogPoolTest {

    @Test
    fun `nao mistura ambiente, AMBOS serve os dois`() {
        val soAcademia = exercicioV3("Leg press", TargetMuscle.QUADS, composto = true, ambiente = Ambiente.ACADEMIA)
        val soCasa = exercicioV3("Afundo com halteres", TargetMuscle.QUADS, composto = true, ambiente = Ambiente.CASA, equipamento = "DUMBBELL")
        val ambos = exercicioV3("Agachamento livre", TargetMuscle.QUADS, composto = true, ambiente = Ambiente.AMBOS, equipamento = "BODYWEIGHT")
        val catalogo = listOf(soAcademia, soCasa, ambos)

        val academia = CatalogPool.pool(catalogo, TrainingEnvironment.ACADEMIA, Level.ADVANCED, Accessory.entries.toSet())
        val casa = CatalogPool.pool(catalogo, TrainingEnvironment.CASA, Level.ADVANCED, Accessory.entries.toSet())

        assertEquals(setOf("Leg press", "Agachamento livre"), academia.map { it.name }.toSet())
        assertEquals(setOf("Afundo com halteres", "Agachamento livre"), casa.map { it.name }.toSet())
    }

    @Test
    fun `em casa so sobrevive equipamento de EQUIPAMENTO_CASA`() {
        val comBarra = exercicioV3("Supino reto barra", TargetMuscle.CHEST, composto = true, equipamento = "BARBELL", ambiente = Ambiente.AMBOS)
        val comHalteres = exercicioV3("Supino halteres", TargetMuscle.CHEST, composto = true, equipamento = "DUMBBELL", ambiente = Ambiente.AMBOS)
        val catalogo = listOf(comBarra, comHalteres)

        val casa = CatalogPool.pool(catalogo, TrainingEnvironment.CASA, Level.ADVANCED, Accessory.entries.toSet())
        assertEquals(listOf("Supino halteres"), casa.map { it.name })

        val academia = CatalogPool.pool(catalogo, TrainingEnvironment.ACADEMIA, Level.ADVANCED, Accessory.entries.toSet())
        assertEquals(setOf("Supino reto barra", "Supino halteres"), academia.map { it.name }.toSet())
    }

    @Test
    fun `nivel e cumulativo - iniciante nao ve exercicio avancado`() {
        val iniciante = exercicioV3("Flexao de joelhos", TargetMuscle.CHEST, composto = true, nivel = Level.BEGINNER)
        val avancado = exercicioV3("Supino com pausa", TargetMuscle.CHEST, composto = true, nivel = Level.ADVANCED)
        val catalogo = listOf(iniciante, avancado)

        assertEquals(listOf("Flexao de joelhos"), CatalogPool.pool(catalogo, TrainingEnvironment.ACADEMIA, Level.BEGINNER, Accessory.entries.toSet()).map { it.name })
        assertEquals(setOf("Flexao de joelhos", "Supino com pausa"), CatalogPool.pool(catalogo, TrainingEnvironment.ACADEMIA, Level.ADVANCED, Accessory.entries.toSet()).map { it.name }.toSet())
    }

    @Test
    fun `valvula de escape do nivelMaximo e por musculo, nunca global`() {
        // CHEST so tem opcao com nivelMaximo BEGINNER (acima do nivel do aluno, seria cortado) ->
        // escapa so pra CHEST. BACK tem opcao com nivelMaximo ADVANCED -> essa fica fora do
        // escape, porque sobrou opcao dentro do teto.
        val chestForaDoTeto = exercicioV3("Flexao avancada", TargetMuscle.CHEST, composto = true, nivel = Level.ADVANCED, nivelMaximo = Level.BEGINNER)
        val backDentro = exercicioV3("Remada maquina", TargetMuscle.BACK, composto = true, nivel = Level.ADVANCED, nivelMaximo = Level.ADVANCED)
        val backForaDoTeto = exercicioV3("Remada curvada barra livre", TargetMuscle.BACK, composto = true, nivel = Level.ADVANCED, nivelMaximo = Level.BEGINNER)
        val catalogo = listOf(chestForaDoTeto, backDentro, backForaDoTeto)

        val resultado = CatalogPool.pool(catalogo, TrainingEnvironment.ACADEMIA, Level.ADVANCED, Accessory.entries.toSet())

        assertTrue(resultado.contains(chestForaDoTeto), "CHEST so tinha essa opcao -> escapa do teto")
        assertTrue(resultado.contains(backDentro), "BACK tem opcao dentro do teto -> fica")
        assertTrue(!resultado.contains(backForaDoTeto), "BACK nao escapa, porque sobrou opcao dentro do teto")
    }

    @Test
    fun `acessorio ausente corta o exercicio que exige`() {
        val precisaDeBanco = exercicioV3("Supino reto banco", TargetMuscle.CHEST, composto = true, acessorios = setOf(Accessory.BENCH))
        val semAcessorio = exercicioV3("Flexao de braco", TargetMuscle.CHEST, composto = true, acessorios = emptySet())
        val catalogo = listOf(precisaDeBanco, semAcessorio)

        assertEquals(listOf("Flexao de braco"), CatalogPool.pool(catalogo, TrainingEnvironment.ACADEMIA, Level.ADVANCED, emptySet()).map { it.name })
        assertEquals(setOf("Supino reto banco", "Flexao de braco"), CatalogPool.pool(catalogo, TrainingEnvironment.ACADEMIA, Level.ADVANCED, setOf(Accessory.BENCH)).map { it.name }.toSet())
    }
}
