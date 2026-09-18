package dev.rafael.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Os invariantes de FORMA do catálogo pt-BR (fatia G.3, ARCH #37).
 *
 * ## Por que este arquivo nasceu na área de ERROS
 *
 * Antes da extração, "nenhum texto parece código" e "nenhum texto usa travessão" eram asserções do
 * `TextosDeErroTest` e do `ErrorUiTest`, e conferiam **literais Kotlin**. A extração leva esses
 * literais para o `strings.xml` — e levaria as asserções junto para o vazio, verdes e sem objeto.
 *
 * Aqui elas passam a valer para o catálogo inteiro, incluindo as áreas da G.3 que ainda não foram
 * extraídas. É de propósito: o custo de escrever isto uma vez é o mesmo, e a alternativa era seis
 * revisões manuais.
 *
 * > **Invariante que só vale para a área que você está extraindo não é invariante, é revisão.**
 *
 * ## De lista enumerada a invariante
 *
 * Travessão e caixa alta chegaram ao catálogo porque **vieram assim do código** — extrair não é
 * o momento de reescrever a tela. Durante a G.3 os dois foram ENUMERADOS em listas fixadas, e a
 * lista servia para uma coisa só: impedir que o débito crescesse enquanto ninguém decidia.
 *
 * > **Débito que ninguém consegue enumerar não é débito, é surpresa.**
 *
 * **Em 2026-09-10 o portão do pt-BR decidiu os dois, e as listas viraram proibições.** É a forma
 * final que um débito enumerado deve tomar: ou ele é pago e o teste endurece, ou a lista vira
 * mobília. A única lista que sobrou é a dos textos repetidos, porque lá a decisão foi MANTER.
 *
 * ## O que este arquivo NÃO garante
 *
 * Que cada string tenha um comentário **próprio**. Um comentário cobre a sequência abaixo dele, e
 * agrupar dezessete rótulos de enum sob um comentário é o estilo certo do arquivo. A verificação
 * frase a frase é do `gen_strings.py` e do portão em que o Rafael valida o pt-BR.
 */
class CatalogoDeStringsTest {

    private val entradas get() = CatalogoDeStrings.entradas
    private val todos get() = CatalogoDeStrings.todosOsTextos

    /** Se o arquivo não for encontrado, todo o resto passaria vazio e verde. */
    @Test
    fun `o catalogo foi lido e nao esta vazio`() {
        assertTrue(
            entradas.size > 100,
            "só ${entradas.size} strings em ${CatalogoDeStrings.arquivo.absolutePath}",
        )
    }

    /** Chave repetida é a única forma de uma tradução sumir sem ninguém notar. */
    @Test
    fun `nenhuma chave repetida`() {
        val repetidas = todos.groupBy { it.first }.filterValues { it.size > 1 }.keys
        assertTrue(repetidas.isEmpty(), "chave duplicada no strings.xml: $repetidas")
    }

    /**
     * ⭐ **Toda string está sob um comentário de contexto.**
     *
     * *"Quem traduz não tem o app aberto. `Denunciar` sozinho pode virar Report, Flag ou Denounce,
     * e as três estão certas em algum contexto e erradas neste."* — é a regra escrita no topo do
     * `strings.xml`, e sem este teste ela é só um pedido.
     */
    @Test
    fun `toda string esta sob um comentario de contexto`() {
        val orfas = entradas.filter { it.comentario.isNullOrBlank() }.map { it.chave } +
            CatalogoDeStrings.plurais.filter { it.comentario.isNullOrBlank() }.map { it.nome }
        assertTrue(orfas.isEmpty(), "string sem nenhum comentário acima: $orfas")
    }

    /**
     * E o comentário não pode estar longe demais.
     *
     * Um comentário cobre a sequência abaixo dele; sequência muito longa quer dizer que ele virou
     * cabeçalho de seção e parou de descrever a string. O limite é generoso de propósito — as
     * famílias de enum têm 17 valores — e existe para pegar o caso real: **string acrescentada no
     * fim de um bloco cujo comentário fala de outra coisa.**
     */
    @Test
    fun `nenhuma string ficou longe demais do seu comentario`() {
        val distantes = entradas.filter { it.distanciaDoComentario > 20 }
            .map { "${it.chave} (+${it.distanciaDoComentario})" }
        assertTrue(distantes.isEmpty(), "comentário longe demais para descrever: $distantes")
    }

    /**
     * ⭐ **Nenhum texto é um identificador disfarçado.**
     *
     * O `.name` de enum já vazou para a tela **duas** vezes: "TETO_ATINGIDO" na fatia A.2, e na
     * própria G.3 o `it.name.lowercase().replace('_',' ')` do detalhe do desafio, que mostrava
     * `localizacao` sem cedilha para o usuário brasileiro.
     *
     * A forma que denuncia os dois é a mesma: **sublinhado num texto sem espaço nenhum**. Frase
     * de gente tem espaço e não tem `_`; identificador não tem espaço e tem.
     *
     * A regra olha o valor INTEIRO e não procura `_` solto — `Procurar (ex.: Sao_Paulo)` contém
     * sublinhado e está certíssimo: é um identificador IANA citado dentro de uma frase.
     *
     * ## ⚠️ O que esta regra NÃO faz mais, e por quê
     *
     * A primeira versão também acusava **palavra única em caixa alta**, e isso reprovou
     * `HISTÓRICO` — um cabeçalho de seção legítimo. Não era um caso limite: a cláusula fazia o
     * trabalho do teste de caixa alta logo abaixo, e o preço era um valor honesto ter de aparecer
     * em DUAS listas de exceção para o build passar.
     *
     * > **Dois invariantes que reprovam a mesma coisa não dobram a proteção; dobram o custo de
     * > cada exceção legítima.**
     *
     * Nada ficou descoberto: um `TETOATINGIDO` vazado continua quebrando o build, pelo teste de
     * caixa alta. A responsabilidade só ficou dividida — aqui, sublinhado; lá, caixa alta.
     */
    @Test
    fun `nenhum valor e um identificador disfarcado`() {
        todos.forEach { (chave, valor) ->
            assertTrue(valor.isNotBlank(), "`$chave` está vazia")
            val semEspaco = valor.none { it.isWhitespace() }
            assertTrue(
                !(semEspaco && valor.contains('_')),
                "`$chave` = `$valor` parece identificador de código, não texto de tela",
            )
        }
    }

    /**
     * ⭐ **Nenhuma string carrega a própria CAIXA ALTA.**
     *
     * ## A decisão, e por que ela custou 12 telas
     *
     * Doze cabeçalhos vinham em maiúsculas cravadas no texto (`"SEU NOME"`, `"HISTÓRICO"`).
     * Caixa é decisão de DESENHO, e desenho morando no dado quebra em quem traduz: o alemão
     * capitaliza substantivos por gramática, o turco tem duas letras i, e há escritas sem caixa
     * nenhuma. Com a caixa na string, cada uma dessas línguas herda uma escolha tipográfica que
     * foi tomada olhando só para o português.
     *
     * As três saídas eram:
     *
     * | | custo |
     * |---|---|
     * | deixar na string | o problema acima, em 12 pontos |
     * | `.uppercase()` na tela | impõe a caixa a TODO idioma, e erra o i turco |
     * | caixa normal no catálogo | **os 12 cabeçalhos deixam de ser maiúsculos** |
     *
     * O Compose não tem `text-transform`, então a terceira é a única que não mente: a aparência
     * mudou de propósito, em 2026-09-10, e é isso que este teste protege.
     *
     * > **Caixa alta cravada no texto é decisão de desenho viajando dentro do dado.**
     *
     * ⚠️ Não confunda com sigla: `XP` e `TRX` continuam válidos — a regra pede mais de três
     * letras, e uma string inteira em caixa é o que ela pega.
     *
     * ⚠️ **O PLACEHOLDER SAI ANTES DA COMPARAÇÃO, e isto é uma correção de 2026-09-11.**
     *
     * A primeira versão comparava a string crua com a própria em caixa alta, e `%1$d` tem um `d`
     * minúsculo: `"PARTICIPANTES · %1$d".uppercase()` dá `"PARTICIPANTES · %1$D"` — diferente do
     * original, e por isso a string gritada nunca era igual a si mesma em caixa alta.
     * Dois cabeçalhos gritados atravessaram a faxina de 2026-09-10 exatamente por isso, e o teste
     * passou verde nos dois.
     *
     * > **Invariante com buraco é pior que invariante ausente: o ausente você sabe que não tem.**
     */
    @Test
    fun `nenhuma string carrega a propria caixa alta`() {
        val semPlaceholder = Regex("%\\d+\\${'$'}[-#+ 0,(]*\\d*(?:\\.\\d+)?[sdfe]")
        val gritando = todos
            .map { it.first to semPlaceholder.replace(it.second, "") }
            .filter { (_, v) -> v.any { it.isLetter() } && v == v.uppercase() && v.count { it.isLetter() } > 3 }
            .map { "${it.first} = ${it.second}" }

        assertTrue(
            gritando.isEmpty(),
            "caixa alta é decisão de desenho e não pode voltar para o texto: $gritando",
        )
    }

    /**
     * **Reticências são o caractere `…`, nunca três pontos.**
     *
     * Três das quatro frases do catálogo já usavam `…`; uma usava `...`, e a divergência
     * atravessou o portão do pt-BR inteiro **porque ninguém enxerga a diferença lendo**. Quem
     * enxerga é o leitor de tela: `…` é anunciado como reticência, `...` vira três pausas. E numa
     * fonte proporcional os três pontos ficam com espaçamento diferente do resto da linha.
     *
     * É a mesma família do travessão e da caixa alta, e por isso vira invariante em vez de nota:
     * convenção tipográfica que depende de alguém reparar não sobrevive à segunda pessoa que
     * escreve uma string.
     *
     * > **Decisão de desenho não viaja dentro do dado.**
     */
    @Test
    fun `nenhuma string usa tres pontos no lugar das reticencias`() {
        val comTresPontos = todos
            .filter { (_, v) -> v.contains("...") }
            .map { "${it.first} = ${it.second}" }

        assertTrue(
            comTresPontos.isEmpty(),
            "use o caractere `…` no lugar de `...`: $comTresPontos",
        )
    }

    /**
     * ⭐ **Interpolação é placeholder posicional, nunca concatenação.**
     *
     * `"Também: " + lista` vira `%1$s`, e não `%s`: é a forma que o Android reordena por idioma.
     * Alguns idiomas precisam do argumento em outra posição da frase, e `%s` não permite isso.
     *
     * Foi a segunda das três armadilhas desta fatia, e a única que **não** quebra o build sozinha:
     * `%s` compila, roda e só falha na tradução, meses depois.
     *
     * O índice vem antes das flags (`%1$02d`, não `%02d$1`) — é o que o `String.format` do Java
     * aceita, e escrever ao contrário lança em runtime, não em compilação.
     */
    @Test
    fun `todo placeholder e posicional`() {
        val posicional = Regex("%\\d+\\$[-#+ 0,(]*\\d*(?:\\.\\d+)?[sdfe]")
        val qualquer = Regex("%[^%]")

        todos.forEach { (chave, valor) ->
            val achados = qualquer.findAll(valor).count()
            if (achados == 0) return@forEach
            assertEquals(
                achados,
                posicional.findAll(valor).count(),
                "`$chave` usa placeholder não posicional: `$valor`",
            )
        }
    }

    /**
     * ⭐ **Todo `plurals` declara `one` e `other`.**
     *
     * `other` é a forma que TODO idioma usa; `one` é a que o pt-BR precisa. Um `plurals` sem uma
     * delas não quebra o build — o Android só devolve a forma que achar, ou nada — e o defeito
     * aparece na tela de quem tiver exatamente aquela contagem.
     *
     * O teste NÃO exige `zero`, `two`, `few` e `many`: essas o pt-BR não usa, e quem traduz
     * acrescenta as que o idioma dele pedir.
     */
    @Test
    fun `todo plural declara as formas que o pt-BR usa`() {
        assertTrue(CatalogoDeStrings.plurais.isNotEmpty(), "nenhum plurals foi lido do catálogo")

        CatalogoDeStrings.plurais.forEach { plural ->
            assertTrue(
                "other" in plural.formas,
                "`${plural.nome}` não tem a forma `other`, que todo idioma usa",
            )
            assertTrue(
                "one" in plural.formas,
                "`${plural.nome}` não tem a forma `one`, que o pt-BR usa",
            )
            plural.formas.forEach { (quantidade, texto) ->
                assertTrue(texto.isNotBlank(), "`${plural.nome}/$quantidade` está vazia")
            }
        }
    }

    /**
     * ⚠️ **Os textos IDÊNTICOS em duas chaves, enumerados.**
     *
     * ## Por que isto é um invariante e não uma limpeza
     *
     * *"Duplicata de texto sobrevive num idioma e diverge no segundo"* é a frase que justifica a
     * `Rotulos.kt` inteira — e a G.3 já a viu se cumprir: `MuscleGroup.FOREARMS` era "Antebraço"
     * numa tela e "Antebraços" noutra **antes de qualquer tradutor entrar**.
     *
     * O tradutor recebe duas linhas iguais, em pontos diferentes do catálogo, e não tem como
     * saber que precisam combinar. Então cada par aqui é uma decisão, não um descuido.
     *
     * ## As três famílias, e por que cada uma fica
     *
     * **1. Enums diferentes que coincidem em pt-BR.** `MuscleGroup` e `ExerciseCategory` são dois
     * enums do contrato com nove valores homônimos. Unificar as chaves acoplaria os dois enums:
     * acrescentar uma categoria que não é grupo muscular passaria a exigir mexer no outro.
     *
     * **2. Falsos duplicados — a mesma palavra para coisas diferentes.** São os perigosos, porque
     * "limpar" um deles quebra uma tela num idioma que ninguém desta equipe lê:
     * - `grupo_detalhe_sair_confirmar` × `menu_sair`: sair de um DESAFIO × encerrar a SESSÃO.
     *   Em inglês, *Leave* e *Log out*.
     * - `programa_detalhe_descanso` × `sessao_descanso`: dia sem treino × cronômetro entre séries.
     *   Em inglês, *Rest day* e *Rest*.
     * - `app_name` × `login_titulo`: a marca (não se traduz) × o título da tela.
     * - `grupo_detalhe_fazer_checkin` × `checkin_enviar`: ABRIR a tela de check-in × ENVIAR o
     *   check-in preenchido. Em inglês, *Check in* e *Submit*.
     * - `home_secao_treino_de_hoje` × `home_treino_de_hoje`: o RÓTULO do card × o NOME de um
     *   treino que o programa não nomeou. Rótulo e dado.
     *
     * **3. Mantidos separados por decisão de 2026-09-10.** Mesma palavra, telas de contexto
     * diferente: `erro_acao_voltar` × `comum_voltar`, `erro_acao_tentar_de_novo` ×
     * `programa_reveal_tentar_de_novo`, `checkin_invalidar_confirmar` × `moderacao_invalidar`,
     * `login_email` × `conta_email`. E dois códigos de erro que compartilham frase de propósito
     * (`erro_dia_da_semana_invalido` × `erro_id_de_treino_invalido`): causas técnicas distintas
     * que o usuário não distingue, e uma delas pode ganhar frase própria depois.
     *
     * O risco aceito é conhecido: são quatro pares que podem divergir na tradução, e é por isso
     * que continuam nesta lista em vez de sumirem do radar.
     */
    @Test
    fun `os textos repetidos sao exatamente os declarados`() {
        val esperados = setOf(
            // 1. enums distintos que coincidem em pt-BR
            "Antebraços", "Bíceps", "Core", "Costas", "Glúteos", "Ombros", "Peito", "Pernas", "Tríceps",
            // 2. falsos duplicados: a mesma palavra para coisas diferentes
            "Sair", "Descanso", "FitJourney", "Fazer check-in", "Treino de hoje",
            // `nav_aba_treino` × `treino_detalhe_titulo_padrao`: a ABA e o nome de um treino sem
            // nome. Viram `Training` e `Workout` em inglês — falso duplicado descoberto em
            // 2026-09-11, quando as abas saíram do enum e entraram no catálogo.
            "Treino",
            // 3. a decidir no portão do pt-BR
            "Voltar", "Tentar de novo", "Invalidar", "E-mail",
            "Não consegui salvar o cronograma. Tente de novo.",
            // 4. abas × mesma palavra em outro lugar. Coincidem em inglês também, e continuam
            // separadas pelo ESPAÇO: a aba divide a largura da tela por quatro, e um idioma pode
            // precisar abreviar ali sem abreviar no título da tela.
            "Grupos", "Progresso",
        )

        val repetidos = entradas
            .groupBy { it.valor }
            .filterValues { it.size > 1 }
            .keys

        assertEquals(
            esperados,
            repetidos,
            "a lista de textos repetidos mudou. Texto novo repetido: ou vira UMA chave, " +
                "ou entra aqui com o motivo de continuarem separados.",
        )
    }

    /**
     * ⭐ **Nenhuma string usa travessão.**
     *
     * O #37, seção 8, proíbe `—` e `–` em qualquer idioma. Quinze vieram do código e foram
     * extraídos como estavam, para a extração não virar reescrita; **em 2026-09-10 o portão do
     * pt-BR os reescreveu**, e o critério foi o trabalho que cada um fazia:
     *
     * | o travessão estava | virou |
     * |---|---|
     * | introduzindo a consequência | `:` ou `.` |
     * | juntando orações coordenadas | `,` |
     *
     * O décimo quinto não era pontuação de frase: era o travessão **sozinho**, marcando "não
     * preenchido" nas linhas da Conta. Virou `Não informado`. Marcador tipográfico não é palavra —
     * quem usa leitor de tela ouve um traço, e nenhum idioma herda o sentido que o português deu
     * a ele.
     *
     * > **Pontuação que só existe num idioma vira ruído em todos os outros.**
     *
     * Como o de caixa alta, este teste deixou de contar e passou a proibir: não há mais lista de
     * pendências no catálogo, só invariantes.
     */
    @Test
    fun `nenhuma string usa travessao`() {
        val comTravessao = todos
            .filter { (_, v) -> v.contains('—') || v.contains('–') }
            .map { "${it.first} = ${it.second}" }

        assertTrue(
            comTravessao.isEmpty(),
            "o #37 seção 8 proíbe travessão em qualquer idioma: $comTravessao",
        )
    }
}
