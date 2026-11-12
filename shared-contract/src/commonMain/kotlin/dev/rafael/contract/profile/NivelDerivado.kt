package dev.rafael.contract.profile

/**
 * Nível deixou de ser autodeclarado (fatia 12 — questionário e triagem, `motor-v3-ESPECIFICACAO.md`
 * linha 169: "o nível é derivado, não escolhido"). Pura, compartilhada — cliente pode antecipar
 * na UI, servidor é a autoridade e recalcula sempre em `ProfileService.saveProfile`.
 *
 * [REGRA] (`docs/regras/16-onboarding-e-triagem.md`): menos de ~6 meses consistentes OU parado há
 * mais de ~6 meses é iniciante. Caso contrário, intermediário — **nunca avançado pelo cadastro**;
 * avançado só se alcança pela promoção por histórico (`PromocaoDeNivel`).
 */
fun nivelDerivado(consistencia: TrainingConsistency, tempoParado: TimeOffTraining): Level =
    if (consistencia == TrainingConsistency.NEVER_TRAINED ||
        consistencia == TrainingConsistency.LESS_THAN_6_MONTHS ||
        tempoParado == TimeOffTraining.MORE_THAN_6_MONTHS
    ) {
        Level.BEGINNER
    } else {
        Level.INTERMEDIATE
    }
