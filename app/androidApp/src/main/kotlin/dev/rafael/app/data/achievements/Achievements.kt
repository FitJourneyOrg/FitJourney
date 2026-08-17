package dev.rafael.app.data.achievements

import dev.rafael.contract.stats.AchievementDto
import kotlinx.coroutines.flow.Flow

/**
 * Conquistas como as TELAS as enxergam (ARCH #16). Mesmo desenho do [dev.rafael.app.data.stats.Stats]:
 * interface porque a implementação recebe `FitJourneyDatabase`, e sem ela todo teste de
 * ViewModel dependeria de um SQLite real.
 *
 * [REGRA] Não existe operação de escrita. Quem concede é o servidor; o cliente lê e pede
 * atualização. A ausência de setter é o contrato, não um esquecimento.
 */
interface Achievements {

    /**
     * Catálogo inteiro — desbloqueadas e bloqueadas — do cache local. Nunca falha; lista vazia
     * antes do primeiro sync da vida.
     */
    fun observar(): Flow<List<AchievementDto>>

    /**
     * Busca no servidor e grava no cache; o Flow re-emite. Offline: não faz nada, sem erro.
     *
     * @param forcar ignora o TTL. Use quando SABE que o progresso mudou (sessão sincronizada).
     */
    suspend fun sincronizar(forcar: Boolean = false)
}
