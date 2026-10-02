package dev.rafael.contract.wiki

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * As categorias do acervo "Aprender" (Fase 8).
 *
 * ## Categoria é o ÚNICO eixo, e isso foi decisão, não omissão
 *
 * A primeira versão do plano tinha um segundo eixo, de nível (iniciante/intermediário/avançado).
 * Caiu por dois motivos: [dev.rafael.contract.profile.Level] já existe, é obrigatório no perfil e
 * alimenta o motor — um segundo vocabulário para o mesmo conceito é a duplicação que o débito das
 * "12 frases cravam constante do servidor" pagou em 2026-09-23; e a palavra "nível" já tem duas
 * acepções na tela (o XP da Home e a experiência do onboarding), então uma terceira confundiria
 * quem lê.
 *
 * ## `RECOVERY` existe porque o conteúdo cobrou
 *
 * As quatro primeiras eram Treino, Técnica, Nutrição e Mentalidade. "Descanso, sono e recuperação"
 * não cabia em nenhuma — e era justamente o exemplo que o plano usava para ilustrar a taxonomia.
 *
 * > **Taxonomia que não acomoda o próprio exemplo não está pronta.**
 *
 * O texto de cada categoria mora no `strings.xml` do cliente, nos dois idiomas ([REGRA] do #37).
 * Aqui só viaja a chave.
 */
@Serializable
enum class WikiCategory {
    @SerialName("TRAINING") TRAINING,
    @SerialName("TECHNIQUE") TECHNIQUE,
    @SerialName("NUTRITION") NUTRITION,
    @SerialName("RECOVERY") RECOVERY,
    @SerialName("MINDSET") MINDSET,
    ;

    companion object {
        /** O vocabulário que o `CHECK` da V61 espelha. Se um sair daqui, a migration precisa saber. */
        val TODAS: List<WikiCategory> = entries
    }
}
