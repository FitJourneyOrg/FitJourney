package dev.rafael.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.rafael.app.R
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
 *
 * ## Depois da G.3: ids, não frases
 *
 * `titulo` e `rotuloDaAcao` são `@StringRes Int` — sempre nossos. `texto` é [Frase], porque é o
 * único campo bimodal: catálogo do cliente OU `message` do servidor. Ver o KDoc de [Frase].
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
    @StringRes val titulo: Int,
    val texto: Frase,
    val acao: ErroAcao,
) {
    @get:StringRes
    val rotuloDaAcao: Int?
        get() = when (acao) {
            ErroAcao.TENTAR_DE_NOVO -> R.string.erro_acao_tentar_de_novo
            ErroAcao.IR_PRO_LOGIN -> R.string.erro_acao_entrar_de_novo
            ErroAcao.VER_PLANOS -> R.string.erro_acao_ver_planos
            ErroAcao.VOLTAR -> R.string.erro_acao_voltar
            ErroAcao.NENHUMA -> null
        }
}

/**
 * A `message` default do `AppError.NotFound`, lida DELE em vez de reescrita aqui.
 *
 * ⚠️ Esta é a única comparação de texto que sobrou no arquivo, e ela é sentinela, não frase de
 * tela: serve para reconhecer "o erro veio sem mensagem útil". Duplicar o literal criaria a pior
 * espécie de acoplamento — mudar o default no `core` deixaria a comparação silenciosamente falsa,
 * sem quebrar build nem teste. Derivando dele, não há o que divergir.
 */
private val MENSAGEM_PADRAO_DE_NOT_FOUND: String = AppError.NotFound().message

/**
 * Traduz o erro. `temRede` só existe por causa do [AppError.Connection]: o domínio sabe que
 * não conseguiu falar com o servidor, mas quem sabe se a culpa é do wifi do usuário ou do
 * servidor caído é o sistema operacional — e a diferença importa, porque uma o usuário
 * resolve e a outra não.
 */
