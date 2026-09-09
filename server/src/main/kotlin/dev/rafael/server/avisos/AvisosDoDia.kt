package dev.rafael.server.avisos

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.group.services.AvisosDiarios
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/** O que a passada fez. Serve para o log e para os testes afirmarem sem espiar o banco. */
data class ResultadoDosAvisos(
    val entradasAvisadas: Int = 0,
    val filasLembradas: Int = 0,
    val casosEncerrados: Int = 0,
) {
    val fezAlgo: Boolean get() = entradasAvisadas > 0 || filasLembradas > 0 || casosEncerrados > 0
}

/**
 * O laço diário do grupo (fatia F).
 *
 * ## Por que mora FORA de `features/`
 *
 * Ele lê `group_members` (feature `group`) **e** `group_reports` (feature `checkin`), e escreve
 * notificação (feature `notificacao`). Posto em qualquer uma das três, seria feature dependendo de
 * feature — a regra que o `UserCodePolicy` já custou caro no #35.
 *
 * É o mesmo lugar e o mesmo motivo do [dev.rafael.server.media.PurgaDeMidia]: um varredor não é
 * uma feature, é composição. Ele conhece as três; nenhuma delas o conhece.
 *
 * ## Três trabalhos, e por que num laço só
 *
 * Ao contrário das duas purgas — que ficaram em laços separados porque **falham por razões
 * diferentes** —, estes três respondem à mesma pergunta: *"o que precisa ser dito sobre este grupo
 * hoje?"*. Partilham a varredura de grupos, o dia civil e a tabela de controle. Separá-los seria
 * varrer a mesma lista três vezes por dia para escrever na mesma tabela.
 *
 * | Trabalho | Para quem | Decisão |
 * |---|---|---|
 * | Entradas do dia, agregadas | todos os membros | 10.6 |
 * | Fila parada há 3+ dias | só o admin | emenda de 2026-09-07 |
 * | Fechar denúncia de desafio encerrado | ninguém — é só registro | emenda de 2026-09-07 |
 *
 * ## O que ele NÃO faz: expirar denúncia por tempo
 *
 * Proposto e **recusado** em 2026-09-07. Se a denúncia sumisse sozinha em 24h, a inação do admin
 * viraria absolvição — bastaria não abrir o app, e num grupo de conhecidos o admin pode ser amigo
 * de quem fraudou. A 6.12 já resolve isso pelo outro lado: ele não tem prazo **porque** o
 * `EM_ANALISE` continua contando ponto (6.8), então o caso parado não prejudica ninguém.
 *
 * O lembrete existe justamente por isso: o remédio para fila parada é **fazer o admin ver**, não
 * apagar o que ele não viu.
 */
