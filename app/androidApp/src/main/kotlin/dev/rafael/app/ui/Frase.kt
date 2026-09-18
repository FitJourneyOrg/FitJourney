package dev.rafael.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Um texto para o usuário que pode vir de DOIS lugares (fatia G.3, ARCH #31 e #37).
 *
 * ## Por que um tipo, e não uma `String`
 *
 * Depois da G.3 quase todo texto do app é `@StringRes Int`, resolvido pela tela. A apresentação de
 * erro é a exceção, e não por acidente: **parte das frases não pode virar recurso**.
 *
 * - Código de erro conhecido → a frase é do catálogo do cliente → [Recurso].
 * - Código desconhecido, ou família que usa a `message` (`Forbidden`, `Conflict`, `Validation`) →
 *   a frase chega pronta do servidor, em runtime → [DoServidor].
 *
 * O segundo caso é a lição do #31: **um app antigo falando com um servidor novo continua
 * explicando melhor do que qualquer genérico que a gente escrevesse aqui**. Ele custou três
 * defeitos para ser aprendido e não pode sumir na extração.
 *
 * ## O que este tipo compra
 *
 * Guardar os dois casos como `Int?` + `String?` lado a lado compila igual, e é exatamente o
 * arranjo em que um ramo esquecido não quebra nada — foi assim que o `ErroInline` descartou a
 * frase certa por duas fatias.
 *
 * > **A fronteira "quem escreve a frase" vira TIPO em vez de convenção.**
 *
 * De quebra, quem produz [ErroVisual] continua sendo Kotlin puro: sem `@Composable`, sem
 * `Context`, testável sem Android — a mesma escolha do `Rotulos.kt`.
 */
sealed interface Frase {

    /** Texto do catálogo do cliente. É o caminho normal, e o único que se traduz. */
    data class Recurso(@StringRes val id: Int) : Frase

    /**
     * Texto que chegou pronto do servidor, em português.
     *
     * ⚠️ **Não se traduz, e isso é sabido.** Acontece nos nove códigos de
     * `TextosDeErro.SEM_TEXTO_PROPRIO` (carregam um número que só o servidor conhece) e em código
     * que este app ainda não conhece. Ver a lista lá: débito que ninguém enumera é surpresa.
     */
    data class DoServidor(val texto: String) : Frase
}

/** Resolve fora de composição — dentro de `LaunchedEffect`, num snackbar, num `Worker`. */
fun Frase.resolver(context: Context): String = when (this) {
    is Frase.Recurso -> context.getString(id)
    is Frase.DoServidor -> texto
}

/** Resolve na tela. `stringResource` acompanha troca de idioma e de configuração sozinho. */
@Composable
fun Frase.resolver(): String = when (this) {
    is Frase.Recurso -> stringResource(id)
    is Frase.DoServidor -> texto
}
