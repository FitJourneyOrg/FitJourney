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
import dev.rafael.app.ui.DialogoDeSaida
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.erroDoCampo
import dev.rafael.core.result.AppError
import org.koin.androidx.compose.koinViewModel
import dev.rafael.app.R
import androidx.compose.ui.res.stringResource

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
    onVerBloqueados: () -> Unit,
    onIdioma: () -> Unit,
    viewModel: ContaViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saiu by viewModel.saiu.collectAsStateWithLifecycle()
    var confirmarSaida by remember { mutableStateOf(false) }

    LaunchedEffect(saiu) { if (saiu) onSaiu() }

    if (confirmarSaida) {
        DialogoDeSaida(
            onConfirmar = { confirmarSaida = false; viewModel.sair() },
            onCancelar = { confirmarSaida = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conta_titulo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.comum_voltar),
                        )
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
                stringResource(R.string.comum_seu_nome),
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
                    label = { Text(stringResource(R.string.comum_nome)) },
                    singleLine = true,
                    isError = erroDoNome != null,
                    supportingText = {
                        Text(erroDoNome ?: stringResource(R.string.conta_nome_ajuda))
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
                            Text(stringResource(R.string.comum_salvar))
                        }
                    }
                    OutlinedButton(onClick = viewModel::cancelar, enabled = !state.salvando) {
                        Text(stringResource(R.string.comum_cancelar))
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.nome.ifBlank { stringResource(R.string.comum_sem_valor) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(onClick = viewModel::editar) {
                        Text(stringResource(R.string.comum_editar))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Linha(
                stringResource(R.string.conta_email),
                state.email ?: stringResource(R.string.comum_sem_valor),
            )
            Spacer(Modifier.height(14.dp))
            Linha(
                stringResource(R.string.conta_plano),
                stringResource(if (state.premium) R.string.comum_premium else R.string.comum_gratis),
            )

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            /*
             * BLOQUEADOS (#35) mora em Configurações da conta, não no perfil.
             *
             * É configuração de privacidade, não vitrine — e é aqui que quem esqueceu que
             * bloqueou alguém descobre por que o app recusou um "Adicionar". A mensagem de erro
             * não conta isso de propósito, para o bloqueio não virar recado.
             */
            TextButton(onClick = onVerBloqueados) { Text(stringResource(R.string.comum_bloqueados)) }

            /*
             * IDIOMA aparece aqui E na gaveta, por decisão de 2026-09-11.
             *
             * Duas entradas para a MESMA tela não são duas fontes de verdade: a preferência mora
             * num lugar só (`IdiomaDoAparelho`), e estas são portas. A duplicação que custaria
             * caro seria duas telas, ou duas gravações.
             *
             * A entrada da gaveta é a que serve a quem não lê o idioma atual, porque lá existe o
             * ícone de globo. Esta aqui é a que serve a quem procura onde ficam os ajustes.
             */
            TextButton(onClick = onIdioma) { Text(stringResource(R.string.comum_idioma)) }

            TextButton(onClick = { confirmarSaida = true }) {
                Text(stringResource(R.string.conta_sair), color = MaterialTheme.colorScheme.error)
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
