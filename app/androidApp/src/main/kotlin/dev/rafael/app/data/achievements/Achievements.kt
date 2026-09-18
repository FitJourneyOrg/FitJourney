package dev.rafael.app.data.achievements

import dev.rafael.contract.stats.AchievementDto
import dev.rafael.core.result.AppError
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
     * Busca no servidor e grava no cache; o Flow re-emite.
     *
     * ⚠️ **Devolve o erro, e isso é uma correção de 2026-09-11.** Antes devolvia `Unit` e o KDoc
     * dizia *"Offline: não faz nada, sem erro"* — apoiado num raciocínio que tinha um buraco:
     * *"a grade vem do cache local, então a rede falhar não tem consequência visível"*. A premissa
     * é **"há cache local"**, e ela é falsa exatamente na primeira execução, que é quando a pessoa
     * mais precisa. Sem cache e sem rede, a tela não ficava vazia: ela AFIRMAVA `0 de 0`.
     *
     * > **Tela que não distingue "não tem" de "não chegou" não fica incompleta: ela mente com**
     * > **confiança.**
     *
     * @param forcar ignora o TTL. Use quando SABE que o progresso mudou (sessão sincronizada).
     * @return `null` quando deu certo — inclusive quando não precisou sincronizar. O erro, quando
     *   a rede ou o servidor falharam.
     */
    suspend fun sincronizar(forcar: Boolean = false): AppError?

    /** Já baixou o catálogo alguma vez nesta conta, neste aparelho? Distingue "não tem" de "não chegou". */
    suspend fun jaSincronizou(): Boolean
}
