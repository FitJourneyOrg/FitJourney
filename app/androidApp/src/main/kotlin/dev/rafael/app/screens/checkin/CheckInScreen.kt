package dev.rafael.app.screens.checkin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.rafael.app.data.checkin.PrecisaoDoLocal
import dev.rafael.app.ui.ErroInline
import dev.rafael.app.ui.erroDoCampo
import dev.rafael.app.ui.erroGeral
import org.koin.androidx.compose.koinViewModel

/**
 * Fazer check-in (fatia B).
 *
 * A tela é **guiada pelas regras do grupo**: exige foto? mostra a câmera. Exige local? mostra o
 * campo. Grupo sem regra nenhuma vira um botão só. As regras foram aceitas na entrada (2-B.0), e
 * é isso que torna a exigência aqui uma escolha informada e não uma imposição descoberta agora.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    groupId: String,
    onBack: () -> Unit,
    onPronto: () -> Unit,
    viewModel: CheckInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    /**
     * A permissão pertence ao SISTEMA — aqui ela é sempre RELIDA, nunca lembrada.
     *
     * A primeira versão guardava a resposta do launcher num `var`. Quem ia às Configurações,
     * concedia e voltava encontrava o app dizendo que não tinha permissão: nada reexecutava a
     * pergunta. Reler no `ON_RESUME` cobre esse caminho e qualquer outro (revogar por fora,
     * "apenas desta vez" expirando).
     */
    var permissoes by remember { mutableStateOf(lerPermissoes(contexto)) }
    var jaPediu by remember { mutableStateOf(false) }

    /**
     * A INTENÇÃO da pessoa, separada da permissão que ela tem.
     *
     * São coisas diferentes: ela pode querer exatidão e ainda não ter concedido, ou ter concedido
     * e voltar para o bairro. Guardar isso na tela — e não no ViewModel — porque é estado de
     * interação, some com a tela e não sobrevive a nada que importe.
     */
    var querExata by remember { mutableStateOf(false) }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissoes = lerPermissoes(contexto)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissoes = lerPermissoes(contexto) }

    LaunchedEffect(groupId) { viewModel.carregar(groupId) }
    LaunchedEffect(state.pronto) { if (state.pronto) onPronto() }

    /**
     * UM pedido só, com todas as permissões que ESTE grupo exige.
     *
     * Dois `launch()` seguidos não funcionam: o Android não mostra dois diálogos de permissão ao
     * mesmo tempo, e o segundo é engolido enquanto o primeiro está em voo. Foi o que fez a
     * localização nunca ser pedida. O `RequestMultiplePermissions` existe exatamente para isso —
     * ele encadeia os diálogos e devolve um resultado só.
     *
     * E só as que o grupo exige: pedir câmera "por via das dúvidas" num desafio sem foto gasta a
     * boa vontade que a gente vai precisar depois.
     */
    LaunchedEffect(state.exigeFoto, state.exigeLocal, permissoes) {
        if (jaPediu) return@LaunchedEffect
        val faltando = buildList {
            if (state.exigeFoto && !permissoes.camera) add(Manifest.permission.CAMERA)
            if (state.exigeLocal && !permissoes.local) add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (faltando.isNotEmpty()) {
            jaPediu = true
            pedir.launch(faltando.toTypedArray())
        }
    }

    /**
     * Busca a posição quando houver permissão — venha ela do diálogo ou das Configurações.
     *
     * Amarrado à PERMISSÃO e à intenção, não ao callback do launcher: é isso que faz funcionar
     * tanto o caminho pelas Configurações quanto o de conceder a permissão fina **depois** de já
     * termos uma posição aproximada. Sem a segunda condição, tocar "Exatamente aqui" pediria a
     * permissão, ganharia, e continuaria mostrando o bairro.
     */
    LaunchedEffect(
        permissoes.local,
        permissoes.localPreciso,
        state.exigeLocal,
        querExata,
        // `buscandoLocal` e `local` PRECISAM ser chaves, não só condições.
        //
        // A busca aproximada dispara ao abrir a tela. Se a permissão fina chegar enquanto ela
        // ainda está em voo, o efeito re-executa, encontra `buscandoLocal == true` e desiste — e
        // nunca mais roda, porque nenhuma chave muda depois. Resultado: a pessoa concede a
        // exatidão e continua vendo o bairro. Com as duas como chave, o fim da busca reavalia.
        state.buscandoLocal,
        state.local,
    ) {
        if (!state.exigeLocal || !permissoes.local || state.buscandoLocal) return@LaunchedEffect
        val podeExata = querExata && permissoes.localPreciso
        val precisaBuscar = state.local == null ||
            (podeExata && state.local?.precisao != PrecisaoDoLocal.EXATA)
        if (precisaBuscar) {
            viewModel.localizar(if (podeExata) PrecisaoDoLocal.EXATA else PrecisaoDoLocal.APROXIMADA)
        }
    }

    val temCamera = permissoes.camera
    val temLocal = permissoes.local
    // "Negado" só depois de termos PERGUNTADO — senão a tela abriria acusando o usuário de ter
    // recusado algo que ninguém pediu.
    val camaraNegada = jaPediu && !temCamera
    val localNegado = jaPediu && !temLocal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check-in") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            when {
                state.carregando -> Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }

                // SEGUNDA barreira. A primeira é o detalhe do grupo não oferecer o botão; esta
                // cobre o que ele não alcança: dado com alguns segundos de atraso, ou check-in
                // feito em outro aparelho. Sem ela, a pessoa faz a foto e o GPS para nada.
                //
                // Não é redundância inútil — é a diferença entre "não oferecer" e "não deixar".
                // A terceira barreira é o índice único no banco, que é quem realmente decide.
                state.grupo?.myCheckInToday != null ->
                    Bloqueio(
                        "Você já treinou hoje",
                        "Só vale um check-in por dia neste desafio. Para refazer, apague o de hoje na tela do desafio.",
                        contexto = contexto,
                        acao = null,
                    )

                // 5.3: permissão negada num grupo que exige a regra = não dá para fazer check-in
                // AQUI. A tela precisa DIZER o motivo e mostrar a saída — botão morto sem
                // explicação é o pior desfecho possível.
                state.exigeFoto && camaraNegada && !temCamera ->
                    Bloqueio(
                        "Este desafio exige foto",
                        "Sem acesso à câmera não dá para fazer check-in neste desafio. Você pode liberar nas configurações do sistema.",
                        contexto = contexto,
                    )

                else -> {
                    if (state.exigeFoto) {
                        BlocoDaFoto(state.foto, temCamera, viewModel::aoFotografar, viewModel::descartarFoto)
                        Spacer(Modifier.height(20.dp))
                    }

                    if (state.exigeLocal) {
                        BlocoDoLocal(
                            state = state,
                            temPermissao = temLocal,
                            negado = localNegado,
                            contexto = contexto,
                            onDigitar = viewModel::aoDigitarLocal,
                            // Voltar para "Bairro" não desfaz a permissão fina (nem deveria: só o
                            // usuário revoga permissão). O que muda é o que a gente PEDE ao
                            // localizador — e uma sugestão de bairro nunca vaza rua.
                            querExata = querExata,
                            onAproximado = {
                                querExata = false
                                viewModel.localizar(PrecisaoDoLocal.APROXIMADA)
                            },
                            onExato = {
                                querExata = true
                                if (permissoes.localPreciso) {
                                    viewModel.localizar(PrecisaoDoLocal.EXATA)
                                } else {
                                    // A permissão fina é pedida SÓ AQUI, quando ela é escolhida.
                                    pedir.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                        ),
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    state.erro.erroGeral(setOf("foto", "localizacao", "nomeDoLocal"))?.let {
                        ErroInline(it)
                        Spacer(Modifier.height(10.dp))
                    }

                    Button(
                        onClick = { viewModel.enviar(groupId) },
                        enabled = state.podeEnviar,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.enviando) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Fazer check-in")
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        // Dito ANTES, não depois: check-in não tem edição, e só o dono apaga, no
                        // mesmo dia (4.11). Quem publica o endereço de casa e percebe amanhã não
                        // tem conserto.
                        "Depois de enviar, dá para apagar só até o fim do dia — e não dá para editar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BlocoDaFoto(
    foto: ByteArray?,
    temPermissao: Boolean,
    onFoto: (ByteArray) -> Unit,
    onDescartar: () -> Unit,
) {
    Text("FOTO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    if (foto != null) {
        val bitmap = remember(foto) {
            android.graphics.BitmapFactory.decodeByteArray(foto, 0, foto.size)
        }
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDescartar) { Text("Tirar outra") }
    } else if (temPermissao) {
        CameraDoCheckIn(
            onFoto = onFoto,
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun BlocoDoLocal(
    state: CheckInState,
    temPermissao: Boolean,
    negado: Boolean,
    querExata: Boolean,
    contexto: android.content.Context,
    onDigitar: (String) -> Unit,
    onAproximado: () -> Unit,
    onExato: () -> Unit,
) {
    Text("ONDE VOCÊ TREINOU", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))

    if (negado && !temPermissao) {
        Bloqueio(
            "Este desafio exige o local",
            "Sem a localização não dá para fazer check-in neste desafio.",
            contexto = contexto,
        )
        return
    }

    // As duas precisões, e o padrão é a APROXIMADA. Em escolha de privacidade o padrão pesa mais
    // que a opção — a maioria não troca, e por isso o que protege tem que estar selecionado.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !querExata,
            onClick = onAproximado,
            label = { Text("Bairro") },
            leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp)) },
        )
        FilterChip(
            selected = querExata,
            onClick = onExato,
            label = { Text("Exatamente aqui") },
            leadingIcon = { Icon(Icons.Outlined.MyLocation, null, Modifier.size(16.dp)) },
        )
    }

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = state.nomeDoLocal,
        onValueChange = onDigitar,
        label = { Text("Nome do lugar") },
        placeholder = { Text("Smart Fit, Casa, Parque…") },
        supportingText = {
            Text(
                state.erro.erroDoCampo("nomeDoLocal")
                    // A sugestão é ponto de partida, não resposta. Quem treina em casa escreve
                    // "Casa" e o endereço nunca chega às outras 49 pessoas (5.2).
                    ?: "O grupo vê só este texto. Você pode trocar por um apelido.",
            )
        },
        isError = state.erro.erroDoCampo("nomeDoLocal") != null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (state.buscandoLocal) {
        Spacer(Modifier.height(6.dp))
        Text("Procurando você…", style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * O que o sistema diz AGORA sobre as permissões.
 *
 * `localPreciso` separado de `local` porque o Android 12+ concede as duas coisas pelo mesmo
 * diálogo: "Aproximada" dá só `COARSE`, "Precisa" dá as duas. É esse campo que decide se a
 * sugestão pode ser de rua ou tem que ser de bairro.
 */
private data class Permissoes(
    val camera: Boolean,
    val local: Boolean,
    val localPreciso: Boolean,
)

private fun lerPermissoes(contexto: android.content.Context): Permissoes {
    fun concedida(nome: String) =
        ContextCompat.checkSelfPermission(contexto, nome) == PackageManager.PERMISSION_GRANTED

    val fina = concedida(Manifest.permission.ACCESS_FINE_LOCATION)
    return Permissoes(
        camera = concedida(Manifest.permission.CAMERA),
        // FINE implica COARSE: basta uma das duas para haver posição.
        local = fina || concedida(Manifest.permission.ACCESS_COARSE_LOCATION),
        localPreciso = fina,
    )
}

/**
 * Diz por que não dá, e oferece a saída quando existe uma.
 *
 * [acao] nulo é o caso em que **não há o que o usuário possa fazer aqui** — já treinou hoje. Um
 * botão "Abrir configurações" ali seria pior que nenhum: sugere que o problema é de permissão.
 */
@Composable
private fun Bloqueio(
    titulo: String,
    texto: String,
    contexto: android.content.Context,
    acao: String? = "Abrir configurações",
) {
    Column {
        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (acao != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                contexto.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", contexto.packageName, null),
                    ),
                )
            }) { Text(acao) }
        }
    }
}
