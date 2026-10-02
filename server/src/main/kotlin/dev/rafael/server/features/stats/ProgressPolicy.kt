package dev.rafael.server.features.stats

import dev.rafael.contract.profile.MuscleGroup
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.uuid.Uuid

/**
 * Analise de progressao (fatia J.2) - tonelagem, carga estimada e volume por grupo.
 *
 * Kotlin puro, sem I/O, como [XpPolicy] e [AchievementPolicy]: a regra inteira se testa sem banco.
 *
 * ## [REGRA] So serie com CARGA EXTERNA entra aqui
 *
 * `weight_kg` e nullable na `session_set_logs` desde a V20, e o nulo quer dizer peso corporal.
 * Entao o criterio de elegibilidade sai do DADO, nao do catalogo: quem fez barra fixa com cinto
 * de 10 kg entra; quem fez a mesma barra fixa livre, nao. Marcar o exercicio como "com carga" no
 * catalogo erraria nos dois sentidos.
 *
 * > **Quem decide se a serie tem carga e a serie, nao o exercicio.**
 *
 * Consequencia assumida: um usuario 100% calistenia nao tem o que mostrar aqui. A tela precisa de
 * estado vazio explicativo - XP, streak e conquistas seguem contando normalmente para ele.
 *
 * ## Por que a entrada e [SerieFeita] e nao `WorkoutSession`
 *
 * Mesma escolha do [XpPolicy], que recebe `Map<LocalDate, List<Int>>` em vez do historico: a
 * politica nao precisa saber como o servidor modela sessao, e o teste monta o caso em uma linha.
 * O achatamento (e o filtro do [elegivel]) acontece na borda, no servico.
 */
object ProgressPolicy {

    /** Uma serie COM CARGA ja validada por [elegivel]. `kg` nunca e nulo aqui, por construcao. */
    data class SerieFeita(
        val sessaoId: Uuid,
        val data: LocalDate,
        val nomeDoTreino: String,
        val exercicioId: Uuid,
        val reps: Int,
        val kg: Double,
    )

    /** Peso zero e peso ausente sao a mesma coisa para analise: nao ha carga a somar. */
    fun elegivel(done: Boolean, weightKg: Double?): Boolean =
        done && weightKg != null && weightKg > 0.0

    /** A formula, isolada: quem tem reps e kg na mao nao precisa montar uma [SerieFeita]. */
    fun cargaDaSerie(reps: Int, kg: Double): Double = reps * kg

    fun cargaDaSerie(s: SerieFeita): Double = cargaDaSerie(s.reps, s.kg)

    fun tonelagem(series: List<SerieFeita>): Double = series.sumOf { cargaDaSerie(it) }

    /**
     * Elegibilidade + formula numa chamada so, para quem tem o SetLog cru e nao monta
     * [SerieFeita]: hoje o `StatsService`, que calcula o `totalKg` do `/me/stats`.
     *
     * Existe porque "mesma formula" nao basta: se um lado chamasse [elegivel] e o outro
     * repetisse a condicao, a conquista de carga e a tela de progresso poderiam discordar sobre
     * uma serie de 0 kg. Serie nao elegivel vale 0.0, nunca e descartada pelo chamador.
     */
    fun cargaDeSerieCrua(done: Boolean, reps: Int, weightKg: Double?): Double =
        if (elegivel(done, weightKg) && weightKg != null) cargaDaSerie(reps, weightKg) else 0.0

    // ---- semanas -----------------------------------------------------------

    private fun isoDia(d: LocalDate): Int = when (d.dayOfWeek) {
        DayOfWeek.MONDAY -> 1; DayOfWeek.TUESDAY -> 2; DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4; DayOfWeek.FRIDAY -> 5; DayOfWeek.SATURDAY -> 6
        else -> 7
    }

    /** Segunda-feira da semana de [d]. Semana ISO, igual em todo o app. */
    fun segundaDaSemana(d: LocalDate): LocalDate = d.minus(DatePeriod(days = isoDia(d) - 1))

    data class CargaDaSemana(val semana: LocalDate, val kg: Double)

    /**
     * Carga por semana, da mais antiga para a mais recente, SEMPRE com [semanas] posicoes.
     *
     * Semana sem treino vem com `0.0` em vez de sumir da lista. O buraco e informacao - foi a
     * semana que a pessoa faltou - e omiti-la faria o grafico mentir sobre a continuidade,
     * encostando duas semanas que nao sao vizinhas.
     */
    fun porSemana(series: List<SerieFeita>, hoje: LocalDate, semanas: Int = 8): List<CargaDaSemana> {
        require(semanas > 0) { "semanas tem de ser positivo" }
        val primeira = segundaDaSemana(hoje).minus(DatePeriod(days = 7 * (semanas - 1)))
        val porInicio = series
            .filter { it.data >= primeira }
            .groupBy { segundaDaSemana(it.data) }
            .mapValues { (_, s) -> tonelagem(s) }
        return (0 until semanas).map { i ->
            val inicio = primeira.plus(DatePeriod(days = 7 * i))
            CargaDaSemana(inicio, porInicio[inicio] ?: 0.0)
        }
    }

