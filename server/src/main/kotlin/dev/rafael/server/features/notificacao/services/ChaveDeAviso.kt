package dev.rafael.server.features.notificacao.services

/**
 * O QUE aconteceu, sem dizer com que palavras (fatia G.1, ARCH #37).
 *
 * ## Sealed com `data class`, e não enum com `Map<String, String>`
 *
 * Cada aviso precisa de parâmetros diferentes: um nome, um trecho de comentário, uma contagem. A
 * forma óbvia seria um enum mais um mapa de parâmetros, e ela é ruim por duas razões que só
 * aparecem depois:
 *
 * - **o mapa não tem tipo.** `params["quantas"]` é `String?`, e o catálogo teria que reconverter
 *   para `Int` para decidir plural. Um erro de digitação na chave vira texto sem o número, em
 *   produção, silenciosamente;
 * - **o compilador não ajuda.** Com sealed, o `when` de cada idioma é **exaustivo**: adicionar um
 *   aviso quebra o build em todos os idiomas que ainda não o traduziram.
 *
 * > **O compilador é o `MissingTranslation` do lado do servidor.** No cliente o Lint faz isso de
 * > graça; aqui a exaustividade do `when` é o que ocupa o lugar dele.
 *
 * ## O [tipo] mora aqui, não no `dados`
 *
 * É a string que o cliente lê para decidir qual tela abrir, e é também o que vai para a coluna
 * `notifications.tipo`. Antes ela era escrita à mão dentro de cada factory, ao lado da chave: dois
 * lugares dizendo a mesma coisa, e nada impedindo que discordassem. Derivada da chave, não tem como.
 */
sealed interface ChaveDeAviso {

    /** O que o deep link do cliente lê, e o que vai para `notifications.tipo`. */
    val tipo: String

    /** 35.x: alguém pediu amizade. O NOME vai no corpo porque é o que faz a pessoa decidir. */
    data class PedidoDeAmizade(val nome: String) : ChaveDeAviso {
        override val tipo = TIPO_PEDIDO_DE_AMIZADE
    }

    /**
     * 10.4: comentário no meu check-in. Só o dono recebe.
     *
     * O [trecho] já chega cortado no tamanho da bandeja. Cortar no catálogo faria cada idioma ter
     * que lembrar do limite, e o primeiro que esquecesse entregaria um push truncado pelo sistema
     * no meio de uma palavra.
     */
    data class ComentarioNoMeuCheckIn(val nome: String, val trecho: String) : ChaveDeAviso {
        override val tipo = TIPO_COMENTARIO
    }

    /**
     * 10.4: denúncia contra mim.
     *
     * Sem campo de denunciante e sem campo de motivo, e isso é o desenho. Quem foi denunciado
     * sabendo por quem tem motivo e oportunidade de retaliar dentro do próprio grupo, e o motivo
     * foi escrito para o admin julgar, não para o denunciado ler.
     *
     * O jeito de garantir que não vaza é **não carregar o dado até aqui**.
     */
    data class DenunciaContraMim(val ehComentario: Boolean) : ChaveDeAviso {
        override val tipo = TIPO_DENUNCIA_CONTRA_MIM
    }

    /** 10.4: nova denúncia no meu grupo, só para o admin. Uma por CASO, não por denúncia (6.11). */
    data class NovaDenunciaNoGrupo(val grupo: String) : ChaveDeAviso {
        override val tipo = TIPO_DENUNCIA_NO_GRUPO
    }

    /**
     * 10.4: check-in invalidado.
     *
     * **Não tem campo dizendo se veio de julgamento ou da invalidação direta (6.10)**, de propósito.
     * Quem perde o ponto não precisa saber por qual rota o admin passou, e um campo aqui viraria,
     * cedo ou tarde, um `if` no texto de algum idioma.
     *
     * A bateria da fatia F pegou exatamente esse erro na versão anterior: o texto dizia *"o admin
     * avaliou a denúncia"* e era reusado num caminho onde denúncia não existe.
     *
     * > **Mensagem compartilhada só pode afirmar o que é verdade em todos os caminhos que a usam.**
     */
    data class CheckInInvalidado(val grupo: String) : ChaveDeAviso {
        override val tipo = TIPO_CHECK_IN_INVALIDADO
    }

    /**
     * 10.6: entradas do dia, agregadas. No máximo uma por pessoa por dia.
     *
     * [souOCriador] decide o possessivo. O critério é quem CRIOU, não quem é admin hoje: o cargo é
     * transferível (2.12), e depois de uma transferência o novo admin não fundou nada, mas quem
     * fundou continua sendo quem fundou.
     *
     * [quantas] é `Int` e não `String` porque é o plural, e plural é decisão de cada idioma.
     */
    data class EntradasDoDia(
        val grupo: String,
        val quantas: Int,
        val souOCriador: Boolean,
    ) : ChaveDeAviso {
        override val tipo = TIPO_ENTRADAS_DO_DIA
    }

    /**
     * A fila do admin está parada há [dias] ou mais.
     *
     * Existe porque a expiração automática de denúncia foi recusada: se o caso sumisse sozinho, a
     * inação do admin viraria absolvição. O remédio é fazê-lo ver, não apagar o que ele não viu.
     */
    data class FilaParada(val grupo: String, val casos: Int, val dias: Int) : ChaveDeAviso {
        override val tipo = TIPO_FILA_PARADA
    }

    companion object {
        const val TIPO_PEDIDO_DE_AMIZADE = "PEDIDO_DE_AMIZADE"
        const val TIPO_COMENTARIO = "COMENTARIO_NO_CHECKIN"
        const val TIPO_DENUNCIA_CONTRA_MIM = "DENUNCIA_CONTRA_MIM"
        const val TIPO_DENUNCIA_NO_GRUPO = "DENUNCIA_NO_GRUPO"
        const val TIPO_CHECK_IN_INVALIDADO = "CHECK_IN_INVALIDADO"
        const val TIPO_ENTRADAS_DO_DIA = "ENTRADAS_DO_DIA"
        const val TIPO_FILA_PARADA = "FILA_PARADA"
    }
}
