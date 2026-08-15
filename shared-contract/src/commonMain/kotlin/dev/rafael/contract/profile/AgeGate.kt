package dev.rafael.contract.profile

/**
 * Gate de idade (#24). Regra compartilhada (cliente antecipa na UI; servidor é a autoridade e
 * revalida no `/programs/generate`).
 *
 * - **< 18:** só passa com `minorSupervised = true` (reconhecimento de que um responsável/
 *   profissional supervisiona).
 * - **≥ 69:** apenas informativo — NÃO bloqueia.
 * - `age` nulo (perfil legado ou não informado): NÃO bloqueia.
 */
fun ageGateSatisfied(age: Int?, minorSupervised: Boolean): Boolean =
    age == null || age >= 18 || minorSupervised