fun AppError.visual(
    temRede: Boolean,
    contexto: ErroContexto = ErroContexto.LOGADO,
): ErroVisual = comTextoDoCodigo(when (this) {
    is AppError.Connection ->
        if (temRede) ErroVisual(
            icone = Icons.Outlined.SyncProblem,
            titulo = R.string.erro_titulo_servidor_mudo,
            texto = Frase.Recurso(R.string.erro_texto_servidor_mudo),
            acao = ErroAcao.TENTAR_DE_NOVO,
        ) else ErroVisual(
            icone = Icons.Outlined.WifiOff,
            titulo = R.string.erro_titulo_sem_conexao,
            texto = Frase.Recurso(R.string.erro_texto_sem_conexao),
            acao = ErroAcao.TENTAR_DE_NOVO,
        )

    is AppError.Unauthorized ->
        if (contexto == ErroContexto.AUTENTICANDO) ErroVisual(
            // Credenciais erradas: o erro é do formulário, não da sessão. Sem ação de
            // navegação — o usuário já está exatamente onde precisa estar.
            icone = Icons.Outlined.Lock,
            titulo = R.string.erro_titulo_credenciais,
            texto = Frase.Recurso(R.string.erro_texto_credenciais),
            acao = ErroAcao.NENHUMA,
        ) else ErroVisual(
            // Token morto. Nenhum retry resolve — insistir aqui prende o usuário num loop.
            icone = Icons.Outlined.Lock,
            titulo = R.string.erro_titulo_sessao_expirada,
            texto = Frase.Recurso(R.string.erro_texto_sessao_expirada),
            acao = ErroAcao.IR_PRO_LOGIN,
        )

    /**
     * Portão de plano (ARCH #23) leva ao paywall. Outros 403 são bloqueio de verdade.
     *
     * ⚠️ **Compara com o CONJUNTO, não com um código.** Até a G.2 os quatro portões compartilhavam
     * `ENTITLEMENT_REQUIRED` e aqui havia `if (code == ENTITLEMENT_REQUIRED)`. Dar texto próprio a
     * cada um exigiu código próprio, e isso **quase apagou o paywall em silêncio**: os códigos
     * novos não casariam com o `if`, nenhum teste falharia, e a tela ofereceria "Voltar" no lugar
     * da assinatura.
     *
     * > **Ramificar por um código só e depois multiplicar os códigos quebra a ramificação sem
     * > quebrar o build.**
     *
     * O conjunto mora no `ErrorCodes`, no contrato: acrescentar um portão é acrescentar lá, e esta
     * linha acompanha sozinha.
     */
    is AppError.Forbidden ->
        if (code in ErrorCodes.PORTOES_DE_PLANO) ErroVisual(
            icone = Icons.Outlined.Lock,
            titulo = R.string.erro_titulo_recurso_premium,
            texto = Frase.DoServidor(message),
            acao = ErroAcao.VER_PLANOS,
        ) else ErroVisual(
            icone = Icons.Outlined.Lock,
            titulo = R.string.erro_titulo_sem_permissao,
            texto = Frase.DoServidor(message),
            acao = ErroAcao.VOLTAR,
        )

    /**
     * 404 **usa o texto do servidor**, como Conflict, Validation e Forbidden.
     *
     * A versão anterior dizia sempre "Isto não existe mais. Pode ter sido removido em outro
     * aparelho." — frase escrita pensando em CONTEÚDO DO PRÓPRIO USUÁRIO, um treino apagado no
     * outro celular. Aplicada a "código de amigo não encontrado" ela mentia duas vezes: afirmava
     * que o código existiu, e sugeria que quem o removeu foi você. Achado na bateria do #35, ao
     * buscar um código regenerado.
     *
     * É o TERCEIRO erro do mesmo tipo nesta base — Conflict (A.2) e ErroInline (A.4) foram os
     * outros dois. O padrão: **texto fixo no cliente para um erro que o servidor sabe explicar
     * melhor**. Quando o servidor tem contexto e o cliente não, quem escreve a frase é o servidor.
     *
     * O [MENSAGEM_PADRAO_DE_NOT_FOUND] cobre o default genérico do `AppError.NotFound`, que existe
     * para quem constrói o erro sem mensagem — nesse caso a frase antiga volta, e aí ela está
     * correta.
     */
    is AppError.NotFound -> ErroVisual(
        icone = Icons.Outlined.SearchOff,
        titulo = R.string.erro_titulo_nao_encontrado,
        texto = message
            .takeIf { it.isNotBlank() && it != MENSAGEM_PADRAO_DE_NOT_FOUND }
            ?.let { Frase.DoServidor(it) }
            ?: Frase.Recurso(R.string.erro_texto_nao_encontrado),
        acao = ErroAcao.VOLTAR,
    )

    /**
     * 409 aqui é REGRA RECUSANDO, não dado velho.
     *
     * A versão anterior dizia "Dados desatualizados — alguma coisa mudou antes de você salvar,
     * recarregue e tente de novo" para tudo. Quando o admin tentava sair do grupo, o servidor
     * respondia "Transfira o cargo de admin antes de sair" e a tela trocava por essa frase: nada
     * tinha mudado, recarregar não resolvia, e a única instrução útil — transferir o cargo — era
     * justamente a que se perdia. Mandar tentar de novo o que nunca vai passar é pior que não
     * dizer nada.
     *
     * Mesmo tratamento de [AppError.Forbidden] e [AppError.Validation]: o texto do servidor
     * chega inteiro, e a ação é NENHUMA porque não existe retry que ajude.
     */
    is AppError.Conflict -> ErroVisual(
        icone = Icons.Outlined.ErrorOutline,
        titulo = R.string.erro_titulo_conflito,
        texto = Frase.DoServidor(message),
        acao = ErroAcao.NENHUMA,
    )

    // Validação some da tela: o lugar dela é embaixo do campo (usa fieldErrors, fatia 4).
    is AppError.Validation -> ErroVisual(
        icone = Icons.Outlined.ErrorOutline,
        titulo = R.string.erro_titulo_dados_invalidos,
        texto = Frase.DoServidor(message),
        acao = ErroAcao.NENHUMA,
    )

    // 500. Culpa nossa — não mandar o usuário "verificar a conexão".
    is AppError.Unexpected -> ErroVisual(
        icone = Icons.Outlined.ErrorOutline,
        titulo = R.string.erro_titulo_falha_nossa,
        texto = Frase.Recurso(R.string.erro_texto_falha_nossa),
        acao = ErroAcao.TENTAR_DE_NOVO,
    )
})

/**
 * O código do erro, quando a família carrega um. Desde a G.2 são todas menos `Connection` e
 * `Unexpected`, que nunca chegam a uma tela com texto do servidor.
 */
val AppError.codigo: String?
    get() = when (this) {
        is AppError.Validation -> code
        is AppError.Unauthorized -> code
        is AppError.Forbidden -> code
        is AppError.NotFound -> code
        is AppError.Conflict -> code
        else -> null
    }

/**
 * Troca o texto pelo do CÓDIGO, quando existe (G.2, ARCH #37).
 *
 * ## Por que é um envelope em volta do `when`, e não um ramo dentro dele
 *
 * A escolha de **título, ícone e ação** continua sendo por família de erro: é o desenho que o
 * `ErroInline` já documentava, *"o título é a CATEGORIA, o texto é o que aconteceu"*. Só o TEXTO
 * muda por código, e envolver o `when` inteiro deixa isso valer para todas as famílias sem repetir
 * a mesma linha em cinco ramos — que é onde a sexta seria esquecida.
 *
 * ## O fallback é a lição do #31 sobrevivendo
 *
 * Código que este app não conhece mantém a `message` do servidor. Um servidor novo falando com um
 * app antigo continua explicando melhor do que qualquer texto genérico que a gente escrevesse aqui.
 * É a mesma proteção de antes, agora só para o caso em que ela é de fato necessária.
 *
 * Depois da G.3 a troca é literalmente de [Frase.DoServidor] para [Frase.Recurso] — a fronteira
 * está no tipo, não numa convenção que alguém precise lembrar.
 */
