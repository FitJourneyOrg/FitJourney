package dev.rafael.app.idioma

import android.content.Context
import dev.rafael.app.data.me.Me
import dev.rafael.contract.i18n.IdiomaPolicy
import kotlinx.coroutines.flow.first

/**
 * Conta ao servidor em que idioma ESTE aparelho está (fatia G.4, V47, ARCH #37).
 *
 * ## O aparelho manda, e é por isso que só existe uma direção
 *
 * Decisão de 2026-09-11: idioma é preferência **por aparelho** — dá para ter o app em inglês no
 * celular do trabalho e em português no pessoal. Então esta classe só empurra; ela nunca puxa.
 *
 * A alternativa (o servidor mandar) foi recusada com motivo escrito: alguém que trocasse o idioma
 * pelos ajustes do sistema veria o app **desfazer a escolha sozinho** na abertura seguinte, e o
 * KDoc do `Idioma.PADRAO` já registra o princípio — *idioma trocado por uma atualização que ninguém
 * pediu parece defeito, não recurso*.
 *
 * A consequência é aceita e está no handoff: trocar no celular A não troca no B, e o push sai no
 * idioma do último aparelho que abriu o app.
 *
 * ## Por que só escreve quando diverge
 *
 * `users.locale` só serve para montar o push (#36). Reescrever o mesmo valor a cada abertura seria
 * um PATCH por sessão sem nada para mudar — custo sem efeito, e ruído em qualquer log de auditoria
 * que venha a existir.
 *
 * ## Falhar aqui não custa nada
 *
 * Sem rede, o PATCH não sai e o `users.locale` fica com o valor anterior. A próxima abertura
 * reconcilia. Por isso o erro é ignorado de propósito: **não há nada para o usuário fazer a
 * respeito, e nada se perde** — diferente do `renomear`, cuja recusa a pessoa precisa ver.
 */
class ReconciliarIdiomaDaConta(
    private val context: Context,
    private val me: Me,
) : dev.rafael.app.data.sessao.ReagirASessao.ReconciliarIdioma {

    override suspend fun reconciliar() {
        val doAparelho = IdiomaDoAparelho.efetivo(context)

        // O último `/me` conhecido. `null` antes do primeiro sync da vida: aí não há com o que
        // comparar, e escrever seria chutar. A abertura seguinte, já com cache, resolve.
        val cacheado = me.observar().first() ?: return
        val doServidor = IdiomaPolicy.de(cacheado.locale)

        if (doAparelho != doServidor) {
            me.definirIdioma(doAparelho)   // falhar não custa: a próxima abertura tenta de novo
        }
    }
}
