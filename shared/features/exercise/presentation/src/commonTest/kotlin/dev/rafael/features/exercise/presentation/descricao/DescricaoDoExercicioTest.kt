package dev.rafael.features.exercise.presentation.descricao

import dev.rafael.contract.i18n.Idioma
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DescricaoDoExercicioTest {

    @Test
    fun `PT-BR remove o paragrafo de boilerplate profissional e mantem o resto`() {
        val desc = "O Supino Reto é um exercício que trabalha o peitoral.\n\n" +
            "Aviso: Sempre busque a orientação de um profissional de educação física para " +
            "garantir a correta execução do exercício e prevenir lesões."

        val paragrafos = paragrafosDaDescricao(desc, Idioma.PT_BR)

        assertEquals(1, paragrafos.size)
        assertTrue(paragrafos[0].texto.startsWith("O Supino Reto"))
    }

    @Test
    fun `EN remove o paragrafo de boilerplate profissional e mantem o resto`() {
        val desc = "The Flat Bench Press is an exercise that works the chest.\n\n" +
            "Warning: Always seek guidance from a fitness professional to ensure correct " +
            "technique and prevent injuries."

        val paragrafos = paragrafosDaDescricao(desc, Idioma.EN)

        assertEquals(1, paragrafos.size)
        assertTrue(paragrafos[0].texto.startsWith("The Flat Bench Press"))
    }

    @Test
    fun `EN nao usa o regex de PT-BR — boilerplate em ingles nao vaza se aplicado o idioma errado`() {
        val desc = "The Flat Bench Press is an exercise that works the chest.\n\n" +
            "Warning: Always seek guidance from a fitness professional to ensure correct " +
            "technique and prevent injuries."

        // usar o idioma errado é o caminho de falha: prova que a escolha do padrão importa de
        // verdade, e não é só um detalhe cosmético do regex.
        val paragrafosComIdiomaErrado = paragrafosDaDescricao(desc, Idioma.PT_BR)

        assertEquals(2, paragrafosComIdiomaErrado.size)
    }

    @Test
    fun `PT-BR destaca paragrafo que comeca com Atencao ou Importante`() {
        val desc = "Atenção: mantenha a postura correta durante o movimento."

        val paragrafos = paragrafosDaDescricao(desc, Idioma.PT_BR)

        assertEquals(1, paragrafos.size)
        assertTrue(paragrafos[0].destaque)
    }

    @Test
    fun `EN destaca paragrafo que comeca com Warning ou Important`() {
        val desc = "Important: keep your back straight throughout the movement."

        val paragrafos = paragrafosDaDescricao(desc, Idioma.EN)

        assertEquals(1, paragrafos.size)
        assertTrue(paragrafos[0].destaque)
    }

    @Test
    fun `paragrafo comum nao vira destaque`() {
        val desc = "Este exercício trabalha o quadríceps e os glúteos."

        val paragrafos = paragrafosDaDescricao(desc, Idioma.PT_BR)

        assertEquals(1, paragrafos.size)
        assertFalse(paragrafos[0].destaque)
    }

    @Test
    fun `descricao em branco vira lista vazia`() {
        assertEquals(emptyList(), paragrafosDaDescricao("", Idioma.PT_BR))
        assertEquals(emptyList(), paragrafosDaDescricao("   \n\n  ", Idioma.EN))
    }

    // ---- P1.1 (2026-09-22): regex de segurança por FRASE, não por parágrafo inteiro ----

    /**
     * ⭐ O caso real que motivou o fix (achado auditando os 923 `descricao_texto` do catálogo,
     * ex. `Agachamento no Landmine`): o disclaimer é a ÚLTIMA frase de um parágrafo que abre com
     * conteúdo de verdade sobre o exercício. Antes, o parágrafo inteiro sumia — a frase real ia
     * junto, sem necessidade.
     */
    @Test
    fun `PT-BR remove so a frase de aviso quando ela fecha um paragrafo com conteudo real`() {
        val desc = "Esse exercício fortalece o peitoral e os tríceps, além de melhorar a " +
            "estabilidade do ombro. Consulte um profissional de educação física antes de " +
            "iniciar qualquer programa de exercícios."

        val paragrafos = paragrafosDaDescricao(desc, Idioma.PT_BR)

        assertEquals(1, paragrafos.size)
        assertEquals(
            "Esse exercício fortalece o peitoral e os tríceps, além de melhorar a estabilidade do ombro.",
            paragrafos[0].texto,
        )
    }

    /** Caminho de falha do filtro: paragrafo que É o disclaimer em MAIS de uma frase some por inteiro, não sobra pedaço. */
    @Test
    fun `PT-BR paragrafo que e so disclaimer em duas frases some inteiro`() {
        val desc = "Sempre busque a orientação de um profissional de educação física. " +
            "Consulte um profissional qualificado antes de iniciar qualquer programa de treino."

        val paragrafos = paragrafosDaDescricao(desc, Idioma.PT_BR)

        assertEquals(emptyList(), paragrafos, "as duas frases são disclaimer — não pode sobrar nada")
    }
}
