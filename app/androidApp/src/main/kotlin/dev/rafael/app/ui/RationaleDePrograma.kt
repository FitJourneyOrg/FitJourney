package dev.rafael.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.rafael.app.R
import dev.rafael.contract.profile.MuscleGroup
import dev.rafael.contract.profile.SplitType

/**
 * A frase que explica o programa gerado -- split + dias, e o foco quando presente. Fatia
 * "rationale derivado" (2026-09-22).
 *
 * ## De onde isto saiu
 *
 * Até esta fatia o `StructureEngine` (servidor) montava a frase pronta EM PORTUGUÊS e o
 * cliente só exibia via `Frase.DoServidor` (texto de servidor não traduzido -- contável no
 * `TextoDoServidorTest`). O servidor não sabe em que idioma a tela está ([REGRA] mesma razão
 * da G.2/G.3): a frase nasceria sempre errada assim que o inglês entrasse. E tinha um bug
 * junto -- `focus.joinToString(", ") { it.name }` mostrava o NOME CRU do enum ("CHEST",
 * "BACK"), não o rótulo traduzido.
 *
 * Agora o servidor só persiste dado estruturado (`split`, `focusMuscles` -- o snapshot do
 * foco NO MOMENTO da geração) e quem monta a frase é o cliente, no idioma da tela,
 * reaproveitando `SplitType.rotulo()/.descricao()` e `MuscleGroup.rotulo()` -- que já
 * existiam desde a fatia G.3 e só não estavam ligados a este dado.
 *
 * ## Por que uma função `@Composable` devolvendo `String`, e não `@StringRes`
 *
 * Mesmo caso do [nomeDoPrograma]: o resultado é uma frase com parâmetros variáveis (dias,
 * split, lista de músculos), não a escolha de um recurso único -- não dá pra devolver um
 * `Int` sozinho.
 *
 * `split == null` é programa MANUAL (shell sem motor) -- devolve string vazia, e quem chama
 * decide não mostrar nada, igual ao `rationale` vazio de antes.
 */
@Composable
fun rationaleDoPrograma(split: String?, daysPerWeek: Int, focusMuscles: List<String>): String {
    val splitEnum = split?.let { runCatching { SplitType.valueOf(it) }.getOrNull() } ?: return ""
    val base = stringResource(
        R.string.programa_rationale_base,
        daysPerWeek,
        stringResource(splitEnum.rotulo()),
        stringResource(splitEnum.descricao()),
    )
    val grupos = focusMuscles.mapNotNull { chave -> runCatching { MuscleGroup.valueOf(chave) }.getOrNull() }
    if (grupos.isEmpty()) return base
    // `joinToString(transform = ...)` NÃO é inline (o parâmetro é nullable) -- uma chamada
    // @Composable dentro da lambda quebra o compilador. `map` é inline; resolve a lista de
    // rótulos primeiro e só depois junta com vírgula.
    val foco = grupos.map { stringResource(it.rotulo()) }.joinToString(", ")
    return base + " " + stringResource(R.string.programa_rationale_foco, foco)
}
