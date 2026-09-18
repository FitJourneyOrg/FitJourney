package dev.rafael.server.features.exercise.db

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.server.features.exercise.models.Exercise
import kotlin.uuid.Uuid

/**
 * O catálogo, read-only.
 *
 * ## Por que o `idioma` entra em três métodos e NÃO tem valor padrão (fatia H)
 *
 * Um default `= Idioma.PADRAO` deixaria toda chamada antiga compilando, e é exatamente por isso
 * que ele não existe: o caminho errado seria o silencioso. Quem lê o catálogo para MOSTRAR precisa
 * declarar em que idioma, e quem lê para decidir (o motor) não passa por aqui — usa a
 * `ExercisesTable` direto, no `ExercisePreFilter`.
 *
 * > **Parâmetro de idioma com valor padrão transforma "esqueci de traduzir" em código que compila.**
 */
interface ExerciseRepository {
    suspend fun findAll(idioma: Idioma): AppResult<List<Exercise>>
    suspend fun findByCategory(category: ExerciseCategory, idioma: Idioma): AppResult<List<Exercise>>
    suspend fun findById(id: Uuid, idioma: Idioma): AppResult<Exercise?>

    suspend fun existsByIds(ids: List<Uuid>): AppResult<Boolean>

    /**
     * Só os nomes, para uma lista de ids que quem chama JÁ tem em mãos.
     *
     * Existe por causa do `alternatives`: ele monta o resultado a partir do pool do motor, que lê
     * a tabela crua e não conhece idioma. Traduzir ali dentro levaria o idioma para o
     * `ExercisePreFilter`, que decide por taxonomia e não deveria saber que idiomas existem.
     *
     * Então a tradução entra na BORDA: o serviço filtra, sobra um punhado de ids, e só eles são
     * traduzidos. Uma consulta a mais sobre poucas linhas, em troca de o motor continuar surdo
     * para idioma.
     *
     * > **Tradução é assunto da borda de apresentação; motor que a conhece vira motor bilíngue
     * > sem precisar.**
     *
     * Devolve só o que EXISTE: id sem tradução fica fora do mapa, e quem chama cai no piso.
     */
    suspend fun nomesTraduzidos(ids: List<Uuid>, idioma: Idioma): AppResult<Map<Uuid, String>>
}
