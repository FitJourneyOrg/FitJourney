package dev.rafael.features.program.domain.repository

/**
 * O plano do usuário, como a feature de programas precisa saber dele.
 *
 * Interface no domínio porque quem sabe o plano é o `/me` (módulo do app), e feature não depende
 * de feature nem do app: o app implementa, a feature só pergunta. A resposta é uma CÓPIA
 * otimista; quem decide de verdade é o servidor ([REGRA] autoridade do servidor).
 */
interface PlanoDoUsuario {

    /**
     * `true`/`false` conforme o último `/me` conhecido; `null` se ainda não se sabe (nunca
     * sincronizou). Quem recebe `null` NÃO deve bloquear nada: deixa o servidor decidir.
     *
     * @param atualizar tenta buscar o `/me` antes de responder. Offline não falha: devolve o que
     * estava em cache. Serve para confirmar um plano antes de dizer "não" a alguém que pode ter
     * assinado há pouco.
     */
    suspend fun ehPremium(atualizar: Boolean = false): Boolean?
}
