package dev.rafael.features.session.domain

import dev.rafael.contract.session.WorkoutSessionDto
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Sessão como a UI a enxerga: o dado + se ainda está esperando o servidor. */
data class SessaoLocal(
    val dto: WorkoutSessionDto,
    val pendente: Boolean,
)

/**
 * Histórico de sessões como as TELAS o enxergam.
 *
 * POR QUE existe: a implementação concreta recebe `FitJourneyDatabase` no construtor. Testar um
 * ViewModel que dependa dela exigiria um SQLite de verdade — o que na prática significou zero
 * testes para a Home, a tela mais importante do app. Três defeitos saíram dela num único dia (o
 * `locked` rebaixado, o `/me/stats` sem TTL, o 403 do dia trancado) e nenhum teria sido pego por
 * teste, porque não havia como escrever um.
 *
 * A interface é o corte mínimo para isso: o ViewModel passa a depender de comportamento, não
 * de persistência, e o fake do teste é uma classe de 10 linhas.
 *
 * Deliberadamente NÃO é um "repositório de domínio" no sentido de regra de negócio: estas
 * operações são encanamento de sync (fila, flush, download). Mesmo assim, o corte de módulo é
 * este — extraído para `features:session` (débito P3 "stats+session+achievements extrair do
 * módulo app"), terceiro e último dos três, junto com `achievements` e `stats`.
 */
interface HistoricoDeSessoes {

    /** Histórico local do usuário atual (sincronizado + pendente). Nunca falha, nunca espera rede. */
    fun observarHistorico(): Flow<List<SessaoLocal>>

    /** Grava a sessão executada (aparece na hora, como pendente) e tenta enviar. */
    suspend fun record(dto: WorkoutSessionDto)

    /** Envia as pendentes. Confirmada = marcador cai, a linha continua no histórico. */
    suspend fun flush()

    /** Puxa o histórico do servidor para o banco local. Falha em silêncio. */
    suspend fun sincronizarHistorico(forcar: Boolean = false): AppResult<Unit>
}
