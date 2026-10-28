package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.contract.profile.Goal
import dev.rafael.server.features.exercise.engine.TargetMuscle

/**
 * Motor v3, etapa [7] — AUDITORIA DO VOLUME EFETIVO. Espelha
 * `server/src/test/oracle/motor_v3.py::auditar()` termo a termo.
 *
 * **Por que esta peça existe** (achado de 2026-10-27, `docs/historico/motor-v3-fatia-8.md`): o
 * motor media `entregue` (trabalho direto) e `indireto` em separado e nunca somava os dois, então
 * a grandeza que responde *"este programa serve?"* — o estímulo total que o músculo recebe — não
 * existia no modelo. Por isso nenhuma verificação contra o piso era possível: não faltava um
 * `if`, faltava o conceito. Medido antes de construir: **2.079 pares (perfil, músculo)** dos 720
 * perfis entregam efetivo abaixo do piso, e nenhum era sinalizado.
 *
 * **O que ela NÃO faz:** não muda nenhuma série prescrita. Roda depois do pipeline inteiro, lê o
 * resultado e o classifica. Removê-la não altera programa nenhum — só devolve o motor ao silêncio.
 */
object ProgramAudit {

    /**
     * O que o motor pode dizer sobre um músculo ao fim da geração.
     *
     * [ATENDIDO] o efetivo alcançou o alvo da tabela de volume (ou o alvo é zero, como o abdômen
     * hoje) · [MANUTENCAO] passou do piso mas não do alvo — treina o suficiente para manter, não
     * para o alvo do nível · [ABAIXO_DO_MINIMO] recebeu alguma coisa, mas menos que o piso ·
     * [NAO_TREINADO] não recebeu nada, nem direto nem indireto · [SEM_CATALOGO] não havia
     * exercício para ele no pool do perfil — não é escolha do motor.
     */
    enum class Status { ATENDIDO, MANUTENCAO, ABAIXO_DO_MINIMO, NAO_TREINADO, SEM_CATALOGO }

    /**
     * O que UM músculo recebeu, e se isso basta. [efetivo] = [direto] + [indireto]: o trabalho
     * direto entregue mais o crédito que os compostos de OUTROS músculos deram a este. [razao] é
     * nula para quem não tem piso (abdômen).
     */
    data class Musculo(
        val musculo: TargetMuscle,
        val direto: Int,
        val indireto: Double,
        val efetivo: Double,
        /** O limiar APLICÁVEL: MEV, ou MV quando o objetivo é manutenção. Ver [VolumeTargets.minimoEfetivo]. */
        val minimo: Double,
        val alvo: Double,
        val razao: Double?,
        val status: Status,
        /**
         * `[REGRA]` (Fatia 8 / D-1): eixo SEPARADO do [status], não um status novo — "o alvo foi
         * atendido" e "houve estímulo direto" são duas perguntas, e fundi-las esconderia uma das
         * duas. `true` quando o músculo tinha piso direto ([VolumeTargets.pisoDireto]) e não o
         * recebeu: o alvo foi batido pelo trabalho indireto, mas não coube exercício direto no
         * orçamento da semana.
         *
         * É o que permite a asserção (b) passar honestamente: nenhum [Status.ATENDIDO] com
         * `direto == 0` sem esta marca (fora do iniciante, que é isento). Débito de produto
         * registrado: estes casos precisam de mensagem ao usuário, no mesmo espírito do rótulo de
         * manutenção — "alvo batido pelo trabalho indireto; não coube exercício direto de glúteo
         * nesta semana".
         */
        val pisoDiretoNegado: Boolean = false,
        /**
         * [status] mede contra o ALVO (a prescrição do perfil); este campo mede contra o PISO (o
         * mínimo fisiológico), que é outra pergunta. O fator de objetivo multiplica o alvo **sem
         * respeitar o piso**: `MAINTAIN`/`BEGINNER` nasce com alvo abaixo da coluna do iniciante
         * nos 10 músculos (peito 4,8 contra 8). Resolvido em 2026-10-27 pelo limiar por objetivo
         * ([VolumeTargets.minimoEfetivo]): quem pediu manutenção é medido contra o MV, não contra
         * o MEV — prescrever abaixo do MEV não é erro nesse caso, é o que manutenção significa.
         * O campo continua existindo porque [status] e o piso respondem perguntas diferentes.
         */
        val atingiuMinimo: Boolean,
    )

