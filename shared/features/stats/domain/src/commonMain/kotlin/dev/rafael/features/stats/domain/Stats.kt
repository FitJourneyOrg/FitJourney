package dev.rafael.features.stats.domain

import dev.rafael.contract.stats.UserStatsDto
import kotlinx.coroutines.flow.Flow

/**
 * XP/nível/streak como as TELAS os enxergam. Interface porque a implementação recebe
 * `FitJourneyDatabase` no construtor, e isso tornava qualquer teste de ViewModel dependente de
 * um SQLite real.
 *
 * [REGRA] ARCH #16: o cálculo é do SERVIDOR. Aqui só se lê a última verdade conhecida e se
 * pede uma atualização — não existe operação que altere XP no cliente, e a interface reflete
 * isso: não há setter.
 *
 * Extraído do módulo `app` para `features:stats` (débito P3 "stats+session+achievements extrair
 * do módulo app") — segundo dos três, depois de `achievements`. Sem módulo `presentation`
 * próprio: diferente de achievements, não existe StatsViewModel — quem consome é o ViewModel de
 * cada tela (Home/Menu/Perfil/Progresso), então não há MVI para extrair aqui.
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
