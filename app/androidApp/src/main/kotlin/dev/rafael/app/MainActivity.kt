package dev.rafael.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.rafael.app.navigation.AppNavHost
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
    private val destinoDoPush = MutableStateFlow<String?>(null)

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
        // Só o TIPO: é ele que decide a tela. O `fromUserId` viaja junto no push e fica
        // disponível para quando algum tipo precisar de destino mais específico.
        destinoDoPush.value = intent?.getStringExtra("tipo")
    }
}
