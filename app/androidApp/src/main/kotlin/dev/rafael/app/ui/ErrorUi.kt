package dev.rafael.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError

/**
 * POLÍTICA DE APRESENTAÇÃO DE ERRO — ponto único onde `AppError` vira pixel.
 *
 * Antes cada ViewModel decidia sozinho, e o resultado foi: banner vermelho de rede numa tela
 * que tinha dado local, "sem conexão" quando o servidor estava morto, e 401 sem caminho de
 * volta pro login. Aqui a regra é uma só, e toda feature herda.
 *
 * Três níveis (ARCH #30):
 *  1. SILENCIOSO   — sync falhou mas há dado local. A tela não mostra nada; o usuário nem
 *                    precisa saber. Não existe componente pra isso, é a ausência dele.
 *  2. ESTADO DE TELA — falhou e não há NADA local pra mostrar → [ErroDeTela].
 *  3. ERRO DE AÇÃO — o usuário tocou algo e falhou → [ErroEmSnackbar], efêmero e com retry.
 *
 * O que este arquivo NÃO faz: navegar. Ele devolve a [ErroAcao] recomendada e cada tela decide
 * como executá-la (a rota de login/paywall é assunto do NavHost, não do componente de erro).
 */

/** O que faz sentido oferecer ao usuário diante deste erro. A tela executa. */
enum class ErroAcao { TENTAR_DE_NOVO, IR_PRO_LOGIN, VER_PLANOS, VOLTAR, NENHUMA }

/**
 * O MESMO status HTTP quer dizer coisas diferentes dependendo de onde o usuário está.
 *
 * 401 numa tela logada = o token morreu → "Sessão expirada, entre de novo".
 * 401 na tela de LOGIN = a senha está errada → "E-mail ou senha incorretos". Aplicar a
 * primeira mensagem na segunda tela seria absurdo (mandar pro login quem já está no login)
 * e perigoso, porque o handler global de sessão expirada deslogaria quem só errou a senha.
 */
enum class ErroContexto { LOGADO, AUTENTICANDO }

/** Erro já traduzido para o que a tela precisa desenhar. */
data class ErroVisual(
    val icone: ImageVector,
    val titulo: String,
    val texto: String,
    val acao: ErroAcao,
) {
    val rotuloDaAcao: String?
        get() = when (acao) {
            ErroAcao.TENTAR_DE_NOVO -> "Tentar de novo"
            ErroAcao.IR_PRO_LOGIN -> "Entrar de novo"
            ErroAcao.VER_PLANOS -> "Ver planos"
            ErroAcao.VOLTAR -> "Voltar"
            ErroAcao.NENHUMA -> null
        }
}

/**
 * Traduz o erro. `temRede` só existe por causa do [AppError.Connection]: o domínio sabe que
 * não conseguiu falar com o servidor, mas quem sabe se a culpa é do wifi do usuário ou do
 * servidor caído é o sistema operacional — e a diferença importa, porque uma o usuário
 * resolve e a outra não.
 */
fun AppError.visual(
    temRede: Boolean,
    contexto: ErroContexto = ErroContexto.LOGADO,
): ErroVisual = when (this) {
    is AppError.Connection ->
        if (temRede) ErroVisual(
            icone = Icons.Outlined.SyncProblem,
            titulo = "Não conseguimos falar com o FitJourney",
            texto = "O aplicativo está no ar, mas o servidor não respondeu. Tente de novo em instantes.",
            acao = ErroAcao.TENTAR_DE_NOVO,
        ) else ErroVisual(
            icone = Icons.Outlined.WifiOff,
            titulo = "Sem conexão",
            texto = "Você está offline. Conecte-se para sincronizar — o que já foi baixado continua funcionando.",
            acao = ErroAcao.TENTAR_DE_NOVO,
        )

    is AppError.Unauthorized ->
        if (contexto == ErroContexto.AUTENTICANDO) ErroVisual(
            // Credenciais erradas: o erro é do formulário, não da sessão. Sem ação de
            // navegação — o usuário já está exatamente onde precisa estar.
            icone = Icons.Outlined.Lock,
            titulo = "E-mail ou senha incorretos",
            texto = "Confira os dados e tente de novo.",
            acao = ErroAcao.NENHUMA,
        ) else ErroVisual(
            // Token morto. Nenhum retry resolve — insistir aqui prende o usuário num loop.
            icone = Icons.Outlined.Lock,
            titulo = "Sessão expirada",
            texto = "Faça login novamente para continuar.",
            acao = ErroAcao.IR_PRO_LOGIN,
        )

    // Falta entitlement (ARCH #23) → paywall. Outros 403 são bloqueio de verdade.
    is AppError.Forbidden ->
        if (code == ErrorCodes.ENTITLEMENT_REQUIRED) ErroVisual(
            icone = Icons.Outlined.Lock,
            titulo = "Recurso do plano Premium",
            texto = message,
            acao = ErroAcao.VER_PLANOS,
        ) else ErroVisual(
            icone = Icons.Outlined.Lock,
            titulo = "Sem permissão",
            texto = message,
            acao = ErroAcao.VOLTAR,
        )

    is AppError.NotFound -> ErroVisual(
        icone = Icons.Outlined.SearchOff,
        titulo = "Não encontrado",
        texto = "Isto não existe mais. Pode ter sido removido em outro aparelho.",
        acao = ErroAcao.VOLTAR,
    )

    is AppError.Conflict -> ErroVisual(
        icone = Icons.Outlined.SyncProblem,
        titulo = "Dados desatualizados",
        texto = "Alguma coisa mudou antes de você salvar. Recarregue e tente de novo.",
        acao = ErroAcao.TENTAR_DE_NOVO,
    )

    // Validação some da tela: o lugar dela é embaixo do campo (usa fieldErrors, fatia 4).
    is AppError.Validation -> ErroVisual(
        icone = Icons.Outlined.ErrorOutline,
        titulo = "Dados inválidos",
        texto = message,
        acao = ErroAcao.NENHUMA,
    )

    // 500. Culpa nossa — não mandar o usuário "verificar a conexão".
    is AppError.Unexpected -> ErroVisual(
        icone = Icons.Outlined.ErrorOutline,
        titulo = "Algo deu errado do nosso lado",
        texto = "Já sabemos do problema. Tente de novo em instantes.",
        acao = ErroAcao.TENTAR_DE_NOVO,
    )
}

