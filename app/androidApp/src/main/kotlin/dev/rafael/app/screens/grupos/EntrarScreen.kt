package dev.rafael.app.screens.grupos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.rafael.app.ui.ErroInline
import dev.rafael.contract.group.GroupPreviewDto
import dev.rafael.contract.group.GroupRule
import dev.rafael.contract.group.JoinBlock
import dev.rafael.core.network.HttpClientFactory
import org.koin.androidx.compose.koinViewModel

/**
 * Entrar num desafio: digitar o código (ou chegar por link) e ver o **preview** antes de aceitar
 * (2-B.0).
 *
 * O preview mostra as regras obrigatórias porque é ali que o opt-in do #17 acontece — a pessoa
 * decide sabendo que o grupo vai exigir localização, e não descobre depois.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrarScreen(
    inviteToken: String? = null,
    onBack: () -> Unit,
    onEntrou: () -> Unit,
    viewModel: EntrarViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(inviteToken) { inviteToken?.let(viewModel::buscarPorConvite) }
    LaunchedEffect(state.entrou) { if (state.entrou) onEntrou() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entrar num desafio") },
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
            // Quem chegou por link não digita nada: o campo só existe para a entrada por código.
            if (inviteToken == null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.codigo,
                    onValueChange = viewModel::aoDigitarCodigo,
                    label = { Text("Código do desafio") },
                    singleLine = true,
                    supportingText = { Text("6 caracteres, como aparece para quem convidou você.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = viewModel::buscarPorCodigo,
                    enabled = state.codigo.length == 6 && !state.carregando,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.carregando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Procurar")
                }
            }

            state.erro?.let {
                Spacer(Modifier.height(12.dp))
                ErroInline(it)
            }

            state.preview?.let { preview ->
                Spacer(Modifier.height(20.dp))
                Preview(
                    preview = preview,
                    entrando = state.entrando,
                    onEntrar = { viewModel.entrar(inviteToken) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Preview(preview: GroupPreviewDto, entrando: Boolean, onEntrar: () -> Unit) {
    Column {
        // O banner vem do servidor: todo grupo já nasce com um padrão, então o convite nunca
        // aparece "pelado" — e o convite é o gargalo do produto (2-B.0).
        preview.bannerUrl?.let { url ->
            AsyncImage(
                model = HttpClientFactory.BASE_URL.removeSuffix("/") + url,
                contentDescription = null,
                // PROPORÇÃO fixa, não altura fixa. Com altura cravada, a razão largura/altura
                // muda em cada aparelho (2,3:1 num celular estreito, 2,8:1 num largo) e nenhuma
                // imagem serve para todos sem cortar. Com `aspectRatio` + `Crop`, uma arte na
                // MESMA proporção não é cortada em lugar nenhum — só redimensionada.
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BANNER_RATIO)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(14.dp))
        }

        Text(preview.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        preview.description?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "${preview.memberCount} ${if (preview.memberCount == 1) "pessoa" else "pessoas"} · " +
                "${preview.startDate} a ${preview.endDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "O dia vira no fuso ${preview.timezone}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (preview.rules.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                "PARA O CHECK-IN VALER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            // AQUI é o opt-in do #17: a exigência aparece ANTES do aceite.
            preview.rules.forEach { Text("· ${it.emPortugues()}", style = MaterialTheme.typography.bodyMedium) }
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onEntrar,
            enabled = preview.joinable && !entrando,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (entrando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text("Entrar no desafio")
        }

        // Botão desabilitado sem explicação é um mistério. O motivo vem do servidor como enum,
        // e a frase é escolhida aqui — quem conhece a plataforma escreve o texto (#31).
        preview.blockedReason?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it.emPortugues(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Proporção do banner de grupo: **2,5 : 1**.
 *
 * Vale para toda tela que mostrar banner (preview do convite, detalhe do grupo na A.4, cartão da
 * lista se um dia tiver). Ter a constante aqui é o que impede duas telas divergirem e passarem a
 * exigir duas artes diferentes da mesma imagem.
 *
 * Arte esperada: **1600 × 640 px**.
 */
const val BANNER_RATIO = 2.5f

private fun GroupRule.emPortugues(): String = when (this) {
    GroupRule.FOTO -> "Uma foto, tirada na hora pelo app"
    GroupRule.LOCALIZACAO -> "O nome do lugar onde você treinou"
    GroupRule.EMOJI_DO_DIA -> "Reproduzir o emoji do dia na foto"
    GroupRule.GYM_PASS -> "Gympass (ainda não disponível)"
}

private fun JoinBlock.emPortugues(): String = when (this) {
    JoinBlock.JA_COMECOU -> "Este desafio já começou — a entrada fecha quando ele começa."
    JoinBlock.ENCERRADO -> "Este desafio já terminou."
    JoinBlock.LOTADO -> "Este desafio já tem 50 participantes."
    JoinBlock.JA_E_MEMBRO -> "Você já participa deste desafio."
    JoinBlock.CONVITE_INVALIDO -> "Este link expirou ou foi revogado. Peça o código do desafio."
}
