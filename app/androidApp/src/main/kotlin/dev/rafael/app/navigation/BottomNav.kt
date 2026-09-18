package dev.rafael.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.rafael.app.R
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlin.reflect.KClass

/**
 * As 4 abas do app. `route` é a rota-raiz de cada aba.
 *
 * Eram cinco: **Perfil saiu** (ARCH #34) e virou item do menu lateral. As abas são para tarefa
 * frequente; perfil e configurações são destino secundário, e manter os dois conjuntos
 * disjuntos é o que permite bottom bar e drawer conviverem sem disputar o mesmo destino.
 *
 * ## ⭐ `rotulo` é `@StringRes`, e a razão é uma cicatriz
 *
 * Até 2026-09-11 isto era `val label: String` com as quatro palavras em português cravadas no
 * enum. A barra de baixo — **o componente mais visível do app** — atravessou a fatia G.3 inteira
 * em português, e apareceu assim no primeiro teste com o app em inglês.
 *
 * A extração não pegou porque nenhuma das duas metades parecia texto de UI:
 *
 * | onde | o que a varredura via |
 * |---|---|
 * | `INICIO("Início", ...)` | um literal em argumento de construtor, longe de qualquer `Text(` |
 * | `Text(tab.label)` | uma chamada sem literal nenhum |
 *
 * > **Texto guardado como propriedade de enum é invisível para quem procura texto em posição de**
 * > **UI: o literal não está na tela, e a tela não tem literal.**
 *
 * Quarta ocorrência desta família nesta base — antes foram `MuscleGroup`, `Environment` e
 * `ExerciseCategory`. O `EnumsSemTextoTest` existe para que não haja uma quinta.
 *
 * `Int` e não `String` pelo mesmo motivo do `ui/Rotulos.kt`: mantém o enum Kotlin puro, sem
 * `Context` e sem `@Composable`, e resolvível em teste de unidade.
 */
enum class BottomTab(
    @StringRes val rotulo: Int,
    val icon: ImageVector,
    val route: AppRoute,
    val routeClass: KClass<out AppRoute>,
) {
    INICIO(R.string.nav_aba_inicio, Icons.Filled.Home, AppRoute.Home, AppRoute.Home::class),
    TREINO(R.string.nav_aba_treino, Icons.Outlined.FitnessCenter, AppRoute.Programs, AppRoute.Programs::class),
    GRUPOS(R.string.nav_aba_grupos, Icons.Outlined.Group, AppRoute.Grupos, AppRoute.Grupos::class),
    PROGRESSO(R.string.nav_aba_progresso, Icons.Outlined.BarChart, AppRoute.Progresso, AppRoute.Progresso::class),
}

/**
 * Barra de navegação das abas. Só aparece nas telas-raiz (ver `AppNavHost`): telas de
 * detalhe/execução ocupam a tela inteira, sem a barra.
 */
@Composable
fun FitJourneyBottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val destino = entry?.destination

    NavigationBar {
        BottomTab.entries.forEach { tab ->
            val selecionada = destino?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
            NavigationBarItem(
                selected = selecionada,
                onClick = {
                    if (!selecionada) {
                        nav.navigate(tab.route) {
                            // volta pra raiz da aba atual sem empilhar cópias
                            popUpTo(AppRoute.Home) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                // A mesma chave nos dois: o ícone e o rótulo nomeiam a MESMA aba, e o leitor de
                // tela lê os dois em sequência. Duas frases diferentes ali viram repetição confusa.
                icon = { Icon(tab.icon, contentDescription = stringResource(tab.rotulo)) },
                label = { Text(stringResource(tab.rotulo)) },
            )
        }
    }
}
