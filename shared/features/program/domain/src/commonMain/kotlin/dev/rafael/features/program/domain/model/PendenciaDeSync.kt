package dev.rafael.features.program.domain.model

/**
 * Uma mudança que o usuário fez e que ainda não chegou ao servidor (ARCH #30, B.4).
 *
 * POR QUE a tela precisa disto: com escrita otimista, salvar é instantâneo e o usuário não tem
 * como distinguir "está no servidor" de "está só neste aparelho". Sem o selo, ele desinstala o
 * app achando que sincronizou, e perde o que fez. O selo é o preço honesto do otimismo.
 *
 * @property alvoId id do programa ou treino.
 * @property erroPermanente `null` = ainda vai tentar. Preenchido = o servidor RECUSOU e não
 *   adianta insistir; a tela mostra a mensagem e oferece descartar. É a diferença entre
 *   "aguardando rede" (some sozinho) e "deu errado" (exige o usuário).
 */
data class PendenciaDeSync(
    val alvoId: String,
    val erroPermanente: String? = null,
) {
    val aguardando: Boolean get() = erroPermanente == null
}
