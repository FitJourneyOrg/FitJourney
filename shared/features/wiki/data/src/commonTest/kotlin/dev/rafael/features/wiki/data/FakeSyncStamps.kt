package dev.rafael.features.wiki.data

import dev.rafael.core.database.SyncStamps

/**
 * Fake em memória do [SyncStamps], escrito à mão (sem biblioteca de mock, convenção C2).
 *
 * Existe porque o `SyncStamps` virou interface (B2): antes era classe concreta e exigia o banco,
 * então nenhum repositório do cliente era construível num teste de unidade.
 *
 * O relógio é um campo ([agora]), não `Clock.System`: o teste move o tempo à mão, e é assim que
 * se prova "carimbo vencido" sem esperar 24 horas.
 *
 * Não há módulo de teste compartilhado entre features, então cada módulo copia este arquivo
 * (mesmo julgamento do `precisaRebaixar` duplicado: feature nunca depende de feature).
 */
class FakeSyncStamps(var agora: Long = 1_000_000L) : SyncStamps {

    private val carimbos = mutableMapOf<String, Long>()

    /** Tudo o que foi carimbado via [marcar], na ordem. */
    val marcados = mutableListOf<Pair<String, SyncStamps.Escopo>>()

    private fun k(chave: String, escopo: SyncStamps.Escopo) = "$escopo:$chave"

    override suspend fun fresco(chave: String, ttlMs: Long, escopo: SyncStamps.Escopo): Boolean {
        val quando = carimbos[k(chave, escopo)] ?: return false
        return agora - quando < ttlMs
    }

    override suspend fun jaSincronizou(chave: String, escopo: SyncStamps.Escopo): Boolean =
        k(chave, escopo) in carimbos

    override suspend fun marcar(chave: String, escopo: SyncStamps.Escopo) {
        carimbos[k(chave, escopo)] = agora
        marcados += chave to escopo
    }

    override suspend fun invalidar(chave: String, escopo: SyncStamps.Escopo) {
        carimbos.remove(k(chave, escopo))
    }
}
