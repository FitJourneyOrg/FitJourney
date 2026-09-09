package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.contract.group.MemberRole
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Denúncia e moderação, em Kotlin puro (fatia E.2, ARCH #33 seção 6).
 *
 * ## O que esta política decide, e o que ela deliberadamente não decide
 *
 * Decide: **se cabe denunciar** (prazo, autoria) e **para onde vai o estado** do check-in depois de
 * cada evento. Não decide filiação nem papel — quem barra o não-membro é a guarda do serviço, como
 * na [SocialPolicy].
 *
 * ## A máquina de estados, inteira
 *
 * ```
 *                  1ª denúncia
 *   VALIDO ─────────────────────────► EM_ANALISE
 *      │                                 │    │
 *      │  admin invalida direto (6.10)   │    │ admin mantém  ──► VALIDO
 *      └─────────────────────────────────┼────┘
 *                                        │ admin acata (6.3)
 *                                        ▼
 *                                   INVALIDADO  ◄── terminal
 * ```
 *
 * **`INVALIDADO` é terminal** — é o que sustenta o [INV] "check-in invalidado nunca volta a
 * contar". Denunciar de novo o que já foi invalidado não tem o que produzir, e é por isso que
 * [podeDenunciarCheckIn] recusa.
 *
 * **`EM_ANALISE` continua contando ponto (6.8)**, e isso não é um esquecimento: se o ponto sumisse
 * durante a análise, a denúncia viraria arma — bastaria denunciar quem lidera o ranking na véspera
 * do fim. A presunção de boa-fé é o que impede a moderação de virar tática de jogo.
 */
object ModeracaoPolicy {

    /**
     * 6.9: sete dias.
     *
     * A foto é retida bem mais tempo, mas sete dias é curto o bastante para a evidência estar
     * fresca e longo o bastante para alguém notar. Sem prazo nenhum, o admin julgaria check-ins de
     * meses atrás sem nenhum contexto — e 6.12 diz que ele **não tem prazo para julgar**, então a
     * fila já pode carregar casos antigos por conta própria.
     */
    const val PRAZO_EM_DIAS = 7

    /**
     * Teto do texto do motivo. Frase, não redação: o admin do grupo conhece as pessoas.
     *
     * O `VARCHAR(300)` da V45 usa o mesmo número — os dois têm de concordar, como o
     * [SocialPolicy.MAX_COMENTARIO] e o `CHECK` do `body`.
     */
    const val MAX_MOTIVO = 300

    /**
     * Dá para denunciar este check-in? `null` = pode.
     *
     * O `souOAutor` recusa antes do prazo de propósito: quem quer desfazer o próprio check-in tem
     * a 4.11 (apagar no mesmo dia), e oferecer a denúncia como segundo caminho criaria uma forma
     * de "apagar" fora do prazo, passando pelo admin. **Dois caminhos para o mesmo efeito é como
     * uma regra de prazo deixa de valer.**
     */
    fun podeDenunciarCheckIn(
        status: CheckInStatus,
        criadoEm: LocalDateTime,
        agora: Instant,
        souOAutor: Boolean,
    ): DenunciaBlock? = when {
        souOAutor -> DenunciaBlock.E_SEU
        status == CheckInStatus.INVALIDADO -> DenunciaBlock.JA_JULGADO
        expirou(criadoEm, agora) -> DenunciaBlock.PRAZO
        else -> null
    }

    /**
     * Dá para denunciar este comentário? `null` = pode.
     *
     * Mesmo prazo do check-in, contado do **comentário** e não do check-in em que ele mora: um
     * comentário ofensivo escrito hoje num check-in de duas semanas atrás precisa poder ser
     * denunciado hoje.
     *
     * Sem `JA_JULGADO`: comentário não tem estado. Denúncia acatada o **remove**, e o que não
     * existe não aparece na tela para ser denunciado de novo.
     */
    fun podeDenunciarComentario(
        criadoEm: LocalDateTime,
        agora: Instant,
        souOAutor: Boolean,
    ): DenunciaBlock? = when {
        souOAutor -> DenunciaBlock.E_SEU
        expirou(criadoEm, agora) -> DenunciaBlock.PRAZO
        else -> null
    }

    /**
     * O estado do check-in depois de uma denúncia aceita.
     *
     * `VALIDO` vira `EM_ANALISE`; qualquer outro fica onde está. A segunda denúncia do mesmo
     * check-in **não** muda nada — ela só engrossa o contador da 6.11, e é isso que faz "várias
     * denúncias viram uma solicitação" ser verdade também no estado, não só na tela.
     */
    fun aposDenuncia(atual: CheckInStatus): CheckInStatus =
        if (atual == CheckInStatus.VALIDO) CheckInStatus.EM_ANALISE else atual

    /**
     * O estado depois do julgamento do admin.
     *
     * Recusar devolve a `VALIDO`, e não a "VALIDO com marca": o check-in que passou pela análise e
     * foi mantido é indistinguível de um que nunca foi denunciado. Guardar a suspeita seria punir
     * quem foi absolvido — e como o admin não vê quem denunciou, nem serviria de aviso.
     */
    fun aposJulgamento(acatou: Boolean): CheckInStatus =
        if (acatou) CheckInStatus.INVALIDADO else CheckInStatus.VALIDO

    /**
     * Só o **admin** julga e só ele vê a fila (6.2 + 6.7).
     *
     * Não há recurso e não há segundo julgador. Um `MEMBRO` que conhecesse a rota da fila veria
     * denúncias de terceiros — que é justamente o que o anonimato do denunciante tenta evitar.
     */
    fun podeModerar(meuPapel: MemberRole?): Boolean = meuPapel == MemberRole.ADMIN

    /**
     * O motivo aparado, ou `null` se não serve. **Obrigatório** (decisão de 2026-09-07).
     *
     * Devolve o TEXTO e não um booleano, pelo mesmo motivo do [SocialPolicy.comentarioValido]: é o
     * que impede o serviço de validar uma coisa e gravar outra. Foi a lição do `display_name`.
     *
     * Corta em 300 em vez de recusar: quem escreveu demais não perde a denúncia, e o `CHECK` do
     * banco nunca é acionado por texto longo — só por texto vazio, que aqui já virou `null`.
     */
    fun motivoValido(texto: String?): String? =
        texto?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_MOTIVO)

    private fun expirou(criadoEm: LocalDateTime, agora: Instant): Boolean =
        agora - criadoEm.toInstant(TimeZone.UTC) > PRAZO_EM_DIAS.days
}

/**
 * Por que não dá para denunciar. Enum e não frase: a tela escreve o texto (#31).
 *
 * Mesmo desenho do [CheckInBlock] — e pelo mesmo motivo. Uma `String` aqui obrigaria o servidor a
 * saber português e a tela a comparar texto para decidir o que desenhar.
 */
enum class DenunciaBlock {
    /** Passaram os sete dias (6.9). */
    PRAZO,

    /** É seu. Para o próprio check-in existe a 4.11 (apagar no mesmo dia). */
    E_SEU,

    /** Já foi invalidado — não há o que julgar de novo. */
    JA_JULGADO,
}
