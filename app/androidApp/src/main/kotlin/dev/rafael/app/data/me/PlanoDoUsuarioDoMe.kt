package dev.rafael.app.data.me

import dev.rafael.features.program.domain.repository.PlanoDoUsuario
import kotlinx.coroutines.flow.first

/**
 * O plano para a feature de programas, lido do `/me` em cache ([Me]).
 *
 * `null` quando o `/me` nunca foi sincronizado: quem pergunta não bloqueia nada e deixa o
 * servidor decidir. Com [atualizar], tenta o `/me` antes de responder; offline não falha.
 */
class PlanoDoUsuarioDoMe(private val me: Me) : PlanoDoUsuario {
    override suspend fun ehPremium(atualizar: Boolean): Boolean? {
        if (atualizar) me.sincronizar(forcar = true)
        return me.observar().first()?.isPremium
    }
}
