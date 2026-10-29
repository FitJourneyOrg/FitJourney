package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Level
import dev.rafael.server.features.exercise.engine.TargetMuscle
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao
import dev.rafael.server.features.exercise.engine.v3.SessionComposition.Papel

/**
 * Onde a pessoa está no mesociclo. `numero` é o que entra como `mesocicloAtual` no
 * [MotorV3Pipeline.Entrada]; `semana` vai de 1 a [CicloDoMesociclo.SEMANAS].
 *
 * **O mesociclo avança por CONCLUSÃO, não por calendário** (spec): quem some três semanas não
 * "perde" semanas, volta de onde parou — e é por isso que o retorno após pausa existe.
 */
data class Mesociclo(
    val numero: Int = 1,
    val semana: Int = 1,
    /**
     * `[REGRA]` 2026-10-28: aberto por retorno de 60+ depois de >28 dias de pausa ([RetornoAposPausa]).
     * Vale até o fim DESTE mesociclo; abrir o seguinte o apaga.
     */
    val conservador: Boolean = false,
)

/**
 * Motor v3, Fatia 10 — o CICLO de 4 semanas e a CONTINUIDADE do programa. Espelha
 * `server/src/test/oracle/progressao_v3.py` (seção "mesociclo" e "continuidade").
 *
 * Semanas 1 a 3: séries FIXAS, progride carga e repetições. Semana 4: deload — metade do volume,
 * carga mantida. **As séries nunca sobem dentro do ciclo** (contradição do item 15 da spec: a série
 * extra estourava o teto ou não acontecia).
 *
 * ## Fechamento do débito do 60+ (2026-10-28)
 *
 * Desde a Fatia 2, `mesocicloAtual == null` conta como 1º mesociclo, e como nada acompanhava o
 * mesociclo, TODO aluno de 60+ ficava em modo conservador (fator de idade 0,75 no teto, RIR ≥ 3)
 * para sempre. [paraPerfil] devolve o número real; a regra em si não mudou. Quem ainda não tem
 * estado (nunca treinou) é o 1º mesociclo — o lado seguro, igual ao `null` de antes.
 */
object CicloDoMesociclo {
    const val SEMANAS = 4
    const val SEMANA_DELOAD = 4
    const val DELOAD_FATOR_SERIES = 0.5

    /** Fechar a semana 4 abre o mesociclo seguinte na semana 1. */
    fun concluirSemana(m: Mesociclo): Mesociclo =
        if (m.semana >= SEMANAS) Mesociclo(m.numero + 1, 1) else Mesociclo(m.numero, m.semana + 1, m.conservador)

    /**
     * Semana 4 para intermediário e avançado. **O iniciante não tem deload por calendário**: só por
     * estagnação ([AncoraDoIniciante]) — 25% do tempo dele seria desperdício (spec, item 24).
     */
    fun deloadProgramado(nivel: Level, m: Mesociclo): Boolean =
        nivel != Level.BEGINNER && m.semana == SEMANA_DELOAD

    /** Metade das séries (meio pra cima), no mínimo 1. Fora do deload não muda nada. */
    fun seriesDoDia(seriesBase: Int, deload: Boolean): Int =
        if (!deload) seriesBase else maxOf(1, Progressao.arredondar(seriesBase * DELOAD_FATOR_SERIES))

    /** O `mesocicloAtual` que o pipeline recebe. Sem estado = nunca treinou = 1º mesociclo. */
    fun paraPerfil(m: Mesociclo?): Int = m?.numero ?: 1

    /** O `retornoConservador` que o pipeline recebe (só produz efeito com idade ≥ 60). */
    fun retornoConservadorParaPerfil(m: Mesociclo?): Boolean = m?.conservador == true

    data class VagaDaSemana(val alvo: TargetMuscle, val papel: Papel, val series: Int, val exercicio: String)

