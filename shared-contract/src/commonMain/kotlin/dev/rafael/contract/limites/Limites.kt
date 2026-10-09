package dev.rafael.contract.limites

/**
 * Números que aparecem em MENSAGEM pro usuário — CONTRATO entre servidor e cliente, mesmo
 * espírito do [dev.rafael.contract.profile.SplitCatalog] e do
 * [dev.rafael.contract.i18n.IdiomaPolicy]: Kotlin puro, uma fonte, os dois lados leem.
 *
 * ## O problema que isto fecha
 *
 * Doze frases do catálogo (`strings.xml`) cravavam o número em português: "Use pelo menos 2
 * caracteres" porque `DisplayNamePolicy.MIN` era 2, só que a frase não ENXERGAVA a constante —
 * era texto solto que por acaso dizia o mesmo número. Mudar o servidor sem lembrar de mudar a
 * frase fazia o backend recusar num limite e o app anunciar outro, sem nenhum teste pegando.
 *
 * ## Por que aqui, e não deixar cada lado com a sua cópia
 *
 * `SplitCatalog` já resolveu esse mesmo problema pro split: uma tabela, os dois lados leem. A
 * saída considerada e descartada foi mandar o número no `ErrorResponse` — funcionaria, mas criaria
 * DUAS maneiras de a mesma informação chegar ao cliente (parâmetro no envelope × constante
 * partilhada aqui), e a base já resolve isto de um jeito só em outro lugar. Ver `debitos.md`,
 * "12 frases + números de conquista cravam constante do servidor".
 *
 * ## O que NÃO mora aqui
 *
 * Os alvos de conquista (`AchievementPolicy.Conquista`) ficam de fora — já são uma tabela única no
 * SERVIDOR, e o cliente já lê o número pelo `AchievementDto.target`. Não havia duplicação pra
 * fechar ali, só a frase faltando o `%1$d` (ver `conquista_treinos_10_descricao` e as outras sete).
 *
 * As policies do servidor (`DisplayNamePolicy`, `ProgramLimits`, `SocialPolicy`, `CheckInPolicy`,
 * `ModeracaoPolicy`, `GroupPolicy`) continuam sendo o lugar onde a REGRA vive — validação, gate,
 * cálculo. Os `const val` delas passam a APONTAR pra cá (`const val MIN = Limites.DisplayName.MIN`)
 * em vez de duplicar o número, então quem lê o código do servidor continua achando o nome que já
 * conhecia, e o cliente ganha uma fonte que pode ler sem depender do servidor estar de pé.
 *
 * [REGRA] Todo número aqui é usado em pelo menos uma frase visível ao usuário nos dois lados
 * (mensagem de erro do servidor E `strings.xml` do cliente). Constante que só o servidor usa fica
 * na Policy dele — não sobe pra cá só por prevenção.
 */
object Limites {

    /** Espelha `DisplayNamePolicy` (servidor: `features/user/services`). */
    object DisplayName {
        const val MIN = 2
        const val MAX = 30
    }

    /** Espelha `ProgramLimits` (servidor: `features/program/services`). */
    object Program {
        /** Grátis: 1 programa no total, de qualquer tipo (IA ou manual). */
        const val FREE_TOTAL_LIMIT = 1
        const val PREMIUM_TOTAL_LIMIT = 3
    }

    /** Espelha `SocialPolicy` (servidor: `features/checkin/services`). */
    object Social {
        const val MAX_COMENTARIO = 500
    }

    /** Espelha `CheckInPolicy` (servidor: `features/checkin/services`). */
    object CheckIn {
        const val MAX_NOME_DO_LOCAL = 60
    }

    /** Espelha `ModeracaoPolicy` (servidor: `features/checkin/services`). */
    object Moderacao {
        const val PRAZO_EM_DIAS = 7
    }

    /**
     * Espelha `GroupPolicy` (servidor: `features/group/services`).
     * `MAX_MEMBROS` alimenta `enum_bloqueio_lotado`; `TITULO_MAX`/`DESCRICAO_MAX` alimentam
     * `erro_grupo_titulo_longo`/`erro_grupo_descricao_longa` (débito "erroDoCampo devolve frase
     * do servidor", debitos.md — nascem parametrizados, sem repetir o erro das 12 frases).
     */
    object Group {
        const val MAX_MEMBROS = 50
        const val TITULO_MAX = 60
        const val DESCRICAO_MAX = 300
    }

    /**
     * Espelha os limites de `ProfileService.saveProfile` — nunca tiveram nome nem no servidor,
     * eram literais soltos (`dto.age !in 5..120`) validados sem constante nenhuma.
     */
    object Profile {
        const val DAYS_PER_WEEK_MIN = 2
        const val DAYS_PER_WEEK_MAX = 6
        const val FOCUS_AREAS_MAX = 2
        const val AGE_MIN = 5
        const val AGE_MAX = 120
    }
}
