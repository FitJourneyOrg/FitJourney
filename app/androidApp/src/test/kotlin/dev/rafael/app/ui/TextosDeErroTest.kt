package dev.rafael.app.ui

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O catálogo de textos de erro do cliente (G.2, ARCH #37).
 *
 * ## O que este arquivo impede
 *
 * A G.2 moveu 103 frases do servidor para o cliente. O jeito de essa mudança apodrecer é simples e
 * silencioso: **alguém acrescenta um código no servidor e não acrescenta o texto aqui**. A tela
 * cai no fallback e mostra a frase em português do servidor, para sempre, sem ninguém notar.
 *
 * O teste da cobertura fecha essa porta pelo build.
 */
class TextosDeErroTest {

    /** Todas as constantes `String` do `ErrorCodes`, por reflexão. */
    private fun todosOsCodigos(): List<String> =
        ErrorCodes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.isAccessible = true; it.get(ErrorCodes) as String }

    /**
     * ⭐ **Todo código tem texto próprio, ou está declarado como exceção.**
     *
     * As duas listas de exceção não são escapatória: elas obrigam a decisão a ficar ESCRITA.
     *
     * - `GENERICOS` são os fallbacks de família, cujo texto o `ErrorUi` já escreve;
     * - `SEM_TEXTO_PROPRIO` são os nove que carregam um número que só o servidor conhece, e que por
     *   decisão de 2026-09-09 continuam usando a frase dele.
     *
     * > **Débito que ninguém consegue enumerar não é débito, é surpresa.**
     *
     * Quando o inglês entrar, os nove aparecerão em português. Isso é sabido, está listado, e este
     * teste é o que garante que a lista continua completa.
     */
    @Test
    fun `todo codigo tem texto no cliente ou excecao declarada`() {
        val orfaos = todosOsCodigos().filter { codigo ->
            TextosDeErro.de(codigo) == null &&
                codigo !in TextosDeErro.SEM_TEXTO_PROPRIO &&
                codigo !in TextosDeErro.GENERICOS
        }

        assertTrue(
            orfaos.isEmpty(),
            "código sem texto no cliente e sem exceção declarada: $orfaos. " +
                "Acrescente a frase em TextosDeErro.de, ou o código em SEM_TEXTO_PROPRIO com o motivo.",
        )
    }

    /**
     * A outra metade: **exceção declarada para código que não existe mais**.
     *
     * Sem isto, apagar um código do `ErrorCodes` deixaria a entrada órfã na lista de exceções, e a
     * lista passaria a superestimar o débito — o que é tão ruim quanto subestimar, porque ninguém
     * confia numa lista que já errou.
     */
    @Test
    fun `nenhuma excecao aponta para codigo inexistente`() {
        val existentes = todosOsCodigos().toSet()
        val fantasmas = (TextosDeErro.SEM_TEXTO_PROPRIO + TextosDeErro.GENERICOS)
            .filter { it !in existentes }

        assertTrue(fantasmas.isEmpty(), "exceção para código que não existe mais: $fantasmas")
    }

    /**
     * ⭐ **Todo portão de plano leva ao paywall.**
     *
     * Este teste existe por causa de um defeito que eu ia introduzir na G.2 e quase não vi.
     *
     * Até então os quatro portões compartilhavam `ENTITLEMENT_REQUIRED`, e o `ErrorUi` fazia
     * `if (code == ENTITLEMENT_REQUIRED)`. Dar texto próprio a cada um exigiu código próprio, e os
     * códigos novos **simplesmente não casariam com o `if`**. Nada quebraria: o build passa, os
     * testes passam, e a tela oferece "Voltar" no lugar da assinatura.
     *
     * > **Ramificar por um código só e depois multiplicar os códigos quebra a ramificação sem
     * > quebrar o build.**
     *
     * A defesa é dupla: o conjunto mora no contrato, e este teste percorre o conjunto inteiro. Um
     * portão novo entra em `PORTOES_DE_PLANO` e passa a ser verificado sozinho.
     */
    @Test
    fun `todo portao de plano abre o paywall`() {
        ErrorCodes.PORTOES_DE_PLANO.forEach { codigo ->
            val visual = AppError.Forbidden("qualquer coisa", codigo)
                .visual(temRede = true, contexto = ErroContexto.LOGADO)

            assertEquals(
                ErroAcao.VER_PLANOS,
                visual.acao,
                "`$codigo` está em PORTOES_DE_PLANO e não abriu o paywall",
            )
        }
    }

    /**
     * E o contraexemplo, que é o que dá sentido ao teste acima.
     *
     * `LIMITE_DE_PROGRAMAS_PREMIUM` está fora do conjunto **de propósito**: quem bate nele já é
     * premium, e oferecer o plano a quem acabou de pagar é pior que não oferecer nada.
     *
     * Sem esta metade, o teste anterior passaria com um `PORTOES_DE_PLANO` que contivesse tudo.
     */
    @Test
    fun `teto de quem ja e premium NAO abre o paywall`() {
        val visual = AppError.Forbidden("qualquer coisa", ErrorCodes.LIMITE_DE_PROGRAMAS_PREMIUM)
            .visual(temRede = true, contexto = ErroContexto.LOGADO)

        assertEquals(ErroAcao.VOLTAR, visual.acao)
    }

    /**
     * ⭐ **Código desconhecido cai na mensagem do servidor.**
     *
     * É a lição do #31 sobrevivendo à inversão: um servidor atualizado falando com um app antigo
     * continua explicando melhor do que qualquer texto genérico escrito aqui.
     *
     * Sem este caminho, cada código novo do servidor viraria "Não encontrado" na tela de quem não
     * atualizou o app.
     */
    @Test
    fun `codigo desconhecido usa a mensagem do servidor`() {
        val frase = "Uma explicação que este app ainda não conhece."
        val visual = AppError.Conflict(frase, "CODIGO_DO_FUTURO")
            .visual(temRede = true, contexto = ErroContexto.LOGADO)

        assertEquals(frase, visual.texto)
    }

    /** E erro sem código nenhum também. É o caminho de todo `AppError` construído sem motivo. */
    @Test
    fun `erro sem codigo usa a mensagem do servidor`() {
        val frase = "Alguma coisa específica que o servidor sabe."
        val visual = AppError.Conflict(frase)
            .visual(temRede = true, contexto = ErroContexto.LOGADO)

        assertEquals(frase, visual.texto)
    }

    /**
     * [INV] Nenhum texto do catálogo parece código, e nenhum usa travessão.
     *
     * O `.name` de enum já vazou para a tela uma vez ("TETO_ATINGIDO", fatia A.2). O travessão é a
     * convenção do #37, seção 8, e **este é o único lugar do cliente onde ela é verificada hoje** —
     * os outros ~500 literais do app só entram na varredura na G.3.
     */
    @Test
    fun `nenhum texto parece codigo nem usa travessao`() {
        todosOsCodigos().mapNotNull { TextosDeErro.de(it) }.forEach { texto ->
            assertTrue(texto.isNotBlank(), "texto vazio no catálogo")
            assertTrue(
                !texto.contains("_") && texto != texto.uppercase(),
                "`$texto` parece constante de código",
            )
            assertTrue(!texto.contains('—') && !texto.contains('–'), "travessão em `$texto`")
        }
    }

    /** Os cinco desmembramentos da G.2 têm textos DIFERENTES. Unificá-los desfaria a fatia. */
    @Test
    fun `os desmembramentos da G2 dizem coisas diferentes`() {
        val pares = listOf(
            ErrorCodes.PESSOA_NAO_EXISTE to ErrorCodes.MINHA_CONTA_SUMIU,
            ErrorCodes.PERFIL_DE_TERCEIRO_INDISPONIVEL to ErrorCodes.MEU_PERFIL_INCOMPLETO,
            ErrorCodes.HEALTH_GATE_REQUIRED to ErrorCodes.PERFIL_AUSENTE_PARA_GERAR,
            ErrorCodes.PEDIDO_NAO_EXISTE to ErrorCodes.PEDIDO_JA_RESPONDIDO,
            ErrorCodes.GRUPO_NAO_EXISTE to ErrorCodes.NAO_SOU_MEMBRO,
        )

        pares.forEach { (um, outro) ->
            val a = TextosDeErro.de(um)
            val b = TextosDeErro.de(outro)
            assertNotNull(a, "$um perdeu o texto")
            assertNotNull(b, "$outro perdeu o texto")
            assertTrue(a != b, "`$um` e `$outro` foram desmembrados e voltaram a dizer a mesma coisa")
        }
    }
}
