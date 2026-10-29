package dev.rafael.server.features.exercise.engine.v3

import dev.rafael.server.features.exercise.engine.v3.AncoraDoIniciante.Evento
import dev.rafael.server.features.exercise.engine.v3.Progressao.Decisao

/**
 * Motor v3, Fatia 11 — diagnósticos de promoção de nível. Espelha
 * `server/src/test/oracle/progressao_v3.py` (seção "promocao (Fatia 11)") termo a termo.
 */
object PromocaoDeNivel {

    enum class Diagnostico { PROMOVER, RECUPERACAO, RECUPERACAO_SEM_RETOMADA }

    /**
     * `[REGRA]` deload-como-diagnóstico (`ESPECIFICACAO`, "[PENDENTE 3]"). Opera sobre o
     * histórico de UMA âncora — mesma entrada de [AncoraDoIniciante.situacao].
     *
     * A spec, nas palavras dela: "a carga estagna e o motor aplica o deload (1); depois do
     * deload a carga volta a subir e estagna de novo em valores parecidos: os ganhos fáceis
     * acabaram, o iniciante é promovido (2); depois do deload a carga não volta a subir: é
     * recuperação, não nível — não há promoção, e o problema está fora do programa (3)".
     *
     * Devolve [Diagnostico.PROMOVER] (subiu depois do último deload e reestagnou),
     * [Diagnostico.RECUPERACAO] (nunca subiu e este é o 1º deload da âncora),
     * [Diagnostico.RECUPERACAO_SEM_RETOMADA] (nunca subiu depois do último deload E o deload
     * anterior a este TAMBÉM não teve retomada entre os dois — "dois deloads consecutivos sem
     * retomada" da spec, gatilho do aviso do app) ou `null` (ainda não há o que diagnosticar).
     * Espelha `progressao_v3.py::diagnostico_promocao_iniciante()`.
     */
    fun diagnosticoIniciante(historico: List<Evento>): Diagnostico? {
        val ultimoDeload = historico.indexOfLast { it is Evento.Deload }
        if (ultimoDeload < 0) return null
        val depois = historico.subList(ultimoDeload + 1, historico.size)
        if (AncoraDoIniciante.semSubirNoFim(depois) < AncoraDoIniciante.ESTAGNACAO_SESSOES) return null
        if (depois.any { it is Evento.Carga && it.decisao == Decisao.SOBE }) return Diagnostico.PROMOVER
        val antes = historico.subList(0, ultimoDeload)
        val penultimoDeload = antes.indexOfLast { it is Evento.Deload }
        if (penultimoDeload >= 0) {
            val entre = antes.subList(penultimoDeload + 1, antes.size)
            if (entre.none { it is Evento.Carga && it.decisao == Decisao.SOBE }) {
                // nenhuma retomada entre os dois deloads mais recentes -- "consecutivos SEM retomada"
                return Diagnostico.RECUPERACAO_SEM_RETOMADA
            }
        }
        return Diagnostico.RECUPERACAO
    }

    /**
     * `[REGRA]` (decisão do Rafael, 2026-10-29): agrega [diagnosticoIniciante] de TODAS as
     * âncoras do perfil. Promove com **2 âncoras em `PROMOVER`, ou 1 se o perfil só tiver uma**.
     * Motivo: o nível barra exigência técnica (acesso a barra livre); o lift que estagna
     * primeiro — tipicamente o mais isolado — não representa a maturidade da pessoa inteira.
     * Número (2) é CALIBRÁVEL, não evidência.
     *
     * `[INV]` INALCANÇÁVEL hoje: nenhum perfil `BEGINNER` da matriz de 720 gera programa com
     * menos de 5 vagas preenchidas (mínimo 5, mediana 14) — o ramo `limite = 1` fica DECLARADO,
     * não é remendo morto. Espelha `progressao_v3.py::diagnostico_promocao_do_perfil()`.
     */
    fun diagnosticoDoPerfil(porAncora: List<Diagnostico?>): Diagnostico? {
        val limite = if (porAncora.size <= 1) 1 else 2
        if (porAncora.count { it == Diagnostico.PROMOVER } >= limite) return Diagnostico.PROMOVER
        if (Diagnostico.RECUPERACAO_SEM_RETOMADA in porAncora) return Diagnostico.RECUPERACAO_SEM_RETOMADA
        if (Diagnostico.RECUPERACAO in porAncora) return Diagnostico.RECUPERACAO
        return null
    }

