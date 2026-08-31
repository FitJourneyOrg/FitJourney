package dev.rafael.app.push

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

/**
 * Pede a permissão de notificação uma vez, assim que existe sessão (F.1).
 *
 * ## Por que ao ENTRAR, e não na tela de amigos
 *
 * Decisão do Rafael em 2026-08-27: **tudo que for notificação passa pelo ícone da barra**, que
 * existe em toda tela-raiz. Pedir a permissão só quando alguém abre Amigos deixaria sem push
 * quem recebe um pedido antes disso — e o pedido recebido é justamente o caso principal.
 *
 * ## Quem chama é o `AppNavHost`, atrelado ao `uidFlow`
 *
 * A primeira versão morava na `LoginScreen`, e a bateria da F.1 mostrou o defeito: o
 * `LaunchedEffect` abaixo dispara quando a TELA COMPÕE, então o diálogo aparecia antes de a
 * pessoa digitar a senha — e quem tinha sessão restaurada nunca via a `LoginScreen`, logo nunca
 * era perguntado. O gatilho certo é "existe usuário logado", não "abri a tela de login".
 *
 * ## O que acontece se negar
 *
 * **Nada quebra.** A notificação continua GRAVADA no servidor (V42) e a central a mostra quando
 * o app abre. O push é o aviso; a central é a verdade. É por isso que este componente não insiste,
 * não explica, e não mostra tela de justificativa: negar é uma escolha legítima com consequência
 * pequena.
 *
 * Abaixo do Android 13 a permissão não existe e a notificação é permitida por padrão — o
 * `LaunchedEffect` sai sem fazer nada.
 */
@Composable
fun PedirPermissaoDeNotificacao() {
    val contexto = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* concedida ou não, o app segue igual — ver KDoc */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect

        val jaTem = ContextCompat.checkSelfPermission(
            contexto,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        // Só pede se ainda não tem. O sistema ignora o segundo pedido depois de uma negativa
        // definitiva, então insistir aqui seria código que não faz nada.
        if (!jaTem) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
