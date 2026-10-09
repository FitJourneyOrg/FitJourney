package dev.rafael.contract.stats

import kotlinx.serialization.Serializable

/**
 * Detalhe de UM exercicio dentro de UM programa (J.5) — a tela que "ver detalhado" abre a
 * partir da lista de exercicios do recorte de programa.
 *
 * ## Pago sem meio-termo
 *
 * Ao contrario do [ProgressDto], aqui nao ha bloco gratis para misturar numa resposta so — a
 * tela inteira e profundidade paga (e um drill-down de dentro de uma lista que ja e paga). Por
 * isso o portao e 403 com `ErrorCodes.ENTITLEMENT_REQUIRED`, o padrao do `ProgramLimits`, e nao
 * um campo nulo. Ver `StatsRoutes`.
 *
 * ## Programa INTEIRO, sem faixa
 *
 * Nao herda o `de`/`ate` da tela de Progress: seria um TERCEIRO conceito de janela concorrendo
 * com os dois que ja existem (calendario e faixa de programa). "Ver tudo que registrei" e, ao
 * pe da letra, tudo — do inicio do programa ate a ultima sessao.
 */
@Serializable
data class ExercicioDetalheDto(
    val exerciseId: String,
    val name: String,
    val points: List<PontoDeSessaoDto>,
)

/**
 * UM ponto = UMA sessao de treino, nao uma semana.
 *
 * [estimated1rm] e [volumeKg] sao a MESMA sessao, dois angulos: o primeiro e intensidade
 * maxima estimada (Epley, melhor serie), o segundo e quantidade de trabalho (soma de kg x reps
 * de TODAS as series do exercicio naquela sessao). Podem divergir de proposito — forca subindo
 * com volume caindo e treino de pico, nao erro.
 *
 * [kg]/[reps]/[sets] sao so para a legenda ao tocar no ponto, igual a carga semanal ja faz. O
 * eixo nunca e peso bruto: sem reps do lado, 70kg x5 parece "pior" que 65kg x10, quando pesa
 * mais em 1RM estimado.
 */
@Serializable
data class PontoDeSessaoDto(
    val date: String,
    val weekNumber: Int,
    val estimated1rm: Double,
    val volumeKg: Double,
    val kg: Double,
    val reps: Int,
    val sets: Int,
)
