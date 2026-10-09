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
     *
     * ⚠️ **A V58 NÃO muda este número.** `Prancha` (sem mídia, decisão deliberada do Rafael,
     * ver `fora do formato novo nao sobra ninguem` abaixo) não entra neste balde — ela cai no
     * outro, o de quem está fora do formato. 923 continua sendo exatamente quem tem `chave`
     * válida; o catálogo inteiro subiu pra 924 (ver `o catalogo tem 924 exercicios`).
     */
    @Test
    fun `exatamente 921 caminhos usam a chave em snake_case`() {
        val noFormatoNovo = refs().filter { (_, video, thumb) ->
            padraoVideo.matches(video) && padraoThumb.matches(thumb)
        }

        assertEquals(
            921,
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
     * junto — os 23 novos só entraram porque tinham vídeo e thumb confirmados em disco. Zero era o
     * estado são: se subir, alguém inseriu exercício sem mídia e nenhuma outra guarda pega isso.
     *
     * **A V58 rompe o zero de propósito, uma vez, nomeada.** `Prancha` voltou ao catálogo sem
     * mídia (decisão do Rafael, 2026-09-24 — débito P3 "falta prancha isométrica", nenhuma mídia
     * disponível na fonte externa). Em vez de apagar a guarda ou zerar o teste, a exceção é
     * ENUMERADA: só `Prancha` pode estar fora do padrão. Qualquer OUTRO nome aqui continua
     * reprovando — é o mesmo teste, só que "zero" virou "zero, mais o que eu já sei que existe".
     *
     * > **Débito que ninguém consegue enumerar não é débito, é surpresa** (G.2) — e o inverso
     * > também vale: débito enumerado por nome não é mais um "zero" quebrado, é um item contado.
     */
    @Test
    fun `fora do formato novo nao sobra ninguem alem da Prancha`() {
        val excecoesConhecidas = setOf("Prancha")
        val fora = refs().filterNot { (_, video, thumb) ->
            padraoVideo.matches(video) && padraoThumb.matches(thumb)
        }

        assertEquals(
            excecoesConhecidas,
            fora.map { it.first }.toSet(),
            "caminho inesperado fora do padrão (esperava só a exceção conhecida): " +
                "${fora.map { it.first }}",
        )
    }

    /**
     * 965 - 39 duplicados = 926 (V51). 926 - 26 legado sem mídia (V54) + 23 novos do catálogo
     * (V55) = 923. Fixa o tamanho do catálogo depois da fatia H sessão B.
     */
    /**
     * 923 + 1 (`Prancha`, V58, sem mídia por decisão deliberada -- ver teste acima) - 2 (V63:
     * os dois Terra Romeno, unidos ao Stiff; os dois tinham `chave`, então o balde do formato novo
     * também cai de 923 para 921).
     */
    @Test
    fun `o catalogo tem 922 exercicios`() {
        assertEquals(922, refs().size)
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
     * ⭐ **Convenção de nome pra máquina de placas** (débito P3, fechado sem migration).
     *
     * O catálogo original (V4) tinha três formatos pro mesmo tipo de equipamento: `com Alavanca`
     * (24), `Máquina de X` (9) — `Máquina de Abdução de Quadril`, `Máquina de remo`,
     * `Máquina de rosca direta`, entre outros — e `X na Máquina` (38). Sem convenção
     * declarada, duplicata por nome (mesma máquina, dois formatos) voltaria no próximo import.
     *
     * **A V52 (798 nomes revisados) já convergência tudo pra `X na Máquina`** — confirmado
     * rastreando os 9 `Máquina de X` originais pelo `_de-para.csv` da revisão: `Máquina de
     * remo` → `Remo Ergômetro`, `Máquina de rosca direta` → `Rosca Bíceps na Máquina`, etc.
     * Zero `Máquina de X` sobrou. Confirmado TAMBÉM por revisão visual: as 52 folhas de
     * `X na Máquina` (miniatura + nome, incluindo os 20 com sufixo `(Alavanca)`, usado só quando
     * existe outro exercício quase homônimo por equipamento diferente) foram conferidas pelo
     * Rafael uma a uma — todas usam máquina de verdade, nenhum peso livre/cabo disfarçado.
     *
     * Ou seja: **o dado já estava certo, só o débito nunca tinha sido fechado.** O que faltava
     * não era migration — era exatamente o que a nota do débito temia: nada travava o PRÓXIMO
     * import de reintroduzir `Máquina de X`. Este teste é essa trava.
     *
     * > **Convenção que ninguém verificou não é convenção fechada, é convenção por sorte.**
     */
    @Test
    fun `nenhum nome de exercicio comeca com Maquina de`() {
        val foraDaConvencao = nomes().filter {
            it.startsWith("Máquina de ", ignoreCase = true) ||
                it.startsWith("Maquina de ", ignoreCase = true)
        }

        assertTrue(
            foraDaConvencao.isEmpty(),
            "nome fora da convenção 'X na Máquina' -- reintroduziu 'Máquina de X': " +
                "$foraDaConvencao",
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
     * ele SOBE além da exceção nomeada abaixo, alguém acrescentou exercício sem mídia e este teste
     * cobra. Zero era o estado são; agora é "só quem eu já sei que não tem".
     *
     * **`Prancha` é a exceção (V58, decisão deliberada do Rafael — ver débito P3 "falta prancha
     * isométrica" em `debitos.md`).** Mesma técnica do teste `fora do formato novo`: nomear em vez
     * de zerar, pra continuar pegando qualquer OUTRO exercício sem mídia que apareça depois.
     *
     * > **Débito que ninguém consegue contar volta a crescer sem que ninguém perceba.**
     */
    @Test
    fun `nao existem mais exercicios sem midia alem da Prancha`() {
        val excecoesConhecidas = setOf("Prancha")
        val semMidia = refs().filter { (_, video, _) -> video.isEmpty() }

        assertEquals(
            excecoesConhecidas,
            semMidia.map { it.first }.toSet(),
            "exercícios sem mídia (esperava só a exceção conhecida): ${semMidia.map { it.first }}",
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
