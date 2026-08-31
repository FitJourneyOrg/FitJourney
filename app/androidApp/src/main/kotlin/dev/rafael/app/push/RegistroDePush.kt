package dev.rafael.app.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dev.rafael.app.data.notificacoes.Notificacoes
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Registra e dá baixa no aparelho para push (F.1).
 *
 * ## Nunca falha para o chamador
 *
 * Push é acessório: o app funciona inteiro sem ele, e a central de notificações (V42) mostra
 * tudo que chegou de qualquer forma. Uma falha aqui não pode impedir o login nem o logout.
 */
private const val TAG = "FitJourneyPush"

class RegistroDePush(
    private val contexto: Context,
    private val notificacoes: Notificacoes,
) {

    /**
     * Chamado no login e no boot.
     *
     * Registra o token ATUAL do FCM, e também o que ficou pendente do `onNewToken` — que roda
     * fora de qualquer sessão e por isso não consegue registrar sozinho.
     */
    suspend fun registrar() {
        runCatching {
            val token = tokenAtual()
            // O PREFIXO do token, nunca ele inteiro: é credencial de envio para este aparelho, e
            // logcat é lido por qualquer app com permissão. Doze caracteres bastam para casar com
            // o `LEFT(token, 12)` do banco — que é a única pergunta que este log responde: **qual
            // linha de `device_tokens` é ESTE aparelho**.
            //
            // Nasceu de uma bateria travada: dois aparelhos, dois tokens no banco, e nenhuma forma
            // de saber qual era qual. Sem isso o diagnóstico vira tentativa e erro.
            Log.i(TAG, "Registrando aparelho: ${token?.take(12) ?: "SEM TOKEN DO FCM"}")

            if (token == null) return

            // O RESULTADO é registrado. A primeira versão só chamava e seguia dentro de um
            // `runCatching`: se a requisição falhasse — 401 por token do Ktor ainda não pronto,
            // rede fora, servidor recusando — nada aparecia em lugar nenhum, e o sintoma era o
            // aparelho simplesmente não receber push. **Engolir exceção é decisão de fluxo, não
            // de log.**
            when (val r = notificacoes.registrarDispositivo(token)) {
                is AppResult.Success -> Log.i(TAG, "Aparelho registrado.")
                is AppResult.Failure -> {
                    Log.w(TAG, "Registro do aparelho FALHOU: ${r.error}")
                    return
                }
            }
            // Só limpa o pendente DEPOIS do registro dar certo. Limpar antes perderia o token
            // se a requisição falhasse, e o aparelho ficaria mudo até o FCM reemitir sozinho.
            TokenPendente.limpar(contexto)
        }
    }

    /**
     * Chamado no LOGOUT, **antes** do `signOut`.
     *
     * Apaga o registro DESTE aparelho — não os do usuário. Sair do celular não derruba o push do
     * tablet, que continua logado.
     */
    suspend fun darBaixa() {
        runCatching {
            val token = tokenAtual() ?: return
            Log.i(TAG, "Dando baixa no aparelho: ${token.take(12)}")
            notificacoes.darBaixaNoDispositivo(token)
        }
    }

    /**
     * O token do FCM, que vem por callback.
     *
     * `suspendCancellableCoroutine` com tipo explícito: sem ele o Kotlin não infere o `String?` a
     * partir de um `Task` do Google, e o erro que aparece fala de `Nothing` — foi o que aconteceu
     * com o `Localizador` na fatia B.
     */
    private suspend fun tokenAtual(): String? =
        suspendCancellableCoroutine<String?> { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
}
