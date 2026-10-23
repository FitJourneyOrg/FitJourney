package dev.rafael.app.data.notificacoes

import dev.rafael.contract.notificacao.NotificacaoDto
import dev.rafael.core.database.FitJourneyDatabase
import dev.rafael.core.database.Notificacao
import dev.rafael.core.network.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Armazenamento local da central de notificações (9.sqm), por trás de [NotificacoesRepositoryImpl].
 *
 * INTERFACE pelo mesmo motivo do `SyncStamps` (B2, 2026-10-07): sem ela, testar a lógica de
 * cache/pendência do repositório exigiria banco real em todo teste do módulo app. A guarda que
 * de fato precisa de SQLite de verdade -- `INSERT OR REPLACE` e o `WHERE readAt IS NULL` --
 * mora em [NotificacaoLocalDataSourceTest], contra o driver JDBC.
 *
 * [REGRA] tudo chaveado por uid (ARCH #30).
 */
interface NotificacaoLocalDataSource {
    /**
     * Substitui o estado local pelo snapshot do servidor.
     *
     * @param marcarTodasComoLidas quando true, reaplica a marcação local IMEDIATAMENTE depois
     * do replace -- é o que evita "desler" quem já tinha marcado como lido localmente e cujo
     * `POST /me/notifications/read` ainda não chegou ao servidor (ver NotificacoesRepositoryImpl).
     * Notificação nunca nasce offline, então o replace não precisa de exceção de "pendente de
     * criar" como `program`/`workout` -- é sempre total.
     */
    suspend fun save(notificacoes: List<NotificacaoDto>, marcarTodasComoLidas: Boolean)

    /** Leitura pontual (caminho cache-first do repositório). Vazio quando não há notificação. */
    suspend fun ler(): List<NotificacaoDto>

    /** Escrita otimista: marca tudo como lido JÁ, antes do outbox confirmar com o servidor. */
    suspend fun marcarTodasComoLidasLocal()
}

class NotificacaoLocalDataSourceImpl(
    db: FitJourneyDatabase,
    private val tokenProvider: TokenProvider,
    private val clock: Clock,
) : NotificacaoLocalDataSource {

    private val q = db.notificacaoQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val dadosSerializer = MapSerializer(String.serializer(), String.serializer())

    private suspend fun uid(): String = tokenProvider.currentUid() ?: ""

    override suspend fun save(notificacoes: List<NotificacaoDto>, marcarTodasComoLidas: Boolean) {
        val dono = uid()
        withContext(Dispatchers.Default) {
            q.transaction {
                q.limparTudo(dono)
                notificacoes.forEach { n ->
                    q.salvar(
                        id = n.id,
                        uid = dono,
                        tipo = n.type,
                        titulo = n.title,
                        corpo = n.body,
                        dados = json.encodeToString(dadosSerializer, n.data),
                        readAt = n.readAt,
                        createdAt = n.createdAt,
                    )
                }
                if (marcarTodasComoLidas) q.marcarTodasComoLidas(clock.now().toString(), dono)
            }
        }
    }

    override suspend fun ler(): List<NotificacaoDto> {
        val dono = uid()
        return withContext(Dispatchers.Default) { q.listarTudo(dono).executeAsList() }.map { montar(it) }
    }

    override suspend fun marcarTodasComoLidasLocal() {
        val dono = uid()
        withContext(Dispatchers.Default) { q.marcarTodasComoLidas(clock.now().toString(), dono) }
    }

    private fun montar(n: Notificacao): NotificacaoDto = NotificacaoDto(
        id = n.id,
        type = n.tipo,
        title = n.titulo,
        body = n.corpo,
        data = runCatching { json.decodeFromString(dadosSerializer, n.dados) }.getOrDefault(emptyMap()),
        readAt = n.readAt,
        createdAt = n.createdAt,
    )
}
