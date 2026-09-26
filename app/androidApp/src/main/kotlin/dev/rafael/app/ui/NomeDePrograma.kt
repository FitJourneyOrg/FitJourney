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
 * Ele é chave do motor: o `StructureEngine` (servidor) compara e grava `SplitType.name`, e o
 * `WeekSpread`/`NomeDePrograma` comparam `split == SplitType.FULL_BODY` (enum, não mais
 * string). "Push/Pull/Legs" é jargão de academia e é o mesmo em qualquer idioma — os dois
 * catálogos (`enum_split_*`) já trazem o texto idêntico nas duas línguas.
 *
 * ⚠️ **Corrigido pela fatia "rationale derivado" (2026-09-22):** até aqui esta função recebia
 * `split: String` já como RÓTULO pronto (o servidor mandava a label direto, ex. "Full Body") e
 * só interpolava. Isso parou de fazer sentido quando o servidor passou a mandar a CHAVE do
 * enum ("FULL_BODY") — interpolar a chave crua mostraria "FULL_BODY" pro usuário. Agora
 * `split` é a chave (`Program.split`, `String?`) e quem resolve pro rótulo traduzido é
 * [rotuloDoSplit], como `ProgramDetailScreen`/`ProgramRevealScreen` já faziam para o resumo.
 *
 * ⚠️ `@Composable` e devolvendo `String`, ao contrário do [Rotulos] e do [TextosDeConquista]: aqui
 * o resultado é uma frase FORMATADA com dois parâmetros, não a escolha de um `@StringRes`. Um id
 * sozinho não carregaria os argumentos, e devolver um par (id + args) empurraria a formatação para
 * quem chama, que é o que esta função existe para centralizar.
 */
@Composable
fun nomeDoPrograma(name: String?, daysPerWeek: Int, split: String?): String = when {
    !name.isNullOrBlank() -> name
    daysPerWeek > 0 && split != null ->
        // Fallback pra chave crua (em vez de esconder o texto) se o valor não bater com
        // nenhum SplitType conhecido -- mesmo espírito defensivo do `rotuloDoSplit`.
        stringResource(R.string.programa_nome_derivado, daysPerWeek, rotuloDoSplit(split) ?: split)
    else -> stringResource(R.string.programa_detalhe_titulo_padrao)
}
