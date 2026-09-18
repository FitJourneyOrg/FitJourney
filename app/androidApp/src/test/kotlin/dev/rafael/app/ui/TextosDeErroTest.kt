package dev.rafael.app.ui

import dev.rafael.contract.error.ErrorCodes
import dev.rafael.core.result.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O catálogo de textos de erro do cliente (G.2, extraído na G.3, ARCH #37).
 *
 * ## O que este arquivo impede
 *
 * A G.2 moveu 103 frases do servidor para o cliente. O jeito de essa mudança apodrecer é simples e
 * silencioso: **alguém acrescenta um código no servidor e não acrescenta o texto aqui**. A tela
 * cai no fallback e mostra a frase em português do servidor, para sempre, sem ninguém notar.
 *
 * O teste da cobertura fecha essa porta pelo build.
 *
 * ## O que mudou com a extração
 *
 * `TextosDeErro.de` devolve `@StringRes Int?`, e num teste JVM um `R.string.*` é **um número sem o
 * texto**. Duas consequências, e as duas melhoraram o arquivo:
 *
 * 1. As asserções sobre a FORMA do texto (não parece código, sem travessão) saíram daqui e viraram
 *    o `CatalogoDeStringsTest`, que varre o `strings.xml` inteiro em vez de só a área de erros.
 * 2. As que precisam da frase leem o catálogo pela chave DERIVADA do código
 *    (`PESSOA_NAO_EXISTE` → `erro_pessoa_nao_existe`). Isso passou a ser verificável: chave fora do
 *    padrão quebra o build em vez de virar string órfã no documento do tradutor.
 */
class TextosDeErroTest {

    /** Todas as constantes `String` do `ErrorCodes`, por reflexão. */
    private fun todosOsCodigos(): List<String> =
        ErrorCodes::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.isAccessible = true; it.get(ErrorCodes) as String }

    /** A chave do `strings.xml` que corresponde a um código, pela convenção do arquivo. */
    private fun chaveDe(codigo: String) = "erro_" + codigo.lowercase()

    /** A frase que o usuário lê para este código, ou `null` se ele não tem texto próprio. */
    private fun fraseDe(codigo: String) = CatalogoDeStrings.porChave[chaveDe(codigo)]

    /**
     * ⭐ **Todo código tem texto próprio, ou está declarado como exceção.**
     *
     * As duas listas de exceção não são escapatória: elas obrigam a decisão a ficar ESCRITA.
     *
     * - `GENERICOS` são os fallbacks de família, cujo texto o `ErrorUi` já escreve;
     * - `SEM_TEXTO_PROPRIO` está VAZIO desde 2026-09-11: os nove que carregavam número do
     *   servidor entraram no catálogo com o número dentro da frase, e o conjunto continua
     *   existindo porque é onde a decisão de um código novo sem texto vai morar.
     *
     * > **Débito que ninguém consegue enumerar não é débito, é surpresa.**
     *
     * ⚠️ A frase acima dizia *"quando o inglês entrar, os nove aparecerão em português"*. Isso
     * deixou de ser verdade em 2026-09-11: eles entraram no catálogo, e **nenhuma frase de erro do
     * app fica sem tradução**. O que sobrou no lugar é outro débito, menor e registrado: doze
     * strings do catálogo cravam números que são constantes do SERVIDOR e que elas não enxergam.
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
     * ⭐ **A chave no `strings.xml` é DERIVADA do código, e o build confere.**
     *
     * Este é o teste que a extração acrescentou, e ele fecha um buraco novo: `de()` devolve um
     * `Int`, e um `Int` errado é indistinguível de um certo lendo o código. A ponte é a convenção
     * — `PESSOA_NAO_EXISTE` → `erro_pessoa_nao_existe` — e ela só vale se for verificada.
     *
     * Nos dois sentidos, porque os dois erros são reais:
     *
     * - código com texto em `de()` e **sem** a chave no catálogo → alguém escreveu a chave errada,
     *   e o app mostraria a frase de OUTRO erro;
     * - chave `erro_*` no catálogo e **sem** código correspondente → sobrou lixo de um código
     *   apagado, e o tradutor recebe uma frase que ninguém vai ler.
     */
    @Test
    fun `a chave de cada codigo segue a convencao, nos dois sentidos`() {
        val comTexto = todosOsCodigos().filter { TextosDeErro.de(it) != null }

        val semChave = comTexto.filter { fraseDe(it) == null }
        assertTrue(
            semChave.isEmpty(),
            "código com texto em TextosDeErro.de e sem a chave derivada no strings.xml: " +
                "${semChave.map(::chaveDe)}",
        )

        // O caminho inverso ignora as chaves de CATEGORIA (erro_titulo_*, erro_texto_*, erro_acao_*),
        // que pertencem à família de erro e não a nenhum ErrorCode.
        val deCategoria = setOf("erro_titulo_", "erro_texto_", "erro_acao_")
        val esperadas = comTexto.map(::chaveDe).toSet()
        val sobrando = CatalogoDeStrings.entradas
            .map { it.chave }
            .filter { chave -> chave.startsWith("erro_") && deCategoria.none { chave.startsWith(it) } }
            .filter { chave -> chave !in esperadas }

        assertTrue(sobrando.isEmpty(), "chave `erro_*` sem código correspondente: $sobrando")
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
     * atualizou o app. Depois da G.3 a asserção ficou mais forte de graça: o texto tem de ser
     * literalmente [Frase.DoServidor], e não uma coincidência de conteúdo.
     */
    @Test
    fun `codigo desconhecido usa a mensagem do servidor`() {
        val frase = "Uma explicação que este app ainda não conhece."
        val visual = AppError.Conflict(frase, "CODIGO_DO_FUTURO")
            .visual(temRede = true, contexto = ErroContexto.LOGADO)

        assertEquals(Frase.DoServidor(frase), visual.texto)
    }

    /** E erro sem código nenhum também. É o caminho de todo `AppError` construído sem motivo. */
    @Test
    fun `erro sem codigo usa a mensagem do servidor`() {
        val frase = "Alguma coisa específica que o servidor sabe."
        val visual = AppError.Conflict(frase)
            .visual(temRede = true, contexto = ErroContexto.LOGADO)

        assertEquals(Frase.DoServidor(frase), visual.texto)
    }

    /**
     * Os cinco desmembramentos da G.2 têm textos DIFERENTES. Unificá-los desfaria a fatia.
     *
     * Compara as FRASES do catálogo, não os ids: dois ids diferentes apontando para o mesmo texto
     * é exatamente a regressão que este teste procura, e comparar `Int` não a veria.
     */
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
            val a = fraseDe(um)
            val b = fraseDe(outro)
            assertNotNull(a, "$um perdeu o texto")
            assertNotNull(b, "$outro perdeu o texto")
            assertTrue(a != b, "`$um` e `$outro` foram desmembrados e voltaram a dizer a mesma coisa")
        }
    }
}
