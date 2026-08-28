package dev.rafael.app.ui

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
 */
class ErrorUiTest {

    /** Sem rede no teste; o visual não depende dela para os erros que carregam mensagem. */
    private fun visualDe(erro: AppError) = erro.visual(temRede = true, contexto = ErroContexto.LOGADO)

    @Test
    fun `todo erro com mensagem do servidor mostra a mensagem do servidor`() {
        val frase = "Transfira o cargo de admin antes de sair."

        val comMensagem = listOf(
            AppError.Conflict(frase),
            AppError.Validation(frase),
            AppError.Forbidden(frase),
            AppError.NotFound(frase),
        )

        comMensagem.forEach { erro ->
            assertEquals(
                frase,
                visualDe(erro).texto,
                "${erro::class.simpleName} descartou a mensagem do servidor",
            )
        }
    }

    /**
     * O default genérico do `AppError` NÃO vai para a tela.
     *
     * `AppError.NotFound()` sem argumento traz "Não encontrado", que é rótulo de categoria e não
     * frase para o usuário. Nesse caso o cliente completa — é o único momento em que ele tem
     * direito de escrever o texto, porque não recebeu nenhum.
     */
    @Test
    fun `erro sem mensagem util cai no texto do cliente, nao no rotulo`() {
        val visual = visualDe(AppError.NotFound())

        assertTrue(
            visual.texto.length > "Não encontrado".length,
            "o texto para o usuário não pode ser o rótulo da categoria: `${visual.texto}`",
        )
    }

    /**
     * [INVARIANTE] Nenhum texto de erro parece código.
     *
     * O `.name` de enum já vazou para a tela uma vez ("TETO_ATINGIDO", fatia A.2). O teste checa a
     * FORMA — texto muda, "não parece constante" precisa continuar valendo.
     */
    @Test
    fun `nenhum texto de erro parece constante de codigo`() {
        val erros = listOf(
            AppError.Conflict("Vocês já são amigos."),
            AppError.Validation("Nome muito curto."),
            AppError.Forbidden("Sem permissão para editar."),
            AppError.NotFound("Nenhum usuário com esse código."),
            AppError.Connection(),
            AppError.Unexpected(),
            AppError.Unauthorized(),
        )

        erros.forEach { erro ->
            val v = visualDe(erro)
            listOf(v.titulo, v.texto).forEach { texto ->
                assertTrue(texto.isNotBlank(), "${erro::class.simpleName} deixou texto vazio")
                assertTrue(
                    !texto.contains("_") && texto != texto.uppercase(),
                    "`$texto` parece constante de código (${erro::class.simpleName})",
                )
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