    // ---- semanas DO PROGRAMA (J.3) -----------------------------------------

    /**
     * Em que semana do programa (1-based) [data] caiu, dado o [inicio] dele.
     *
     * ## Conta por DIAS corridos, nao por semana ISO
     *
     * Semana 1 e o periodo de 7 dias a partir do `started_at`, mesmo que ele caia numa quarta.
     * Usar a segunda-feira ISO faria a "semana 1" durar as vezes dois dias — quem comecou na
     * sexta veria a primeira semana acabar no domingo seguinte, com um treino dentro, e
     * concluiria que o programa esta errado.
     *
     * > **A semana do programa pertence ao programa, nao ao calendario.** O eixo de calendario
     * > continua existindo para quando nenhum programa esta selecionado — sao duas reguas, e
     * > misturar as duas e o que faz "semana 10" nao significar nada.
     *
     * Data ANTES do inicio devolve numero <= 0. Nao e erro: sessao mais antiga que o programa
     * existe (o programa foi criado depois), e quem chama a descarta pela faixa.
     */
    fun semanaDoPrograma(data: LocalDate, inicio: LocalDate): Int {
        // `floorDiv`, e nao `/`: divisao de Int em Kotlin trunca em direcao a ZERO, entao um dia
        // ANTES do inicio (-3 dias) daria semana 1 em vez de 0, misturando o que veio antes do
        // programa com a primeira semana dele.
        val dias = (data.toEpochDays() - inicio.toEpochDays()).toInt()
        return Math.floorDiv(dias, 7) + 1
    }

    data class CargaDaSemanaDoPrograma(val semana: Int, val kg: Double)

    /**
     * Carga por semana DO PROGRAMA, de [de] ate [ate], sempre com uma posicao por semana.
     *
     * Mesma regra do [porSemana]: semana sem treino vem com `0.0` em vez de sumir. Aqui ela pesa
     * ainda mais — numa faixa escolhida a mao ("semana 10 a 14"), uma semana ausente deixaria a
     * faixa com menos barras do que a pessoa pediu, e ela leria isso como dado faltando.
     */
    fun porSemanaDoPrograma(
        series: List<SerieFeita>,
        inicio: LocalDate,
        de: Int,
        ate: Int,
    ): List<CargaDaSemanaDoPrograma> {
        require(de >= 1 && ate >= de) { "faixa invalida: $de..$ate" }
        val porNumero = series
            .groupBy { semanaDoPrograma(it.data, inicio) }
            .mapValues { (_, s) -> tonelagem(s) }
        return (de..ate).map { CargaDaSemanaDoPrograma(it, porNumero[it] ?: 0.0) }
    }

    /**
     * So as series que caem na faixa [de]..[ate] do programa.
     *
     * Existe como funcao propria porque TODOS os blocos usam a mesma faixa: tonelagem, evolucao,
     * volume por grupo e a comparacao. Recortar em cada um separadamente e como quatro recortes
     * discordam sobre o que e "a faixa".
     */
    fun naFaixaDoPrograma(
        series: List<SerieFeita>,
        inicio: LocalDate,
        de: Int,
        ate: Int,
    ): List<SerieFeita> = series.filter { semanaDoPrograma(it.data, inicio) in de..ate }

    /** [evolucao], mas com o ponto rotulado pela semana do programa em vez da data. */
    fun evolucaoNoPrograma(
        series: List<SerieFeita>,
        exercicioId: Uuid,
        inicio: LocalDate,
    ): List<PontoNoPrograma> =
        series.filter { it.exercicioId == exercicioId }
            .groupBy { semanaDoPrograma(it.data, inicio) }
            .map { (semana, doGrupo) ->
                PontoNoPrograma(semana, doGrupo.maxOf { e1rm(it.kg, it.reps) })
            }
            .sortedBy { it.semana }

    data class PontoNoPrograma(val semana: Int, val e1rm: Double)

    // ---- carga estimada ----------------------------------------------------

    /**
     * 1RM estimado por Epley: `kg * (1 + reps/30)`.
     *
     * Existe porque carga crua maior nao e, sozinha, progresso: 3x8 a 67,5 kg "pesa mais" que
     * 3x10 a 65 kg e estima MENOS (85,5 contra 86,7). Sem normalizar pela repeticao, o grafico
     * aplaudiria a troca. Estimativa, nao medicao - serve para comparar a pessoa com ela mesma,
     * nao com outra.
     */
    fun e1rm(kg: Double, reps: Int): Double = if (reps <= 0) 0.0 else kg * (1 + reps / 30.0)

    data class PontoDeEvolucao(val semana: LocalDate, val e1rm: Double)

    /** Os exercicios que mais pesaram no periodo - os que valem virar linha no grafico. */
    fun maisRelevantes(series: List<SerieFeita>, quantos: Int = 3): List<Uuid> =
        series.groupBy { it.exercicioId }
            .mapValues { (_, s) -> tonelagem(s) }
            .entries
            .sortedWith(compareByDescending<Map.Entry<Uuid, Double>> { it.value }.thenBy { it.key.toString() })
            .take(quantos)
            .map { it.key }

