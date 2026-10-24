package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.features.exercise.models.Ambiente
import dev.rafael.server.features.exercise.models.Exercise
import dev.rafael.server.features.exercise.models.Modality

/**
 * Motor v3, pré-filtro do catálogo para a etapa [6] — espelha `tools/motor_sim/motor_v3.py::pool()`
 * termo a termo. **Não é o `ExercisePreFilter` de produção** (esse abre transação e consulta o
 * banco; decisão do Rafael, 2026-10-23, foi não tocar nele). Este é puro, em memória, 100%
 * testável com fakes — a entrada já é um `List<Exercise>` carregado por fora.
 *
 * Wiring em produção (decidir se este objeto substitui o `ExercisePreFilter` ou se os dois
 * convivem) fica para depois da Fatia 9, junto com a troca do `StructureEngine`.
 */
object CatalogPool {

    /** Mesmo conjunto de `spec_v3.py::EQUIPAMENTO_CASA` — o que dá para treinar em casa sem
     *  equipamento de carga de academia. */
    private val EQUIPAMENTO_CASA = setOf("DUMBBELL", "BODYWEIGHT", "BAND", "HAND_GRIP")

    fun pool(
        catalogo: List<Exercise>,
        ambiente: TrainingEnvironment,
        nivel: Level,
        acessorios: Set<Accessory>,
    ): List<Exercise> {
        val idxNivel = Level.entries.indexOf(nivel)
        val ateNivel = Level.entries.take(idxNivel + 1).toSet()
        val ambientesAceitos = when (ambiente) {
            TrainingEnvironment.ACADEMIA -> setOf(Ambiente.ACADEMIA, Ambiente.AMBOS)
            TrainingEnvironment.CASA -> setOf(Ambiente.CASA, Ambiente.AMBOS)
        }

        val viavel = catalogo.filter { e ->
            e.volumeTarget != null &&
                e.modality == Modality.STRENGTH &&
                e.level != null && e.level in ateNivel &&
                e.ambiente != null && e.ambiente in ambientesAceitos &&
                (ambiente == TrainingEnvironment.ACADEMIA || e.equipment in EQUIPAMENTO_CASA) &&
                acessorios.containsAll(e.acessorios)
        }

        // Válvula de escape do nivelMaximo: só por músculo (volumeTarget), nunca global — é
        // melhor dar ao avançado em casa a variação mais fraca do que deixar o alvo sem nada.
        val noTeto = viavel.filter { e -> idxNivel <= Level.entries.indexOf(e.nivelMaximo!!) }
        val alvosNoTeto = noTeto.map { it.volumeTarget }.toSet()
        val alvosQuePerderamTudo = viavel.map { it.volumeTarget }.toSet() - alvosNoTeto
        return noTeto + viavel.filter { it.volumeTarget in alvosQuePerderamTudo }
    }
}
