package dev.rafael.server.media

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.checkin.db.CheckInRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** O que a passada fez. Serve para o log e para os testes afirmarem sem espiar o disco. */
data class ResultadoDaPurga(
    val fotosExpiradas: Int = 0,
    val orfaosRecolhidos: Int = 0,
) {
    val fezAlgo: Boolean get() = fotosExpiradas > 0 || orfaosRecolhidos > 0
}

/**
 * Apaga foto que perdeu a razão de existir (4.8, emendada).
 *
 * Duas responsabilidades que são o mesmo problema visto de dois lados — **arquivo sem dono**:
 *
 * 1. **Foto expirada** — o desafio ENCERROU há mais de [CARENCIA_EM_DIAS]. A linha do check-in
 *    fica; o que sai é a imagem, o nome do lugar e a coordenada. Some o dado pessoal, fica o fato.
 *
 * 2. **Órfão** — arquivo em disco que nenhum check-in referencia. Acontece quando o grupo é
 *    apagado (a cascata leva as linhas e deixa os arquivos), quando alguém apaga linha por SQL,
 *    e quando o `INSERT` do check-in falha depois de a foto já ter sido gravada.
 *
 * **Por que a âncora é o fim do desafio e não um prazo fixo.** A 4.8 dizia "90 dias" contados do
 * check-in, e isso conflitava com a própria criação de grupo, que não tem duração máxima: um
 * desafio de 180 dias perderia as fotos do primeiro mês enquanto ainda estava rolando. A foto é
 * prova do desafio — enquanto ele corre, ela serve.
 *
 * **Por que não apagamos as linhas junto.** Elas são 0,3% do peso (~200 bytes contra ~60 KB da
 * foto) e sustentam a contagem, o ranking e as conquistas. Apagar não economizaria nada e
 * destruiria o histórico pessoal.
 */
class PurgaDeMidia(
    private val checkIns: CheckInRepository,
    private val midia: ArmazenamentoDeMidia,
    private val clock: Clock = Clock.System,
) {

    suspend fun rodar(): ResultadoDaPurga {
        val agora = clock.now()
        return ResultadoDaPurga(
            fotosExpiradas = purgarExpiradas(agora),
            orfaosRecolhidos = recolherOrfaos(agora),
        )
    }

    /**
     * O ARQUIVO sai antes da MARCA, e a ordem é deliberada.
     *
     * Se a marca viesse primeiro e a exclusão falhasse, a linha diria "purgada" com o arquivo
     * ainda em disco — e ninguém voltaria a olhar para ele, porque a consulta filtra por
     * `photo_purged_at IS NULL`. Vazamento permanente e silencioso.
     *
     * Na ordem escolhida, o pior caso é o arquivo sumir e a marca não entrar: a próxima passada
     * tenta de novo, o `apagar` é idempotente, e no meio-tempo a rota da foto responde 404 — que
     * é a verdade.
     */
    private suspend fun purgarExpiradas(agora: kotlin.time.Instant): Int {
        val lote = when (val r = checkIns.comFotoExpirada(CARENCIA_EM_DIAS, LOTE)) {
            is AppResult.Failure -> return 0
            is AppResult.Success -> r.value
        }
        if (lote.isEmpty()) return 0

        lote.forEach { midia.apagar(it.photoRef) }
        checkIns.marcarPurgados(lote.map { it.id }, agora.toLocalDateTime(TimeZone.UTC))
        return lote.size
    }

    /**
     * Recolhe arquivo que nenhum check-in referencia.
     *
     * **O corte de 24h é a guarda que impede o varredor de destruir dado bom.** O
     * `CheckInService` grava a foto e só depois tenta o `INSERT`; entre as duas coisas existe um
     * arquivo legítimo sem linha nenhuma. Sem o corte, uma passada no instante errado apagaria a
     * foto de alguém no meio do próprio check-in.
     *
     * As refs vivas são lidas ANTES da listagem, e não depois: assim uma foto gravada durante a
     * varredura pode no máximo escapar desta passada — nunca ser confundida com órfã.
     */
    private suspend fun recolherOrfaos(agora: kotlin.time.Instant): Int {
        val vivas = when (val r = checkIns.refsVivas()) {
            is AppResult.Failure -> return 0
            is AppResult.Success -> r.value
        }
        val candidatas = when (val r = midia.listarRefs(agora - IDADE_MINIMA_DO_ORFAO)) {
            is AppResult.Failure -> return 0
            is AppResult.Success -> r.value
        }

        val orfas = candidatas.filterNot { it in vivas }
        orfas.forEach { midia.apagar(it) }
        return orfas.size
    }

    companion object {
        /**
         * Carência depois de o desafio encerrar.
         *
         * Apagar no instante em que acaba seria hostil: as pessoas abrem o feed no dia seguinte
         * para ver como foi.
         */
        const val CARENCIA_EM_DIAS = 30

        /** Nenhum arquivo com menos de 24h é considerado órfão. Ver [recolherOrfaos]. */
        val IDADE_MINIMA_DO_ORFAO = 24.hours

        /** Teto por passada: a purga é manutenção, não deve segurar o servidor. */
        const val LOTE = 500

        /** De quanto em quanto tempo o varredor acorda. */
        val INTERVALO = 1.days
    }
}
