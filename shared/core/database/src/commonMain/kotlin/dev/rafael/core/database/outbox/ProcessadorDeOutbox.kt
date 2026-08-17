package dev.rafael.core.database.outbox

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult

/**
 * A superfície ESTREITA do outbox que o processador usa.
 *
 * Existe para o processador ser testável sem SQLDelight: a decisão "temporário vs. permanente"
 * é a regra de maior consequência da fila (é ela que separa "tenta de novo" de "descarta o que
 * o usuário fez"), e ela não pode depender de subir um banco para ser exercitada.
 */
interface FilaDeSaida {
    suspend fun paraEnviar(): List<Operacao>
    suspend fun concluir(alvoId: String)
    suspend fun registrarTentativa(seq: Long)
    suspend fun marcarFalhaPermanente(alvoId: String, mensagem: String)
}

/**
 * Quem sabe TRANSFORMAR uma operação da fila em requisição HTTP.
 *
 * POR QUE é uma interface aqui e não um `when` no processador: os DataSources moram em
 * `features/<nome>/data`, e `core:database` não pode depender de feature ([REGRA] dependência
 * é de mão única, para baixo). A inversão resolve — o core declara o contrato, cada feature
 * registra o seu executor no Koin, e o processador só despacha.
 *
 * Consequência prática: adicionar um tipo novo de operação não toca em `core:database`.
 */
interface ExecutorDeOperacao {
    /** Os tipos que este executor atende. O processador usa para rotear. */
    val tipos: Set<TipoOperacao>

    suspend fun executar(operacao: Operacao): AppResult<Unit>
}

/**
 * ESVAZIA A FILA (ARCH #30, fatia B.4).
 *
 * O QUE faz: pega a fila compactada, envia uma operação por vez, e decide o destino de cada
 * uma conforme o erro.
 *
 * A distinção que sustenta tudo — **falha temporária vs. permanente**:
 *  - TEMPORÁRIA (sem rede, 5xx, timeout): o servidor não recusou, só não respondeu. A operação
 *    fica na fila, conta a tentativa e o WorkManager tenta de novo.
 *  - PERMANENTE (400/403/404/409): o servidor recusou com razão. Tentar de novo dá o mesmo
 *    resultado para sempre — só gasta bateria enquanto o usuário vê "não sincroniza" sem
 *    entender por quê. Marca, para de tentar, e a UI mostra a mensagem.
 *
 * [REGRA] A ORDEM É SAGRADA: na primeira falha temporária o processamento PARA. Não pula para
 * a próxima operação. Criar um treino e agendá-lo são duas operações em que a segunda depende
 * da primeira — se o POST falhar e o PUT da agenda for tentado assim mesmo, o servidor recusa
 * com 400 (agenda referenciando treino inexistente), o 400 é permanente, e a agenda do usuário
 * é descartada por causa de uma falha de rede passageira.
 */
class ProcessadorDeOutbox(
    private val outbox: FilaDeSaida,
    private val executores: List<ExecutorDeOperacao>,
) {
    enum class Resultado {
        /** Fila vazia ou totalmente resolvida. */
        CONCLUIDO,

        /** Sobrou operação com falha temporária — o worker deve reagendar. */
        TENTAR_DEPOIS,
    }

    suspend fun processar(): Resultado {
        val fila = outbox.paraEnviar()
        if (fila.isEmpty()) return Resultado.CONCLUIDO

        for (op in fila) {
            val executor = executores.firstOrNull { op.tipo in it.tipos }
            if (executor == null) {
                // Tipo sem executor registrado = bug de wiring, não erro do usuário. Marcar como
                // permanente evita a fila travar para sempre e deixa o rastro visível.
                outbox.marcarFalhaPermanente(op.alvoId, "Operação não suportada (${op.tipo})")
                continue
            }

            when (val r = executor.executar(op)) {
                is AppResult.Success -> outbox.concluir(op.alvoId)
                is AppResult.Failure ->
                    if (ehPermanente(r.error)) {
                        outbox.marcarFalhaPermanente(op.alvoId, r.error.message)
                        // Segue para a próxima: a recusa é DESTE alvo, e as operações de outros
                        // alvos não têm por que pagar por ela.
                    } else {
                        outbox.registrarTentativa(op.seq)
                        return Resultado.TENTAR_DEPOIS   // ver [REGRA] da ordem, acima
                    }
            }
        }
        return Resultado.CONCLUIDO
    }

    /**
     * O servidor RESPONDEU e recusou → não adianta insistir.
     *
     * `Connection` e `Unexpected` ficam de fora de propósito: a primeira é falta de rede, a
     * segunda é 5xx ou erro que não soubemos classificar — nos dois casos o dado do usuário
     * vale mais que a suposição de que o retry é inútil.
     *
     * `Unauthorized` também é temporário aqui: o token expirado é renovado pelo `refreshTokens`
     * do HttpClient, e a próxima tentativa passa. Descartar a escrita do usuário porque o
     * token venceu seria o pior desfecho possível.
     */
    private fun ehPermanente(erro: AppError): Boolean = when (erro) {
        is AppError.Forbidden, is AppError.NotFound, is AppError.Validation, is AppError.Conflict -> true
        else -> false
    }
}
