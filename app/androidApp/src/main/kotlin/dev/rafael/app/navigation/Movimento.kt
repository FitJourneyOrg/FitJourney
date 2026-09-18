package dev.rafael.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute

/**
 * O movimento entre telas (2026-09-15).
 *
 * ## Por que não é um movimento só para as 36 rotas
 *
 * Porque o app navega de três jeitos diferentes, e usar a mesma animação nos três comunica a coisa
 * errada. A animação de navegação não é enfeite: ela é a única pista que diz **o que aconteceu com
 * a tela anterior**.
 *
 * | gesto | o que a pessoa fez | movimento |
 * |---|---|---|
 * | **empilhar** | abriu um detalhe, e existe um "voltar" | entra da direita, volta para a direita |
 * | **trocar de aba** | pulou para outra seção, sem voltar | esmaece, sem direção |
 * | **entrar numa tarefa** | execução, check-in, paywall | sobe de baixo, como painel |
 *
 * > **A direção do movimento é uma promessa sobre o botão voltar.**
 *
 * ## O detalhe que decidiu o desenho: as abas não têm direção
 *
 * A navegação entre as quatro raízes usa `popUpTo`, então a pilha não guarda de onde você veio. Um
 * deslize horizontal aqui teria de escolher um lado arbitrário, e ele acertaria metade das vezes —
 * tocar num ícone à direita e ver a tela entrar pela esquerda é o tipo de detalhe que ninguém sabe
 * nomear e todo mundo sente.
 *
 * O fade com escala resolve por não prometer nada. É o *fade through* do Material.
 *
 * ## Os números
 *
 * `RAPIDO` é curto de propósito. Animação de navegação é atravessada dezenas de vezes por sessão, e
 * o que encanta na primeira vez atrasa na vigésima. O Material sugere 300ms para entrada e menos
 * para saída, e a assimetria é intencional: **a tela que chega merece mais tempo que a que sai**,
 * porque é nela que a pessoa vai olhar.
 *
 * `EMPURRA` é a curva "emphasized decelerate": começa rápido e freia no fim, que é como um objeto
 * físico chega ao lugar.
 */
object Movimento {

    /** Entrada. Longo o bastante para ser lido como movimento, curto para não virar espera. */
    private const val RAPIDO = 280

    /** Saída. Mais curta que a entrada: quem sai não precisa ser acompanhado. */
    private const val MAIS_RAPIDO = 200

    private val EMPURRA = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val SOME = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * Quanto a tela que SAI anda para o lado, em fração da largura.
     *
     * Não é 1: a tela de trás não vai embora, ela fica parcialmente à mostra e escurecida pela que
     * chegou. Empurrá-la a tela inteira faria parecer que ela foi descartada, quando na verdade ela
     * está esperando o botão voltar.
     */
    private const val RECUO = 4

    // -----------------------------------------------------------------------
    // Empilhar: abrir um detalhe e voltar dele
    // -----------------------------------------------------------------------

    fun empilharEntra(escopo: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        escopo.slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(RAPIDO, easing = EMPURRA),
        ) + fadeIn(tween(RAPIDO))

    fun empilharSai(escopo: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        escopo.slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(RAPIDO, easing = EMPURRA),
            targetOffset = { it / RECUO },
        ) + fadeOut(tween(MAIS_RAPIDO))

    fun desempilharEntra(escopo: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        escopo.slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(RAPIDO, easing = EMPURRA),
            initialOffset = { it / RECUO },
        ) + fadeIn(tween(RAPIDO))

    fun desempilharSai(escopo: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        escopo.slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(RAPIDO, easing = EMPURRA),
        ) + fadeOut(tween(MAIS_RAPIDO))

    // -----------------------------------------------------------------------
    // Trocar de aba: sem direção, porque não há "de onde"
    // -----------------------------------------------------------------------

    /**
     * A escala é sutil de propósito (92% a 100%). Ela não deve ser vista como zoom: serve só para o
     * fade não parecer que a tela piscou.
     */
    val abaEntra: EnterTransition =
        fadeIn(tween(RAPIDO)) + scaleIn(tween(RAPIDO, easing = EMPURRA), initialScale = 0.92f)

    val abaSai: ExitTransition =
        fadeOut(tween(MAIS_RAPIDO, easing = SOME)) +
            scaleOut(tween(MAIS_RAPIDO), targetScale = 0.92f)

    // -----------------------------------------------------------------------
    // Entrar numa tarefa: execução, check-in, paywall
    // -----------------------------------------------------------------------

    /**
     * Sobe de baixo, como um painel.
     *
     * Estas telas já escondem a barra de abas (`mostrarAbas`), e o comentário que justifica isso no
     * `AppNavHost` diz o porquê: *"o usuário está numa tarefa, não navegando"*. O movimento vertical
     * é a mesma frase, dita antes de a tela aparecer.
     *
     * > **Tela que esconde a navegação deveria chegar por um caminho diferente dela.**
     */
    val tarefaEntra: EnterTransition =
        slideInVertically(tween(RAPIDO, easing = EMPURRA)) { it } + fadeIn(tween(RAPIDO))

