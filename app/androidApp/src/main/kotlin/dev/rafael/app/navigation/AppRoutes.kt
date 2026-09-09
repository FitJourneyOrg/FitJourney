package dev.rafael.app.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Splash : AppRoute
    @Serializable data object Login : AppRoute
    /**
     * Primeiro passo do onboarding: confirmar o nome (decisão 1-A.2).
     *
     * Antes do Quiz, e não dentro dele: o quiz escreve em `profiles` e o nome mora em `users`,
     * via `PATCH /me`. Um passo dentro do quiz faria `profile` depender de `auth` — [REGRA]
     * feature nunca depende de feature.
     */
    @Serializable data object Nome : AppRoute
    @Serializable data object Quiz : AppRoute
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute
    @Serializable data class ExerciseDetail(val id: String) : AppRoute

    @Serializable data object Grupos : AppRoute
    @Serializable data object GrupoNovo : AppRoute
    /**
     * Detalhe do desafio. [aba] escolhe qual das quatro abre (fatia F).
     *
     * ## Por que o deep link precisa disso
     *
     * "Seu check-in foi denunciado" abrindo em **Sobre** faz a pessoa trocar de aba para ver o que
     * motivou o aviso — o card está em **Posts**. E "3 pessoas entraram" não tem nada a ver com
     * Posts. **Levar ao grupo certo na aba errada é meio caminho**, e o critério da F.1 era levar
     * *onde se age*.
     *
     * `Int` e não o enum `GrupoDetalheViewModel.Aba`: a rota é serializada pela navegação e não
     * deve depender de um tipo de tela. O índice é o do `ABAS`, e a tela traduz.
     *
     * Default `0` (Sobre) mantém retrocompatível toda navegação que já existia — a lista de grupos
     * continua chamando `GrupoDetalhe(id)` sem saber que o parâmetro existe.
     */
    @Serializable data class GrupoDetalhe(val id: String, val aba: Int = 0) : AppRoute

    /** Entrar num desafio. `inviteToken` != null = chegou por link; null = vai digitar o código. */
    @Serializable data class GrupoEntrar(val inviteToken: String? = null) : AppRoute

    /**
     * Fazer check-in num grupo (fatia B).
     *
     * Tela própria e não diálogo no detalhe: pode envolver câmera em tela cheia, permissão de
     * localização e edição de texto — três coisas que um `AlertDialog` faz mal, e que juntas
     * precisam sobreviver a rotação e a ida às configurações do sistema.
     */
    @Serializable data class CheckIn(val groupId: String) : AppRoute

    /**
     * A conversa de um check-in (8.1, fatia E.1).
     *
     * Tela própria em vez de expansão no card: o feed viraria uma lista de alturas imprevisíveis
     * que salta a cada polling de 10s, e o teclado taparia metade do que se está lendo.
     *
     * Leva o **grupo** junto porque toda rota social é do grupo — é ele a fronteira de acesso
     * ([REGRA] #33), e sem o id aqui a tela teria de descobri-lo para chamar a API.
     */
    @Serializable data class Comentarios(val groupId: String, val checkInId: String) : AppRoute

    /**
     * A fila de moderação do admin (6.2, fatia E.2).
     *
     * Tela própria, e não um modal ou uma quinta aba do detalhe. Uma aba estaria visível para os
     * 49 membros que não podem abri-la, e escondê-la só para o admin faria a barra de abas mudar
     * de tamanho conforme quem olha — a lista de abas passaria a depender de papel.
     *
     * Aqui o gesto é raro e deliberado: o admin vai à fila quando o badge sobe. Alcançada só pelo
     * atalho na barra do detalhe, que também só existe para ele.
     */
    @Serializable data class Moderacao(val groupId: String) : AppRoute

    // Aba ainda sem implementação (placeholder) — Progresso é Fase 5 (#16).
    @Serializable data object Progresso : AppRoute

    /** Conquistas (ARCH #16). Tela própria, alcançada pelo Progresso — não é aba. */
    @Serializable data object Conquistas : AppRoute

    /**
     * Perfil PÚBLICO (ARCH #34). `userId = null` significa "o meu".
     *
     * O parâmetro já nasce aqui, mesmo que na fatia A.0 só o próprio perfil seja alcançável:
     * é ele que garante que a tela seja escrita para renderizar QUALQUER pessoa desde o
     * primeiro dia. Uma tela nascida "só minha" acumula suposições que depois viram vazamento
     * quando alguém a aponta para outro usuário.
     */
    @Serializable data class Perfil(val userId: String? = null) : AppRoute

    /** Configurações da conta (ARCH #34): privada, nunca renderiza outra pessoa. */
    @Serializable data object Conta : AppRoute

    /**
     * Amigos e pedidos (#35). Alcançada por **Perfil → Amigos**, e não pelo drawer.
     *
     * Fica dentro do perfil porque amizade é uma extensão da identidade, não uma seção do app —
     * e assim o contador de pedidos não exige estado no ícone global do menu.
     */
    @Serializable data object Amigos : AppRoute

    /** Configurações da conta → Bloqueados (#35). Só do dono, nunca de terceiro. */
    @Serializable data object Bloqueados : AppRoute

    /**
     * A central de notificações (F.1) — o que o ícone da barra abre.
     *
     * **Ponto único de aviso do app.** Tudo que for notificação aparece aqui, e é para cá que o
     * deep link de um push leva quando o tipo não tem destino próprio.
     */
    @Serializable data object Notificacoes : AppRoute

    /** Itens do menu que ainda não existem — abrem EmBreve com o selo da fase. */
    @Serializable data object Wiki : AppRoute
    @Serializable data object Duvidas : AppRoute

    // ARCH #27: "Meus treinos" (lista plana) virou "Meus Programas" (programas com
    // treinos aninhados). Workout.* continua existindo, mas Create agora exige programId
    // e só é alcançável a partir de ProgramDetail.
    @Serializable data object Programs : AppRoute
    @Serializable data class ProgramDetail(val id: String) : AppRoute
    @Serializable data object ProgramGenerate : AppRoute
    /**
     * Fim do onboarding: pergunta se o usuário QUER o primeiro programa (Fase 7).
     * Existe pra que nada seja criado no servidor sem alguém pedir — antes o Reveal gerava
     * no `init`, então todo mundo ganhava um programa quisesse ou não.
     */
    @Serializable data object ProgramOffer : AppRoute
    @Serializable data object ProgramReveal : AppRoute   // revelação do onboarding (Fase 7 — conversão)

    /**
     * Página de assinatura. `voltarParaHome` distingue os dois contextos de abertura:
     * - onboarding (vindo do Reveal): recusar tem que levar pra Home. Voltar pro Reveal, que
     *   é a tela que oferece premium, dá a sensação de gaiola — o usuário recusou e caiu de
     *   volta na oferta.
     * - dentro do app (programa trancado): recusar volta pra tela de onde veio, como sempre.
     */
    @Serializable data class Paywall(val voltarParaHome: Boolean = false) : AppRoute

    // editLocked = true quando o treino pertence a um programa IA trancado p/ o usuário
    // (free): o botão editar barra na hora com paywall, sem entrar na tela de edição.
    @Serializable data class WorkoutDetail(val id: String, val editLocked: Boolean = false) : AppRoute
    // takenDays = CSV dos dias já ocupados no programa (ex.: "1,3,5") — o form desabilita esses.
    @Serializable data class WorkoutCreate(val programId: String, val takenDays: String = "") : AppRoute
    @Serializable data class WorkoutEdit(val id: String) : AppRoute
    @Serializable data class WorkoutSession(val id: String) : AppRoute   // execução do treino (Fase 5)
}