private fun AppError.comTextoDoCodigo(visual: ErroVisual): ErroVisual {
    val doCodigo = TextosDeErro.de(codigo) ?: return visual
    return visual.copy(texto = Frase.Recurso(doCodigo))
}

/**
 * Mensagem específica de UM campo, quando o servidor disse qual recusou (`fieldErrors`).
 *
 * Devolve null quando o erro não é de validação ou não menciona este campo — então dá pra
 * usar direto em `isError`/`supportingText` de um TextField sem `if` na tela.
 *
 * ⚠️ Continua sendo `String` do SERVIDOR, e portanto não traduzida. É a mesma classe de débito de
 * `TextosDeErro.SEM_TEXTO_PROPRIO`: o `fieldErrors` é um mapa aberto, sem código, e traduzi-lo
 * exigiria o servidor mandar chave em vez de frase. Decisão para a G.4, registrada aqui.
 *
 * A diferença prática: em vez de "Dados inválidos" no rodapé, o campo errado fica vermelho
 * com o motivo embaixo dele. O usuário não precisa adivinhar qual dos cinco campos falhou.
 */
fun AppError?.erroDoCampo(campo: String): String? =
    (this as? AppError.Validation)?.fieldErrors?.get(campo)

/**
 * O erro AINDA PRECISA SER MOSTRADO depois que os campos visíveis já se marcaram?
 *
 * Devolve o erro quando ele não é de validação (aí é sempre da tela inteira) **ou** quando é de
 * validação mas nenhuma das suas chaves corresponde a um campo que a tela desenha. Null quando
 * os campos já deram conta.
 *
 * POR QUE existe: um formulário que só chama [erroDoCampo] engole a recusa cujo campo ele não
 * mostra. Aconteceu no formulário de grupo — o servidor recusou por `timezone`, que ali é texto
 * e não campo, e o usuário ficou com um botão que não fazia nada e nenhuma explicação. O pior
 * tipo de erro é o que não aparece.
 */
fun AppError?.erroGeral(camposVisiveis: Set<String>): AppError? {
    val erro = this ?: return null
    if (erro !is AppError.Validation) return erro
    val cobertos = erro.fieldErrors.keys.any { it in camposVisiveis }
    return if (cobertos) null else erro
}

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
        Text(
            stringResource(visual.titulo),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            visual.texto.resolver(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val rotulo = visual.rotuloDaAcao
        if (rotulo != null && onAcao != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { onAcao(visual.acao) }) { Text(stringResource(rotulo)) }
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
        // `texto` e NÃO `titulo`.
        //
        // O título é a CATEGORIA do erro ("Não dá para fazer isso agora", "Sem conexão"); o texto
        // é a frase que diz o que houve e o que fazer. Numa tela de erro inteira os dois cabem, e
        // o título orienta a leitura. Aqui, uma linha ao lado do botão que falhou, a categoria não
        // acrescenta nada — quem tocou já sabe que falhou. O que falta é o motivo.
        //
        // Custou dois ciclos achar isto: primeiro o `Conflict` tinha texto fixo errado, e quando
        // consertei a frase ela foi parar exatamente no campo que este componente descartava. O
        // servidor vinha dizendo "Transfira o cargo de admin antes de sair" desde o começo.
        visual.texto.resolver(),
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
    // O `Context` é lido AQUI, em composição, e não dentro do efeito: `LaunchedEffect` roda numa
    // corrotina, onde `stringResource` e `LocalContext` não existem mais.
    val context = LocalContext.current
    LaunchedEffect(erro) {
        val e = erro ?: return@LaunchedEffect
        // Validação NÃO vira snackbar: ela pertence ao campo (ver erroDoCampo). Mandar as duas
        // coisas seria dizer o mesmo erro duas vezes, uma delas longe de onde ele aconteceu.
        if (e is AppError.Validation) return@LaunchedEffect
        val visual = e.visual(temRede, contexto)
        val resultado = host.showSnackbar(
            message = context.getString(visual.titulo),
            actionLabel = visual.rotuloDaAcao?.takeIf { onAcao != null }?.let { context.getString(it) },
            duration = SnackbarDuration.Short,
        )
        if (resultado == SnackbarResult.ActionPerformed) onAcao?.invoke(visual.acao)
        onConsumir()
    }
}