class AvisosDoDia(
    private val repository: AvisosDiariosRepository,
    private val avisar: EnviarAviso,
    private val clock: Clock = Clock.System,
) {

    /**
     * Porta estreita para o push. Este varredor não conhece `Aviso` nem `NotificacaoService`.
     *
     * Três verbos e nada de objeto de domínio — o mesmo desenho do `AvisarPedido` (#36) e do
     * `AvisarComentario` (E.1).
     */
    interface EnviarAviso {
        suspend fun entradasDoDia(
            destinatario: Uuid,
            grupo: String,
            groupId: Uuid,
            quantas: Int,
            /** "no **seu** desafio" só para quem o criou (emenda de 2026-09-08). */
            souOCriador: Boolean,
        )
        suspend fun filaParada(admin: Uuid, grupo: String, groupId: Uuid, casos: Int, dias: Int)
    }

    suspend fun rodar(): ResultadoDosAvisos {
        val grupos = (repository.gruposVivos() as? AppResult.Success)?.value
            ?: return ResultadoDosAvisos()

        var entradas = 0
        var filas = 0
        var encerrados = 0

        for (g in grupos) {
            // O dia é o do GRUPO, não o do servidor: um desafio em Tóquio vira o dia nove horas
            // antes de um em São Paulo, e "entraram hoje" tem de seguir o calendário daquele
            // desafio. Mesma regra do `local_date` do check-in (4.6).
            val hoje = AvisosDiarios.diaDoGrupo(clock.now(), g.fuso)

            if (g.encerrado) {
                encerrados += fecharCasosDoEncerrado(g)
                // Desafio encerrado não recebe aviso de entrada (ninguém entra) nem lembrete de
                // fila (não há o que julgar). Sai daqui.
                continue
            }

            if (avisarEntradas(g, hoje)) entradas++
            if (lembrarFila(g, hoje)) filas++
        }

        return ResultadoDosAvisos(entradas, filas, encerrados)
    }

    /**
     * 10.6 — "3 pessoas entraram no seu desafio hoje", para **todos os membros**.
     *
     * Uma por entrada teria a aritmética que matou a votação: as entradas acontecem em rajada na
     * janela `AGENDADO`, e num desafio de 50 pessoas o primeiro a entrar receberia 49 avisos —
     * 1.225 no total, todos dizendo a mesma coisa. Agregado, o teto é **um por pessoa por dia,
     * independentemente do tamanho do grupo**.
     */
    private suspend fun avisarEntradas(g: GrupoParaAvisar, hoje: LocalDate): Boolean {
        // O criador não conta como entrada — **criar não é entrar**. Ele vira membro junto com o
        // grupo, e contar essa linha fazia quem acabou de fundar um desafio receber "1 pessoa
        // entrou no seu desafio hoje" sobre si mesmo (achado na bateria).
        val quantas = (repository.entradasNoDia(g.id, hoje, g.fuso, exceto = g.criadorId) as? AppResult.Success)
            ?.value ?: return false
        AvisosDiarios.entradasParaAvisar(quantas) ?: return false

        // O registro vem ANTES do envio, e a PK composta decide. Se duas instâncias rodarem o laço
        // ao mesmo tempo, só uma grava — a outra recebe `false` e não manda nada. Enviar primeiro
        // e registrar depois deixaria a janela clássica para o aviso duplicado.
        if (!marcou(g.id, hoje, AvisosDiarios.Tipo.ENTRADAS_DO_DIA)) return false

        val membros = (repository.membros(g.id) as? AppResult.Success)?.value ?: return false
        // Todos os membros (10.6) — inclusive quem entrou hoje: "3 pessoas entraram" diz a ele que
        // veio acompanhado, e é informação que ele não tem.
        //
        // O TEXTO muda conforme quem lê: "no **seu** desafio" só para quem o criou. Para os outros
        // 49, o possessivo seria falso — eles participam, não são donos.
        membros.forEach {
            avisar.entradasDoDia(it, g.titulo, g.id, quantas, souOCriador = it == g.criadorId)
        }
        return true
    }

    /**
     * Emenda de 2026-09-07 — a fila esperando há [AvisosDiarios.DIAS_ATE_LEMBRAR] dias ou mais.
     *
     * Conta do caso **mais antigo**: uma denúncia nova não pode zerar o cronômetro de outra que já
     * espera. Fosse pelo mais recente, o grupo com denúncias frequentes nunca geraria lembrete —
     * exatamente o que mais precisa.
     *
     * Repete a cada 3 dias enquanto houver caso aberto, porque a PK é `(grupo, dia, tipo)` e o
     * `dias` volta a cruzar o limiar. Um admin que ignora recebe lembrete de novo; um que julga
     * para de receber.
     */
    private suspend fun lembrarFila(g: GrupoParaAvisar, hoje: LocalDate): Boolean {
        val fila = (repository.filaAberta(g.id) as? AppResult.Success)?.value ?: return false
        if (fila.casos == 0) return false

        val dias = ((clock.now() - fila.maisAntigoEm) / 1.days).toInt()
        if (!AvisosDiarios.filaEstaParada(dias)) return false
        if (!marcou(g.id, hoje, AvisosDiarios.Tipo.FILA_PARADA)) return false

        val admins = (repository.admins(g.id) as? AppResult.Success)?.value ?: return false
        admins.forEach { avisar.filaParada(it, g.titulo, g.id, fila.casos, dias) }
        return true
    }

    /**
     * Denúncia num desafio encerrado fecha sozinha, com registro próprio na auditoria.
     *
     * **Aqui resolver automaticamente é honesto**, e o contraste com a expiração por tempo é o
     * ponto: lá o caso ainda podia produzir efeito, e sumir seria perdoar por inércia. Aqui o
     * ranking congelou e as recompensas foram concedidas — invalidar mudaria um resultado que as
     * pessoas já comemoraram, contra a 6.5.
     *
     * **Um caso que não pode produzir efeito não deveria continuar pedindo decisão.**
     *
     * Ninguém é notificado: não houve julgamento, e avisar "sua denúncia foi arquivada porque o
     * desafio acabou" contaria ao denunciante algo que ele nem sabe que estava esperando — e a
     * decisão foi que ele não recebe retorno.
     */
    private suspend fun fecharCasosDoEncerrado(g: GrupoParaAvisar): Int =
        (repository.encerrarCasosPendentes(g.id, clock.now().toLocalDateTime(TimeZone.UTC)) as? AppResult.Success)
            ?.value ?: 0

    private suspend fun marcou(groupId: Uuid, dia: LocalDate, tipo: AvisosDiarios.Tipo): Boolean =
        (repository.registrarAviso(groupId, dia, tipo, clock.now().toLocalDateTime(TimeZone.UTC))
            as? AppResult.Success)?.value == true

    companion object {
        /**
         * De quanto em quanto tempo o laço acorda.
         *
         * **Uma hora, não um dia.** As purgas dormem 24h porque o corte delas não tem pressa; aqui
         * o dia civil de cada grupo vira num instante diferente conforme o fuso, e acordar uma vez
         * por dia faria o aviso sair com até 24h de atraso para quem está do outro lado do mundo.
         * A tabela de controle é quem garante o "uma vez por dia", não o intervalo do laço.
         */
        val INTERVALO = 1.days / 24
    }
}