    private const val EPS = 1e-9

    /**
     * Classifica cada músculo pelo que de fato recebeu. Determinística e sem efeito colateral:
     * duas chamadas com as mesmas entradas devolvem o mesmo mapa.
     */
    fun auditar(
        alvo: Map<TargetMuscle, Double>,
        entregue: Map<TargetMuscle, Int>,
        indireto: Map<TargetMuscle, Double>,
        treinaveis: Collection<TargetMuscle>,
        objetivo: Goal,
        pisoDiretoDe: Map<TargetMuscle, Int> = emptyMap(),
    ): Map<TargetMuscle, Musculo> = TargetMuscle.entries.associateWith { m ->
        val direto = entregue[m] ?: 0
        val ind = indireto[m] ?: 0.0
        val efetivo = direto + ind
        val minimo = VolumeTargets.minimoEfetivo(m, objetivo)
        val alvoM = alvo[m] ?: 0.0
        val status = when {
            m !in treinaveis -> Status.SEM_CATALOGO
            alvoM <= 0.0 || efetivo >= alvoM - EPS -> Status.ATENDIDO
            efetivo >= minimo - EPS -> Status.MANUTENCAO
            efetivo > EPS -> Status.ABAIXO_DO_MINIMO
            else -> Status.NAO_TREINADO
        }
        Musculo(
            musculo = m, direto = direto, indireto = ind, efetivo = efetivo, minimo = minimo,
            alvo = alvoM, razao = if (minimo > 0.0) efetivo / minimo else null, status = status,
            pisoDiretoNegado = (pisoDiretoDe[m] ?: 0) > 0 && direto < (pisoDiretoDe[m] ?: 0),
            atingiuMinimo = efetivo >= minimo - EPS,
        )
    }

    /**
     * Gatilho do aviso de honestidade (Fatia 9): quem não alcançou o PISO, qualquer que seja o
     * status. Usa [Musculo.atingiuMinimo], não o status — um perfil `MAINTAIN`/`BEGINNER` sai
     * [Status.ATENDIDO] com efetivo abaixo do piso, e é justamente esse caso que não pode passar
     * calado.
     */
    fun abaixoDoMinimo(auditoria: Map<TargetMuscle, Musculo>): List<Musculo> =
        auditoria.values.filter { it.status != Status.SEM_CATALOGO && it.minimo > 0.0 && !it.atingiuMinimo }

    /**
     * POR QUE o aviso do usuário é diferente para cada causa — o texto acionável depende do que
     * aconteceu, não só de "ficou abaixo": [NAO_TREINADO] pede mais dias/tempo ou mais equipamento;
     * [ABAIXO_DO_PISO] pede mais tempo por sessão; [PISO_DIRETO_NEGADO] não é falta de estímulo
     * (o piso de volume foi batido pelo indireto) e sim falta de exercício direto na semana.
     * O domínio só diz a CAUSA; a frase mora no i18n (Presentation), e o idioma é do cliente.
     */
    enum class Causa { NAO_TREINADO, ABAIXO_DO_PISO, PISO_DIRETO_NEGADO }

    data class Aviso(val musculo: TargetMuscle, val causa: Causa, val efetivo: Double, val minimo: Double)

    /**
     * O aviso de honestidade da Fatia 9: um [Aviso] por músculo que merece fala, do mais grave ao
     * menos (NAO_TREINADO, depois ABAIXO_DO_PISO, depois PISO_DIRETO_NEGADO; empate pela ordem do
     * enum, para ser determinístico). [Status.SEM_CATALOGO] nunca avisa — não é escolha do motor.
     * Não muda programa nenhum.
     */
    fun avisos(auditoria: Map<TargetMuscle, Musculo>): List<Aviso> =
        auditoria.values.filter { it.status != Status.SEM_CATALOGO }.mapNotNull { m ->
            val causa = when {
                m.minimo > 0.0 && m.efetivo <= EPS -> Causa.NAO_TREINADO
                m.minimo > 0.0 && !m.atingiuMinimo -> Causa.ABAIXO_DO_PISO
                m.pisoDiretoNegado -> Causa.PISO_DIRETO_NEGADO
                else -> null
            }
            causa?.let { Aviso(m.musculo, it, m.efetivo, m.minimo) }
        }.sortedWith(compareBy({ it.causa }, { it.musculo }))
}
