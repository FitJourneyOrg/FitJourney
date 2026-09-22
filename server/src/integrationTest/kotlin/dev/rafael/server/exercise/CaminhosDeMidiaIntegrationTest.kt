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
     * ⭐ **Exatamente 923 caminhos estão no formato da `chave`.**
     *
     * Este é o teste que teria acusado o defeito no mesmo dia: ele falha se alguém renomear as
     * mídias sem a migration correspondente, e renomear mídia é o tipo de arrumação que parece
     * inofensiva.
     *
     * O número era 900 desde a V50 (965 - 39 duplicados - 26 sem mídia). A fatia H sessão B
     * (V53-V55) mexeu nas duas pontas: a V54 consolidou 4 duplicados por capitalização/sufixo no
     * exercício moderno que já estava no formato novo, e removeu os outros 22 sem mídia; a V55
     * inseriu os 23 exercícios que só existiam no catalogo.json, todos com `video_ref`/`thumb_ref`
     * em `chave` snake_case porque vieram direto de `arquivo_video`/`arquivo_thumb` do catálogo.
     * 900 + 23 = 923, e agora ninguém fica de fora (ver o teste seguinte).
     *
     * > **Guarda que afirma sobre a tabela inteira quando a migration tocou uma parte dela acusa o
     * > que não mudou.**
     *
     * Contagem exata, e não "pelo menos": menos que 923 significa mapeamento que não alcançou
     * alguém, mais significa que o mapa está desatualizado. Os dois são defeito.
     */
    @Test
    fun `exatamente 923 caminhos usam a chave em snake_case`() {
        val noFormatoNovo = refs().filter { (_, video, thumb) ->
            padraoVideo.matches(video) && padraoThumb.matches(thumb)
        }

        assertEquals(
            923,
            noFormatoNovo.size,
            "as mídias foram renomeadas sem a migration, ou o mapa da V50/V55 está desatualizado",
        )
    }

    /**
     * A outra metade: **quem está fora do formato novo só pode ser exercício sem mídia.**
     *
     * Sem isto, o teste acima passaria com 923 certos e um 924º apontando para qualquer coisa.
     *
     * ⚠️ Este número foi `39 + 26` entre a V50 e a V51, depois `26` até a fatia H sessão B. A V54
     * pagou essa dívida (consolidou 4, removeu 22) e a V55 não trouxe nenhum exercício sem mídia
     * junto — os 23 novos só entraram porque tinham vídeo e thumb confirmados em disco. Zero é o
     * estado são: se subir, alguém inseriu exercício sem mídia e nenhuma outra guarda pega isso.
     *
     * > **Dois testes que se movem juntos provam mais que um que se move sozinho.**
     */
    @Test
    fun `fora do formato novo nao sobra ninguem`() {
        val fora = refs().filterNot { (_, video, thumb) ->
            padraoVideo.matches(video) && padraoThumb.matches(thumb)
        }

        assertTrue(
            fora.isEmpty(),
            "caminho inesperado fora do padrão: ${fora.take(5).map { it.first }}",
        )
    }

    /**
     * 965 - 39 duplicados = 926 (V51). 926 - 26 legado sem mídia (V54) + 23 novos do catálogo
     * (V55) = 923. Fixa o tamanho do catálogo depois da fatia H sessão B.
     */
    @Test
    fun `o catalogo tem 923 exercicios`() {
        assertEquals(923, refs().size)
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
     * ✅ **A dívida dos 26 sem mídia foi paga na fatia H sessão B (V54).**
     *
     * Eles nasceram em migrations de curadoria (V29 a V33) com `video_ref` vazio e nunca tiveram
     * arquivo: `Prancha`, `Pallof Press`, `Dead Bug`, `Russian Twist`, as seis variações de
     * `Abdominal`, entre outros. A investigação da V54 achou 3 grupos: 4 eram duplicata do mesmo
     * exercício sob outra capitalização/sufixo (consolidados no moderno, que já tinha mídia); 3
     * estavam em uso em treinos sem equivalente moderno (removidos com o histórico — ambiente de
     * desenvolvimento); os 19 restantes nunca tiveram uso nem equivalente (removidos direto).
     *
     * O número fixado aqui é o que transforma "existem alguns sem mídia" em dívida enumerável. Se
     * ele SOBE, alguém acrescentou exercício sem mídia e este teste cobra. Zero é o estado são.
     *
     * > **Débito que ninguém consegue contar volta a crescer sem que ninguém perceba.**
     */
    @Test
    fun `nao existem mais exercicios sem midia`() {
        val semMidia = refs().filter { (_, video, _) -> video.isEmpty() }

        assertTrue(
            semMidia.isEmpty(),
            "exercícios sem mídia reapareceram: ${semMidia.map { it.first }}",
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
