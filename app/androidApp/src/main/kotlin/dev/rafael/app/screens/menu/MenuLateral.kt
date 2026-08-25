package dev.rafael.app.screens.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.ui.AvatarInicial
import dev.rafael.app.ui.DialogoDeSaida
import dev.rafael.app.ui.shimmer
import org.koin.androidx.compose.koinViewModel

/**
 * MENU LATERAL (ARCH #34) — os destinos que não são tarefa do dia a dia.
 *
 * Por que ele e a barra de abas convivem, se o Material desaconselha os dois juntos: os
 * conjuntos são DISJUNTOS. As abas são as quatro tarefas frequentes (início, treino, grupos,
 * progresso); aqui ficam perfil, biblioteca, ajuda e configurações. Nenhum destino aparece nos
 * dois — que é exatamente o problema que a recomendação do Material quer evitar.
 *
 * [REGRA] Item de menu NAVEGA. Interruptor (tema, notificação) não entra aqui: quebra a
 * expectativa do componente e não entra no back stack. Vai em Configurações da conta.
 */
@Composable
fun MenuLateral(
    /**
     * O menu está aberto AGORA. Serve para reavaliar a chave do cache (ver `MenuViewModel`):
     * este composable entra em composição antes do login, e sem isto o cabeçalho fica preso ao
     * uid nulo — foi o defeito do "?" eterno.
     */
    aberto: Boolean,
    onSaiu: () -> Unit,
    onPerfil: () -> Unit,
    onExercicios: () -> Unit,
    onWiki: () -> Unit,
    onDuvidas: () -> Unit,
    onConta: () -> Unit,
    viewModel: MenuViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saiu by viewModel.saiu.collectAsStateWithLifecycle()
    var confirmarSaida by remember { mutableStateOf(false) }

    // Só sincroniza com sessão viva: pedir dado sem token volta 401, e 401 acorda o
    // SessionExpiryBus, que força navegação para o Login.
    LaunchedEffect(aberto, state.semSessao) { if (aberto && !state.semSessao) viewModel.aoAbrir() }
    // CONSOME o evento: sem isso, `saiu` fica true para sempre e a SEGUNDA saída não dispara,
    // porque este ViewModel atravessa logout e login sem ser recriado.
    LaunchedEffect(saiu) { if (saiu) { onSaiu(); viewModel.consumirSaida() } }

    if (confirmarSaida) {
        DialogoDeSaida(
            onConfirmar = { confirmarSaida = false; viewModel.sair() },
            onCancelar = { confirmarSaida = false },
        )
    }

    ModalDrawerSheet(modifier = Modifier.width(288.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {

            // CABEÇALHO, não item: leva ao perfil, mas não é uma linha de lista. É ele que dá
            // ao menu a cara de "sua conta" antes de qualquer tela existir.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ESQUELETO enquanto não há dado. Na primeira instalação o cache está vazio e o
                // nome chega ~1s depois, com a rede. A versão anterior mostrava "?" e "Você" —
                // que PARECEM dado, e por um segundo o app afirmava que o usuário se chama
                // "Você". Esqueleto não afirma nada, que é a verdade nesse instante.
                if (state.semSessao) {
                    // Saiu da conta com o menu aberto. Não há dado e não vai haver — esqueleto
                    // aqui seria um carregamento que nunca termina.
                    Spacer(Modifier.height(44.dp))
                } else if (state.nome.isBlank()) {
                    Box(Modifier.size(44.dp).clip(CircleShape).shimmer())
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(120.dp).height(14.dp).shimmer(RoundedCornerShape(4.dp)))
                        Box(Modifier.width(64.dp).height(11.dp).shimmer(RoundedCornerShape(4.dp)))
                    }
                } else {
                    AvatarInicial(nome = state.nome, id = state.id, tamanho = 44.dp)
                    Column {
                        Text(state.nome, style = MaterialTheme.typography.titleSmall)
                        val nivel = state.nivel
                        if (nivel != null) {
                            Text(
                                "Nível $nivel",
                                style = MaterialTheme.typography.bodySmall,
                                // lime: é recompensa do perfil individual ([REGRA] ARCH #16).
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            ItemDoMenu("Meu perfil", Icons.Outlined.Person, onPerfil)
            ItemDoMenu("Exercícios", Icons.Outlined.FitnessCenter, onExercicios)
            // O selo de fase existe para o item não virar promessa vazia: quem toca e cai num
            // "em breve" sem aviso fica com a sensação de app inacabado.
            ItemDoMenu("Wiki fitness", Icons.AutoMirrored.Outlined.MenuBook, onWiki, selo = "fase 8")
            ItemDoMenu("Dúvidas frequentes", Icons.AutoMirrored.Outlined.HelpOutline, onDuvidas)

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            ItemDoMenu("Configurações da conta", Icons.Outlined.Settings, onConta)

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            // SAIR no rodapé, e ele SAI. Na primeira versão este item navegava para
            // Configurações, porque a sequência de logout só existia lá — eu tinha protegido a
            // regra empurrando o usuário. Um item chamado "Sair" que leva a outra tela mente
            // sobre o que faz.
            ItemDoMenu(rotulo = "Sair", icone = Icons.AutoMirrored.Outlined.Logout, onClick = {confirmarSaida = true})
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ItemDoMenu(
    rotulo: String,
    icone: ImageVector,
    onClick: () -> Unit,
    selo: String? = null,
) {
    NavigationDrawerItem(
        icon = { Icon(icone, contentDescription = null) },
        label = { Text(rotulo) },
        badge = selo?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
