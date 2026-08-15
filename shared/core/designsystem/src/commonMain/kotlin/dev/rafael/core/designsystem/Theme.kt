package dev.rafael.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Tema do FitJourney — dark-first (o app nasce escuro; não há tema claro por ora).
 *
 * Mapeamento pro Material 3:
 *   primary    -> azul de ação    (botões primários, links)
 *   secondary  -> roxo de IA      (gerar com IA)
 *   tertiary   -> LIME/recompensa (XP, nível, streak) — [REGRA] ARCH #16, só recompensa
 *
 * Ou seja: `MaterialTheme.colorScheme.tertiary` é o lime. Use-o SÓ pra recompensa do perfil
 * individual. Para ação, use `primary`.
 */
private val FitJourneyDark = darkColorScheme(
    primary = ActionBlue,
    onPrimary = TextHigh,
    primaryContainer = ActionBlueDark,
    onPrimaryContainer = TextHigh,

    secondary = AiPurple,
    onSecondary = TextHigh,
    secondaryContainer = AiPurpleDark,
    onSecondaryContainer = TextHigh,

    tertiary = Volt,                 // recompensa (XP/nível/streak)
    onTertiary = VoltOn,
    tertiaryContainer = VoltDim,     // recompensa pendente (#17)
    onTertiaryContainer = TextHigh,

    background = Ink0,
    onBackground = TextHigh,
    surface = Ink1,
    onSurface = TextHigh,
    surfaceVariant = Ink2,           // usado pelo shimmer/placeholder
    onSurfaceVariant = TextMid,
    surfaceContainerHighest = Ink2,

    outline = Ink3,
    outlineVariant = Ink3,

    error = Danger,
    onError = TextHigh,
)

@Composable
fun FitJourneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FitJourneyDark,
        typography = MaterialTheme.typography,
        content = content,
    )
}
