package dev.rafael.features.program.domain.repository

import dev.rafael.core.result.AppResult
import dev.rafael.features.program.domain.model.Program
import dev.rafael.features.program.domain.model.PendenciaDeSync
import dev.rafael.features.program.domain.model.ProgramScheduleEntry
import kotlinx.coroutines.flow.Flow

interface ProgramRepository {
    /**
     * Programas do BANCO LOCAL, reativo (ARCH #30). A tela observa isto e pinta na hora —
     * offline inclusive. Quando o sync grava, o Flow re-emite e a tela se atualiza sozinha.
     * Use junto com `refresh()`, que é quem fala com a rede.
     */
    fun observePrograms(): Flow<List<Program>>

    /**
     * CACHE-FIRST: devolve o cache local se ainda estiver fresco; só vai à rede quando o cache
     * está vazio, vencido (TTL) ou foi invalidado por uma mutação. Trocar de aba não refaz
     * GET /programs a cada vez.
     */
    suspend fun list(): AppResult<List<Program>>                  // GET /programs (condicional)

    /**
     * Já sincronizou programas neste aparelho, com esta conta? Persistido, então sobrevive a
     * fechar o app. A UI precisa disto para não dizer "você não tem programas" a quem apenas
     * não baixou ainda — nem "sem conexão" a quem já baixou e está offline.
     */
    suspend fun jaSincronizou(): Boolean

    /** Força ida à rede (pull-to-refresh / "tentar de novo"), ignorando o cache. */
    suspend fun refresh(): AppResult<List<Program>>

    /**
     * Marca o cache como sujo — a próxima `list()` vai à rede. Chamar depois de qualquer
     * mudança feita FORA desta feature (ex.: criar/editar/excluir treino, virar premium),
     * já que as mutações daqui invalidam sozinhas.
     */
    fun invalidate()

    /**
     * O que ainda NÃO chegou ao servidor (ARCH #30, B.4): ids de programa/treino na fila.
     *
     * Está no contrato de DOMÍNIO, e não vazando o Outbox para a tela, porque a presentation
     * não pode enxergar `core:database` ([REGRA] dependência de mão única). O que a tela
     * precisa saber é "isto aqui já subiu?", que é uma pergunta de domínio — o mecanismo
     * (tabela outbox, WorkManager) continua sendo detalhe da camada de dados.
     */
    fun observarPendentes(): Flow<Set<PendenciaDeSync>>

    suspend fun generate(): AppResult<Program>                     // POST /programs/generate
    suspend fun createManual(name: String): AppResult<Program>     // POST /programs
    suspend fun rename(id: String, name: String): AppResult<Program>  // PUT /programs/{id}
    suspend fun delete(id: String): AppResult<Unit>                    // DELETE /programs/{id}
    suspend fun setSchedule(id: String, schedule: List<ProgramScheduleEntry>): AppResult<Program>  // PUT /programs/{id}/schedule

    /**
     * V59 — marca este treino como o ativo do usuário (ponteiro simples, exclusivo,
     * autoridade do servidor). ONLINE-ONLY, como [generate]: não é conteúdo que o usuário
     * criou, é só um ponteiro que o próprio servidor guarda — não há otimismo sensato aqui,
     * e o outbox existe para proteger CRIAÇÃO offline, não isto.
     */
    suspend fun activateWorkout(workoutId: String): AppResult<Unit>   // POST /workouts/{id}/activate

    /**
     * O usuário reconheceu uma falha PERMANENTE do outbox (ver [PendenciaDeSync.erroPermanente])
     * e quer parar de tentar. Só remove da fila -- LOCAL, nunca falha. O dado volta a bater com
     * o servidor no refresh seguinte (quem chama isto deve seguir com `refresh()`/`list(forcar)`):
     * uma vez fora da fila, o alvo deixa de estar em `alvosPendentes()` e o próximo
     * `local.save()` deixa de protegê-lo -- se o servidor nunca teve esse recurso (criação
     * recusada), ele some localmente; se o servidor tem uma versão diferente (edição recusada),
     * ela sobrescreve a tentativa local.
     */
    suspend fun descartarPendencia(alvoId: String)
}
