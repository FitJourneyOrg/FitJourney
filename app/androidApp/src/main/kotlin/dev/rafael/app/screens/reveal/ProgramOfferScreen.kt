package dev.rafael.app.screens.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Última tela do onboarding: pergunta se o usuário QUER o primeiro programa.
 *
 * POR QUE existe: antes o `ProgramRevealViewModel` chamava `generate()` no `init`, então o
 * programa era criado e gravado no servidor no instante em que a tela abria — sem ninguém
 * pedir. Quem não queria ficava com um plano pendurado na conta e sem caminho óbvio pra Home.
 *
 * POR QUE fica AQUI e não antes do quiz: o quiz não é sobre o programa, é sobre o PERFIL.
 * Ele define ambiente (ARCH #28), idade (gate do #24) e limitações — coisas que a Home e a
 * biblioteca de exercícios usam. Pular o quiz deixaria `onboardingCompleted = false` e a
 * Splash devolveria o usuário pro quiz em toda abertura, além de quebrar `GET /exercises`
 * por falta de ambiente. Programa é consequência opcional do perfil; perfil não é opcional.
 *
 * O ARCH #23 (value-first) continua intacto para quem aceita: gera → revela → paywall.
 */
@Composable
fun ProgramOfferScreen(
    onGerar: () -> Unit,
    onPular: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,   // roxo = IA ([REGRA] ARCH #16)
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Quer que a gente monte seu primeiro programa?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Usamos suas respostas para montar uma semana de treinos sob medida. " +
                "Se preferir, você pode explorar o app primeiro e criar seus treinos na mão.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onGerar, modifier = Modifier.fillMaxWidth()) {
            Text("Montar meu programa")
        }
        TextButton(onClick = onPular, modifier = Modifier.fillMaxWidth()) {
            Text("Agora não, quero explorar")
        }
    }
}
