package dev.rafael.server.avisos

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.group.services.AvisosDiarios
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** O mínimo que o laço precisa saber de um grupo. Sem `Group` inteiro: ele não desenha tela. */
data class GrupoParaAvisar(
    val id: Uuid,
    val titulo: String,
    val fuso: TimeZone,
    /**
     * Derivado das datas, como todo estado de grupo (#33) — nunca uma coluna.
     *
     * Vem resolvido do repositório porque a consulta já tem `end_date` na mão; recalcular no laço
     * exigiria carregar as datas só para isso.
     */
    val encerrado: Boolean,
    /**
     * Quem **criou** o desafio. `null` só se a linha estiver órfã.
     *
     * Serve a duas coisas, e as duas nasceram da bateria: não contar o criador como entrada
     * (*criar não é entrar*) e escrever *"no **seu** desafio"* só para ele. As duas perguntas são
     * sobre origem, não sobre cargo — o admin é transferível (2.12), o fundador não.
     */
    val criadorId: Uuid?,
)

/** A fila de um grupo, resumida. `maisAntigoEm` é o que decide o lembrete. */
data class FilaResumida(val casos: Int, val maisAntigoEm: Instant)

/**
 * Acesso a dados do laço diário (fatia F).
 *
 * **Atravessa duas features** — `group_members` e `group_reports` — e é por isso que ele mora aqui
 * e não dentro de nenhuma delas. A interface existe para o [AvisosDoDia] ser testável sem Postgres:
 * o que precisa de teste é a **decisão** de quando avisar, não o SQL.
 */
interface AvisosDiariosRepository {

    /**
     * Grupos que ainda importam: `AGENDADO` ou `ATIVO`, mais os que **encerraram** e ainda têm
     * caso aberto.
     *
     * Não é "todos os grupos". Um desafio encerrado há seis meses e sem denúncia pendente não tem
     * nada a receber, e varrê-lo todo dia seria pagar por uma pergunta cuja resposta nunca muda.
     */
    suspend fun gruposVivos(): AppResult<List<GrupoParaAvisar>>

    /**
     * Quantas pessoas entraram neste grupo no dia civil dele.
     *
     * O [fuso] entra porque `joined_at` é um instante UTC e [dia] é uma data no calendário do
     * grupo: a conversão de "dia 7 em São Paulo" para o intervalo UTC correspondente acontece na
     * consulta. Comparar `joined_at::date` direto contaria o dia do servidor.
     */
    suspend fun entradasNoDia(
        groupId: Uuid,
        dia: LocalDate,
        fuso: TimeZone,
        /** Não conta. É o criador — **criar não é entrar** (achado na bateria da F). */
        exceto: Uuid?,
    ): AppResult<Int>

    /** Todos os membros, para o aviso agregado. O teto de 50 é o teto da lista. */
    suspend fun membros(groupId: Uuid): AppResult<List<Uuid>>

    /** Só os admins — o lembrete de fila é deles (6.2). */
    suspend fun admins(groupId: Uuid): AppResult<List<Uuid>>

    /**
     * Casos abertos e a idade do mais antigo. `null` quando não há nenhum.
     *
     * Conta **casos** (alvos distintos), não denúncias — a mesma aritmética do badge (6.11): cinco
     * pessoas denunciando o mesmo check-in são um item para o admin abrir.
     */
    suspend fun filaAberta(groupId: Uuid): AppResult<FilaResumida>

    /**
     * Registra que o aviso foi mandado. Devolve `false` se **já tinha sido** hoje.
     *
     * Quem decide é a PK composta `(group_id, day, kind)` da V46, não um `SELECT` antes — mesma
     * escolha do check-in do dia (V38) e da denúncia (V45). Com duas instâncias do servidor, só
     * uma grava e só ela envia.
     */
    suspend fun registrarAviso(
        groupId: Uuid,
        dia: LocalDate,
        tipo: AvisosDiarios.Tipo,
        quando: LocalDateTime,
    ): AppResult<Boolean>

    /**
     * Fecha os casos abertos de um desafio encerrado, gravando `ENCERRADO_SEM_JULGAMENTO` na
     * auditoria com `admin_id` nulo — **não foi decisão de ninguém**.
     *
     * Devolve quantos foram fechados.
     */
    suspend fun encerrarCasosPendentes(groupId: Uuid, quando: LocalDateTime): AppResult<Int>
}
