package dev.rafael.app.screens.amigos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.rafael.app.data.amizades.Amizades
import dev.rafael.app.ui.AvatarInicial
import dev.rafael.app.ui.ErroInline
import dev.rafael.contract.friendship.PersonDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class BloqueadosState(
    val pessoas: List<PersonDto> = emptyList(),
    val carregando: Boolean = true,
    val ocupado: Boolean = false,
    val erro: AppError? = null,
)

class BloqueadosViewModel(private val amizades: Amizades) : ViewModel() {

    private val _state = MutableStateFlow(BloqueadosState())
    val state: StateFlow<BloqueadosState> = _state.asStateFlow()

    fun carregar() {
        viewModelScope.launch {
            _state.update { it.copy(carregando = true) }
            when (val r = amizades.bloqueados()) {
                is AppResult.Success ->
                    _state.update { it.copy(pessoas = r.value, carregando = false, erro = null) }
                is AppResult.Failure ->
                    _state.update { it.copy(carregando = false, erro = r.error) }
            }
        }
    }

    fun desbloquear(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(ocupado = true, erro = null) }
            when (val r = amizades.desbloquear(userId)) {
                is AppResult.Success -> { _state.update { it.copy(ocupado = false) }; carregar() }
                is AppResult.Failure -> _state.update { it.copy(ocupado = false, erro = r.error) }
            }
        }
    }
}

/**
 * Configurações da conta → **Bloqueados** (#35).
 *
 * ## Por que esta tela existe, e não é refino
 *
 * A frase que o servidor devolve quando alguém tenta adicionar uma pessoa bloqueada é vaga de
 * propósito — *"Não é possível adicionar esta pessoa"* —, para o bloqueio não virar recado.
 *
 * O efeito colateral: **quem bloqueou alguém e esqueceu fica sem entender a recusa.** Esta lista
 * é onde essa informação pertence. A alternativa seria a mensagem de erro contar de que lado veio
 * a barreira, e aí ela contaria também para quem não deveria saber.
 *
 * Mostra o nome real porque quem bloqueou continua vendo o perfil (emenda 35.6, assimétrica) —
 * sem isso a lista seria uma fileira de "Usuário" e desbloquear viraria adivinhação.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloqueadosScreen(
    onBack: () -> Unit,
    viewModel: BloqueadosViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.carregar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bloqueados") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        if (state.pessoas.isEmpty() && !state.carregando) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Você não bloqueou ninguém.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.erro?.let { erro -> item { ErroInline(erro, Modifier.padding(bottom = 8.dp)) } }

            items(state.pessoas, key = { it.userId }) { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarInicial(nome = p.displayName, id = p.userId, tamanho = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        p.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { viewModel.desbloquear(p.userId) },
                        enabled = !state.ocupado,
                    ) { Text("Desbloquear") }
                }
            }

            item {
                Spacer(Modifier.padding(top = 16.dp))
                Text(
                    // Dito aqui porque é o que a pessoa quer saber ao desbloquear alguém: o
                    // desbloqueio devolve o acesso, não a amizade.
                    "Desbloquear não restaura a amizade — se quiserem, um dos dois envia um " +
                        "pedido novo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
