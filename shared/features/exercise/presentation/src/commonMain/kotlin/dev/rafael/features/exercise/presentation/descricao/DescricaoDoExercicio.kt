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
 * ## Regex de segurança por FRASE, não por parágrafo (P1.1, 2026-09-22)
 *
 * Media original: `containsMatchIn` no parágrafo inteiro — se a frase de segurança batesse EM
 * QUALQUER PONTO, o parágrafo inteiro sumia. Auditando os 923 `descricao_texto` reais
 * (`gifs_exercicios/catalogo.json`) contra o regex atual: o disclaimer é quase sempre a ÚLTIMA
 * frase de um parágrafo que começa com conteúdo de verdade sobre O EXERCÍCIO ("Esse exercício é
 * ideal para quem busca aumentar a força e a resistência nas pernas... Por isso, recomenda-se
 * sempre seguir a orientação de um profissional..." — caso real, `Agachamento no Landmine`). Não
 * era hipótese: em quase metade dos 923 parágrafos que batem no regex, sobra frase de conteúdo
 * real depois de tirar só a frase do disclaimer. `^`-ancorar não bastaria — o disclaimer raramente
 * abre o parágrafo, ele fecha.
 *
 * A correção divide o parágrafo em frases (limite ingênuo: pontuação de frase + espaço — sem
 * abreviação neste texto para confundir, checado no catálogo inteiro) e filtra só a(s) frase(s)
 * que batem no regex, preservando o resto. Parágrafo que era SÓ disclaimer (a maioria ainda,
 * ~428/923) some por inteiro do mesmo jeito de antes — `restantes` fica vazio.
 *
 * ## O que NÃO mudou
 *
 * O regex em português é o mesmo de sempre, char por char — só passou a operar por FRASE em vez de
 * por PARÁGRAFO.
 */
fun paragrafosDaDescricao(descricao: String, idioma: Idioma): List<ParagrafoDeDescricao> {
    val aviso = regexDeAviso(idioma)
    val seguranca = regexDeSeguranca(idioma)
    return descricao.split("\n\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { semFraseDeSeguranca(it, seguranca) }
        .map { ParagrafoDeDescricao(texto = it, destaque = aviso.containsMatchIn(it)) }
}

/** Fim de frase: pontuação de frase seguida de espaço. Limite ingênuo de propósito — ver KDoc acima. */
private val fimDeFrase = Regex("(?<=[.!?])\\s+")

/**
 * Remove só a(s) FRASE(S) que acionam o regex de segurança, não o parágrafo inteiro.
 *
 * `null` = não sobrou nada (o parágrafo era só disclaimer) — quem chama trata como "sem
 * parágrafo", igual ao `filter` antigo.
 */
private fun semFraseDeSeguranca(paragrafo: String, seguranca: Regex): String? {
    val restantes = paragrafo.split(fimDeFrase)
        .map { it.trim() }
        .filter { it.isNotBlank() && !seguranca.containsMatchIn(it) }
    return restantes.joinToString(" ").ifBlank { null }
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
