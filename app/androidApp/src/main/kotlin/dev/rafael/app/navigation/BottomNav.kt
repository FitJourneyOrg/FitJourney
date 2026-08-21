package dev.rafael.app.navigation

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
 */
enum class BottomTab(
    val label: String,
    val icon: ImageVector,
    val route: AppRoute,
    val routeClass: KClass<out AppRoute>,
) {
    INICIO("Início", Icons.Filled.Home, AppRoute.Home, AppRoute.Home::class),
    TREINO("Treino", Icons.Outlined.FitnessCenter, AppRoute.Programs, AppRoute.Programs::class),
    GRUPOS("Grupos", Icons.Outlined.Group, AppRoute.Grupos, AppRoute.Grupos::class),
    PROGRESSO("Progresso", Icons.Outlined.BarChart, AppRoute.Progresso, AppRoute.Progresso::class),
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
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
