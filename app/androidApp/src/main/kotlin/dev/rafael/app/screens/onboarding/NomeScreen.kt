package dev.rafael.app.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.erroDoCampo
import dev.rafael.app.ui.erroGeral
import org.koin.androidx.compose.koinViewModel

/**
 * "Como podemos te chamar?" — primeiro passo do onboarding (decisão 1-A.2).
 *
 * O campo já vem preenchido: o nome nasceu com a conta, derivado do e-mail. A pessoa confirma
 * ou ajusta. É deliberado que dê para simplesmente tocar em "Continuar" — obrigar todo mundo a
 * digitar um nome que já está certo é atrito no pior lugar possível, que é a primeira tela
 * depois do cadastro.
 */
@Composable
fun NomeScreen(
    onPronto: () -> Unit,
    viewModel: NomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Guarda o nome que chegou do servidor para saber se HOUVE mudança — sem isso, "Continuar"
    // dispararia um PATCH mesmo quando nada foi editado.
    var original by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.carregando, state.nome) {
        if (!state.carregando && original == null && state.nome.isNotBlank()) original = state.nome
    }
    LaunchedEffect(state.pronto) { if (state.pronto) onPronto() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Como podemos te chamar?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "É esse nome que aparece para as outras pessoas nos desafios em grupo. Dá para mudar depois.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.nome,
            onValueChange = viewModel::aoDigitar,
            label = { Text("Seu nome") },
            singleLine = true,
            enabled = !state.carregando && !state.salvando,
            isError = state.erro.erroDoCampo("displayName") != null,
            supportingText = { state.erro.erroDoCampo("displayName")?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        state.erro.erroGeral(setOf("displayName"))?.let {
            Spacer(Modifier.height(8.dp))
            ErroInline(it)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.continuar(original.orEmpty()) },
            enabled = !state.carregando && !state.salvando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.salvando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text("Continuar")
        }
    }
}