    val tarefaSai: ExitTransition =
        slideOutVertically(tween(RAPIDO, easing = EMPURRA)) { it } + fadeOut(tween(MAIS_RAPIDO))

    // -----------------------------------------------------------------------
    // Sem movimento
    // -----------------------------------------------------------------------

    /**
     * Splash e a saída para o Login não animam.
     *
     * O Splash decide para onde ir antes de qualquer pixel importar, e animar a saída dele faria a
     * abertura do app parecer mais lenta do que é. O logout usa `popUpTo(0)`, que apaga a pilha: não
     * existe relação espacial entre a tela de onde se saiu e a de login.
     *
     * > **Animar uma transição que não tem "de onde" nem "para onde" é decorar uma frase sem verbo.**
     */
    val semMovimento: EnterTransition = EnterTransition.None
    val semMovimentoSaindo: ExitTransition = ExitTransition.None

    // -----------------------------------------------------------------------
    // Quem decide: as quatro funções que o NavHost chama
    // -----------------------------------------------------------------------

    /**
     * ⭐ **A escolha é feita AQUI, lendo as rotas, e não anotada nos 36 `composable`.**
     *
     * Anotar destino a destino seria o caminho óbvio e é o errado: 36 lugares para manter, e a
     * rota 37 nasce sem movimento nenhum — o defeito aparece numa tela só, que é o modo mais caro
     * de aparecer. É a mesma lição que o `AppNavHost` acabou de registrar sobre os insets.
     *
     * > **Regra que se repete por destino não é regra: é 36 oportunidades de divergir.**
     *
     * O preço é que a classificação depende de listas de rotas. Elas estão logo abaixo, curtas e
     * com o critério escrito — e o critério é o mesmo `mostrarAbas` que o `AppNavHost` já usa para
     * decidir quem vê a barra de abas. As duas decisões respondem à mesma pergunta.
     */
    fun entrada(escopo: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        escopo.envolveTelaSemPilha() -> semMovimento
        escopo.entreAbas() -> abaEntra
        escopo.targetState.ehTarefa() -> tarefaEntra
        else -> empilharEntra(escopo)
    }

    fun saida(escopo: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        escopo.envolveTelaSemPilha() -> semMovimentoSaindo
        escopo.entreAbas() -> abaSai
        escopo.targetState.ehTarefa() -> ExitTransition.None
        else -> empilharSai(escopo)
    }

    fun entradaVoltando(escopo: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = when {
        escopo.envolveTelaSemPilha() -> semMovimento
        escopo.initialState.ehTarefa() -> EnterTransition.None
        else -> desempilharEntra(escopo)
    }

    fun saidaVoltando(escopo: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = when {
        escopo.envolveTelaSemPilha() -> semMovimentoSaindo
        escopo.initialState.ehTarefa() -> tarefaSai
        else -> desempilharSai(escopo)
    }

    // -----------------------------------------------------------------------
    // Classificação
    // -----------------------------------------------------------------------

    /**
     * As telas de TAREFA: a pessoa parou de navegar e está fazendo uma coisa.
     *
     * É a mesma lista que o `AppNavHost` descreve ao esconder a barra de abas, e por isso o
     * critério para entrar aqui é o mesmo: **ocupa a tela inteira porque tem um trabalho em curso**,
     * não porque é fundo de pilha.
     *
     * O Quiz e o Nome ficam de FORA de propósito, mesmo escondendo as abas: eles são uma sequência
     * de passos, e passo que sobe de baixo a cada pergunta vira um elevador.
     */
    private fun NavBackStackEntry.ehTarefa(): Boolean =
        destination.hasRoute(AppRoute.WorkoutSession::class) ||
            destination.hasRoute(AppRoute.CheckIn::class) ||
            destination.hasRoute(AppRoute.Paywall::class)

    /** As quatro raízes, lidas do mesmo enum que desenha a barra. Uma fonte, não duas. */
    private fun NavBackStackEntry.ehAba(): Boolean =
        BottomTab.entries.any { destination.hasRoute(it.routeClass) }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.entreAbas(): Boolean =
        initialState.ehAba() && targetState.ehAba()

    /**
     * Splash e Login não participam de pilha nenhuma.
     *
     * O Splash decide o destino antes de a tela importar, e o logout faz `popUpTo(0)`, que apaga
     * tudo. Nos dois casos não existe "de onde" nem "para onde" — animar seria decorar uma frase
     * sem verbo, e no caso do Splash ainda faria a abertura do app parecer mais lenta.
     */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.envolveTelaSemPilha(): Boolean =
        listOf(initialState, targetState).any {
            it.destination.hasRoute(AppRoute.Splash::class) ||
                it.destination.hasRoute(AppRoute.Login::class)
        }
}
