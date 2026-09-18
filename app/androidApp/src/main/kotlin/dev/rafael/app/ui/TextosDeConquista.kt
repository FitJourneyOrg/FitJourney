package dev.rafael.app.ui

import androidx.annotation.StringRes
import dev.rafael.app.R
import dev.rafael.contract.stats.ConquistaIds

/**
 * O nome e a frase de cada conquista (fatia G.5, ARCH #37).
 *
 * ## O que este arquivo conserta
 *
 * Até a G.5 o `AchievementDto` chegava com `title` e `description` **prontos, em português**,
 * escritos num enum do servidor. A tela de Conquistas inteira, o perfil público e a fileira de
 * medalhas do perfil próprio ficavam em português para quem escolheu inglês — três telas, e
 * nenhuma varredura desta base tinha como pegar:
 *
 * - o inventário da G.3 varreu **literais do cliente**, e a frase não estava no cliente;
 * - o `EnumsSemTextoTest` da G.4 proíbe propriedade `String` em enum **do módulo app**, e o enum
 *   era do servidor;
 * - o `TextoDoServidorTest` fixa os pontos que passam por [Frase.DoServidor], e a tela fazia
 *   `Text(conquista.title)` direto.
 *
 * > **Três invariantes verdes e o texto em português na tela: cada um media uma forma do defeito,
 * > e o defeito tinha uma quarta.**
 *
 * ## Por que o id e não o texto
 *
 * Mesmo desenho da G.2 com os erros, e pelo mesmo motivo: **o servidor tem conhecimento da
 * situação, não direito sobre as palavras**. O que ele manda é o id, que é contrato e já ia no DTO.
 *
 * A chave é DERIVADA do id — `PRIMEIRO_TREINO` → `conquista_primeiro_treino_titulo` — e o
 * `TextosDeConquistaTest` confere a derivação nos dois sentidos lendo o `strings.xml`. Chave fora
 * do padrão quebra o build em vez de virar string órfã no documento do tradutor.
 *
 * ## `null` é caso previsto, e a tela PULA a medalha
 *
 * Diferente do [TextosDeErro], aqui não existe frase de fallback do servidor para cair: se o id é
 * desconhecido, não há o que escrever. Acontece num caso só — **servidor novo, app antigo** — e a
 * escolha é esconder a medalha, que é o espelho do que o servidor já fazia com id órfão no
 * caminho inverso.
 *
 * Esconder é feio e é o menos ruim: a alternativa é um cartão com o identificador cru
 * (`STREAK_90`) no meio da grade, que é exatamente o defeito que o `Rotulos.kt` existe para
 * impedir. E o `AchievementPolicyTest` garante que isso nunca aconteça por descuido nosso — id
 * novo no servidor sem constante no contrato quebra o build lá.
 */
object TextosDeConquista {

    /** O nome curto da medalha, o que aparece em negrito no cartão. */
    @StringRes
    fun titulo(id: String): Int? = when (id) {
        ConquistaIds.PRIMEIRO_TREINO -> R.string.conquista_primeiro_treino_titulo
        ConquistaIds.TREINOS_10 -> R.string.conquista_treinos_10_titulo
        ConquistaIds.TREINOS_50 -> R.string.conquista_treinos_50_titulo
        ConquistaIds.TREINOS_100 -> R.string.conquista_treinos_100_titulo
        ConquistaIds.STREAK_7 -> R.string.conquista_streak_7_titulo
        ConquistaIds.STREAK_30 -> R.string.conquista_streak_30_titulo
        ConquistaIds.STREAK_90 -> R.string.conquista_streak_90_titulo
        ConquistaIds.NIVEL_5 -> R.string.conquista_nivel_5_titulo
        ConquistaIds.NIVEL_10 -> R.string.conquista_nivel_10_titulo
        else -> null
    }

    /**
     * A linha que diz o que fazer para ganhar.
     *
     * ⚠️ **Os números aqui estão cravados na frase, e o alvo verdadeiro viaja no
     * `AchievementDto.target`.** *"Registre 10 treinos"* é texto; o `10` que a barra de progresso
     * usa vem do servidor. Mudar `TREINOS_10.alvo` no `AchievementPolicy` e não mudar esta frase
     * faz a tela pedir 10 e a barra contar até outro número.
     *
     * É a mesma classe das 12 frases que espelham constante do servidor (débito de 2026-09-11), e
     * a saída é a mesma: `%1$d` alimentado pelo `target`. Não foi feito agora porque `PRIMEIRO_TREINO`
     * quebra o molde (*"seu primeiro treino"*, não *"registre 1 treino"*) e parametrizar oito para
     * deixar uma fora é troca ruim.
     *
     * > **Número na frase que também existe no dado é duas verdades esperando divergir.**
     */
    @StringRes
    fun descricao(id: String): Int? = when (id) {
        ConquistaIds.PRIMEIRO_TREINO -> R.string.conquista_primeiro_treino_descricao
        ConquistaIds.TREINOS_10 -> R.string.conquista_treinos_10_descricao
        ConquistaIds.TREINOS_50 -> R.string.conquista_treinos_50_descricao
        ConquistaIds.TREINOS_100 -> R.string.conquista_treinos_100_descricao
        ConquistaIds.STREAK_7 -> R.string.conquista_streak_7_descricao
        ConquistaIds.STREAK_30 -> R.string.conquista_streak_30_descricao
        ConquistaIds.STREAK_90 -> R.string.conquista_streak_90_descricao
        ConquistaIds.NIVEL_5 -> R.string.conquista_nivel_5_descricao
        ConquistaIds.NIVEL_10 -> R.string.conquista_nivel_10_descricao
        else -> null
    }
}
