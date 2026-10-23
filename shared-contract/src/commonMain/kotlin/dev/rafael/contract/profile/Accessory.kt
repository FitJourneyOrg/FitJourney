package dev.rafael.contract.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Acessório que o usuário tem além do equipamento de carga, declarado no onboarding.
 *
 * Espelha 1:1 as colunas `requires_*` de `exercises` (V65): o motor só prescreve o exercício se o
 * usuário tiver TODOS os acessórios que ele exige. Quem treina na ACADEMIA tem os seis por
 * definição — o checklist só aparece para CASA.
 *
 * NÃO é equipamento: halter, elástico e barra são carga e já vêm de `TrainingEnvironment`. Isto é
 * o que sustenta o corpo ou prende a carga, e é o que decide se o supino com halteres é viável na
 * sala de casa.
 *
 * Por que BENCH e ELEVATED_SURFACE são dois: o banco de musculação regula a inclinação e aguenta
 * carga (supino inclinado, scott); a superfície elevada é qualquer apoio firme na altura certa —
 * cadeira, sofá, mesa, caixa (búlgaro, mergulho no banco, remada invertida). Um enum só erraria
 * sempre para um dos lados.
 *
 * [REGRA] São independentes — o filtro (`ExercisePreFilter`) faz `containsAll` sem nenhuma
 * expansão. Quem declara só BENCH não recebe exercícios que exigem só ELEVATED_SURFACE, mesmo
 * que um banco sirva fisicamente como apoio elevado. Se algum dia implementarmos essa implicação
 * no filtro, documentar aqui e promover a regra em docs/regras/01-motor-de-geracao.md.
 */
@Serializable
enum class Accessory {
    @SerialName("BENCH") BENCH,
    @SerialName("ELEVATED_SURFACE") ELEVATED_SURFACE,
    @SerialName("PULLUP_BAR") PULLUP_BAR,
    @SerialName("DIP_STATION") DIP_STATION,
    @SerialName("STEP") STEP,
    @SerialName("ANCHOR_POINT") ANCHOR_POINT,
    ;

    companion object {
        /** Academia tem tudo; em casa vale o que o usuário declarou. */
        fun paraAmbiente(ambiente: TrainingEnvironment?, declarados: List<Accessory>): Set<Accessory> =
            if (ambiente == TrainingEnvironment.ACADEMIA) entries.toSet() else declarados.toSet()
    }
}
