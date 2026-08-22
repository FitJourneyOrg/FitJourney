package dev.rafael.app.data.sessao

import dev.rafael.features.auth.domain.repository.AuthRepository
import dev.rafael.features.profile.domain.repository.ProfileRepository

/**
 * SAIR DA CONTA — um dono da sequência, duas portas de entrada.
 *
 * Existe porque o `Sair` acontece em dois lugares (rodapé do menu lateral e tela de conta) e a
 * ORDEM importa: limpar o cache de onboarding **antes** do `signOut`. Sem isso, o próximo
 * cadastro herda `onboardingCompleted = true` e cai direto na Home, pulando o quiz — defeito que
 * já tem teste desde a Fase 5.
 *
 * Antes desta classe, a lógica morava só no `ContaViewModel`, e por isso o item "Sair" do menu
 * **navegava para Configurações** em vez de sair: eu protegi a regra empurrando o usuário. Um
 * item chamado "Sair" que leva a outra tela mente sobre o que faz.
 */
class SairDaConta(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
) {
    suspend operator fun invoke() {
        profile.clearOnboardingCache()   // ORDEM: antes do signOut
        auth.signOut()                   // limpa a sessão + o token cacheado do Ktor
    }
}
