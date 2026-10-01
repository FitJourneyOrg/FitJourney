package dev.rafael.app.screens.splash

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.rafael.app.R
import dev.rafael.app.navigation.AppRoute
import org.koin.androidx.compose.koinViewModel

/**
 * A mesma tela nos dois momentos da abertura. O que muda é [posLogin], e o que ele muda é **quanto
 * a tela espera** antes de liberar o destino -- ver `SplashViewModel`.
 */
@Composable
fun SplashScreen(
    onDecided: (AppRoute) -> Unit,
    posLogin: Boolean = false,
    viewModel: SplashViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // `posLogin` é da ROTA e entra por aqui, não pelo construtor (ver `SplashViewModel.iniciar`).
    // `Unit` como chave: recompor não é motivo pra recomeçar o preparo.
    LaunchedEffect(Unit) { viewModel.iniciar(posLogin) }

    LaunchedEffect(state) {
        (state as? SplashState.Decided)?.let { onDecided(it.destination) }
    }

    // `Scaffold` sem barra nenhuma: ele existe aqui SÓ para aplicar os insets. Ver a [REGRA]
    // no KDoc do `AppNavHost` -- o Scaffold de lá não reserva inset para si, então tela sem
    // Scaffold próprio desenha por baixo da status bar.
    Scaffold { insetsDaTela ->
        Column(
            Modifier.fillMaxSize().padding(insetsDaTela).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MarcaDoApp()
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))

            // Altura reservada mesmo sem texto: sem isto a marca e o indicador PULAM quando a
            // primeira frase aparece, e um salto no primeiro segundo do app é a pior hora pra ele.
            Box(Modifier.heightIn(min = 24.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = state, label = "passo") { atual ->
                    val passo = (atual as? SplashState.Preparando)?.passo
                    Text(
                        text = passo?.let { stringResource(it.rotulo()) }.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * ⬅️ **SLOT DA MARCA.** Hoje é o nome do app tipografado, porque o projeto ainda não tem asset de
 * logo (só o `ic_launcher`, que é ícone de sistema e não serve como marca de tela).
 *
 * Quando o arquivo chegar, isto vira um `Image(painterResource(R.drawable.logo), ...)` e nada mais
 * na tela precisa mudar.
 */
@Composable
private fun MarcaDoApp() {
    Text(
        stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * O passo é um enum do ViewModel e o TEXTO mora no `strings.xml` ([REGRA] do #37). `when`
 * exaustivo pelo mesmo motivo de todo enum de texto do app: passo novo quebra o BUILD até alguém
 * escrever a frase nos dois idiomas, em vez de aparecer na tela como `EXERCICIOS`.
 */
private fun PassoDoPreparo.rotulo(): Int = when (this) {
    PassoDoPreparo.PERFIL -> R.string.preparando_perfil
    PassoDoPreparo.PROGRAMAS -> R.string.preparando_programas
    PassoDoPreparo.PROGRESSO -> R.string.preparando_progresso
    PassoDoPreparo.EXERCICIOS -> R.string.preparando_exercicios
}
