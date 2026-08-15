package dev.rafael.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Paleta "Volt Athletic" — dark-first.
 *
 * [REGRA] (ARCH #16) O LIME é exclusivo de recompensa do PERFIL INDIVIDUAL — XP, nível,
 * conquistas, streak. Nunca use lime para ação comum, navegação ou grupo.
 *
 * Hierarquia de cor:
 *   lime   -> recompensa (XP, nível, streak, conquista)
 *   azul   -> ação primária do usuário (iniciar treino, tentar de novo, salvar)
 *   roxo   -> geração por IA (o "premium inteligente")
 *   neutro -> tudo o mais
 */

// --- recompensa (perfil individual) ---
val Volt = Color(0xFFCCFF00)          // XP conquistado, streak aceso, nível
val VoltDim = Color(0xFF7A8729)       // XP pendente (aguardando validação do grupo, #17)
val VoltOn = Color(0xFF14180A)        // texto sobre lime

// --- ação ---
val ActionBlue = Color(0xFF2E6BE6)
val ActionBlueDark = Color(0xFF1E4FB0)

// --- IA ---
val AiPurple = Color(0xFF8B5CF6)
val AiPurpleDark = Color(0xFF6D28D9)

// --- superfícies (dark-first) ---
val Ink0 = Color(0xFF0B0B0D)          // fundo da tela
val Ink1 = Color(0xFF141518)          // card
val Ink2 = Color(0xFF1D1F24)          // card elevado / input
val Ink3 = Color(0xFF2A2D34)          // borda, divisória

// --- texto ---
val TextHigh = Color(0xFFF5F6F7)
val TextMid = Color(0xFF9BA1AC)
val TextLow = Color(0xFF6B7280)

// --- estado ---
val Danger = Color(0xFFE24B4A)
val Warning = Color(0xFFEF9F27)
val Success = Volt                     // sucesso é recompensa: usa o lime
