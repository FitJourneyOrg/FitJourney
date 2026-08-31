package dev.rafael.server.db

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

/**
 * Uma coluna `JSONB` tratada como STRING no Kotlin.
 *
 * ## O defeito que trouxe isto
 *
 * A F.1 declarou `data` como `text("data")` sobre uma coluna `JSONB`, com um KDoc afirmando que
 * o banco continuaria validando o JSON "sem pagar por uma capacidade que não se usa". A afirmação
 * era falsa, e o Postgres cobrou no primeiro INSERT real:
 *
 * ```
 * column "data" is of type jsonb but expression is of type character varying
 * ```
 *
 * **O Postgres não faz cast implícito de `varchar` para `jsonb`** em parâmetro preparado. Ele
 * aceita o valor só com `::jsonb` explícito ou com um `PGobject` que declare o próprio tipo — que
 * é o que esta classe faz.
 *
 * ## Por que não `exposed-json`
 *
 * O módulo oficial mapeia JSONB para um tipo Kotlin, e exige serializador registrado. Aqui o
 * servidor **nunca lê o conteúdo** deste campo: ele grava o mapa serializado e devolve a string
 * para o cliente. Pagar por desserialização tipada de um dado que só transita seria trocar um
 * problema resolvido por uma dependência a mais.
 *
 * A intenção original do KDoc antigo estava certa — errado era supor que ela já acontecia.
 *
 * ## Por que fica em `server.db` e não na feature
 *
 * Nada aqui é sobre notificação. A próxima coluna JSONB do projeto vai querer o mesmo, e uma
 * cópia em cada feature é como dois lugares passam a divergir.
 */
class JsonbTextColumnType : ColumnType<String>() {

    override fun sqlType(): String = "jsonb"

    /** O driver devolve `PGobject`; o `String` cobre bancos que entreguem texto puro. */
    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value.orEmpty()
        is String -> value
        else -> value.toString()
    }

    /** É ESTE tipo declarado que faz o Postgres aceitar o parâmetro. */
    override fun notNullValueToDB(value: String): Any = PGobject().apply {
        type = "jsonb"
        this.value = value
    }
}

/** Coluna `JSONB` lida e escrita como texto. Ver [JsonbTextColumnType]. */
fun Table.jsonbText(name: String): Column<String> = registerColumn(name, JsonbTextColumnType())
