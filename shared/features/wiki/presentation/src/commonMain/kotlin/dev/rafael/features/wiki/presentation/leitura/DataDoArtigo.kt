package dev.rafael.features.wiki.presentation.leitura

/** Dia, mês e ano — números, sem nome de mês e sem ordem. Quem ordena é a FRASE, na tela. */
data class PartesDaData(val dia: Int, val mes: Int, val ano: Int)

/**
 * Quebra o `yyyy-MM-dd` que vem do servidor nos três números.
 *
 * ## Por que não usar uma biblioteca de data
 *
 * Porque não há conta nenhuma a fazer: a data só existe para ser ESCRITA, e escrever data é
 * problema de idioma, não de calendário. O app já resolve isso com placeholder posicional — o
 * `checkin_data_as` põe dia antes do mês em pt-BR e o inverte em en-US, com os mesmos `%1$02d` e
 * `%2$02d`. Uma biblioteca aqui traria fuso, formatação por locale e nenhuma resposta melhor.
 *
 * > **Data que ninguém soma é texto com três números dentro.**
 *
 * `null` quando o formato não bate (dado estranho no cache, versão futura do servidor): a tela
 * omite a data e mostra o resto do artigo. Perder a linha "atualizado em" é irrelevante; não abrir
 * o artigo por causa dela, não.
 */
fun partesDaData(iso: String): PartesDaData? {
    val p = iso.trim().split("-")
    if (p.size != 3) return null
    val ano = p[0].toIntOrNull() ?: return null
    val mes = p[1].toIntOrNull() ?: return null
    val dia = p[2].toIntOrNull() ?: return null
    if (mes !in 1..12 || dia !in 1..31) return null
    return PartesDaData(dia = dia, mes = mes, ano = ano)
}
