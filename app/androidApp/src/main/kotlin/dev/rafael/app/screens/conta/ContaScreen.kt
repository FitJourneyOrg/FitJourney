package dev.rafael.app.screens.conta

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.erroDoCampo
import dev.rafael.core.result.AppError
import org.koin.androidx.compose.koinViewModel

/**
 * Configurações da conta (ARCH #34): nome, e-mail, plano e sair.
 *
 * É a tela PRIVADA. O e-mail aparece aqui e em nenhum outro lugar — [REGRA] #33: ele nunca
 * atravessa a fronteira do grupo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContaScreen(
    onBack: () -> Unit,
    onSaiu: () -> Unit,
    viewModel: ContaViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saiu by viewModel.saiu.collectAsStateWithLifecycle()
    var confirmarSaida by remember { mutableStateOf(false) }

    LaunchedEffect(saiu) { if (saiu) onSaiu() }

    if (confirmarSaida) {
        AlertDialog(
            onDismissRequest = { confirmarSaida = false },
            title = { Text("Sair da conta?") },
            text = { Text("Você precisará entrar de novo para acessar seus programas.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSaida = false
                    viewModel.sair()
                }) { Text("Sair") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSaida = false }) { Text("Cancelar") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "SEU NOME",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (state.editando) {
                // O erro do campo vem do servidor em `fieldErrors["displayName"]` — o mesmo
                // mecanismo do #31. Sem ele, "Dados inválidos" no rodapé deixaria o usuário
                // adivinhando o que houve com um formulário de um campo só.
                val erroDoNome = state.erro.erroDoCampo("displayName")
                OutlinedTextField(
                    value = state.rascunho,
                    onValueChange = viewModel::aoDigitar,
                    label = { Text("Nome") },
                    singleLine = true,
                    isError = erroDoNome != null,
                    supportingText = {
                        Text(erroDoNome ?: "Como as outras pessoas vão te ver nos grupos.")
                    },
                    enabled = !state.salvando,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Erro que NÃO é do campo (sem rede, 500) não cabe no supportingText: ele fala
                // do formulário inteiro, não do que foi digitado.
                val erro = state.erro
                if (erro != null && erro !is AppError.Validation) {
                    Spacer(Modifier.height(6.dp))
                    ErroInline(erro)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::salvar, enabled = state.podeSalvar) {
                        if (state.salvando) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Salvar")
                        }
                    }
                    OutlinedButton(onClick = viewModel::cancelar, enabled = !state.salvando) {
                        Text("Cancelar")
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.nome.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = viewModel::editar) { Text("Editar") }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Linha("E-mail", state.email ?: "—")
            Spacer(Modifier.height(14.dp))
            Linha("Plano", if (state.premium) "Premium" else "Grátis")

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            TextButton(onClick = { confirmarSaida = true }) {
                Text("Sair da conta", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Linha(rotulo: String, valor: String) {
    Column {
        Text(
            rotulo,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Text(valor, style = MaterialTheme.typography.bodyLarge)
    }
}