/**
 * Mensagem específica de UM campo, quando o servidor disse qual recusou (`fieldErrors`).
 *
 * Devolve null quando o erro não é de validação ou não menciona este campo — então dá pra
 * usar direto em `isError`/`supportingText` de um TextField sem `if` na tela.
 *
 * A diferença prática: em vez de "Dados inválidos" no rodapé, o campo errado fica vermelho
 * com o motivo embaixo dele. O usuário não precisa adivinhar qual dos cinco campos falhou.
 */
fun AppError?.erroDoCampo(campo: String): String? =
    (this as? AppError.Validation)?.fieldErrors?.get(campo)

/**
 * O aparelho tem internet AGORA? Rechecado a cada erro novo (`key`), porque entre um erro e
 * outro o usuário pode ter ligado o wifi. Só leitura — não pede permissão em runtime.
 */
@Composable
fun rememberTemRede(key: Any? = null): Boolean {
    val context = LocalContext.current
    return remember(key) { context.temRede() }
}

private fun Context.temRede(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * NÍVEL 2 — a tela não tem nada pra mostrar. Ocupa o corpo inteiro, com uma saída.
 *
 * Use SÓ quando não há dado local. Com dado local, falha de sync é nível 1 (silêncio):
 * exibir isto por cima de uma lista que funciona é assustar o usuário à toa.
 */
@Composable
fun ErroDeTela(
    erro: AppError,
    modifier: Modifier = Modifier,
    contexto: ErroContexto = ErroContexto.LOGADO,
    onAcao: ((ErroAcao) -> Unit)? = null,
) {
    val visual = erro.visual(rememberTemRede(erro), contexto)
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            visual.icone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(visual.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            visual.texto,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val rotulo = visual.rotuloDaAcao
        if (rotulo != null && onAcao != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { onAcao(visual.acao) }) { Text(rotulo) }
        }
    }
}

/**
 * NÍVEL 3, forma compacta — para formulários e telas onde o erro precisa ficar ANCORADO
 * perto do que falhou (login, quiz, paywall). Snackbar não serve aqui: ele some sozinho e
 * o usuário perde a referência de qual campo/ação deu problema.
 *
 * Uma linha, na cor de erro. O texto vem da mesma política dos outros níveis — é só o
 * invólucro que muda.
 */
@Composable
fun ErroInline(
    erro: AppError,
    modifier: Modifier = Modifier,
    contexto: ErroContexto = ErroContexto.LOGADO,
) {
    val visual = erro.visual(rememberTemRede(erro), contexto)
    Text(
        visual.titulo,
        modifier = modifier,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * NÍVEL 3 — o usuário tocou algo e falhou. Efêmero, não ocupa a tela e não vira estado
 * permanente em vermelho (o banner fixo antigo continuava lá muito depois de irrelevante).
 *
 * `onConsumir` é obrigatório: sem limpar o erro no state, o snackbar reaparece a cada
 * recomposição.
 */
@Composable
fun ErroEmSnackbar(
    erro: AppError?,
    host: SnackbarHostState,
    onConsumir: () -> Unit,
    contexto: ErroContexto = ErroContexto.LOGADO,
    onAcao: ((ErroAcao) -> Unit)? = null,
) {
    val temRede = rememberTemRede(erro)
    LaunchedEffect(erro) {
        val e = erro ?: return@LaunchedEffect
        // Validação NÃO vira snackbar: ela pertence ao campo (ver erroDoCampo). Mandar as duas
        // coisas seria dizer o mesmo erro duas vezes, uma delas longe de onde ele aconteceu.
        if (e is AppError.Validation) return@LaunchedEffect
        val visual = e.visual(temRede, contexto)
        val resultado = host.showSnackbar(
            message = visual.titulo,
            actionLabel = if (onAcao != null) visual.rotuloDaAcao else null,
            duration = SnackbarDuration.Short,
        )
        if (resultado == SnackbarResult.ActionPerformed) onAcao?.invoke(visual.acao)
        onConsumir()
    }
}
