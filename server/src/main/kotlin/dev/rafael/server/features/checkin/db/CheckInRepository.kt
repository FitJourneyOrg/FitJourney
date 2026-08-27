package dev.rafael.server.features.checkin.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.checkin.models.CheckInComAutor
import dev.rafael.server.features.checkin.models.NovoCheckIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.uuid.Uuid

/** Acesso a dados de check-in (fatia B). */
interface CheckInRepository {

    /**
     * Grava. Devolve `false` quando **já existe** check-in seu neste grupo hoje (4.3).
     *
     * Quem decide isso é o índice único, não um `SELECT` antes — dois toques com a rede lenta são
     * duas requisições em voo, e só o banco resolve o empate. Diferente do `join` de grupo, aqui
     * o segundo pedido **não** é idempotente: ele traria outra foto, e a regra é uma por dia.
     */
    suspend fun criar(novo: NovoCheckIn): AppResult<Boolean>

    suspend fun porId(id: Uuid): AppResult<CheckInComAutor?>

    /** O meu check-in de um DIA específico neste grupo, ou `null`. Consulta o índice único. */
    suspend fun doDia(groupId: Uuid, userId: Uuid, dia: LocalDate): AppResult<Uuid?>

    /**
     * O FEED do grupo (8.0.4): mais recente primeiro.
     *
     * [antesDe] é cursor, e não número de página: o feed recebe item novo o tempo todo (polling de
     * 10s, 8.3), e paginar por deslocamento faria a página 2 repetir ou pular linhas conforme a
     * lista cresce por cima.
     */
    suspend fun doGrupo(groupId: Uuid, limite: Int, antesDe: LocalDateTime? = null): AppResult<List<CheckInComAutor>>

    /** Apaga de verdade — é o que LIBERA o slot do dia (4.11). */
    suspend fun apagar(id: Uuid): AppResult<Unit>

    /**
     * O RANKING do grupo (7.2), em UMA consulta.
     *
     * Parte de `group_members`, não de `check_ins` — é o que faz a **2.15** valer por construção:
     * quem saiu ou foi expulso não tem vínculo e simplesmente não aparece, mesmo tendo dezenas de
     * linhas na tabela de check-ins. Filtrar depois, no Kotlin, seria confiar em alguém lembrar.
     *
     * `LEFT JOIN` para que membro sem nenhum check-in apareça com zero: ele está competindo, e
     * saber que ainda não começou é informação útil — inclusive para ele.
     *
     * **Quatro critérios de ordem**, e cada um existe por um motivo diferente:
     *
     * 1. **Contagem**, decrescente — a pontuação ([REGRA] #18).
     * 2. **Último check-in, crescente** — quem ATINGIU aquela pontuação primeiro. Duas pessoas com
     *    20 check-ins: quem fez o 20º antes fica na frente.
     * 3. **Entrou antes** — o critério do DIA 1. Cinquenta pessoas com zero check-ins empatam nos
     *    dois primeiros, e sem um terceiro o banco devolveria em ordem arbitrária, que muda entre
     *    consultas. Com o polling de 10s, a lista se reembaralharia sozinha na tela.
     * 4. **`user_id`** — determinismo, não justiça. Nunca é mostrado como critério.
     *
     * Empate não é caso de borda aqui: é o **estado inicial** de todo desafio.
     */
    suspend fun ranking(groupId: Uuid): AppResult<List<LinhaDoRanking>>

    // ---- purga de mídia (4.8, emendada) ----

    /**
     * Check-ins cuja foto já pode ser apagada: o desafio ENCERROU há mais de [carenciaEmDias].
     *
     * A âncora é o fim do DESAFIO, não um prazo fixo contado do check-in. A versão anterior da
     * 4.8 dizia "90 dias" e conflitava com a própria criação de grupo, que não tem duração
     * máxima: um desafio de 180 dias perderia as fotos do primeiro mês **enquanto ainda estava
     * rolando**. A foto é prova do desafio; enquanto ele corre, ela serve.
     */
    suspend fun comFotoExpirada(carenciaEmDias: Int, limite: Int): AppResult<List<FotoExpirada>>

    /**
     * Marca a foto como purgada e **apaga junto o nome do lugar e a coordenada**.
     *
     * A linha fica: ela custa ~200 bytes e é o que sustenta "você treinou 42 dias", as conquistas
     * e o ranking. O que sai é exatamente o DADO PESSOAL — a imagem da pessoa e onde ela estava.
     * Some o que identifica, fica o fato.
     */
    suspend fun marcarPurgados(ids: List<Uuid>, agora: LocalDateTime): AppResult<Unit>

    /**
     * Todas as referências de foto ainda VIVAS. Alimenta o recolhimento de órfãos.
     *
     * Carrega tudo em memória: 42 mil referências são ~4 MB, e o varredor roda uma vez por dia.
     * Deixa de servir na casa dos milhões — aí o recolhimento vira comparação em lote no banco.
     */
    suspend fun refsVivas(): AppResult<Set<String>>
}

/** O mínimo para purgar: a linha a marcar e o arquivo a apagar. */
data class FotoExpirada(val id: Uuid, val photoRef: String)

/**
 * Linha crua do ranking, já ordenada pelo banco.
 *
 * Sem `position`: ela é atribuída pela ordem da lista, e não guardada. Persistir posição seria
 * mais um estado para divergir da contagem — mesma escolha do `state` do grupo, que é derivado.
 */
data class LinhaDoRanking(
    val userId: Uuid,
    val displayName: String,
    val checkIns: Int,
)
