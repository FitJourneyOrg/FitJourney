package dev.rafael.core.database.outbox

/**
 * "Enfileirei algo — acorde o envio."
 *
 * O QUE é: a única coisa que os repositórios sabem sobre o mecanismo de envio. Eles gravam
 * local, enfileiram no outbox e chamam [agendar]. Nada mais.
 *
 * POR QUE existe: quem realmente faz o trabalho é o WorkManager, que é Android. Se o
 * repositório (código comum, KMP) chamasse `WorkManager.enqueue` diretamente, `program:data`
 * e `workout:data` deixariam de compilar para iOS e a camada de dados passaria a depender de
 * um detalhe de plataforma. A interface inverte isso: o comum declara a necessidade, o
 * androidApp fornece a implementação via Koin.
 *
 * PARA QUE serve no FitJourney: é o gatilho imediato. O outbox sozinho já garante que nada se
 * perde (é disco), mas sem este aviso a fila só seria esvaziada na próxima abertura do app.
 * Com ele, criar um treino online sobe em segundos — o caminho offline e o online são o mesmo.
 */
fun interface AgendadorDeSync {
    fun agendar()

    companion object {
        /**
         * Para testes e para o iOS enquanto não há worker. A fila continua correta: o próximo
         * `refresh()` do app processa o que ficou. Perde-se a imediatez, não o dado.
         */
        val Nenhum = AgendadorDeSync { }
    }
}
