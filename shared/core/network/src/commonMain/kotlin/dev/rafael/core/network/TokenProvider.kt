package dev.rafael.core.network

import kotlinx.coroutines.flow.Flow

/** Fornece o ID Token e o uid do usuário atual. Implementado pela feature auth. */
interface TokenProvider {
    suspend fun currentToken(): String?

    /** uid do usuário logado, ou null. Usado p/ chavear o cache de onboarding por usuário. */
    suspend fun currentUid(): String?

    /**
     * O uid AO LONGO DO TEMPO: emite a cada mudança de sessão (login, logout, troca de conta).
     *
     * ## Por que isto existe
     *
     * [REGRA] todo dado local é chaveado por uid. Os repositórios montavam a chave com
     * [currentUid] **uma vez, no início da coleta** — e quem começasse a coletar antes do login
     * ficava preso à chave do uid nulo **para sempre**, porque o `Flow` nunca reinicia sozinho.
     *
     * Foi o defeito do cabeçalho do menu eternamente em "?" (fatia A.0): o conteúdo do drawer
     * entra em composição junto com o `AppNavHost`, antes da tela de login, e aquele ViewModel
     * vive enquanto a Activity viver. `Stats`, `Achievements` e `Groups` tinham o mesmo padrão
     * e escapavam só porque seus ViewModels nascem depois do login — sorte de ciclo de vida,
     * não desenho.
     *
     * Com o uid como `Flow`, a chave passa a ser **consequência da sessão**, e não do instante
     * em que alguém resolveu coletar. É a mesma ideia que já vale para o `SyncStamps`: o
     * isolamento por conta tem de vir da CHAVE, não de um `if` que alguém precisa lembrar.
     */
    fun uidFlow(): Flow<String?>
}