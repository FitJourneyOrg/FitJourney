package dev.rafael.app.data.stats

import dev.rafael.contract.stats.UserStatsDto
import kotlinx.coroutines.flow.Flow

/**
 * XP/nível/streak como as TELAS os enxergam. Ver [HistoricoDeSessoes] para o porquê da
 * interface: [StatsRepository] recebe `FitJourneyDatabase` no construtor, e isso tornava
 * qualquer teste de ViewModel dependente de um SQLite real.
 *
 * [REGRA] ARCH #16: o cálculo é do SERVIDOR. Aqui só se lê a última verdade conhecida e se
 * pede uma atualização — não existe operação que altere XP no cliente, e a interface reflete
 * isso: não há setter.
 */
interface Stats {

    /** Último XP/nível/streak conhecido (cache local). Nunca falha; null antes do 1º sync. */
    fun observar(): Flow<UserStatsDto?>

    /**
     * Busca no servidor e grava no cache; o Flow re-emite. Offline: não faz nada, sem erro.
     *
     * @param forcar ignora o TTL. Use quando você SABE que mudou (pendência sincronizada).
     */
    suspend fun sincronizar(forcar: Boolean = false)
}
