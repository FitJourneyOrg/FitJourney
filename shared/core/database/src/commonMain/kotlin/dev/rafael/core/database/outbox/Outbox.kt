package dev.rafael.core.database.outbox

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.rafael.core.database.FitJourneyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Uma operação da fila como a UI a enxerga (inclui o que já falhou em definitivo). */
data class ItemDaFila(
    val seq: Long,
    val tipo: TipoOperacao,
    val alvoId: String,
    val tentativas: Int,
    /** Preenchido = desistimos. A UI mostra isto ao usuário e oferece descartar. */
    val erroPermanente: String? = null,
)

/**
 * FILA DE ESCRITAS PENDENTES (ARCH #30, fatia B.2).
 *
 * A escrita vai para cá ANTES de ir à rede — inclusive online. Um caminho só: a alternativa
 * ("se tem rede manda direto, senão enfileira") dá dois comportamentos, dois conjuntos de bug,
 * e o caminho offline só é exercitado quando algo dá errado, ou seja, mal testado.
 *
 * Divisão de responsabilidade:
 *  - **Outbox** (aqui) = o registro do que falta enviar. Está no disco: sobrevive a fechar o
 *    app, matar o processo e reiniciar o aparelho.
 *  - **WorkManager** = o despertador que acorda o processo quando a rede volta.
 *
 * Os dois são redundantes de propósito. Se o WorkManager atrasar (Doze, fabricante agressivo),
 * a fila continua lá e é esvaziada na próxima abertura do app.
 *
 * [REGRA] Tudo chaveado por uid — operação de um usuário nunca sobe com o token de outro.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Outbox(
    db: FitJourneyDatabase,
    private val uidAtual: suspend () -> String?,
) : FilaDeSaida {
    private val q = db.outboxQueries

    private suspend fun uid(): String = uidAtual() ?: ""

    /**
     * Registra a intenção. `payload` é o JSON do DTO (vazio em exclusão).
     *
     * NÃO compacta na hora: a fila guarda o histórico bruto do que o usuário fez, e a
     * compactação acontece na leitura. Assim `tentativas` e `erroPermanente` continuam
     * apontando para linhas reais, e um bug no compactador nunca destrói intenção gravada.
     */
    suspend fun enfileirar(tipo: TipoOperacao, alvoId: String, payload: String = "") {
        val dono = uid()
        if (dono.isEmpty()) return   // sem usuário não se enfileira nada de ninguém
        withContext(Dispatchers.Default) {
            q.enfileirar(
                uid = dono,
                tipo = tipo.name,
                alvoId = alvoId,
                payload = payload,
                criadoEm = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    /**
     * O que enviar agora, já COMPACTADO e na ordem certa.
     *
     * Exclui o que falhou em definitivo: retry não resolve 403/400/404, e insistir só gasta
     * bateria enquanto o usuário vê "não sincroniza" sem entender por quê.
     */
    override suspend fun paraEnviar(): List<Operacao> {
        val dono = uid()
        if (dono.isEmpty()) return emptyList()
        val cru = withContext(Dispatchers.Default) {
            q.pendentes(dono).executeAsList().map {
                Operacao(
                    seq = it.seq,
                    tipo = TipoOperacao.valueOf(it.tipo),
                    alvoId = it.alvoId,
                    payload = it.payload,
                )
            }
        }
        // Tira da fila o que a compactação anulou (criar + excluir offline). Estes alvos não
        // passam pelo processador — logo, nunca seriam concluídos — e ficariam na tabela para
        // sempre, mantendo `contarPendentes()` acima de zero eternamente.
        CompactadorDeOutbox.alvosAnulados(cru).forEach { concluir(it) }
        return CompactadorDeOutbox.compactar(cru)
    }

    /** A fila inteira, reativa — a tela marca "pendente" e mostra as falhas a partir daqui. */
    fun observar(): Flow<List<ItemDaFila>> =
        flow { emit(uid()) }.flatMapLatest { dono ->
            q.observarFila(dono).asFlow().mapToList(Dispatchers.Default).map { linhas ->
                linhas.map {
                    ItemDaFila(
                        seq = it.seq,
                        tipo = TipoOperacao.valueOf(it.tipo),
                        alvoId = it.alvoId,
                        tentativas = it.tentativas.toInt(),
                        erroPermanente = it.erroPermanente,
                    )
                }
            }
        }

    /**
     * Ids com operação pendente. O sync usa para NÃO apagar o que ainda não subiu.
     *
     * Sem isto o `save()` do programa (que faz limpar + regravar tudo que veio do servidor)
     * apagaria o treino criado offline antes de ele chegar lá — e o usuário veria o treino
     * sumir sozinho, sem erro nenhum. É a interação mais silenciosa entre outbox e sync.
     */
    suspend fun alvosPendentes(): Set<String> {
        val dono = uid()
        if (dono.isEmpty()) return emptySet()
        return withContext(Dispatchers.Default) { q.alvosPendentes(dono).executeAsList().toSet() }
    }

    suspend fun contarPendentes(): Long {
        val dono = uid()
        if (dono.isEmpty()) return 0
        return withContext(Dispatchers.Default) { q.contarPendentes(dono).executeAsOne() }
    }

    /**
     * Enviou com sucesso. Remove TODAS as operações do alvo, não só a compactada — as outras
     * foram fundidas nela e já estão representadas no que subiu.
     */
    override suspend fun concluir(alvoId: String) {
        val dono = uid()
        withContext(Dispatchers.Default) { q.removerAlvo(dono, alvoId) }
    }

    /** Falha temporária (sem rede, 5xx): fica na fila e conta a tentativa. */
    override suspend fun registrarTentativa(seq: Long) {
        withContext(Dispatchers.Default) { q.registrarTentativa(seq) }
    }

    /**
     * Falha PERMANENTE (403/400/404). Para de tentar e guarda a mensagem para a UI.
     *
     * Marca todas as operações do alvo: se a criação foi recusada, as edições seguintes também
     * não têm o que fazer — insistir nelas geraria uma segunda mensagem de erro sobre um
     * recurso que nunca existiu.
     */
    override suspend fun marcarFalhaPermanente(alvoId: String, mensagem: String) {
        val dono = uid()
        withContext(Dispatchers.Default) {
            q.transaction {
                q.observarFila(dono).executeAsList()
                    .filter { it.alvoId == alvoId }
                    .forEach { q.marcarFalhaPermanente(mensagem, it.seq) }
            }
        }
    }

    /** O usuário reconheceu a falha: some da fila e o dado local volta ao que o servidor tem. */
    suspend fun descartar(alvoId: String) = concluir(alvoId)

    /** Logout: a fila é do usuário, não do aparelho. */
    suspend fun limpar() {
        val dono = uid()
        withContext(Dispatchers.Default) { q.limparDoUsuario(dono) }
    }
}
