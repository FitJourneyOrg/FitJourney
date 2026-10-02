package dev.rafael.features.exercise.domain.model

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.profile.MuscleGroup

/**
 * Tudo que recorta o catálogo, num objeto só.
 *
 * ## Por que existe
 *
 * `observeExercises` recebia dois parâmetros posicionais (`category`, `muscleGroup`). Cada eixo
 * novo acrescentaria mais um, e acrescentaria **em quatro lugares**: a interface, a implementação,
 * o ViewModel e os dois dublês de teste. Com um objeto, eixo novo é um campo aqui e uma linha no
 * filtro — e nenhuma assinatura muda.
 *
 * > **Parâmetro posicional é assinatura; campo é dado. Eixo de filtro é dado.**
 *
 * ## Por que os campos são valor único e não conjunto
 *
 * Eu cheguei a propor `Set` para cada eixo, para permitir "halter **ou** barra" na folha de
 * filtros. A folha foi adiada para a V2 junto com o equipamento, e o que existe hoje é chip de
 * escolha única.
 *
 * Um `Set` agora seria **mentira**: a categoria é filtrada em SQL por `observeByCategory`, que
 * recebe UM valor. Um conjunto de dois elementos passaria pela compilação e filtraria errado, em
 * silêncio. Quando a folha chegar, o `Set` vem junto com o `IN (...)` que o honra.
 */
data class FiltroDeExercicios(
    /** Texto digitado pelo usuário. Em branco = sem busca. */
    val busca: String = "",
    /** `null` = todas. */
    val categoria: ExerciseCategory? = null,
    /** `null` = todos. Coexiste com [categoria] — são eixos diferentes (estilo e anatomia). */
    val musculo: MuscleGroup? = null,
) {
    /** Nada recortado: a tela está mostrando o acervo inteiro. */
    val vazio: Boolean get() = busca.isBlank() && categoria == null && musculo == null
}