    /**
     * `[PROPOSTA]` promoção a `ADVANCED` — 1RM estimado (Epley) da MESMA âncora, congelada,
     * comparado entre dois mesociclos consecutivos. Pré-condições do chamador (adesão, sem dor
     * em escalonamento, âncora congelada no período), NÃO verificadas aqui.
     *
     * "Sem ganho" com ambiguidade resolvida pelo sinal que o motor já tem ([Decisao.DESCE]):
     * poucas reduções de carga no dia PROMOVEM; muitas indicam recuperação (aviso, sem
     * promoção). `limitePoucasReducoes` é CALIBRÁVEL — a spec dá a direção, não o número.
     * Espelha `progressao_v3.py::diagnostico_promocao_avancado()`.
     */
    fun diagnosticoAvancado(
        umRmInicio: Double?,
        umRmFim: Double?,
        reducoesNoPeriodo: Int,
        limitePoucasReducoes: Int = 1,
    ): Diagnostico? {
        if (umRmInicio == null || umRmFim == null) return null
        if (umRmFim > umRmInicio + 1e-9) return null
        return if (reducoesNoPeriodo <= limitePoucasReducoes) Diagnostico.PROMOVER else Diagnostico.RECUPERACAO
    }

    /**
     * `[REGRA]` (aprovado, 2026-10-29): depois de uma recusa de promoção, só volta a sugerir
     * quando o mesociclo atual passar de `mesocicloNaRecusa + 1` — contador de mesociclo, não
     * calendário. `mesocicloNaRecusa == null` = nunca recusou, sempre pode sugerir. Espelha
     * `progressao_v3.py::pode_sugerir_promocao_de_novo()`.
     */
    fun podeSugerirDeNovo(mesocicloAtual: Int, mesocicloNaRecusa: Int?): Boolean =
        mesocicloNaRecusa == null || mesocicloAtual > mesocicloNaRecusa + 1

    /**
     * `[REGRA]` Fatia 11 — a ponte entre ESTE diagnóstico e `diagnosticoFeito` de
     * [AncoraDoIniciante.situacao]: a TROCA de âncora só é autorizada quando o diagnóstico
     * concluiu "não é nível, é recuperação" ([Diagnostico.RECUPERACAO] ou
     * [Diagnostico.RECUPERACAO_SEM_RETOMADA]). [Diagnostico.PROMOVER] é outro caminho
     * inteiramente — o nível sobe, a âncora não troca; `null` (nada a diagnosticar ainda)
     * também não autoriza. Fecha o débito "Diagnóstico de estagnação (`diagnosticoFeito` da
     * âncora do iniciante)" registrado em `docs/fontes/debitos.md` (Fatia 10).
     *
     * **Não fechado**: quem CHAMA isto com o histórico de verdade (o service/rota que lê
     * [dev.rafael.server.features.exercise.db.ProgressaoRepository.historico] e decide se o app
     * deve perguntar "subiu de nível?" ou "trocar de exercício?") ainda não existe — essa é a
     * próxima camada, fora do escopo desta fatia (núcleo puro primeiro).
     */
    fun diagnosticoFeitoParaTrocaDeAncora(diagnostico: Diagnostico?): Boolean =
        diagnostico == Diagnostico.RECUPERACAO || diagnostico == Diagnostico.RECUPERACAO_SEM_RETOMADA
}
