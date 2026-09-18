package dev.rafael.app.ui

import org.w3c.dom.Comment
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lê o `strings.xml` de uma pasta de idioma como ARQUIVO, em teste de unidade comum (ARCH #37).
 *
 * Uma [Catalogo] por pasta de idioma: [CatalogoDeStrings] para o pt-BR e [CatalogoEmIngles] para o
 * inglês. A leitura é a mesma porque o formato é o mesmo; o que muda é o que se PERGUNTA a cada
 * um, e isso vive nos testes.
 *
 * ## Por que ler o XML em vez de usar o `R`
 *
 * Depois da extração, os invariantes de FORMA do texto — não parece código, sem travessão, tem
 * comentário de contexto — deixaram de ter onde morar: eles conferiam literais Kotlin que agora
 * são `R.string.*`. E `R` num teste JVM devolve **um `Int` sem o texto**; resolver id para frase
 * exigiria Robolectric ou teste instrumentado, que este módulo não tem e que custaria minutos de
 * build a cada rodada.
 *
 * Ler o arquivo custa milissegundos e cobre o catálogo INTEIRO, não só a área da vez. É o que faz
 * as áreas seguintes da G.3 herdarem a rede de segurança sem ninguém escrever teste novo.
 *
 * > **Invariante que só vale para a área que você está extraindo não é invariante, é revisão.**
 *
 * O caminho é relativo porque o Gradle roda teste de módulo Android com o diretório de trabalho no
 * próprio módulo. Os pais são tentados assim mesmo — a IDE às vezes discorda do Gradle, e um teste
 * que passa no terminal e falha no Android Studio vira "teste chato" e depois vira teste apagado.
 *
 * ---
 *
 * [CatalogoDeStrings] é o pt-BR, fonte de tudo: é ele que carrega os comentários de contexto.
 */
object CatalogoDeStrings : Catalogo("src/main/res/values/strings.xml")

/**
 * O catálogo em inglês (fatia G.4).
 *
 * Ele NÃO repete os comentários de contexto, de propósito: duas cópias da mesma explicação são
 * dois lugares para atualizar e um para esquecer. Por isso os invariantes de CONTEXTO
 * (`toda string esta sob um comentario`) valem só para o pt-BR, e os de FORMA e de PARIDADE, que
 * são o que pode quebrar em produção, valem para os dois — ver `CatalogoEmInglesTest`.
 */
object CatalogoEmIngles : Catalogo("src/main/res/values-en/strings.xml")

open class Catalogo(private val caminho: String) {

    val arquivo: File by lazy {
        generateSequence(File(".").canonicalFile) { it.parentFile }
            .take(5)
            .map { File(it, caminho) }
            .firstOrNull { it.isFile }
            ?: error("não achei $caminho a partir de ${File(".").absolutePath}")
    }

    /** Uma entrada do catálogo, na ordem do documento. */
    data class Entrada(
        val chave: String,
        val valor: String,
        /** O comentário de contexto que cobre esta entrada, ou `null` se não há. */
        val comentario: String?,
        /** Quantas strings separam esta entrada do comentário que a cobre. 0 = logo abaixo dele. */
        val distanciaDoComentario: Int,
    )

    /**
     * Um `<plurals>`: o nome e as formas que ele declara.
     *
     * Existe separado da [Entrada] porque a pergunta que se faz a um plural é outra — não "esta
     * frase está certa", e sim "**este idioma tem todas as formas que precisa**". pt-BR usa `one`
     * e `other`; russo acrescenta `few`/`many`, árabe acrescenta `zero`/`two`.
     */
    data class Plural(
        val nome: String,
        val formas: Map<String, String>,
        val comentario: String?,
    )

    private data class Lido(val entradas: List<Entrada>, val plurais: List<Plural>)

    private val lido: Lido by lazy {
        val raiz = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(arquivo)
            .documentElement

        val strings = mutableListOf<Entrada>()
        val plurais = mutableListOf<Plural>()
        // O comentário vale para a SEQUÊNCIA de strings que vem depois dele, não só para a
        // primeira: agrupar quatro rótulos irmãos sob um comentário é o estilo do arquivo.
        var comentarioCorrente: String? = null
        var distancia = 0

        val filhos = raiz.childNodes
        for (i in 0 until filhos.length) {
            when (val no: Node = filhos.item(i)) {
                is Comment -> {
                    comentarioCorrente = no.data.trim()
                    distancia = 0
                }

                is Element -> when (no.tagName) {
                    "string" -> {
                        strings += Entrada(
                            chave = no.getAttribute("name"),
                            valor = no.textContent,
                            comentario = comentarioCorrente,
                            distanciaDoComentario = distancia,
                        )
                        distancia++
                    }

                    "plurals" -> {
                        val itens = no.getElementsByTagName("item")
                        plurais += Plural(
                            nome = no.getAttribute("name"),
                            formas = (0 until itens.length).associate { j ->
                                val item = itens.item(j) as Element
                                item.getAttribute("quantity") to item.textContent
                            },
                            comentario = comentarioCorrente,
                        )
                        distancia++
                    }

                    else -> comentarioCorrente = null
                }

                else -> Unit // texto/indentação: não quebra o agrupamento
            }
        }
        Lido(strings, plurais)
    }

    val entradas: List<Entrada> get() = lido.entradas

    val plurais: List<Plural> get() = lido.plurais

    val porChave: Map<String, String> by lazy { entradas.associate { it.chave to it.valor } }

    /**
     * TODO texto que o usuário pode ler, inclusive as formas dos plurais.
     *
     * É o que os invariantes de forma percorrem: um `%s` sem posição dentro de um `<plurals>` erra
     * exatamente igual, e ficaria de fora se a varredura só olhasse `<string>`.
     */
    val todosOsTextos: List<Pair<String, String>> by lazy {
        entradas.map { it.chave to it.valor } +
            plurais.flatMap { p -> p.formas.map { (q, v) -> "${p.nome}/$q" to v } }
    }

    /** O texto de uma chave, ou falha dizendo qual chave faltou. */
    fun texto(chave: String): String =
        porChave[chave] ?: error("`$chave` não está no strings.xml")
}
