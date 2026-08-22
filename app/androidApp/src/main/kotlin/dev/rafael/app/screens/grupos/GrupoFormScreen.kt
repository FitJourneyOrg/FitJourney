package dev.rafael.app.screens.grupos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.erroDoCampo
import dev.rafael.app.ui.erroGeral
import dev.rafael.contract.group.GroupRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import org.koin.androidx.compose.koinViewModel

/**
 * Criar desafio (decisão 2-A).
 *
 * Só três regras aparecem: `GYM_PASS` fica de fora porque nasce **declarada e indisponível** —
 * oferecer uma opção que o servidor recusa seria uma armadilha.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GrupoFormScreen(
    onBack: () -> Unit,
    onCriado: () -> Unit,
    viewModel: GrupoFormViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var escolhendo by remember { mutableStateOf<CampoDeData?>(null) }

    LaunchedEffect(state.criadoId) { if (state.criadoId != null) onCriado() }

    escolhendo?.let { campo ->
        SeletorDeData(
            inicial = if (campo == CampoDeData.INICIO) state.inicio else state.fim,
            onEscolher = { data ->
                if (campo == CampoDeData.INICIO) viewModel.aoEscolherInicio(data)
                else viewModel.aoEscolherFim(data)
                escolhendo = null
            },
            onFechar = { escolhendo = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Criar desafio") },
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

            OutlinedTextField(
                value = state.titulo,
                onValueChange = viewModel::aoDigitarTitulo,
                label = { Text("Nome do desafio") },
                singleLine = true,
                isError = state.erro.erroDoCampo("title") != null,
                supportingText = { state.erro.erroDoCampo("title")?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.descricao,
                onValueChange = viewModel::aoDigitarDescricao,
                label = { Text("Descrição (opcional)") },
                minLines = 2,
                isError = state.erro.erroDoCampo("description") != null,
                supportingText = { state.erro.erroDoCampo("description")?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Data("Começa em", state.inicio, state.erro.erroDoCampo("startDate")) {
                escolhendo = CampoDeData.INICIO
            }
            Spacer(Modifier.height(10.dp))
            Data("Termina em", state.fim, state.erro.erroDoCampo("endDate")) {
                escolhendo = CampoDeData.FIM
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Fuso do desafio: ${state.fuso}. O dia vira nesse fuso para todo mundo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                "O QUE O CHECK-IN EXIGE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Regra("Foto", GroupRule.FOTO, state.regras, viewModel::alternarRegra)
                Regra("Localização", GroupRule.LOCALIZACAO, state.regras, viewModel::alternarRegra)
                Regra("Emoji do dia", GroupRule.EMOJI_DO_DIA, state.regras, viewModel::alternarRegra)
            }
            state.erro.erroDoCampo("rules")?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Quem entrar vê essas exigências antes de aceitar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Erro que os campos NÃO cobrem: sem rede, 500, ou uma validação de algo que este
            // formulário não desenha (o fuso, por exemplo). Sem isto, a recusa some e o usuário
            // fica com um botão que não faz nada.
            state.erro.erroGeral(CAMPOS_VISIVEIS)?.let {
                Spacer(Modifier.height(12.dp))
                ErroInline(it)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::criar,
                enabled = !state.salvando,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.salvando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("Criar desafio")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Os campos que ESTA tela desenha. O que o servidor recusar fora desta lista precisa aparecer
 * em algum lugar — ver `erroGeral`.
 */
private val CAMPOS_VISIVEIS = setOf("title", "description", "startDate", "endDate", "rules")

private enum class CampoDeData { INICIO, FIM }

@Composable
private fun Data(rotulo: String, valor: LocalDate, erro: String?, onClick: () -> Unit) {
    Column {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text("$rotulo: $valor")
        }
        erro?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Regra(
    rotulo: String,
    regra: GroupRule,
    marcadas: Set<GroupRule>,
    onAlternar: (GroupRule) -> Unit,
) {
    FilterChip(
        selected = regra in marcadas,
        onClick = { onAlternar(regra) },
        label = { Text(rotulo) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorDeData(inicial: LocalDate, onEscolher: (LocalDate) -> Unit, onFechar: () -> Unit) {
    // Converte via instante em UTC, e não por aritmética de epoch-days: o `DatePicker` do
    // Material trabalha em milissegundos UTC, e é ele que define a fronteira.
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
    )
    DatePickerDialog(
        onDismissRequest = onFechar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { millis ->
                    onEscolher(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                }
            }) { Text("Escolher") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    ) {
        DatePicker(state = estado)
    }
}
