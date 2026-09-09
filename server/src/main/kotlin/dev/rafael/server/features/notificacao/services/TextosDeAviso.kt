package dev.rafael.server.features.notificacao.services

import dev.rafael.contract.i18n.Idioma

/** Um aviso já com palavras. É o que se grava e o que se despacha. */
data class TextoDeAviso(val titulo: String, val corpo: String)

/**
 * O ÚNICO catálogo de texto que existe no servidor (fatia G.1, ARCH #37).
 *
 * ## Por que existe, se a regra diz que texto vive no cliente
 *
 * A [REGRA] do #37 é que o servidor manda dados, códigos e chaves, e nunca prosa. A exceção é uma
 * só, e é principiada: **o push tem que chegar com título e corpo prontos**, porque mensagem
 * só-dados não aparece com o app fechado ou dormindo, que é exatamente quando a notificação importa.
 *
 * Isso já estava decidido e testado no ARCH #36. Então este arquivo é a exceção inteira: 7 avisos.
 * Qualquer texto novo que não seja notificação vai para o `strings.xml` do cliente.
 *
 * ## Uma função por idioma, e o compilador cobra
 *
 * Os dois `when` são **exaustivos** sobre a [ChaveDeAviso]. Adicionar um aviso quebra o build em
 * todos os idiomas que ainda não o traduziram, e adicionar um idioma quebra o `when` de [render].
 *
 * A alternativa seria um mapa por idioma mais um teste afirmando que toda chave existe em todos.
 * Funciona, e é pior: o teste roda depois, o compilador roda sempre, e um mapa aceita chave escrita
 * errado sem reclamar.
 *
 * > **Verificação que o compilador faz não precisa de teste, e não pode ser esquecida.**
 *
 * ## Sem travessão
 *
 * Nenhum texto daqui usa `-` de travessão nem `–`. É convenção de produto (#37, seção 8) e vale
 * para todos os idiomas. Há um teste que varre este arquivo, porque quem traduzir o sétimo idioma
 * não vai ler este comentário.
 */
object TextosDeAviso {

    fun render(chave: ChaveDeAviso, idioma: Idioma): TextoDeAviso = when (idioma) {
        Idioma.PT_BR -> ptBR(chave)
        Idioma.EN -> en(chave)
    }

    private fun ptBR(c: ChaveDeAviso): TextoDeAviso = when (c) {

        is ChaveDeAviso.PedidoDeAmizade -> TextoDeAviso(
            titulo = "Novo pedido de amizade",
            corpo = "${c.nome} quer ser seu amigo",
        )

        is ChaveDeAviso.ComentarioNoMeuCheckIn -> TextoDeAviso(
            titulo = "${c.nome} comentou no seu check-in",
            corpo = c.trecho,
        )

        is ChaveDeAviso.DenunciaContraMim -> TextoDeAviso(
            titulo = "Seu ${if (c.ehComentario) "comentário" else "check-in"} foi denunciado",
            corpo = "O admin do desafio vai avaliar. Seu ponto continua valendo até lá.",
        )

        is ChaveDeAviso.NovaDenunciaNoGrupo -> TextoDeAviso(
            titulo = "Nova denúncia em ${c.grupo}",
            corpo = "Tem algo esperando sua avaliação.",
        )

        is ChaveDeAviso.CheckInInvalidado -> TextoDeAviso(
            titulo = "Check-in invalidado em ${c.grupo}",
            corpo = "O admin do desafio invalidou este check-in. Ele não conta mais ponto no ranking.",
        )

        // O título é o NOME DO GRUPO, que é conteúdo de usuário e não se traduz.
        is ChaveDeAviso.EntradasDoDia -> TextoDeAviso(
            titulo = c.grupo,
            corpo = buildString {
                append(if (c.quantas == 1) "1 pessoa entrou" else "${c.quantas} pessoas entraram")
                append(if (c.souOCriador) " no seu desafio hoje." else " no desafio hoje.")
            },
        )

        is ChaveDeAviso.FilaParada -> TextoDeAviso(
            titulo = "Denúncias esperando em ${c.grupo}",
            corpo = if (c.casos == 1) {
                "Tem 1 caso parado há ${c.dias} dias."
            } else {
                "Tem ${c.casos} casos parados há ${c.dias} dias."
            },
        )
    }

    /**
     * Inglês.
     *
     * Traduzido preservando as DECISÕES, não as palavras. Três lugares onde a frase inglesa não é
     * literal de propósito:
     *
     * - *"Seu ponto continua valendo até lá"* virou *"Your point still counts for now"*: o literal
     *   *"until then"* apontaria para um prazo, e o admin não tem prazo (6.12);
     * - *"no seu desafio"* virou *"your challenge"*, e o possessivo continua saindo só para quem
     *   criou;
     * - o plural de `EntradasDoDia` e `FilaParada` é decidido aqui, e não herdado do português.
     *   Inglês e português concordam em ter duas formas, mas concordar por acaso não é motivo para
     *   compartilhar código: o terceiro idioma pode ter quatro.
     */
    private fun en(c: ChaveDeAviso): TextoDeAviso = when (c) {

        is ChaveDeAviso.PedidoDeAmizade -> TextoDeAviso(
            titulo = "New friend request",
            corpo = "${c.nome} wants to be your friend",
        )

        is ChaveDeAviso.ComentarioNoMeuCheckIn -> TextoDeAviso(
            titulo = "${c.nome} commented on your check-in",
            corpo = c.trecho,
        )

        is ChaveDeAviso.DenunciaContraMim -> TextoDeAviso(
            titulo = "Your ${if (c.ehComentario) "comment" else "check-in"} was reported",
            corpo = "The challenge admin will review it. Your point still counts for now.",
        )

        is ChaveDeAviso.NovaDenunciaNoGrupo -> TextoDeAviso(
            titulo = "New report in ${c.grupo}",
            corpo = "Something is waiting for your review.",
        )

        is ChaveDeAviso.CheckInInvalidado -> TextoDeAviso(
            titulo = "Check-in invalidated in ${c.grupo}",
            corpo = "The challenge admin invalidated this check-in. It no longer counts in the ranking.",
        )

        is ChaveDeAviso.EntradasDoDia -> TextoDeAviso(
            titulo = c.grupo,
            corpo = buildString {
                append(if (c.quantas == 1) "1 person joined" else "${c.quantas} people joined")
                append(if (c.souOCriador) " your challenge today." else " the challenge today.")
            },
        )

        is ChaveDeAviso.FilaParada -> TextoDeAviso(
            titulo = "Reports waiting in ${c.grupo}",
            corpo = if (c.casos == 1) {
                "1 case has been waiting for ${c.dias} days."
            } else {
                "${c.casos} cases have been waiting for ${c.dias} days."
            },
        )
    }
}
