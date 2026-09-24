package dev.rafael.features.achievements.presentation.state

import dev.rafael.contract.stats.AchievementDto
import dev.rafael.core.result.AppError

data class AchievementsState(
    val conquistas: List<AchievementDto> = emptyList(),
    val carregandoInicial: Boolean = true,
    /** Já baixou o catálogo alguma vez. Sem isto, "não tem" e "não chegou" são o mesmo pixel. */
    val jaSincronizou: Boolean = false,
    /** Só importa quando a grade está vazia: com catálogo em cache, falha de sync é silêncio. */
    val erroSync: AppError? = null,
) {
    val desbloqueadas: List<AchievementDto> get() = conquistas.filter { it.unlocked }
    val bloqueadas: List<AchievementDto> get() = conquistas.filterNot { it.unlocked }

    /** "3 de 9" no topo — dá a dimensão do que falta sem obrigar a contar a grade. */
    val total: Int get() = conquistas.size
}
