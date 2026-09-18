package dev.rafael.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.rafael.app.navigation.AppNavHost
import dev.rafael.app.idioma.IdiomaDoAparelho
import dev.rafael.app.push.DestinoDePush
import dev.rafael.core.designsystem.FitJourneyTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /**
     * O que a notificação pediu para abrir (F.1).
     *
     * `MutableStateFlow` e não parâmetro do Composable: a Activity é `singleTop`, então tocar
     * numa notificação com o app ABERTO chama `onNewIntent` — a tela já está composta e não seria
     * recriada com o valor novo. Um fluxo é o que faz os dois caminhos chegarem no mesmo lugar.
     */
    private val destinoDoPush = MutableStateFlow<DestinoDePush?>(null)

    /**
     * Aplica o idioma escolhido ANTES de qualquer recurso ser lido (G.4, ARCH #37).
     *
     * Só faz efeito abaixo do Android 13; do 33 em diante o sistema já entrega o contexto no
     * idioma certo. Tem de ser aqui e não no `onCreate`: quando o `onCreate` roda, o tema e os
     * primeiros recursos já foram resolvidos, e resolvidos no idioma antigo.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(IdiomaDoAparelho.envolver(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lerDestino(intent)   // app estava FECHADO: o intent veio no onCreate

        setContent {
            FitJourneyTheme {
                AppNavHost(destinoDoPush = destinoDoPush)
            }
        }
    }

    /** App já estava ABERTO quando a notificação foi tocada. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lerDestino(intent)
    }

    private fun lerDestino(intent: Intent?) {
        // O tipo E os ids (fatia F): "alguém comentou" precisa abrir AQUELE check-in, não a lista
        // de grupos. Os ids já viajavam no push desde a F.1 — o que faltava era ler mais de um.
        destinoDoPush.value = DestinoDePush.de(intent)
    }
}
