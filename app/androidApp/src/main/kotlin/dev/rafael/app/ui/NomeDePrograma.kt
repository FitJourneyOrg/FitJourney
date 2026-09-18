package dev.rafael.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rafael.app.R

/**
 * O nome que a tela mostra para um programa (fatia G.5, ARCH #37).
 *
 * ## Três origens, uma função
 *
 * O campo `name` do `ProgramDto` responde por três situações diferentes, e antes da G.5 cada tela
 * resolvia a sua do seu jeito — `p.name` cru na lista, `?: "Programa"` no detalhe, `name` direto na
 * revelação:
 *
 * | `name` | o que significa | o que a tela mostra |
 * |---|---|---|
 * | preenchido | o usuário renomeou (ARCH #27) | o nome dele, intacto |
 * | vazio | programa gerado, sem nome escolhido | rótulo derivado de frequência + split |
 * | vazio e sem frequência | programa manual, ou ainda carregando | "Programa" |
 *
 * ## Por que derivar, em vez de o servidor mandar pronto
 *
 * Porque ele mandava, e era o defeito: `"Programa 4x — Push/Pull/Legs"` nascia em português, com
 * travessão, e era GRAVADO — nenhuma troca de idioma alcançava dado já escrito no banco. A V48
 * zerou os que a máquina tinha gerado, e programas antigos passaram a ficar certos junto com os
 * novos, que é o que só a derivação consegue dar.
 *
 * > **Estado que é função de outra coisa é DERIVADO, não persistido.**
 *
 * ## `split` NÃO se traduz, e isso é de propósito
 *
 * Ele é chave do motor: o `StructureEngine` grava `split.label` no `ProgramSkeleton` e o `WeekSpread`
 * compara `split == SplitType.FULL_BODY.label`. Além disso "Push/Pull/Legs" é jargão de academia e
 * é o mesmo em qualquer idioma. O `programa_reveal_resumo` já o trata assim, com o motivo escrito
 * no comentário do catálogo.
 *
 * ⚠️ `@Composable` e devolvendo `String`, ao contrário do [Rotulos] e do [TextosDeConquista]: aqui
 * o resultado é uma frase FORMATADA com dois parâmetros, não a escolha de um `@StringRes`. Um id
 * sozinho não carregaria os argumentos, e devolver um par (id + args) empurraria a formatação para
 * quem chama, que é o que esta função existe para centralizar.
 */
@Composable
fun nomeDoPrograma(name: String?, daysPerWeek: Int, split: String): String = when {
    !name.isNullOrBlank() -> name
    daysPerWeek > 0 && split.isNotBlank() ->
        stringResource(R.string.programa_nome_derivado, daysPerWeek, split)
    else -> stringResource(R.string.programa_detalhe_titulo_padrao)
}
