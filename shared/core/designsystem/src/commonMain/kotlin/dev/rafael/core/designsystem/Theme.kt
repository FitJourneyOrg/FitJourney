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

    /*
     * ⚠️ [REGRA] `secondaryContainer` NÃO é o roxo de IA, e inverter isto já custou caro.
     *
     * O Material 3 usa este slot como a cor TONAL GENÉRICA: `FilledTonalIconButton`, `FilterChip`
     * selecionado e a trilha do `LinearProgressIndicator` vão todos buscar aqui sem ninguém pedir.
     * Enquanto ele valia `AiPurpleDark`, o roxo de IA aparecia nos `-`/`+` de peso e reps, no chip da
     * série selecionada e na trilha de progresso, onde não há IA nenhuma — 13 arquivos herdavam dele.
     *
     * O defeito já tinha sido achado UMA VEZ e remendado na tela errada: o `AchievementsScreen` passou
     * a declarar `trackColor` à mão porque *"o resultado lido de relance era o inverso do real: 11 de 30
     * parecia 90%"*. O slot continuou roxo, e o mesmo defeito seguiu vivo nas outras doze.
     *
     * > **Cor que o Material pede emprestada para tudo tem de ser neutra. Quem quer significado pede
     * > a cor explicitamente.**
     *
     * A regra que sai daqui: tonal genérico é NEUTRO; tonal com significado usa `primaryContainer`
     * (o tom escuro da ação), declarado no componente. O roxo volta a significar uma coisa só, e
     * `secondary` continua sendo ele, para quem desenha IA de verdade (ver `ProgramOfferScreen`).
     */
    secondaryContainer = Ink3,
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
