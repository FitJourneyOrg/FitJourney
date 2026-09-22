package dev.rafael.features.exercise.presentation.descricao

import dev.rafael.contract.i18n.Idioma

/**
 * Um parágrafo já pronto pra renderizar (fatia H, ARCH #37).
 *
 * [destaque] diz pra UI usar a caixa de nota (NoteBox) em vez de texto comum — é o único sinal que
 * atravessa a fronteira. A UI não sabe (e não precisa saber) que idioma decidiu isso.
 */
data class ParagrafoDeDescricao(val texto: String, val destaque: Boolean)

/**
 * Divide a descrição do exercício em parágrafos prontos pra tela (fatia H, ARCH #37).
 *
 * ## Por que existe — o defeito que isso substitui
 *
 * Antes, `ExerciseDetailScreen` tinha UM par de regex hardcoded em português direto no Composable.
 * Funcionava enquanto só existia um idioma. A descrição (`description`) já chega do servidor
 * corretamente traduzida (V49 + [Idioma]/`IdiomaPolicy`) — mas o regex que decide "este parágrafo é
 * o aviso de segurança repetido, esconde" e "este começa com Atenção/Importante, destaca" só
 * reconhecia palavras em português. Com o locale em EN, os mesmos parágrafos (agora em inglês) não
 * eram reconhecidos: o aviso duplicava (o boilerplate do servidor + a nota fixa) e nada virava nota.
 *
 * ## Por que `when` exaustivo, e não um mapa por idioma
 *
 * Mesmo motivo do `TextosDeAviso`: idioma novo sem os dois padrões quebra o BUILD, não uma tela em
 * produção. Um mapa aceitaria idioma faltando em silêncio.
 *
 * ## O que NÃO mudou
 *
 * O regex em português é o mesmo de sempre, char por char — só mudou de lugar. A imprecisão que já
 * existia (um parágrafo longo que MENCIONA "profissional de educação física" no meio de conteúdo
 * real também é removido inteiro, não só o parágrafo-disclaimer isolado) é comportamento herdado,
 * não introduzido aqui — separado do escopo deste fix.
 */
fun paragrafosDaDescricao(descricao: String, idioma: Idioma): List<ParagrafoDeDescricao> {
    val aviso = regexDeAviso(idioma)
    val seguranca = regexDeSeguranca(idioma)
    return descricao.split("\n\n")
        .map { it.trim() }
        .filter { it.isNotBlank() && !seguranca.containsMatchIn(it) }
        .map { ParagrafoDeDescricao(texto = it, destaque = aviso.containsMatchIn(it)) }
}

/** Prefixo "Aviso:/Atenção:/Importante:" (ou o equivalente no idioma) → vira [ParagrafoDeDescricao.destaque]. */
private fun regexDeAviso(idioma: Idioma): Regex = when (idioma) {
    Idioma.PT_BR -> Regex("^\\s*(aviso|aten[cç][aã]o|importante)\\b", RegexOption.IGNORE_CASE)
    Idioma.EN -> Regex("^\\s*(warning|attention|important|note)\\b", RegexOption.IGNORE_CASE)
}

/**
 * O parágrafo de boilerplate "procure um profissional" que a maioria das descrições repete — some
 * do texto pra não duplicar a nota de segurança fixa que a UI sempre acrescenta por fora.
 */
private fun regexDeSeguranca(idioma: Idioma): Regex = when (idioma) {
    Idioma.PT_BR -> Regex(
        "profissional (de educa[çc][aã]o f[íi]sica|qualificado|de sa[úu]de)|" +
            "orienta[çc][aã]o de um profissional|antes de iniciar qualquer|acompanhamento profissional",
        RegexOption.IGNORE_CASE,
    )
    Idioma.EN -> Regex(
        "fitness professional|before starting any exercise|consult a (fitness )?professional",
        RegexOption.IGNORE_CASE,
    )
}