    /**
     * Melhor serie de cada semana, em 1RM estimado, para UM exercicio.
     *
     * Ao contrario do [porSemana], aqui semana sem o exercicio **nao vira zero**: zero diria que a
     * pessoa ficou mais fraca, quando ela so treinou outra coisa. A linha pula o vazio.
     */
    fun evolucao(series: List<SerieFeita>, exercicioId: Uuid): List<PontoDeEvolucao> =
        series.filter { it.exercicioId == exercicioId }
            .groupBy { segundaDaSemana(it.data) }
            .map { (semana, doGrupo) -> PontoDeEvolucao(semana, doGrupo.maxOf { e1rm(it.kg, it.reps) }) }
            .sortedBy { it.semana }

    // ---- volume por grupo --------------------------------------------------

    /**
     * [semClassificacao] e a media semanal de series de exercicio sem `primary_muscles` no
     * catalogo. A coluna e nullable, entao esse caso existe - e somar essas series em silencio
     * faria sumir volume real do grafico. Melhor aparecer como "nao classificado".
     */
    data class VolumePorGrupo(val porGrupo: Map<MuscleGroup, Double>, val semClassificacao: Double)

    /**
     * Media de SERIES por semana, por grupo muscular - de proposito, e nao quilos.
     *
     * Em kg o leg press esmaga tudo: um teste com 8 semanas reais deu 105 t para pernas contra
     * 6,6 t para ombro, o que leria como desequilibrio de 16x quando em series e 6x. A razao e que
     * carga externa nao e comparavel entre maquina e halter - series sao.
     *
     * > **Comparar grupos musculares por quilo e comparar alavanca, nao esforco.**
     *
     * Conta so [Exercise.primaryMuscles]: o secundario entraria em quase tudo e inflaria o
     * grafico inteiro. Uma serie conta 1 para CADA primario - e a convencao de volume usada em
     * prescricao, nao um rateio.
     */
    fun seriesPorGrupo(
        series: List<SerieFeita>,
        musculos: Map<Uuid, List<MuscleGroup>>,
        semanas: Int,
    ): VolumePorGrupo {
        require(semanas > 0) { "semanas tem de ser positivo" }
        val acumulado = mutableMapOf<MuscleGroup, Int>()
        var orfas = 0
        for (s in series) {
            val grupos = musculos[s.exercicioId].orEmpty()
            if (grupos.isEmpty()) orfas++ else grupos.forEach { acumulado[it] = (acumulado[it] ?: 0) + 1 }
        }
        return VolumePorGrupo(
            porGrupo = acumulado.mapValues { (_, n) -> n.toDouble() / semanas },
            semClassificacao = orfas.toDouble() / semanas,
        )
    }

    // ---- ultimo treino vs o anterior ---------------------------------------

    data class ComparacaoDeTreino(
        val nomeDoTreino: String,
        val dataAtual: LocalDate,
        val dataAnterior: LocalDate,
        val kgAtual: Double,
        val kgAnterior: Double,
        /** Carga do exercicio NA sessao atual - so dos que aparecem nas duas. */
        val kgAtualPorExercicio: Map<Uuid, Double>,
        /** Delta por exercicio: positivo subiu, negativo caiu, ausente nao apareceu nos dois. */
        val deltaPorExercicio: Map<Uuid, Double>,
    )

    /**
     * Compara a sessao mais recente com a anterior DO MESMO TREINO (mesmo `workout_name`).
     *
     * Comparar com a sessao anterior qualquer que fosse daria "voce caiu 40%" ao sair de um
     * inferior para um superior - ruido com cara de diagnostico. Sem par do mesmo nome, devolve
     * `null`: a tela mostra o bloco so quando ele tem o que dizer.
     */
    fun ultimoVsAnterior(series: List<SerieFeita>): ComparacaoDeTreino? {
        if (series.isEmpty()) return null
        val sessoes = series.groupBy { it.sessaoId }
            .map { (_, s) -> Triple(s.first().data, s.first().nomeDoTreino, s) }
            .sortedWith(compareByDescending<Triple<LocalDate, String, List<SerieFeita>>> { it.first }
                .thenByDescending { it.third.first().sessaoId.toString() })
        val atual = sessoes.first()
        val anterior = sessoes.drop(1).firstOrNull { it.second == atual.second } ?: return null

        fun porExercicio(s: List<SerieFeita>): Map<Uuid, Double> =
            s.groupBy { it.exercicioId }.mapValues { (_, l) -> tonelagem(l) }

        val a = porExercicio(atual.third)
        val b = porExercicio(anterior.third)
        return ComparacaoDeTreino(
            nomeDoTreino = atual.second,
            dataAtual = atual.first,
            dataAnterior = anterior.first,
            kgAtual = tonelagem(atual.third),
            kgAnterior = tonelagem(anterior.third),
            kgAtualPorExercicio = a.keys.intersect(b.keys).associateWith { a.getValue(it) },
            deltaPorExercicio = a.keys.intersect(b.keys).associateWith { a.getValue(it) - b.getValue(it) },
        )
    }
}
