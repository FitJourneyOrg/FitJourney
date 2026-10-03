package dev.rafael.features.stats.domain

import dev.rafael.contract.stats.ProgressDto
import kotlinx.coroutines.flow.Flow

/**
 * Analise de progressao (J.2) como a TELA a enxerga — tonelagem, carga por semana, evolucao de
 * 1RM estimado e series por grupo muscular.
 *
 * [REGRA] ARCH #16: o calculo e do SERVIDOR. Nao existe operacao que altere nada aqui, e a
 * interface mostra isso — nao ha setter, so leitura e pedido de atualizacao.
 *
 * ## Por que e uma porta separada do [Stats], e nao mais um campo nele
 *
 * O `/me/stats` e lido pela Home, pelo menu lateral e pelo perfil, em todo `onResume`. A analise
 * e pesada (varre o historico inteiro e consulta o catalogo) e so interessa a UMA tela. Juntar as
 * duas faria toda abertura da Home pagar pelo grafico que ela nao desenha.
 *
 * > **Porta estreita nao e so sobre o que vaza: e tambem sobre o que se paga para ler.**
 *
 * ## O portao de plano chega como AUSENCIA, nao como erro
 *
 * Para quem e free, os tres blocos pagos vem nulos e `analysisLocked` vem `true`. A tela usa a
 * flag para escolher entre paywall e estado vazio: nulo sozinho nao distingue "e pago" de "voce
 * ainda nao treinou com carga", e confundir os dois mostraria paywall a quem acabou de assinar.
 */
interface Progresso {

    /**
     * Ultima analise conhecida DAQUELE recorte (cache local). Nunca falha; null antes do
     * primeiro sync dele.
     *
     * ⚠️ O cache e POR FILTRO, entao offline so existe o que ja foi olhado. [FiltroDeProgresso.Todos]
     * e o unico sempre presente depois do primeiro uso, porque e o que a tela abre — os recortes
     * sao best-effort, e a tela precisa dizer isso em vez de mostrar grafico vazio.
     */
    fun observar(
        filtro: FiltroDeProgresso = FiltroDeProgresso.Todos(),
        selecao: SelecaoDeExercicios = SelecaoDeExercicios.PADRAO,
    ): Flow<ProgressDto?>

    /**
     * Busca no servidor e grava no cache; o Flow re-emite. Offline: nao faz nada, sem erro.
     *
     * @param forcar ignora o TTL. Use depois de subir sessao pendente — e o unico momento em que
     *   os numeros mudam de verdade.
     */
    suspend fun sincronizar(
        filtro: FiltroDeProgresso = FiltroDeProgresso.Todos(),
        selecao: SelecaoDeExercicios = SelecaoDeExercicios.PADRAO,
        forcar: Boolean = false,
    )
}
