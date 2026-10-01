package dev.rafael.core.catalog

/**
 * O mínimo do catálogo que outras features precisam saber sobre um exercício.
 *
 * `videoRef` entrou no redesenho da execução (2026-10-01): a tela de sessão passou a mostrar o
 * MOVIMENTO sem sair do treino, e sem ele a única saída seria a feature de sessão depender da
 * feature de exercício, que a [REGRA] de dependência proíbe.
 *
 * > **Porta estreita no core é o que impede duas features de se enxergarem.**
 *
 * A `description` chegou a entrar aqui no mesmo dia e saiu na mesma sessão: o painel de técnica
 * nasceu com vídeo MAIS texto, e o Rafael cortou o texto na bateria manual ("o usuário está
 * treinando, não vai parar para ler"). Campo sem uso numa porta compartilhada é o tipo de coisa
 * que ninguém remove depois.
 */
data class ExerciseRef(
    val id: String,
    val name: String,
    val thumbRef: String,
    val videoRef: String,
)
