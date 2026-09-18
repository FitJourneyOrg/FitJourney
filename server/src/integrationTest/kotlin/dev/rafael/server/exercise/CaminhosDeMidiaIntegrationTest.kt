package dev.rafael.server.exercise

import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExercisesTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Os caminhos de mídia do catálogo (V50).
 *
 * ## Por que isto virou teste, e não só uma migration bem escrita
 *
 * `video_ref` e `thumb_ref` guardam CAMINHO DE ARQUIVO. É um acoplamento entre o banco e a
 * estrutura de uma pasta, e ele tem uma propriedade péssima: **nada no build o enxerga**. Renomear
 * os arquivos não quebra compilação, não quebra teste, não gera log. Quebra a imagem na tela de
 * quem abriu o app.
 *
 * Foi o que aconteceu em 2026-09-17: a revisão de nomenclatura renomeou 1.846 arquivos para a
 * `chave`, o banco continuou apontando para o nome por extenso, e os 900 exercícios passaram a
 * servir 404 sem que nada acusasse.
 *
 * > **Caminho de arquivo guardado em banco é um acoplamento que nada no build enxerga: o dia em
 * > que alguém organiza a pasta, o banco fica mentindo e ninguém é avisado.**
 *
 * Este arquivo é o "alguém é avisado".
 *
 * ## O que ele NÃO consegue fazer
 *
 * Não confere se o arquivo EXISTE — o container do Postgres não vê `gifs_exercicios/`, e a pasta
 * nem vai para o git (decisão de 2026-08-23). O que dá para afirmar daqui é a **forma**, e ela
 * pega o defeito real: o rename mudou o formato do nome, de frase para snake_case.
 *
 * Quem confere a existência é o `verify.py`, do lado do disco. Os dois juntos fecham o par
 * "ref sem arquivo" e "arquivo sem ref"; nenhum dos dois sozinho fecha.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CaminhosDeMidiaIntegrationTest {

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    /** `<Categoria>/<chave>.<ext>` — a categoria aceita espaço e acento, o nome do arquivo não. */
    private val padraoVideo = Regex("""^[^/]+/[a-z0-9_]+\.mp4$""")
    private val padraoThumb = Regex("""^[^/]+/[a-z0-9_]+\.webp$""")

    private fun refs(): List<Triple<String, String, String>> = transaction {
        ExercisesTable.selectAll().map {
            Triple(it[ExercisesTable.name], it[ExercisesTable.videoRef], it[ExercisesTable.thumbRef])
        }
    }

    /**
     * ⭐ **Exatamente 900 caminhos estão no formato da `chave`.**
     *
     * Este é o teste que teria acusado o defeito no mesmo dia: ele falha se alguém renomear as
     * mídias sem a migration correspondente, e renomear mídia é o tipo de arrumação que parece
     * inofensiva.
     *
     * ⚠️ **A primeira versão dizia "TODO caminho preenchido", e estava errada** — a mesma falha
     * que a guarda da V50 cometeu e que abortou a suíte inteira. A V50 atualiza 900 das 965
     * linhas; as outras 65 continuam legitimamente fora do formato:
     *
     * - 39 são os duplicados que a V52 ainda vai remover, com o caminho antigo por extenso;
     * - 26 nunca tiveram mídia (ver o teste abaixo).
     *
     * > **Guarda que afirma sobre a tabela inteira quando a migration tocou uma parte dela acusa o
     * > que não mudou.**
     *
     * Contagem exata, e não "pelo menos": menos que 900 significa mapeamento que não alcançou
     * alguém, mais significa que o mapa está desatualizado. Os dois são defeito.
     */
    @Test
    fun `exatamente 900 caminhos usam a chave em snake_case`() {
        val noFormatoNovo = refs().filter { (_, video, thumb) ->
            padraoVideo.matches(video) && padraoThumb.matches(thumb)
        }

        assertEquals(
            900,
            noFormatoNovo.size,
            "as mídias foram renomeadas sem a migration, ou o mapa da V50 está desatualizado",
        )
    }

    /**
     * A outra metade: **quem está fora do formato novo só pode ser exercício sem mídia.**
     *
     * Sem isto, o teste acima passaria com 900 certos e um 901º apontando para qualquer coisa.
     *
     * ⚠️ Este número era `39 + 26` entre a V50 e a V51 — os 39 duplicados ainda carregavam o
     * caminho antigo por extenso. A V51 os removeu, e a queda para 26 é a confirmação de que a
     * remoção levou exatamente quem devia: se tivesse levado alguém do grupo bom junto, o teste
     * acima teria caído de 900 antes deste chegar aqui.
     *
     * > **Dois testes que se movem juntos provam mais que um que se move sozinho.**
     */
    @Test
    fun `fora do formato novo existem apenas os 26 sem midia`() {
        val fora = refs().filterNot { (_, video, thumb) ->
            padraoVideo.matches(video) && padraoThumb.matches(thumb)
        }

        assertEquals(
            26,
            fora.size,
            "caminho inesperado fora do padrão: ${fora.take(5).map { it.first }}",
        )
    }

    /** 965 - 39 duplicados = 926. Fixa o tamanho do catálogo depois da limpeza da V51. */
    @Test
    fun `o catalogo tem 926 exercicios`() {
        assertEquals(926, refs().size)
    }

    /**
     * ⭐ **Nome de exercício não usa travessão** (V52, ARCH #37 seção 8).
     *
     * A regra existe desde 2026-09-10 e tem teste varrendo o `strings.xml` inteiro. Só que nome de
     * exercício **não mora no `strings.xml`**: mora no banco, e chega por migration de dado.
     *
     * A fatia H mostrou isso na prática — a revisão de nomenclatura trouxe nove
     * `Liberação Miofascial com Rolo – Isquiotibiais`, todos com travessão curto, e nenhuma guarda
     * do projeto os teria pego. A V52 os trocou por dois-pontos.
     *
     * > **Convenção travada num arquivo não protege o dado que chega por outro caminho.**
     *
     * Este teste é esse outro caminho vigiado. Vale para os dois travessões, o curto e o longo.
     */
    @Test
    fun `nenhum nome de exercicio usa travessao`() {
        val comTravessao = nomes().filter { it.contains('–') || it.contains('—') }

        assertTrue(
            comTravessao.isEmpty(),
            "travessão é proibido em texto de usuário (#37): ${comTravessao.take(5)}",
        )
    }

    /**
     * **Dois exercícios com o mesmo nome são indistinguíveis para quem escolhe.**
     *
     * Hoje são zero, e a checagem custa uma varredura. Ela importa porque a V52 reescreveu 788
     * nomes de uma vez: consolidar redações diferentes na mesma frase é justamente o efeito
     * colateral de padronizar, e ele não aparece lendo o diff — só contando.
     *
     * A V51 já tinha removido os duplicados de MÍDIA; este cobre o duplicado de TEXTO, que é outro
     * problema e sobrevive a ela.
     */
    @Test
    fun `nenhum nome de exercicio se repete`() {
        val repetidos = nomes().groupingBy { it }.eachCount().filterValues { it > 1 }

        assertTrue(repetidos.isEmpty(), "nomes duplicados no catálogo: ${repetidos.keys.take(5)}")
    }

    private fun nomes(): List<String> = refs().map { it.first }

    /**
     * ⚠️ **Os 26 sem mídia são conhecidos, contados e NÃO são defeito deste teste.**
     *
     * Eles nasceram em migrations de curadoria (V29 a V33) com `video_ref` vazio e nunca tiveram
     * arquivo: `Prancha`, `Pallof Press`, `Dead Bug`, `Russian Twist`, as seis variações de
     * `Abdominal`. Já apareciam sem imagem antes da V50.
     *
     * O número fixado aqui é o que transforma "existem alguns sem mídia" em dívida enumerável. Se
     * ele SOBE, alguém acrescentou exercício sem mídia e este teste cobra. Se ele DESCE, a dívida
     * está sendo paga e a linha se atualiza junto.
     *
     * > **Débito que ninguém consegue contar volta a crescer sem que ninguém perceba.**
     */
    @Test
    fun `os exercicios sem midia continuam sendo os mesmos 26`() {
        val semMidia = refs().filter { (_, video, _) -> video.isEmpty() }

        assertEquals(
            26,
            semMidia.size,
            "exercícios sem mídia mudaram de quantidade: ${semMidia.map { it.first }}",
        )
    }

    /**
     * Preenchido é preenchido nos DOIS, ou vazio nos dois.
     *
     * Um exercício com vídeo e sem miniatura aparece na lista como um quadrado cinza e abre com
     * animação — o tipo de inconsistência que ninguém reporta porque parece carregamento lento.
     *
     * Este vale para a tabela inteira, e aqui isso é correto: a regra não fala de formato, fala de
     * COERÊNCIA entre dois campos. Duplicado e exercício sem mídia obedecem a ela igual.
     */
    @Test
    fun `video e miniatura andam juntos`() {
        val meio = refs().filter { (_, video, thumb) -> video.isEmpty() != thumb.isEmpty() }

        assertTrue(meio.isEmpty(), "exercício com só uma das duas mídias: ${meio.map { it.first }}")
    }
}
