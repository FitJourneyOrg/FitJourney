package dev.rafael.contract.stats

import kotlinx.serialization.Serializable

/**
 * Uma conquista do perfil individual (ARCH #16), como a tela a enxerga.
 *
 * O servidor manda o CATÁLOGO INTEIRO, bloqueadas incluídas. Duas razões:
 *  - a tela mostra as bloqueadas em cinza, com o progresso ("7 de 10") — é o que dá direção
 *    ao usuário; uma grade só com o que ele já tem não sugere o próximo passo;
 *  - título e descrição ficam no servidor, então ajustar um texto ou um limiar não exige
 *    publicar uma versão nova do app.
 *
 * [REGRA] `unlockedAt` é do relógio do SERVIDOR e é o que define "desbloqueada" — não o
 * cliente comparando `atual >= alvo`. O cliente não decide gamificação.
 */
@Serializable
data class AchievementDto(
    /** Id estável, contrato entre cliente e servidor. Nunca muda de significado. */
    val id: String,
    val title: String,
    val description: String,
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
