package dev.rafael.app.push

import android.content.Context

/**
 * Guarda o token que o FCM entregou fora de uma sessão (F.1).
 *
 * O `onNewToken` roda no serviço de mensagens, que não tem usuário autenticado nem acesso ao
 * cliente HTTP com token do Firebase. Persistir e registrar no próximo boot resolve sem
 * arrastar a stack inteira para dentro de um `Service`.
 *
 * **SharedPreferences e não SQLDelight**: é UM valor, sem consulta, sem relação, e precisa
 * sobreviver a um processo que pode morrer logo depois. É exatamente o caso de uso da API mais
 * simples — e o token não é segredo (ele identifica a INSTALAÇÃO, não a conta; o servidor é quem
 * decide de quem ele é).
 */
object TokenPendente {

    private const val ARQUIVO = "fitjourney_push"
    private const val CHAVE = "token_pendente"

    fun guardar(contexto: Context, token: String) {
        prefs(contexto).edit().putString(CHAVE, token).apply()
    }

    fun ler(contexto: Context): String? = prefs(contexto).getString(CHAVE, null)

    /** Chamado depois de registrar com sucesso — o pendente deixou de estar pendente. */
    fun limpar(contexto: Context) {
        prefs(contexto).edit().remove(CHAVE).apply()
    }

    private fun prefs(contexto: Context) =
        contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
}
