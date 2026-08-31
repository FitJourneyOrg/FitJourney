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
 *
 * A F.1 acrescentou um terceiro passo — dar baixa no push — e ele CONFIRMA a existência desta
 * classe: com a sequência espalhada por dois ViewModels, um dos dois teria esquecido.
 */
class SairDaConta(
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
    private val push: BaixaDePush,
) {
    /**
     * PORTA ESTREITA para a baixa do push.
     *
     * A primeira versão recebia o `RegistroDePush` inteiro, e o compilador cobrou na hora: ele
     * carrega `Context` e `FirebaseMessaging`, que não existem num teste de unidade — três testes
     * de sessão pararam de compilar por causa de uma dependência que a sequência do logout **não
     * usa**. Ela precisa de um verbo, não de um objeto do Android.
     *
     * É o mesmo padrão do servidor (`CheckInDeHoje`, `GamificacaoDe`, `AvisarPedido`): a porta
     * declara o mínimo, e quem implementa carrega o peso.
     */
    fun interface BaixaDePush {
        suspend fun darBaixa()
    }

    suspend operator fun invoke() {
        // ORDEM, e agora são TRÊS passos com razões diferentes:
        //
        // 1. A baixa do push vem PRIMEIRO porque precisa do token do Firebase ainda válido — o
        //    `signOut` o invalida, e depois dele a requisição sairia sem autenticação.
        //
        //    Sem esta linha, quem sai da conta CONTINUA recebendo notificação neste aparelho até
        //    a próxima pessoa fazer login. É o item mais fácil de esquecer da F.1 e o de pior
        //    consequência: só aparece quando alguém empresta o celular.
        push.darBaixa()

        profile.clearOnboardingCache()   // 2. antes do signOut (defeito da Fase 5)
        auth.signOut()                   // 3. limpa a sessão + o token cacheado do Ktor
    }
}
