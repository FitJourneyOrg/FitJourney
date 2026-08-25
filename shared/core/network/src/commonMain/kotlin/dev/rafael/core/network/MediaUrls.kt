package dev.rafael.core.network

import io.ktor.http.encodeURLPathPart

/**
 * Monta a URL final da mídia de exercício a partir do ref relativo do catálogo.
 *
 * O ExerciseDto traz refs como "Glúteos/Abdução Lateral do Quadril com Alavanca.png" (sem host).
 * O cliente prefixa a base e ENCODA cada segmento (acento/espaço viram %XX), mantendo as barras —
 * senão o servidor devolve 404. Ex.: "Glúteos/Nome.png" -> ".../Gl%C3%BAteos/Nome%20...png".
 *
 * Dev: aponta pro Ktor (/media). Prod: trocar BASE pela base do CDN — nada mais muda.
 *
 * Ref pode vir VAZIO (catálogo com lacuna de mídia — ex.: alguns CORE). Nesse caso retorna null,
 * pra a UI mostrar placeholder em vez de disparar um GET .../media/ que dá 404.
 */
object MediaUrls {
    /**
     * `val` e não `const`: uma constante compilada capturaria o valor do `BASE_URL` **antes** de o
     * app configurá-lo no boot, e a mídia continuaria apontando para o endereço do emulador.
     * Calculado a cada acesso, acompanha a configuração.
     */
    val BASE: String get() = "${HttpClientFactory.BASE_URL}/media"

    fun url(ref: String): String? =
        if (ref.isBlank()) null
        else BASE + "/" + ref.split("/").joinToString("/") { it.encodeURLPathPart() }
}
