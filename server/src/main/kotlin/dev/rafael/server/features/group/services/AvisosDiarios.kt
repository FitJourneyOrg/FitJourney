package dev.rafael.server.features.group.services

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * As regras dos avisos diários do grupo, em Kotlin puro (fatia F, decisões 10.6 + emendas de
 * 2026-09-07).
 *
 * Mesma escolha do [GroupPolicy] e do [EmojiDoDia]: sem banco, sem HTTP, sem relógio próprio. É o
 * que permite testar "o lembrete sai no terceiro dia" sem esperar três dias nem subir Postgres.
 */
object AvisosDiarios {

    /**
     * Depois de quantos dias parada a fila lembra o admin (decisão de 2026-09-07).
     *
     * **Três, e não um.** Um dia daria pressão forte e transformaria o lembrete em push quase
     * diário para quem tem grupo ativo — e aviso que chega todo dia é aviso que se aprende a
     * ignorar, o que o desliga justamente quando importa. Sete estaria alinhado com o prazo da
     * denúncia (6.9), mas uma semana de foto imprópria no feed de 49 pessoas é muito tempo.
     */
    const val DIAS_ATE_LEMBRAR = 3

    /**
     * O dia civil do GRUPO, não o do servidor.
     *
     * Mesma função do [dev.rafael.server.features.checkin.services.CheckInPolicy.diaDoGrupo], e
     * repetida aqui de propósito: `group` não importa `checkin` — feature nunca depende de feature.
     * Duas linhas duplicadas custam menos que o ciclo que o `UserCodePolicy` custou no #35.
     */
    fun diaDoGrupo(agora: Instant, fuso: TimeZone): LocalDate = agora.toLocalDateTime(fuso).date

    /**
     * O aviso das entradas cabe hoje? `null` = não avisa.
     *
     * Devolve a **quantidade** e não um booleano porque é ela que entra no texto — e é o mesmo
     * motivo do `comentarioValido` devolver o texto: quem decide e quem usa leem o mesmo valor,
     * então não há como validar uma coisa e enviar outra.
     *
     * Zero entradas não vira aviso. Parece óbvio e é a regra que impede "0 pessoas entraram no seu
     * desafio hoje" de existir — o laço roda todo dia para todo grupo, inclusive os parados.
     */
    fun entradasParaAvisar(quantasEntraram: Int): Int? = quantasEntraram.takeIf { it > 0 }

    /**
     * Passou tempo demais com caso aberto?
     *
     * O relógio conta do caso **mais antigo**, não do mais recente: uma denúncia nova chegando não
     * pode zerar o cronômetro de outra que já espera há dias. Fosse pelo mais recente, um grupo com
     * denúncias frequentes nunca geraria lembrete — exatamente o grupo que mais precisa.
     */
    fun filaEstaParada(diasDoCasoMaisAntigo: Int): Boolean =
        diasDoCasoMaisAntigo >= DIAS_ATE_LEMBRAR

    /** Os avisos que o laço sabe mandar. Espelha o `CHECK` da V46 — os dois têm de concordar. */
    enum class Tipo {
        /** 10.6: "3 pessoas entraram no seu desafio hoje". Para **todos os membros**. */
        ENTRADAS_DO_DIA,

        /** Emenda de 2026-09-07: caso aberto há 3+ dias. Só para o **admin**. */
        FILA_PARADA,
    }
}
