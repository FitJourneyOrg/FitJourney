package dev.rafael.contract.stats

import kotlinx.serialization.Serializable

/**
 * Uma conquista do perfil individual (ARCH #16), como a tela a enxerga.
 *
 * O servidor manda o CATÁLOGO INTEIRO, bloqueadas incluídas, porque a tela mostra as bloqueadas em
 * cinza com o progresso ("7 de 10") — é o que dá direção ao usuário; uma grade só com o que ele já
 * tem não sugere o próximo passo.
 *
 * ## ⚠️ `title` e `description` saíram daqui na G.5 (2026-09-15, ARCH #37)
 *
 * Este KDoc dizia o contrário, e dizia com razão para a época: *"título e descrição ficam no
 * servidor, então ajustar um texto ou um limiar não exige publicar uma versão nova do app"*.
 *
 * Essa vantagem depende de existir **um** idioma. Com dois, o servidor teria de saber em que
 * idioma a TELA está para escolher a frase — a premissa que o #37 recusa. Enquanto o DTO carregou
 * prosa, a tela de Conquistas inteira ficou em português para quem escolheu inglês, em três
 * lugares (`AchievementsScreen`, `PerfilPublicoScreen`, `PerfilScreen`).
 *
 * Metade da vantagem continua de pé, e é a metade que importa: **o LIMIAR continua no servidor**.
 * Mudar `TREINOS_50` de 50 para 40 segue sendo uma linha no `AchievementPolicy`, sem release. O
 * que passou a exigir release é corrigir uma PALAVRA.
 *
 * > **Texto no servidor economiza um release e custa um idioma.**
 *
 * O [id] é o que liga os dois lados: o cliente deriva a chave dele
 * (`PRIMEIRO_TREINO` → `conquista_primeiro_treino_titulo`) e o vocabulário está em [ConquistaIds].
 * **Id que o cliente não conhece some da tela** em vez de derrubá-la — servidor novo, app antigo.
 *
 * [REGRA] `unlockedAt` é do relógio do SERVIDOR e é o que define "desbloqueada" — não o
 * cliente comparando `atual >= alvo`. O cliente não decide gamificação.
 */
@Serializable
data class AchievementDto(
    /** Id estável, contrato entre cliente e servidor. Nunca muda de significado. Ver [ConquistaIds]. */
    val id: String,
    /** ISO. `null` = ainda bloqueada. */
    val unlockedAt: String? = null,
    /**
     * Progresso rumo ao alvo, para a UI desenhar "7 de 10" e a barra.
     *
     * Em conquista já desbloqueada, `current` pode estar ABAIXO de `target` — streak quebra, e
     * a medalha continua. Não é inconsistência: é a diferença entre o marco (histórico) e o
     * estado de agora. A tela deve ler `unlockedAt`, nunca inferir do par current/target.
     */
    val current: Int = 0,
    val target: Int = 1,
) {
    val unlocked: Boolean get() = unlockedAt != null
}
