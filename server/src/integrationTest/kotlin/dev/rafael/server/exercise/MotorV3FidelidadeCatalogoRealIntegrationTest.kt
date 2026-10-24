package dev.rafael.server.exercise

import dev.rafael.contract.i18n.Idioma
import dev.rafael.contract.profile.Accessory
import dev.rafael.contract.profile.Goal
import dev.rafael.contract.profile.Level
import dev.rafael.contract.profile.TrainingEnvironment
import dev.rafael.core.result.AppResult
import dev.rafael.server.BancoDeTeste
import dev.rafael.server.features.exercise.db.ExerciseRepositoryImpl
import dev.rafael.server.features.exercise.engine.v3.MotorV3Pipeline
import dev.rafael.server.features.exercise.models.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Fidelidade do `CatalogFiller.preencher()` (via [MotorV3Pipeline]) contra o catálogo REAL do
 * Postgres (Fatia 6, fechamento) -- não contra o catálogo sintético de 22 exercícios do
 * `MotorV3PipelineTest`.
 *
 * ## Por que isto, e não os 720 perfis
 *
 * A auditoria completa dos 720 perfis com catálogo real é critério de pronto da Fatia 9 (decisão
 * do Rafael, 2026-10-23) -- precisa de um parser de CSV cross-language e acopla o teste de
 * catálogo ao teste de pipeline. Este arquivo é o substituto PEQUENO: 12 perfis (4 cenários de
 * ambiente × 3 níveis), escolhidos para cobrir exatamente o que a Fatia 6 mediu por ambiente
 * (academia, casa sem acessório, casa com banco, casa com tudo).
 *
 * ## Oráculo
 *
 * [REFERENCIA] foi gerado por `tools/motor_sim/motor_v3.py::gerar()` lendo o MESMO
 * `catalogo_banco.csv` (147 linhas da lista principal) que a V1..V66 inseriu no Postgres --
 * `python3 -c "from motor_v3 import Perfil, gerar; ..."` para os 12 perfis abaixo, uma linha por
 * vaga preenchida (`dia|alvo|papel|series|nome_do_exercicio`, nome vazio = vaga vazia).
 *
 * ## Contra o banco, e não com fake
 *
 * O ponto desta fatia é testar o `toExercise()`/`ExerciseRepositoryImpl.findAll()` REAL contra o
 * `CatalogPool`/`CatalogFiller` puros -- um catálogo fake provaria que a lógica do motor funciona
 * (já provado em `CatalogFillerTest`), não que o dado de produção bate com o oráculo.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MotorV3FidelidadeCatalogoRealIntegrationTest {

    @BeforeAll
    fun setup() {
        BancoDeTeste.dataSource
        BancoDeTeste.limpar()
    }

    private data class LinhaEsperada(val dia: String, val alvo: String, val papel: String, val series: Int, val exercicio: String)

    private data class Cenario(val id: String, val ambiente: TrainingEnvironment, val acessorios: Set<Accessory>)

    private val cenarios = listOf(
        Cenario("academia", TrainingEnvironment.ACADEMIA, Accessory.entries.toSet()),
        Cenario("casa_sem_acessorio", TrainingEnvironment.CASA, emptySet()),
        Cenario("casa_com_banco", TrainingEnvironment.CASA, setOf(Accessory.BENCH)),
        Cenario("casa_com_tudo", TrainingEnvironment.CASA, Accessory.entries.toSet()),
    )

    private val niveis = listOf(Level.BEGINNER, Level.INTERMEDIATE, Level.ADVANCED)

    /** `tools/motor_sim/motor_v3.py::gerar()`, 12 perfis, catálogo real (147 linhas). */
    private val REFERENCIA: Map<String, List<LinhaEsperada>> by lazy {
        REFERENCIA_TXT.lines()
            .filter { it.isNotBlank() }
            .map { it.split("|") }
            .groupBy({ it[0] }) { LinhaEsperada(dia = it[1], alvo = it[2], papel = it[3], series = it[4].toInt(), exercicio = it[5]) }
    }

    @Test
    fun `preencher() com o catalogo real bate com o oraculo nos 12 perfis`() = runBlocking {
        val repo = ExerciseRepositoryImpl()
        val candidatosCompletos: List<Exercise> = (repo.findAll(Idioma.PT_BR) as AppResult.Success).value

        val divergencias = StringBuilder()
        var perfisComDivergencia = 0

        for (cenario in cenarios) {
            for (nivel in niveis) {
                val perfilId = "${cenario.id}_${nivel.name}"
                val entrada = MotorV3Pipeline.Entrada(
                    objetivo = Goal.GAIN_MUSCLE, nivel = nivel, dias = 4, minutos = 60, idade = 30,
                    mesocicloAtual = null, ambiente = cenario.ambiente, acessorios = cenario.acessorios,
                )
                val resultado = MotorV3Pipeline.gerar(entrada, candidatosCompletos)

                val produzidas = resultado.estado.sessoes.flatMap { (dia, vagas) ->
                    vagas.map { vp ->
                        LinhaEsperada(
                            dia = dia, alvo = vp.vaga.alvo.name, papel = vp.vaga.papel.name,
                            series = vp.vaga.series, exercicio = vp.exercicio?.name.orEmpty(),
                        )
                    }
                }
                val esperadas = REFERENCIA[perfilId].orEmpty()

                if (produzidas != esperadas) {
                    perfisComDivergencia++
                    divergencias.append("\n=== $perfilId: ${produzidas.size} produzidas vs ${esperadas.size} esperadas ===\n")
                    val max = maxOf(produzidas.size, esperadas.size)
                    for (i in 0 until max) {
                        val p = produzidas.getOrNull(i)
                        val e = esperadas.getOrNull(i)
                        if (p != e) {
                            divergencias.append("  [$i] esperado=$e produzido=$p\n")
                        }
                    }
                }
            }
        }

        assertTrue(perfisComDivergencia == 0) {
            "$perfisComDivergencia/12 perfis divergiram do oráculo com o catálogo real:\n$divergencias"
        }
    }

    private companion object {
        /** Gerado por `tools/motor_sim/motor_v3.py`, 2026-10-23 -- ver KDoc da classe. */
        val REFERENCIA_TXT = """
academia_BEGINNER|Upper A|CHEST|COMPOSTO|4|Supino Sentado na Máquina
academia_BEGINNER|Upper A|BACK|COMPOSTO|3|Puxada Alta
academia_BEGINNER|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
academia_BEGINNER|Upper A|BACK|ISOLAMENTO|2|Pulldown com Braços Estendidos na Corda
academia_BEGINNER|Lower A|QUADS|COMPOSTO|3|Leg Press 45°
academia_BEGINNER|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
academia_BEGINNER|Lower A|CALVES|ISOLAMENTO|3|Panturrilha em Pé na Máquina
academia_BEGINNER|Upper B|CHEST|COMPOSTO|4|Supino Sentado na Máquina
academia_BEGINNER|Upper B|BACK|COMPOSTO|3|Remada Sentada no Cabo
academia_BEGINNER|Upper B|SHOULDERS|COMPOSTO|3|Desenvolvimento na Máquina Articulada
academia_BEGINNER|Upper B|BACK|ISOLAMENTO|2|Pulldown com Braços Estendidos na Corda
academia_BEGINNER|Upper B|BICEPS|ISOLAMENTO|3|Rosca Direta com Barra W
academia_BEGINNER|Lower B|QUADS|COMPOSTO|3|Agachamento no Hack
academia_BEGINNER|Lower B|POSTERIOR|COMPOSTO|3|Stiff com Halteres
academia_BEGINNER|Lower B|CALVES|ISOLAMENTO|2|Panturrilha Sentado na Máquina (Alavanca)
academia_INTERMEDIATE|Upper A|CHEST|COMPOSTO|4|Supino Reto com Barra
academia_INTERMEDIATE|Upper A|BACK|COMPOSTO|4|Barra Fixa
academia_INTERMEDIATE|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
academia_INTERMEDIATE|Upper A|CHEST|ISOLAMENTO|2|Crucifixo Inclinado com Halteres
academia_INTERMEDIATE|Upper A|BACK|ISOLAMENTO|3|Pulldown com Braços Estendidos na Corda
academia_INTERMEDIATE|Upper A|SHOULDERS|ISOLAMENTO|2|Elevação Lateral com Halteres
academia_INTERMEDIATE|Upper A|TRICEPS|ISOLAMENTO|2|Tríceps Pulley com Barra
academia_INTERMEDIATE|Lower A|QUADS|COMPOSTO|3|Agachamento Livre com Barra
academia_INTERMEDIATE|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Barra
academia_INTERMEDIATE|Lower A|QUADS|ISOLAMENTO|2|Cadeira Extensora
academia_INTERMEDIATE|Lower A|POSTERIOR|ISOLAMENTO|2|Mesa Flexora
academia_INTERMEDIATE|Lower A|CALVES|ISOLAMENTO|3|Panturrilha em Pé na Máquina
academia_INTERMEDIATE|Upper B|CHEST|COMPOSTO|4|Supino Inclinado com Barra
academia_INTERMEDIATE|Upper B|BACK|COMPOSTO|4|Puxada Alta
academia_INTERMEDIATE|Upper B|SHOULDERS|COMPOSTO|4|Desenvolvimento na Máquina Articulada
academia_INTERMEDIATE|Upper B|CHEST|ISOLAMENTO|2|Crossover na Polia Alta
academia_INTERMEDIATE|Upper B|BACK|ISOLAMENTO|3|Pulldown com Braços Estendidos na Corda
academia_INTERMEDIATE|Upper B|BICEPS|ISOLAMENTO|3|Rosca Direta com Barra W
academia_INTERMEDIATE|Lower B|QUADS|COMPOSTO|3|Leg Press 45°
academia_INTERMEDIATE|Lower B|POSTERIOR|COMPOSTO|4|Levantamento Terra
academia_INTERMEDIATE|Lower B|QUADS|ISOLAMENTO|2|Cadeira Extensora
academia_INTERMEDIATE|Lower B|CALVES|ISOLAMENTO|3|Panturrilha Sentado na Máquina (Alavanca)
academia_INTERMEDIATE|Lower B|GLUTES|ISOLAMENTO|2|Cadeira Abdutora
academia_ADVANCED|Upper A|CHEST|COMPOSTO|4|Supino Reto com Barra
academia_ADVANCED|Upper A|BACK|COMPOSTO|4|Barra Fixa
academia_ADVANCED|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
academia_ADVANCED|Upper A|CHEST|ISOLAMENTO|2|Crucifixo Inclinado com Halteres
academia_ADVANCED|Upper A|BACK|ISOLAMENTO|3|Pulldown com Braços Estendidos na Corda
academia_ADVANCED|Upper A|SHOULDERS|ISOLAMENTO|2|Elevação Lateral com Halteres
academia_ADVANCED|Upper A|TRICEPS|ISOLAMENTO|2|Tríceps Pulley com Barra
academia_ADVANCED|Lower A|QUADS|COMPOSTO|3|Agachamento Livre com Barra
academia_ADVANCED|Lower A|POSTERIOR|COMPOSTO|4|Stiff com Barra
academia_ADVANCED|Lower A|QUADS|ISOLAMENTO|2|Cadeira Extensora
academia_ADVANCED|Lower A|CALVES|ISOLAMENTO|3|Panturrilha em Pé na Máquina
academia_ADVANCED|Upper B|CHEST|COMPOSTO|4|Supino Inclinado com Barra
academia_ADVANCED|Upper B|BACK|COMPOSTO|4|Puxada Alta
academia_ADVANCED|Upper B|SHOULDERS|COMPOSTO|4|Desenvolvimento na Máquina Articulada
academia_ADVANCED|Upper B|CHEST|ISOLAMENTO|2|Crossover na Polia Alta
academia_ADVANCED|Upper B|BACK|ISOLAMENTO|3|Pulldown com Braços Estendidos na Corda
academia_ADVANCED|Upper B|BICEPS|ISOLAMENTO|3|Rosca Direta com Barra W
academia_ADVANCED|Lower B|QUADS|COMPOSTO|3|Leg Press 45°
academia_ADVANCED|Lower B|POSTERIOR|COMPOSTO|4|Levantamento Terra
academia_ADVANCED|Lower B|QUADS|ISOLAMENTO|2|Cadeira Extensora
academia_ADVANCED|Lower B|CALVES|ISOLAMENTO|3|Panturrilha Sentado na Máquina (Alavanca)
academia_ADVANCED|Lower B|GLUTES|ISOLAMENTO|2|Cadeira Abdutora
casa_sem_acessorio_BEGINNER|Upper A|CHEST|COMPOSTO|4|Flexão
casa_sem_acessorio_BEGINNER|Upper A|BACK|COMPOSTO|3|Remada Sentada com Faixa Elástica
casa_sem_acessorio_BEGINNER|Upper A|BACK|ISOLAMENTO|2|
casa_sem_acessorio_BEGINNER|Upper A|SHOULDERS|COMPOSTO|3|Elevação Lateral com Halteres
casa_sem_acessorio_BEGINNER|Upper A|TRICEPS|ISOLAMENTO|3|Tríceps Francês com Faixa Elástica
casa_sem_acessorio_BEGINNER|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_sem_acessorio_BEGINNER|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_sem_acessorio_BEGINNER|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_sem_acessorio_BEGINNER|Upper B|CHEST|COMPOSTO|4|Flexão
casa_sem_acessorio_BEGINNER|Upper B|BACK|COMPOSTO|3|Remada Sentada com Faixa Elástica
casa_sem_acessorio_BEGINNER|Upper B|BACK|ISOLAMENTO|2|
casa_sem_acessorio_BEGINNER|Upper B|SHOULDERS|COMPOSTO|3|Elevação Lateral com Halteres
casa_sem_acessorio_BEGINNER|Upper B|BICEPS|ISOLAMENTO|3|Rosca Alternada com Halteres
casa_sem_acessorio_BEGINNER|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_sem_acessorio_BEGINNER|Lower B|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_sem_acessorio_BEGINNER|Lower B|CALVES|ISOLAMENTO|2|Elevação de Panturrilha em Pé com Halteres
casa_sem_acessorio_INTERMEDIATE|Upper A|CHEST|COMPOSTO|4|Flexão
casa_sem_acessorio_INTERMEDIATE|Upper A|BACK|COMPOSTO|4|Remada Curvada com Halteres
casa_sem_acessorio_INTERMEDIATE|Upper A|BACK|ISOLAMENTO|4|Remada Sentada com Faixa Elástica
casa_sem_acessorio_INTERMEDIATE|Upper A|SHOULDERS|COMPOSTO|3|Flexão Pike
casa_sem_acessorio_INTERMEDIATE|Upper A|CHEST|ISOLAMENTO|2|
casa_sem_acessorio_INTERMEDIATE|Upper A|SHOULDERS|ISOLAMENTO|2|Elevação Lateral com Halteres
casa_sem_acessorio_INTERMEDIATE|Upper A|TRICEPS|ISOLAMENTO|2|Tríceps Francês com Faixa Elástica
casa_sem_acessorio_INTERMEDIATE|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_sem_acessorio_INTERMEDIATE|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_sem_acessorio_INTERMEDIATE|Lower A|POSTERIOR|ISOLAMENTO|2|Stiff Unilateral com Halteres
casa_sem_acessorio_INTERMEDIATE|Lower A|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_sem_acessorio_INTERMEDIATE|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_sem_acessorio_INTERMEDIATE|Upper B|CHEST|COMPOSTO|4|Flexão
casa_sem_acessorio_INTERMEDIATE|Upper B|BACK|COMPOSTO|4|Remada Curvada com Halteres
casa_sem_acessorio_INTERMEDIATE|Upper B|BACK|ISOLAMENTO|3|Remada Sentada com Faixa Elástica
casa_sem_acessorio_INTERMEDIATE|Upper B|SHOULDERS|COMPOSTO|3|Flexão Pike
casa_sem_acessorio_INTERMEDIATE|Upper B|CHEST|ISOLAMENTO|2|
casa_sem_acessorio_INTERMEDIATE|Upper B|SHOULDERS|ISOLAMENTO|2|Abertura com Faixa Elástica (Pull-Apart)
casa_sem_acessorio_INTERMEDIATE|Upper B|BICEPS|ISOLAMENTO|2|Rosca Alternada com Halteres
casa_sem_acessorio_INTERMEDIATE|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_sem_acessorio_INTERMEDIATE|Lower B|POSTERIOR|COMPOSTO|4|Stiff com Halteres
casa_sem_acessorio_INTERMEDIATE|Lower B|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_sem_acessorio_INTERMEDIATE|Lower B|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_sem_acessorio_ADVANCED|Upper A|CHEST|COMPOSTO|4|Flexão
casa_sem_acessorio_ADVANCED|Upper A|CHEST|ISOLAMENTO|2|Flexão com Um Braço
casa_sem_acessorio_ADVANCED|Upper A|BACK|COMPOSTO|4|Remada Curvada com Halteres
casa_sem_acessorio_ADVANCED|Upper A|BACK|ISOLAMENTO|3|Remada Sentada com Faixa Elástica
casa_sem_acessorio_ADVANCED|Upper A|SHOULDERS|COMPOSTO|3|Flexão em Parada de Mão na Parede
casa_sem_acessorio_ADVANCED|Upper A|SHOULDERS|ISOLAMENTO|2|Elevação Lateral com Halteres
casa_sem_acessorio_ADVANCED|Upper A|TRICEPS|ISOLAMENTO|2|Tríceps Francês com Faixa Elástica
casa_sem_acessorio_ADVANCED|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_sem_acessorio_ADVANCED|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_sem_acessorio_ADVANCED|Lower A|POSTERIOR|ISOLAMENTO|2|Stiff Unilateral com Halteres
casa_sem_acessorio_ADVANCED|Lower A|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_sem_acessorio_ADVANCED|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_sem_acessorio_ADVANCED|Upper B|CHEST|COMPOSTO|4|Flexão
casa_sem_acessorio_ADVANCED|Upper B|CHEST|ISOLAMENTO|2|Flexão com Um Braço
casa_sem_acessorio_ADVANCED|Upper B|BACK|COMPOSTO|4|Remada Curvada com Halteres
casa_sem_acessorio_ADVANCED|Upper B|BACK|ISOLAMENTO|3|Remada Sentada com Faixa Elástica
casa_sem_acessorio_ADVANCED|Upper B|SHOULDERS|COMPOSTO|3|Flexão Pike
casa_sem_acessorio_ADVANCED|Upper B|SHOULDERS|ISOLAMENTO|2|Abertura com Faixa Elástica (Pull-Apart)
casa_sem_acessorio_ADVANCED|Upper B|BICEPS|ISOLAMENTO|2|Rosca Alternada com Halteres
casa_sem_acessorio_ADVANCED|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_sem_acessorio_ADVANCED|Lower B|POSTERIOR|COMPOSTO|4|Stiff com Halteres
casa_sem_acessorio_ADVANCED|Lower B|QUADS|ISOLAMENTO|2|Agachamento Sissy
casa_sem_acessorio_ADVANCED|Lower B|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_sem_acessorio_ADVANCED|Lower B|GLUTES|ISOLAMENTO|2|Abdução de Quadril Sentado com Faixa Elástica
casa_com_banco_BEGINNER|Upper A|CHEST|COMPOSTO|4|Flexão
casa_com_banco_BEGINNER|Upper A|BACK|COMPOSTO|3|Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres
casa_com_banco_BEGINNER|Upper A|BACK|ISOLAMENTO|2|Remada Sentada com Faixa Elástica
casa_com_banco_BEGINNER|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_banco_BEGINNER|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_com_banco_BEGINNER|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_com_banco_BEGINNER|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_banco_BEGINNER|Upper B|CHEST|COMPOSTO|4|Flexão
casa_com_banco_BEGINNER|Upper B|BACK|COMPOSTO|3|Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres
casa_com_banco_BEGINNER|Upper B|BACK|ISOLAMENTO|2|Remada Sentada com Faixa Elástica
casa_com_banco_BEGINNER|Upper B|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_banco_BEGINNER|Upper B|BICEPS|ISOLAMENTO|2|Rosca Alternada com Halteres
casa_com_banco_BEGINNER|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_com_banco_BEGINNER|Lower B|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_com_banco_BEGINNER|Lower B|CALVES|ISOLAMENTO|2|Elevação de Panturrilha em Pé com Halteres
casa_com_banco_INTERMEDIATE|Upper A|CHEST|COMPOSTO|4|Supino Reto com Halteres
casa_com_banco_INTERMEDIATE|Upper A|BACK|COMPOSTO|4|Remada Serrote com Halter
casa_com_banco_INTERMEDIATE|Upper A|BACK|ISOLAMENTO|4|Remada Curvada com Halteres
casa_com_banco_INTERMEDIATE|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_banco_INTERMEDIATE|Upper A|CHEST|ISOLAMENTO|2|Crucifixo Inclinado com Halteres
casa_com_banco_INTERMEDIATE|Upper A|SHOULDERS|ISOLAMENTO|2|Elevação Lateral com Halteres
casa_com_banco_INTERMEDIATE|Upper A|TRICEPS|ISOLAMENTO|2|Tríceps Testa Pegada Neutra com Halteres
casa_com_banco_INTERMEDIATE|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_com_banco_INTERMEDIATE|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_com_banco_INTERMEDIATE|Lower A|POSTERIOR|ISOLAMENTO|2|Stiff Unilateral com Halteres
casa_com_banco_INTERMEDIATE|Lower A|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_com_banco_INTERMEDIATE|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_banco_INTERMEDIATE|Upper B|CHEST|COMPOSTO|4|Supino Inclinado com Halteres
casa_com_banco_INTERMEDIATE|Upper B|BACK|COMPOSTO|4|Remada Serrote com Halter
casa_com_banco_INTERMEDIATE|Upper B|BACK|ISOLAMENTO|3|Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres
casa_com_banco_INTERMEDIATE|Upper B|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_banco_INTERMEDIATE|Upper B|CHEST|ISOLAMENTO|2|Pullover com Halter
casa_com_banco_INTERMEDIATE|Upper B|SHOULDERS|ISOLAMENTO|2|Crucifixo Inverso Apoiado no Banco Inclinado
casa_com_banco_INTERMEDIATE|Upper B|BICEPS|ISOLAMENTO|2|Rosca Alternada com Halteres
casa_com_banco_INTERMEDIATE|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_com_banco_INTERMEDIATE|Lower B|POSTERIOR|COMPOSTO|4|Stiff com Halteres
casa_com_banco_INTERMEDIATE|Lower B|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_com_banco_INTERMEDIATE|Lower B|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_banco_ADVANCED|Upper A|CHEST|COMPOSTO|4|Supino Reto com Halteres
casa_com_banco_ADVANCED|Upper A|BACK|COMPOSTO|4|Remada Serrote com Halter
casa_com_banco_ADVANCED|Upper A|BACK|ISOLAMENTO|3|Remada Curvada com Halteres
casa_com_banco_ADVANCED|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_banco_ADVANCED|Upper A|CHEST|ISOLAMENTO|2|Crucifixo Inclinado com Halteres
casa_com_banco_ADVANCED|Upper A|SHOULDERS|ISOLAMENTO|2|Elevação Lateral com Halteres
casa_com_banco_ADVANCED|Upper A|TRICEPS|ISOLAMENTO|2|Tríceps Testa Pegada Neutra com Halteres
casa_com_banco_ADVANCED|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_com_banco_ADVANCED|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_com_banco_ADVANCED|Lower A|POSTERIOR|ISOLAMENTO|2|Stiff Unilateral com Halteres
casa_com_banco_ADVANCED|Lower A|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_com_banco_ADVANCED|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_banco_ADVANCED|Upper B|CHEST|COMPOSTO|4|Supino Inclinado com Halteres
casa_com_banco_ADVANCED|Upper B|BACK|COMPOSTO|4|Remada Serrote com Halter
casa_com_banco_ADVANCED|Upper B|BACK|ISOLAMENTO|3|Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres
casa_com_banco_ADVANCED|Upper B|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_banco_ADVANCED|Upper B|CHEST|ISOLAMENTO|2|Pullover com Halter
casa_com_banco_ADVANCED|Upper B|SHOULDERS|ISOLAMENTO|2|Crucifixo Inverso Apoiado no Banco Inclinado
casa_com_banco_ADVANCED|Upper B|BICEPS|ISOLAMENTO|2|Rosca Alternada com Halteres
casa_com_banco_ADVANCED|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_com_banco_ADVANCED|Lower B|POSTERIOR|COMPOSTO|4|Stiff com Halteres
casa_com_banco_ADVANCED|Lower B|QUADS|ISOLAMENTO|2|Agachamento Sissy
casa_com_banco_ADVANCED|Lower B|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_banco_ADVANCED|Lower B|GLUTES|ISOLAMENTO|2|Abdução de Quadril Sentado com Faixa Elástica
casa_com_tudo_BEGINNER|Upper A|CHEST|COMPOSTO|4|Flexão
casa_com_tudo_BEGINNER|Upper A|BACK|COMPOSTO|3|Remada Apoiada no Banco Inclinado Pegada Neutra com Halteres
casa_com_tudo_BEGINNER|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_tudo_BEGINNER|Upper A|BACK|ISOLAMENTO|2|Extensão de Ombro com Faixa Elástica
casa_com_tudo_BEGINNER|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_com_tudo_BEGINNER|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_com_tudo_BEGINNER|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_tudo_BEGINNER|Upper B|CHEST|COMPOSTO|4|Flexão
casa_com_tudo_BEGINNER|Upper B|BACK|COMPOSTO|3|Remada Invertida na Mesa
casa_com_tudo_BEGINNER|Upper B|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_tudo_BEGINNER|Upper B|BACK|ISOLAMENTO|2|Extensão de Ombro com Faixa Elástica
casa_com_tudo_BEGINNER|Upper B|BICEPS|ISOLAMENTO|3|Rosca Alternada com Halteres
casa_com_tudo_BEGINNER|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_com_tudo_BEGINNER|Lower B|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_com_tudo_BEGINNER|Lower B|CALVES|ISOLAMENTO|2|Elevação de Panturrilha em Pé com Halteres
casa_com_tudo_INTERMEDIATE|Upper A|CHEST|COMPOSTO|4|Supino Reto com Halteres
casa_com_tudo_INTERMEDIATE|Upper A|BACK|COMPOSTO|4|Barra Fixa
casa_com_tudo_INTERMEDIATE|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_tudo_INTERMEDIATE|Upper A|CHEST|ISOLAMENTO|2|Crucifixo Inclinado com Halteres
casa_com_tudo_INTERMEDIATE|Upper A|BACK|ISOLAMENTO|3|Extensão de Ombro com Faixa Elástica
casa_com_tudo_INTERMEDIATE|Upper A|SHOULDERS|ISOLAMENTO|2|Elevação Lateral com Halteres
casa_com_tudo_INTERMEDIATE|Upper A|TRICEPS|ISOLAMENTO|2|Tríceps Francês Sentado com Halter (Duas Mãos)
casa_com_tudo_INTERMEDIATE|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_com_tudo_INTERMEDIATE|Lower A|POSTERIOR|COMPOSTO|3|Stiff com Halteres
casa_com_tudo_INTERMEDIATE|Lower A|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_com_tudo_INTERMEDIATE|Lower A|POSTERIOR|ISOLAMENTO|2|Flexão de Pernas Deitado com Faixa Elástica
casa_com_tudo_INTERMEDIATE|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_tudo_INTERMEDIATE|Upper B|CHEST|COMPOSTO|4|Supino Inclinado com Halteres
casa_com_tudo_INTERMEDIATE|Upper B|BACK|COMPOSTO|4|Remada Serrote com Halter
casa_com_tudo_INTERMEDIATE|Upper B|SHOULDERS|COMPOSTO|4|Desenvolvimento Sentado com Halteres
casa_com_tudo_INTERMEDIATE|Upper B|CHEST|ISOLAMENTO|2|Pullover com Halter
casa_com_tudo_INTERMEDIATE|Upper B|BACK|ISOLAMENTO|3|Extensão de Ombro com Faixa Elástica
casa_com_tudo_INTERMEDIATE|Upper B|BICEPS|ISOLAMENTO|3|Rosca Alternada com Halteres
casa_com_tudo_INTERMEDIATE|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_com_tudo_INTERMEDIATE|Lower B|POSTERIOR|COMPOSTO|4|Stiff com Halteres
casa_com_tudo_INTERMEDIATE|Lower B|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_com_tudo_INTERMEDIATE|Lower B|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_tudo_INTERMEDIATE|Lower B|GLUTES|ISOLAMENTO|2|Abdução de Quadril Sentado com Faixa Elástica
casa_com_tudo_ADVANCED|Upper A|CHEST|COMPOSTO|4|Supino Reto com Halteres
casa_com_tudo_ADVANCED|Upper A|BACK|COMPOSTO|4|Barra Fixa
casa_com_tudo_ADVANCED|Upper A|SHOULDERS|COMPOSTO|3|Desenvolvimento Sentado com Halteres
casa_com_tudo_ADVANCED|Upper A|CHEST|ISOLAMENTO|2|Crucifixo Inclinado com Halteres
casa_com_tudo_ADVANCED|Upper A|BACK|ISOLAMENTO|3|Extensão de Ombro com Faixa Elástica
casa_com_tudo_ADVANCED|Upper A|SHOULDERS|ISOLAMENTO|2|Elevação Lateral com Halteres
casa_com_tudo_ADVANCED|Upper A|TRICEPS|ISOLAMENTO|2|Tríceps Francês Sentado com Halter (Duas Mãos)
casa_com_tudo_ADVANCED|Lower A|QUADS|COMPOSTO|3|Agachamento Goblet com Halter
casa_com_tudo_ADVANCED|Lower A|POSTERIOR|COMPOSTO|4|Stiff com Halteres
casa_com_tudo_ADVANCED|Lower A|QUADS|ISOLAMENTO|2|Agachamento Sissy Ajoelhado
casa_com_tudo_ADVANCED|Lower A|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_tudo_ADVANCED|Upper B|CHEST|COMPOSTO|4|Supino Inclinado com Halteres
casa_com_tudo_ADVANCED|Upper B|BACK|COMPOSTO|4|Remada Serrote com Halter
casa_com_tudo_ADVANCED|Upper B|SHOULDERS|COMPOSTO|4|Desenvolvimento Sentado com Halteres
casa_com_tudo_ADVANCED|Upper B|CHEST|ISOLAMENTO|2|Pullover com Halter
casa_com_tudo_ADVANCED|Upper B|BACK|ISOLAMENTO|3|Extensão de Ombro com Faixa Elástica
casa_com_tudo_ADVANCED|Upper B|BICEPS|ISOLAMENTO|3|Rosca Alternada com Halteres
casa_com_tudo_ADVANCED|Lower B|QUADS|COMPOSTO|3|Agachamento Livre com Peso Corporal
casa_com_tudo_ADVANCED|Lower B|POSTERIOR|COMPOSTO|4|Stiff com Halteres
casa_com_tudo_ADVANCED|Lower B|QUADS|ISOLAMENTO|2|Agachamento Sissy
casa_com_tudo_ADVANCED|Lower B|CALVES|ISOLAMENTO|3|Elevação de Panturrilha em Pé com Halteres
casa_com_tudo_ADVANCED|Lower B|GLUTES|ISOLAMENTO|2|Abdução de Quadril Sentado com Faixa Elástica
        """.trimIndent()
    }
}
