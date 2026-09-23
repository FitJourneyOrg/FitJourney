package dev.rafael.contract.error

/**
 * Chaves de `ErrorResponse.fieldErrors` — CONTRATO entre servidor e cliente, igual a [ErrorCodes].
 *
 * O servidor diz QUAL campo recusou; o formulário pinta aquele campo de vermelho em vez de
 * jogar uma frase solta no rodapé. Sem uma lista comum, os dois lados escreveriam a string
 * na mão e um typo de um lado quebraria silenciosamente o outro.
 *
 * [REGRA] O valor é o nome da propriedade no DTO. Renomeou campo no DTO, renomeia aqui.
 *
 * Só entram validações que o USUÁRIO consegue corrigir num campo. "id de programa inválido"
 * não entra: é bug de cliente, não erro de preenchimento — não existe campo pra consertar.
 */
object ErrorFields {
    // ProfileDto (quiz de onboarding)
    const val DAYS_PER_WEEK = "daysPerWeek"
    const val FOCUS_AREAS = "focusAreas"
    const val AGE = "age"
    const val UNAVAILABLE_DAYS = "unavailableDays"

    // WorkoutDto / ProgramDto (formulários)
    const val NAME = "name"
    const val EXERCISES = "exercises"
    const val SETS = "sets"
    const val REPS = "reps"

    // conta (DisplayNamePolicy)
    const val DISPLAY_NAME = "displayName"

    // check-in (PedidoDeCheckIn)
    const val NOME_DO_LOCAL = "nomeDoLocal"

    // CreateGroupRequest — débito "erroDoCampo devolve frase do servidor" (debitos.md, P2):
    // GroupPolicy.validarCriacao usava a string crua, violando o [REGRA] acima em silêncio.
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val TIMEZONE = "timezone"
    const val START_DATE = "startDate"
    const val END_DATE = "endDate"
    const val RULES = "rules"
}
