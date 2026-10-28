package dev.rafael.server.exercise

import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExercisesTable
import dev.rafael.server.features.exercise.engine.EquipmentEnvironmentMap
import dev.rafael.server.features.exercise.engine.ExercisePreFilter
import dev.rafael.server.features.exercise.models.Exercise
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Trava o par **V65 + V66**, contra Postgres REAL: o exercício declara o acessório que exige, o
 * perfil declara o que tem, e o `ExercisePreFilter` cruza os dois.
 *
 * ## O defeito
 *
 * O motor prescrevia supino com halteres para quem treina na sala de casa, sem banco. O catálogo
 * até sabia que o supino precisa de banco — a observação da curadoria dizia "requer: banco" —, mas
 * isso era prosa numa coluna de texto, e **exigência escrita em prosa é exigência que o motor não
 * enxerga**. A V65 transformou as seis exigências em colunas; a V66 deu ao perfil onde guardar a
 * resposta.
 *
 * Medido no simulador (`server/src/test/oracle`), varrendo os 180 perfis de casa: sem acessório declarado,
 * 131 vagas ficavam sem exercício; com cadeira e banco, nenhuma.
 *
 * O segundo defeito é o ambiente. Ele era **deduzido do equipamento**: `BODYWEIGHT` virava "casa",
 * então a Flexão na Parede e o Agachamento com Peso Corporal entravam no treino de quem está na
 * academia com rack e leg press à disposição. A coluna `ambiente` da V65 é curada por exercício,
 * não inferida.
 *
 * ## Por que integração, e não teste unitário
 *
 * O que se afirma aqui é o **dado**: que as 114 linhas com `requires_bench` são as certas e que o
 * `ambiente` separa os dois mundos. Um pool montado à mão provaria a lógica do filtro — que é
 * trivial — e não provaria nada sobre o catálogo, que é o que erra.
 *
 * As contagens abaixo são **relativas** (um conjunto contido no outro), nunca um `count()` global:
 * o catálogo é dado de referência compartilhado pela suíte, como o `BancoDeTeste` avisa.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcessoriosIntegrationTest {

    private val preFilter = ExercisePreFilter()

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private fun casa(vararg tem: Accessory): List<Exercise> =
        preFilter.poolFor(TrainingEnvironment.CASA, Level.ADVANCED, tem.toSet())

    private fun academia(): List<Exercise> =
        preFilter.poolFor(TrainingEnvironment.ACADEMIA, Level.ADVANCED, Accessory.entries.toSet())

    // ---------------------------------------------------------------- acessório

    /** O caso que originou tudo: sala de casa, um par de halteres, sem banco. */
    @Test
    fun `sem banco declarado o supino com halteres nao e oferecido`() {
        val semNada = casa().map { it.name }
        val comBanco = casa(Accessory.BENCH).map { it.name }

        assertFalse("Supino Reto com Halteres" in semNada)
        assertTrue("Supino Reto com Halteres" in comBanco)
        assertTrue("Flexão" in semNada, "quem não tem banco ainda precisa ter o que fazer de peito")
    }

    /**
     * A asserção geral, que não depende de nenhum nome: qualquer que seja o conjunto declarado,
     * nada no pool exige algo fora dele. É o que pega uma linha mal marcada na V65.
     */
    @Test
    fun `nenhum exercicio do pool exige acessorio que o usuario nao declarou`() {
        val cenarios = listOf(
            emptySet(),
            setOf(Accessory.ELEVATED_SURFACE),
            setOf(Accessory.ELEVATED_SURFACE, Accessory.BENCH),
            setOf(Accessory.PULLUP_BAR, Accessory.DIP_STATION),
            Accessory.entries.toSet(),
        )

        for (tem in cenarios) {
            val intrusos = preFilter.poolFor(TrainingEnvironment.CASA, Level.ADVANCED, tem)
                .filterNot { tem.containsAll(it.acessorios) }
                .map { "${it.name} exige ${it.acessorios}" }

            assertEquals(emptyList<String>(), intrusos, "declarado: $tem")
        }
    }

    /** Acessório só abre catálogo. Se declarar algo diminuísse o pool, o filtro estaria invertido. */
    @Test
    fun `cada acessorio declarado so aumenta o pool`() {
        val semNada = casa().size
        val comCadeira = casa(Accessory.ELEVATED_SURFACE).size
        val comBanco = casa(Accessory.ELEVATED_SURFACE, Accessory.BENCH).size
        val comTudo = casa(*Accessory.entries.toTypedArray()).size

        assertTrue(semNada < comCadeira, "sem nada $semNada, com cadeira $comCadeira")
        assertTrue(comCadeira < comBanco, "com cadeira $comCadeira, com banco $comBanco")
        assertTrue(comBanco < comTudo, "com banco $comBanco, com tudo $comTudo")
    }

    /** Academia tem os seis por definição (`Accessory.paraAmbiente`): lá acessório nunca corta. */
    @Test
    fun `na academia o acessorio nunca tira exercicio`() {
        assertEquals(
            academia().map { it.name }.toSet(),
            preFilter.poolFor(TrainingEnvironment.ACADEMIA, Level.ADVANCED).map { it.name }.toSet(),
        )
    }

    // ---------------------------------------------------------------- ambiente

    /**
     * Ambiente curado, não deduzido do equipamento. Os dois nomes abaixo são `BODYWEIGHT`: pelo
     * `EquipmentEnvironmentMap` sozinho eles seriam executáveis na academia, e o iniciante com
     * acesso a rack receberia flexão na parede.
     */
    @Test
    fun `o treino de academia nao recebe exercicio marcado como de casa`() {
        val naAcademia = academia().map { it.name }

        assertFalse("Flexão na Parede" in naAcademia)
        assertFalse("Agachamento Livre com Peso Corporal" in naAcademia)
        assertTrue("Barra Fixa" in naAcademia) {
            "peso corporal marcado AMBOS tem de continuar valendo na academia"
        }
    }

    /** E o contrário: máquina e cabo não aparecem em casa, com acessório nenhum. */
    @Test
    fun `o treino de casa nao recebe maquina nem cabo`() {
        val emCasa = casa(*Accessory.entries.toTypedArray()).map { it.name }

        assertFalse("Leg Press 45°" in emCasa)
        assertFalse("Puxada Alta" in emCasa)
    }

    /**
     * O `ambiente` só existe na lista principal — o CHECK `chk_exercises_selecao` amarra os quatro
     * atributos de seleção a `volume_target`. Fora dela o ambiente continua saindo do equipamento,
     * e o filtro tem de deixar passar: é o catálogo que a troca manual oferece.
     */
    @Test
    fun `exercicio fora da lista principal continua no pool`() {
        val pool = academia()
        val foraDaLista = pool.count { it.volumeTarget == null }

        assertTrue(foraDaLista > pool.size / 2) {
            "só $foraDaLista de ${pool.size} fora da lista principal: ambiente nulo foi filtrado"
        }
    }

    // ---------------------------------------------------------------- alcançabilidade

    /**
     * ⭐ O teste que achou um defeito real.
     *
     * Um exercício da lista principal com `ambiente = CASA` mas com equipamento fora do
     * [EquipmentEnvironmentMap.HOME_EQUIPMENT] não é alcançável em lugar nenhum: a academia o
     * recusa pelo ambiente e a casa pelo equipamento. Era o caso do **Hand Grip (Flexor de Mão)**,
     * que a curadoria deu ao antebraço e que o motor nunca poderia escolher.
     *
     * Duas fontes de verdade sobre "onde isso roda" — a coluna curada e o mapa em código — só
     * convivem enquanto alguém prova que não se contradizem. É o que esta asserção faz.
     */
    @Test
    fun `todo exercicio da lista principal e alcancavel por algum perfil`() {
        val listaPrincipal = transaction {
            ExercisesTable.selectAll()
                .where { ExercisesTable.volumeTarget.isNotNull() }
                .map { it[ExercisesTable.name] }
                .toSet()
        }

        val alcancaveis = (academia() + casa(*Accessory.entries.toTypedArray()))
            .map { it.name }
            .toSet()

        assertEquals(emptySet<String>(), listaPrincipal - alcancaveis)
    }
}
