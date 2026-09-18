package dev.rafael.server.features.exercise.db

import org.jetbrains.exposed.v1.core.Table

/**
 * Espelha V49__catalogo_traduzido.sql. O nome e a descrição do exercício por idioma (fatia H).
 *
 * ## O pt-BR NÃO mora aqui
 *
 * `exercises.name` continua sendo o piso, como o `Idioma.PADRAO` é o piso do `IdiomaPolicy`. Esta
 * tabela guarda só o que DIVERGE dele. Duas consequências que importam ao ler o código:
 *
 * 1. a leitura em pt-BR **não faz join nenhum** — é o caminho de hoje, intacto;
 * 2. linha ausente não é erro, é o fallback funcionando.
 *
 * ## `locale` é `String`, e não o enum `Idioma`
 *
 * Mesmo precedente do `users.locale` (V47) e do `ErrorResponse`: o que atravessa uma fronteira
 * viaja como texto. Aqui a fronteira é o banco, e um enum na coluna faria idioma novo exigir
 * mudança de tipo em vez de INSERT — que é justamente a vantagem pela qual esta tabela foi
 * escolhida em vez de colunas `name_en`.
 */
object ExerciseTranslationsTable : Table("exercise_translations") {
    val exerciseId = uuid("exercise_id").references(ExercisesTable.id)
    val locale = varchar("locale", 5)
    val name = varchar("name", 200)
    val description = text("description").nullable()

    override val primaryKey = PrimaryKey(exerciseId, locale)
}