package dev.rafael.server.features.notificacao.services

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.notificacao.db.NotificationRepository
import dev.rafael.server.features.notificacao.models.Notificacao
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/**
 * A central de notificações (F.1).
 *
 * ## GRAVA e DEPOIS despacha — nesta ordem, sempre
 *
 * A notificação gravada é a VERDADE; o push é a tentativa de avisar agora. Se o push falhar, se o
 * FCM estiver fora, ou se a pessoa tiver negado a permissão, **a notificação continua lá** e o
 * sininho vai mostrá-la.
 *
 * A ordem inversa perderia o aviso de quem mais precisa dele: justamente quem não recebe push.
 */
class NotificacaoService(
    private val userService: UserService,
    private val repository: NotificationRepository,
    private val notificador: Notificador,
    private val clock: Clock = Clock.System,
) {

    private val log = LoggerFactory.getLogger(NotificacaoService::class.java)

    /** 6 meses (decidido em 2026-08-27). Notificação mais velha que isso não serve a ninguém. */
    private val retencao = 180.days

    /**
     * Teto da lista.
     *
     * Sem paginação de propósito: quem rola 100 notificações para trás está procurando uma coisa
     * específica, e para isso a central é a ferramenta errada. Paginar aqui seria construir
     * navegação para um uso que não existe.
     */
    private val teto = 100

    private fun agora() = clock.now().toLocalDateTime(TimeZone.UTC)

    /**
     * Cria a notificação e tenta avisar. **Nunca falha para o chamador.**
     *
     * O chamador é sempre uma ação de usuário que já aconteceu — um pedido de amizade criado.
     * Derrubá-la porque a notificação falhou seria trocar um problema pequeno por um grande.
     */
    suspend fun avisar(destinatario: Uuid, aviso: Aviso) {
        val renderizado = renderizar(destinatario, aviso)

        val n = Notificacao(
            id = Uuid.random(),
            userId = destinatario,
            tipo = renderizado.tipo,
            titulo = renderizado.titulo,
            corpo = renderizado.corpo,
            dados = renderizado.dados,
            lidaEm = null,
            criadaEm = agora(),
        )

        when (val r = repository.criar(n)) {
            is AppResult.Success -> notificador.notificar(destinatario, renderizado)
            is AppResult.Failure -> {
                // Gravar falhou: o push sai MESMO ASSIM. É a única chance de a pessoa saber, e
                // uma notificação efêmera é melhor que nenhuma.
                log.warn("Não gravei a notificação de {}: {}. Tentando só o push.", destinatario, r.error)
                notificador.notificar(destinatario, renderizado)
            }
        }
    }

    /**
     * Escolhe as palavras (G.1, ARCH #37). **Esta é a borda, e só ela sabe de idioma.**
     *
     * ## Por que aqui e não nas factories do `Aviso`
     *
     * Passar o idioma para `Aviso.pedidoDeAmizade(de, deId, idioma)` seria a mudança menor, e
     * obrigaria **todo serviço que notifica** a descobrir o idioma do destinatário: hoje quatro
     * portas estreitas, e a quinta nasceria já tendo que saber disso.
     *
     * Este método já tinha o `Uuid` do destinatário e o serviço já tinha o `UserService`. A leitura
     * que faltava é uma, e acontece num lugar.
     *
     * ## [INV] Falha ao ler o idioma NÃO cancela a notificação
     *
     * Cai em `PADRAO` e segue. É a mesma escolha da gravação logo abaixo: **uma notificação no
     * idioma errado é incomparavelmente melhor que nenhuma notificação.** Quem não recebe o aviso
     * não descobre que perdeu o ponto, e não há segunda chance; quem o recebe em português entende
     * assim mesmo, ou abre o app e vê a tela traduzida.
     *
     * O `warn` existe porque isso não deveria acontecer: o destinatário é sempre um usuário que o
     * servidor acabou de manipular.
     */
    private suspend fun renderizar(destinatario: Uuid, aviso: Aviso): AvisoRenderizado {
        val idioma = when (val r = userService.porId(destinatario)) {
            is AppResult.Success -> r.value?.idioma ?: Idioma.PADRAO
            is AppResult.Failure -> {
                log.warn(
                    "Não li o idioma de {}: {}. Avisando em {}.",
                    destinatario,
                    r.error,
                    Idioma.PADRAO.tag,
                )
                Idioma.PADRAO
            }
        }

        val texto = TextosDeAviso.render(aviso.chave, idioma)

        return AvisoRenderizado(
            tipo = aviso.chave.tipo,
            titulo = texto.titulo,
            corpo = texto.corpo,
            // O `tipo` entra no payload AQUI, derivado da chave. Antes era escrito à mão dentro de
            // cada factory, ao lado dela, e nada impedia que os dois discordassem.
            dados = aviso.dados + (Aviso.TIPO to aviso.chave.tipo),
        )
    }

    suspend fun minhas(uid: String, email: String?): AppResult<List<Notificacao>> =
        userService.findOrCreate(uid, email).flatMap { repository.doUsuario(it.id, teto) }

    suspend fun naoLidas(uid: String, email: String?): AppResult<Int> =
        userService.findOrCreate(uid, email).flatMap { repository.naoLidas(it.id) }

    suspend fun marcarComoLidas(uid: String, email: String?): AppResult<Unit> =
        userService.findOrCreate(uid, email)
            .flatMap { repository.marcarTodasComoLidas(it.id, agora()) }
            .map { }

    /**
     * Apaga o que passou de 6 meses.
     *
     * Roda no agendador do boot, como a purga de mídia (fatia B) — mesma peça, mesmo motivo:
     * dado que se acumula por usuário precisa de alguém que jogue fora, e um job externo seria
     * mais uma coisa para lembrar de configurar em produção.
     */
    suspend fun purgar(): Int {
        val corte = (clock.now() - retencao).toLocalDateTime(TimeZone.UTC)
        return when (val r = repository.purgar(corte)) {
            is AppResult.Success -> {
                if (r.value > 0) log.info("Purga de notificações: {} apagadas (> 6 meses)", r.value)
                r.value
            }
            is AppResult.Failure -> {
                log.warn("Purga de notificações falhou: {}", r.error)
                0
            }
        }
    }
}
