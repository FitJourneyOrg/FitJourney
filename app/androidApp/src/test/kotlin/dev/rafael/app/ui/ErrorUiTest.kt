package dev.rafael.app.ui

import dev.rafael.app.R
import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A política única de apresentação de erro (ARCH #31).
 *
 * ## O defeito que estes testes existem para impedir
 *
 * Três vezes nesta base o mesmo padrão apareceu: **texto fixo no cliente para um erro que o
 * servidor sabia explicar melhor**.
 *
 * 1. **Fatia A.2** — `Conflict` dizia "Dados desatualizados, recarregue" para tudo. O servidor
 *    respondia "Transfira o cargo de admin antes de sair", e a única instrução útil se perdia.
 * 2. **Fatia A.4** — o `ErroInline` renderizava `titulo` e descartava `texto`. A frase certa
 *    chegava e era jogada fora no último metro.
 * 3. **Fatia #35** — `NotFound` dizia "Isto não existe mais. Pode ter sido removido em outro
 *    aparelho." ao buscar um código de amigo inexistente. Mentia duas vezes: afirmava que o
 *    código existiu e sugeria que quem apagou foi o usuário.
 *
 * A regra que ficou: **quando o servidor tem contexto e o cliente não, quem escreve a frase é o
 * servidor.** Estes testes valem para todo erro que carrega mensagem, não só os três que já
 * quebraram — é o que faz o quarto caso não acontecer.
 *
 * ## A G.2 mudou o VEÍCULO da regra, não a regra (ARCH #37)
 *
 * Com dois idiomas o servidor deixou de poder escrever a frase, porque ele não sabe em que idioma a
 * tela está. O que ele tem é **conhecimento da situação, não direito sobre as palavras** — e o
 * `code` carrega esse conhecimento igual.
 *
 * **A proteção original continua inteira no fallback**: código que o app não conhece mantém a
 * mensagem do servidor.
 *
 * ## E a G.3 tornou a fronteira um TIPO
 *
 * Depois da extração, "de quem é esta frase" não é mais convenção nem inspeção de conteúdo: é
 * [Frase.Recurso] contra [Frase.DoServidor]. Estes testes passaram a afirmar o CASO, e não uma
 * igualdade de texto que uma coincidência satisfaria.
 *
 * As asserções de FORMA ("não parece constante de código") foram para o `CatalogoDeStringsTest`,
 * onde valem para o catálogo inteiro em vez de para os sete erros listados aqui.
 */
class ErrorUiTest {

    /** Sem rede no teste; o visual não depende dela para os erros que carregam mensagem. */
    private fun visualDe(erro: AppError) = erro.visual(temRede = true, contexto = ErroContexto.LOGADO)

    /**
     * ⭐ O sucessor direto do teste original desta classe.
     *
     * Antes ele afirmava que **toda** mensagem do servidor chegava à tela. Agora afirma o caso em
     * que isso ainda vale, e é o caso que importa: **servidor novo, app antigo**. Aí o texto do
     * servidor é a melhor coisa disponível, e descartá-lo devolveria o defeito de 2026-08.
     */
    @Test
    fun `mensagem do servidor sobrevive quando o codigo e desconhecido`() {
        val frase = "Transfira o cargo de admin antes de sair."

        val comMensagem = listOf(
            AppError.Conflict(frase, "CODIGO_QUE_ESTE_APP_NAO_CONHECE"),
            AppError.Validation(frase, code = "CODIGO_QUE_ESTE_APP_NAO_CONHECE"),
            AppError.Forbidden(frase, "CODIGO_QUE_ESTE_APP_NAO_CONHECE"),
            AppError.NotFound(frase, "CODIGO_QUE_ESTE_APP_NAO_CONHECE"),
            // E sem código nenhum, que é o caminho de quem constrói o erro sem motivo específico.
            AppError.Conflict(frase),
            AppError.Validation(frase),
            AppError.Forbidden(frase),
            AppError.NotFound(frase),
        )

        comMensagem.forEach { erro ->
            assertEquals(
                Frase.DoServidor(frase),
                visualDe(erro).texto,
                "${erro::class.simpleName} descartou a mensagem do servidor sem ter texto próprio",
            )
        }
    }

    /**
     * ⭐ E o inverso: **com código conhecido, o texto é do CLIENTE**, e a frase do servidor não
     * aparece.
     *
     * É o que faz a tradução funcionar. Sem esta asserção, `comTextoDoCodigo` poderia deixar de ser
     * chamado e todo o resto continuaria verde, porque o fallback devolve algo plausível.
     *
     * > **Fallback que funciona bem esconde a ausência do caminho principal.**
     */
    @Test
    fun `com codigo conhecido o texto vem do cliente`() {
        val doServidor = "Frase em português que o servidor mandou."
        val visual = visualDe(AppError.NotFound(doServidor, ErrorCodes.PESSOA_NAO_EXISTE))

        assertEquals(Frase.Recurso(R.string.erro_pessoa_nao_existe), visual.texto)
    }

    /**
     * O default genérico do `AppError` NÃO vai para a tela.
     *
     * `AppError.NotFound()` sem argumento traz "Não encontrado", que é rótulo de categoria e não
     * frase para o usuário. Nesse caso o cliente completa — é o único momento em que ele tem
     * direito de escrever o texto, porque não recebeu nenhum.
     *
     * ⚠️ A sentinela é lida do próprio `AppError.NotFound()`, aqui e no `ErrorUi`. Escrever
     * "Não encontrado" nos dois lugares faria mudar o default no `core` calar este teste em vez de
     * quebrá-lo.
     */
    @Test
    fun `erro sem mensagem util cai no texto do cliente, nao no rotulo`() {
        assertEquals(Frase.Recurso(R.string.erro_texto_nao_encontrado), visualDe(AppError.NotFound()).texto)

        // E a metade que dá sentido à anterior: o texto do usuário não pode ser o rótulo da
        // categoria. As duas frases vêm do catálogo, então isto continua sendo sobre CONTEÚDO.
        val rotulo = CatalogoDeStrings.texto("erro_titulo_nao_encontrado")
        val texto = CatalogoDeStrings.texto("erro_texto_nao_encontrado")
        assertTrue(texto != rotulo, "o texto para o usuário virou o rótulo da categoria")
        assertTrue(texto.length > rotulo.length, "`$texto` é curto demais para explicar o que houve")
        assertEquals(AppError.NotFound().message, rotulo, "a sentinela do ErrorUi e o rótulo divergiram")
    }

    /**
     * ⭐ **Nenhuma família perde o título.**
     *
     * Antes da extração isto era implícito: `titulo` era `String` e vinha escrito ali. Agora é um
     * id, e id errado — ou zero — compila. Percorrer as sete famílias e exigir que cada uma aponte
     * para uma chave que EXISTE no catálogo é o que substitui a leitura.
     */
    @Test
    fun `toda familia aponta para um titulo que existe no catalogo`() {
        val titulosConhecidos = setOf(
            R.string.erro_titulo_servidor_mudo,
            R.string.erro_titulo_sem_conexao,
            R.string.erro_titulo_credenciais,
            R.string.erro_titulo_sessao_expirada,
            R.string.erro_titulo_recurso_premium,
            R.string.erro_titulo_sem_permissao,
            R.string.erro_titulo_nao_encontrado,
            R.string.erro_titulo_conflito,
            R.string.erro_titulo_dados_invalidos,
            R.string.erro_titulo_falha_nossa,
        )

        val familias = listOf(
            AppError.Connection(),
            AppError.Unauthorized(),
            AppError.Forbidden("x", ErrorCodes.AGE_GATE_REQUIRED),
            AppError.Forbidden("x"),
            AppError.NotFound("x"),
            AppError.Conflict("x"),
            AppError.Validation("x"),
            AppError.Unexpected(),
        )

        familias.forEach { erro ->
            val titulo = visualDe(erro).titulo
            assertTrue(titulo != 0, "${erro::class.simpleName} ficou sem título")
            assertTrue(
                titulo in titulosConhecidos,
                "${erro::class.simpleName} aponta para um título que não é de erro",
            )
        }
    }

    /**
     * ⭐ **Toda ação oferecida tem rótulo, e NENHUMA não tem.**
     *
     * O rótulo é o texto do botão da tela de erro e da ação do snackbar. `null` ali significa
     * "não ofereça nada", e é o único caso em que ele pode faltar — trocar um dos quatro por
     * `null` esconderia o botão sem quebrar nada.
     */
    @Test
    fun `cada acao tem rotulo, e so NENHUMA nao tem`() {
        ErroAcao.entries.forEach { acao ->
            val visual = ErroVisual(
                icone = visualDe(AppError.Unexpected()).icone,
                titulo = R.string.erro_titulo_falha_nossa,
                texto = Frase.Recurso(R.string.erro_texto_falha_nossa),
                acao = acao,
            )
            if (acao == ErroAcao.NENHUMA) {
                assertEquals(null, visual.rotuloDaAcao, "NENHUMA não pode oferecer botão")
            } else {
                assertTrue(visual.rotuloDaAcao != null && visual.rotuloDaAcao != 0, "$acao ficou sem rótulo")
            }
        }
    }

    /**
     * 409 e 422 não oferecem "tentar de novo".
     *
     * Regra recusando não muda por repetição — mandar tentar de novo o que nunca vai passar é
     * pior que não dizer nada. Foi o segundo defeito da fatia A.2.
     */
    @Test
    fun `erro de regra nao oferece tentar de novo`() {
        listOf(
            AppError.Conflict("Vocês já são amigos."),
            AppError.Validation("Nome muito curto."),
        ).forEach { erro ->
            assertEquals(
                ErroAcao.NENHUMA,
                visualDe(erro).acao,
                "${erro::class.simpleName} não pode sugerir retry",
            )
        }
    }
}