    /**
     * A semana [m] de um programa JÁ gerado: `[(rótulo, [VagaDaSemana])]`.
     *
     * **CONTINUIDADE** (registro da Fatia 6, escopo da Fatia 10): o `CatalogFiller.preencher()` roda
     * UMA vez, em [MotorV3Pipeline.gerar]; esta função só LÊ o resultado. O exercício de cada posição
     * é o mesmo em todas as semanas do mesociclo e dos seguintes do MESMO programa — sem exposição
     * repetida não há carga comparável, e sem comparação não há sobrecarga progressiva. Trocar
     * exercício é gerar um NOVO programa. Vaga vazia (sem exercício) não é prescrita.
     *
     * Única exceção nomeada: a troca de âncora por estagnação do iniciante ([AncoraDoIniciante]).
     */
    fun semanaDoPrograma(
        sessoes: List<Pair<String, List<CatalogFiller.VagaPreenchida>>>,
        nivel: Level,
        m: Mesociclo,
        deloadExtra: Boolean = false,
    ): List<Pair<String, List<VagaDaSemana>>> {
        val deload = deloadProgramado(nivel, m) || deloadExtra
        return sessoes.map { (rotulo, vagas) ->
            rotulo to vagas.mapNotNull { vp ->
                val exercicio = vp.exercicio ?: return@mapNotNull null
                VagaDaSemana(vp.vaga.alvo, vp.vaga.papel, seriesDoDia(vp.vaga.series, deload), exercicio.name)
            }
        }
    }
}

/**
 * Máquina da âncora do INICIANTE. Espelha `progressao_v3.py::situacao_iniciante()`.
 *
 * ## `[REGRA]` EXCEÇÃO NOMEADA à fixidez das âncoras
 *
 * Dentro de um programa o exercício de cada posição é fixo ([CicloDoMesociclo.semanaDoPrograma]).
 * A **única** troca no meio do programa é a troca por estagnação do iniciante, e só dispara quando
 * as TRÊS condições valem, nesta ordem (a ordem da spec: *estagnou → deload com a mesma âncora →
 * diagnóstico → só então a troca*; trocar antes compararia exercícios diferentes):
 *
 *  1. um deload JÁ foi aplicado com a mesma âncora;
 *  2. a estagnação PERSISTE depois dele: [RETOMADA_SESSOES] sessões seguintes sem subir ("retomada
 *     é a carga subir em pelo menos uma âncora nas três sessões seguintes ao deload", spec);
 *  3. há diagnóstico registrado — vem de fora (o mecanismo de deload como diagnóstico, Fatia 11, ou a
 *     resposta da pessoa).
 *
 * Subir depois do deload, em qualquer sessão, afasta a troca ([Situacao.RETOMOU]); estagnar DE NOVO
 * depois de retomar não é troca — é o sinal de promoção (Fatia 11) e devolve [Situacao.DELOAD].
 * Referência cruzada: `docs/regras/01-motor-de-geracao.md`, "Fatia 10 — mesociclo e progressão".
 */
object AncoraDoIniciante {
    /** `[PROPOSTA]`: a spec não dá número para "estagnar"; usa-se o mesmo 3 da retomada. */
    const val ESTAGNACAO_SESSOES = 3
    const val RETOMADA_SESSOES = 3

    enum class Situacao { PROGREDINDO, DELOAD, AGUARDANDO_RETOMADA, RETOMOU, DIAGNOSTICO_PENDENTE, TROCA_AUTORIZADA }

    /** Um evento do histórico da âncora: uma decisão de carga ou o marcador do deload. */
    sealed interface Evento {
        data class Carga(val decisao: Decisao) : Evento
        data object Deload : Evento
    }

    private fun semSubirNoFim(eventos: List<Evento>): Int {
        var n = 0
        for (e in eventos.asReversed()) {
            if (e is Evento.Carga && e.decisao == Decisao.SOBE) break
            n++
        }
        return n
    }

