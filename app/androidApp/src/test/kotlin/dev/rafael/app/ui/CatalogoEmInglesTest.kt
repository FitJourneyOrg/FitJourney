package dev.rafael.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prende o `values-en` ao `values` (fatia G.4, ARCH #37).
 *
 * ## ⭐ Por que este arquivo existe
 *
 * Porque **chave que falta na tradução não quebra nada**. O Android resolve o que não achou
 * subindo até a pasta padrão, em silêncio: o app roda, nada é logado, e quem escolheu inglês lê
 * português. É o pior modo de falha possível — o que falha alto você conserta hoje; o que falha
 * calado fica até um usuário reclamar.
 *
 * > **Tradução não quebra o build sozinha. Ou o build pergunta, ou ninguém pergunta.**
 *
 * ## O que é conferido, e o que deliberadamente não é
 *
 * | | onde vale |
 * |---|---|
 * | conjunto de chaves, nos DOIS sentidos | os dois catálogos |
 * | parâmetros posicionais por chave | os dois catálogos |
 * | `one` e `other` em todo plural | os dois catálogos |
 * | travessão e caixa alta | os dois catálogos |
 * | comentário de contexto | **só o pt-BR** |
 *
 * O contexto fica fora de propósito: o `values-en` não repete os comentários, porque duas cópias
 * da mesma explicação são dois lugares para atualizar e um para esquecer. O pt-BR é a fonte.
 *
 * ## Qualidade do inglês não se testa aqui
 *
 * Nada neste arquivo diz que a tradução está BOA. Testa-se o que quebra sozinho: chave sumida,
 * parâmetro trocado, forma de plural faltando. A frase é revisão humana, e fingir o contrário
 * com um teste que conta palavras daria confiança sem base.
 */
class CatalogoEmInglesTest {

    private val pt = CatalogoDeStrings
    private val en = CatalogoEmIngles

    /** `%1$s`, `%2$02d`: o índice e o tipo. A ORDEM na frase pode mudar; o conjunto não pode. */
    private val posicional = Regex("""%(\d+)\$[-#+ 0,(]*\d*(?:\.\d+)?([sdfe])""")

    private fun parametros(vararg textos: String): List<Pair<Int, String>> =
        textos.flatMap { posicional.findAll(it) }
            .associate { it.groupValues[1].toInt() to it.groupValues[2] }
            .toList()
            .sortedBy { it.first }

    @Test
    fun `os dois catalogos declaram exatamente as mesmas chaves`() {
        val chavesPt = pt.entradas.map { it.chave }.toSet()
        val chavesEn = en.entradas.map { it.chave }.toSet()

        assertEquals(
            emptySet(), chavesPt - chavesEn,
            "chave sem tradução: o app mostra PORTUGUÊS para quem escolheu inglês, sem erro nenhum",
        )
        assertEquals(
            emptySet(), chavesEn - chavesPt,
            "chave só em inglês: ou o pt-BR perdeu uma frase, ou isto aqui é lixo que ninguém lê",
        )
    }

    @Test
    fun `os dois catalogos declaram exatamente os mesmos plurais`() {
        val pluraisPt = pt.plurais.map { it.nome }.toSet()
        val pluraisEn = en.plurais.map { it.nome }.toSet()

        assertEquals(emptySet(), pluraisPt - pluraisEn, "plural sem tradução")
        assertEquals(emptySet(), pluraisEn - pluraisPt, "plural só em inglês")
    }

    /**
     * ⭐ **O defeito que nem o compilador nem o Lint pegam.**
     *
     * `%1$s` no catálogo e um argumento a menos na chamada estoura em runtime, com
     * `IllegalFormatException` — e estoura **só no idioma de quem traduziu**, porque a tela foi
     * testada no outro. Trocar `%1$d` por `%1$s` é pior ainda: não estoura, imprime lixo.
     *
     * A ordem dentro da frase PODE mudar (`checkin_data_as` inverte dia e mês em en-US, e é para
     * isso que o placeholder é numerado). O que não pode mudar é o CONJUNTO.
     */
    @Test
    fun `cada chave declara os mesmos parametros nos dois idiomas`() {
        val porChaveEn = en.entradas.associate { it.chave to it.valor }

        val divergentes = pt.entradas.mapNotNull { entrada ->
            val emIngles = porChaveEn[entrada.chave] ?: return@mapNotNull null
            val dePt = parametros(entrada.valor)
            val deEn = parametros(emIngles)
            if (dePt == deEn) null else "${entrada.chave}: pt=$dePt en=$deEn"
        }

        assertTrue(
            divergentes.isEmpty(),
            "parâmetro que só existe de um lado explode em runtime, no idioma de quem traduziu: $divergentes",
        )
    }

    @Test
    fun `cada plural declara os mesmos parametros nos dois idiomas`() {
        val porNomeEn = en.plurais.associateBy { it.nome }

        val divergentes = pt.plurais.mapNotNull { plural ->
            val emIngles = porNomeEn[plural.nome] ?: return@mapNotNull null
            val dePt = parametros(*plural.formas.values.toTypedArray())
            val deEn = parametros(*emIngles.formas.values.toTypedArray())
            if (dePt == deEn) null else "${plural.nome}: pt=$dePt en=$deEn"
        }

        assertTrue(divergentes.isEmpty(), "plural com parâmetro diferente entre idiomas: $divergentes")
    }

    /**
     * O inglês usa `one` e `other`, e `other` cobre o ZERO — "0 people", não "0 person". É regra
     * diferente do português, e é a razão de isto ser `<plurals>` e não um `if` no Kotlin.
     */
    @Test
    fun `todo plural em ingles tem one e other`() {
        val faltando = en.plurais
            .filter { !it.formas.keys.containsAll(listOf("one", "other")) }
            .map { "${it.nome} tem ${it.formas.keys}" }

        assertTrue(faltando.isEmpty(), "plural incompleto em inglês: $faltando")
    }

    /**
     * ⭐ **Os pares que parecem duplicata e não são.**
     *
     * Quatro pares são IDÊNTICOS em português e precisam ser DIFERENTES em inglês. É a única coisa
     * do catálogo que alguém cuidadoso erraria justamente por ser cuidadoso: vê a mesma palavra
     * duas vezes, conclui que é duplicata e usa uma tradução só.
     *
     * Este teste é o que impede que a conclusão razoável vire defeito silencioso.
     */
    @Test
    fun `os pares cruzados divergem em ingles`() {
        val pares = listOf(
            Triple("grupo_detalhe_sair_confirmar", "menu_sair", "sair do DESAFIO × encerrar a SESSÃO"),
            Triple("programa_detalhe_descanso", "sessao_descanso", "DIA sem treino × o cronômetro entre séries"),
            Triple("grupo_detalhe_fazer_checkin", "checkin_enviar", "ABRIR a tela × ENVIAR o check-in"),
            Triple("home_secao_treino_de_hoje", "home_treino_de_hoje", "o RÓTULO do card × o NOME do treino"),
            Triple("nav_aba_treino", "treino_detalhe_titulo_padrao", "a ABA × o NOME de um treino sem nome"),
        )

        val colapsados = pares.mapNotNull { (a, b, motivo) ->
            val primeiro = en.texto(a)
            val segundo = en.texto(b)
            if (primeiro != segundo) null else "`$a` e `$b` ficaram ambos \"$primeiro\" — são $motivo"
        }

        assertTrue(
            colapsados.isEmpty(),
            "estes pares TÊM de ser palavras diferentes em inglês: $colapsados",
        )

        // Em pt-BR eles são idênticos: se deixarem de ser, o aviso perdeu o motivo de existir.
        pares.forEach { (a, b, _) ->
            assertEquals(
                pt.texto(a), pt.texto(b),
                "`$a` e `$b` já não são idênticos em pt-BR: o par saiu do aviso cruzado e este " +
                    "teste está guardando uma regra que não descreve mais o produto",
            )
        }
    }

    /**
     * O quinto par do aviso cruzado é o contrário dos outros quatro: `app_name` e `login_titulo`
     * são iguais nos DOIS idiomas, porque os dois são a marca. Ele fica aqui para não ser
     * "consertado" por alguém que leu os outros quatro e generalizou.
     */
    @Test
    fun `a marca e identica nos dois idiomas, de proposito`() {
        assertEquals("FitJourney", en.texto("app_name"))
        assertEquals("FitJourney", en.texto("login_titulo"))
    }

    /**
     * As 36 chaves que NÃO mudam de um idioma para o outro: marca, jargão de academia que não se
     * traduz (`Push/Pull/Legs`), unidade (`kg`), sigla (`XP`) e frase que é só formato
     * (`%1$d/%2$d`).
     *
     * A lista é fixada porque a pergunta interessante é a inversa: **uma chave nova que aparecer
     * aqui é quase sempre tradução esquecida**, não coincidência. Copiar o português e seguir em
     * frente é o erro mais fácil de cometer neste arquivo, e o único que não deixa rastro.
     */
    @Test
    fun `as chaves iguais nos dois idiomas sao exatamente as declaradas`() {
        val declaradas = setOf(
            "app_name", "checkin_titulo", "comum_kg", "comum_premium", "comum_reps",
            "comum_xp_do_nivel", "entrar_regra_item", "enum_categoria_cardio", "enum_categoria_core",
            "enum_categoria_crossfit", "enum_equipamento_bosu", "enum_equipamento_kettlebell",
            "enum_musculo_core", "enum_regra_gym_pass", "enum_split_arnold", "enum_split_full_body",
            // ⭐ Nome de idioma NUNCA se traduz, em idioma nenhum: quem precisa da tela de idioma
            // é quem abriu o app numa língua que não lê, e reconhece `English`, não `Inglês`.
            // Traduzir a lista tornaria ilegível a única tela que existe para sair de um idioma
            // ilegível. Estes dois são a igualdade MAIS deliberada da lista inteira.
            "idioma_pt_br", "idioma_en",
            "enum_split_push_pull_legs", "enum_split_ul_ppl", "enum_split_upper_lower",
            "enum_split_upper_lower_full", "exercicio_execucao_bilateral",
            "exercicio_execucao_unilateral", "grupo_detalhe_aba_posts", "grupo_detalhe_aba_ranking",
            "grupo_detalhe_admin", "grupo_form_data_botao", "home_treino_minutos", "home_xp_de_hoje",
            "login_titulo", "moderacao_motivo_item", "paywall_coluna_free", "perfil_publico_xp",
            "programa_detalhe_dia_atual", "quiz_progresso", "sessao_mais_30s", "sessao_tempo",
        )

        val porChaveEn = en.entradas.associate { it.chave to it.valor }
        val iguais = pt.entradas
            .filter { porChaveEn[it.chave] == it.valor }
            .map { it.chave }
            .toSet()

        assertEquals(
            declaradas, iguais,
            "chave nova idêntica nos dois idiomas costuma ser tradução esquecida, não jargão",
        )
    }

    /**
     * Os mesmos invariantes de forma do pt-BR: eles são do PRODUTO, não do português.
     *
     * Travessão, caixa alta e reticências de três pontos são decisão de desenho, e desenho não
     * muda de idioma. Um catálogo traduzido é exatamente onde uma convenção dessas se perde, e
     * perder-se num idioma que ninguém do time lê é perder-se para sempre.
     */
    @Test
    fun `o ingles nao usa travessao, caixa alta nem tres pontos`() {
        val semPlaceholder = Regex("""%\d+\$[-#+ 0,(]*\d*(?:\.\d+)?[sdfe]""")

        val travessao = en.todosOsTextos
            .filter { (_, v) -> v.contains('—') || v.contains('–') }
            .map { it.first }
        assertTrue(travessao.isEmpty(), "travessão é proibido em todo idioma (#37, seção 8): $travessao")

        val gritando = en.todosOsTextos
            .map { it.first to semPlaceholder.replace(it.second, "") }
            .filter { (_, v) -> v.any { it.isLetter() } && v == v.uppercase() && v.count { it.isLetter() } > 3 }
            .map { it.first }
        assertTrue(gritando.isEmpty(), "caixa alta é decisão de desenho, não texto: $gritando")

        val tresPontos = en.todosOsTextos
            .filter { (_, v) -> v.contains("...") }
            .map { it.first }
        assertTrue(tresPontos.isEmpty(), "use o caractere `…` no lugar de `...`: $tresPontos")
    }

    /** Frase vazia não falha nada e não mostra nada: some da tela sem deixar rastro. */
    @Test
    fun `nenhuma frase em ingles esta vazia`() {
        val vazias = en.todosOsTextos.filter { it.second.isBlank() }.map { it.first }
        assertTrue(vazias.isEmpty(), "frase vazia desaparece da tela sem erro: $vazias")
    }
}