    fun situacao(historico: List<Evento>, diagnosticoFeito: Boolean): Situacao {
        val ultimoDeload = historico.indexOfLast { it is Evento.Deload }
        if (ultimoDeload < 0) {
            return if (semSubirNoFim(historico) >= ESTAGNACAO_SESSOES) Situacao.DELOAD else Situacao.PROGREDINDO
        }
        val depois = historico.subList(ultimoDeload + 1, historico.size)
        if (depois.any { it is Evento.Carga && it.decisao == Decisao.SOBE }) {
            return if (semSubirNoFim(depois) < ESTAGNACAO_SESSOES) Situacao.RETOMOU else Situacao.DELOAD
        }
        if (depois.size < RETOMADA_SESSOES) return Situacao.AGUARDANDO_RETOMADA
        return if (diagnosticoFeito) Situacao.TROCA_AUTORIZADA else Situacao.DIAGNOSTICO_PENDENTE
    }
}

/**
 * Retorno após pausa. Espelha `progressao_v3.py::retorno_apos_pausa()`/`aplicar_retorno()`.
 * **Uma regra, três ramos** (spec, "Retorno após pausa"); o relógio é o do SERVIDOR.
 *
 *  - até 14 dias ........ segue como estava;
 *  - 15 a 28 dias ....... volta à semana 1 do MESMO mesociclo, com a carga menor;
 *  - mais de 28 dias .... mesociclo NOVO e carga recalibrada — calibração OBRIGATÓRIA (a carga
 *    informada seria a de antes da pausa e pularia a redução).
 */
object RetornoAposPausa {
    const val SEGUE_DIAS = 14
    const val RECALIBRA_DIAS = 28

    /** Mesma idade de corte do fator 0,75 ([CapacityModel.fatorIdade]). */
    const val IDADE_CONSERVADOR = 60

    /**
     * `[PROPOSTA]` 2026-10-28: a spec diz "carga um pouco menor" sem número. 10% é heurística SEM
     * FONTE; trocar é trocar esta constante. Pendente de decisão do Rafael.
     */
    const val REDUCAO = 0.10

    data class Retorno(val mesociclo: Mesociclo, val recalibra: Boolean, val fatorCarga: Double)

    /**
     * `[REGRA]` extensão declarada ALÉM da lista literal da spec (2026-10-28): com [idade] ≥ 60 o
     * mesociclo NOVO nasce com `conservador = true` — modo conservador explícito, independente do
     * número do mesociclo (a regra da spec só olha o 1º). Motivo: quem volta depois de um mês fora é
     * quem mais precisa do lado seguro.
     */
    fun apos(diasParado: Int, m: Mesociclo, idade: Int? = null): Retorno {
        require(diasParado >= 0) { "dias parado negativo" }
        return when {
            diasParado <= SEGUE_DIAS -> Retorno(m, false, 1.0)
            diasParado <= RECALIBRA_DIAS -> Retorno(Mesociclo(m.numero, 1, m.conservador), false, 1.0 - REDUCAO)
            else -> Retorno(Mesociclo(m.numero + 1, 1, idade != null && idade >= IDADE_CONSERVADOR), true, 1.0)
        }
    }

    /**
     * O [Progressao.Estado] de UM exercício depois do retorno. Recalibrar zera a carga e as
     * exposições (a calibração é a 1ª de novo) e preserva o 1RM histórico; a redução arredonda ao
     * incremento e nunca sobe a carga.
     */
    fun aplicar(estado: Progressao.Estado, r: Retorno, incremento: Double): Progressao.Estado {
        if (r.recalibra) return Progressao.Estado(carga = null, exposicoes = 0, posSalto = false, umRm = estado.umRm, semSubir = 0)
        val carga = estado.carga
        if (carga == null || r.fatorCarga == 1.0) return estado
        val nova = maxOf(Progressao.paraIncremento(carga * r.fatorCarga, incremento), incremento)
        return estado.copy(carga = minOf(nova, carga), posSalto = false)
    }
}
