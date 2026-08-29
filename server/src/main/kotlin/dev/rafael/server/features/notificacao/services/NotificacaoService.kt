package dev.rafael.server.features.notificacao.services

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
        val n = Notificacao(
            id = Uuid.random(),
            userId = destinatario,
            tipo = aviso.dados[Aviso.TIPO] ?: "DESCONHECIDO",
            titulo = aviso.titulo,
            corpo = aviso.corpo,
            dados = aviso.dados,
            lidaEm = null,
            criadaEm = agora(),
        )

        when (val r = repository.criar(n)) {
            is AppResult.Success -> notificador.notificar(destinatario, aviso)
            is AppResult.Failure -> {
                // Gravar falhou: o push sai MESMO ASSIM. É a única chance de a pessoa saber, e
                // uma notificação efêmera é melhor que nenhuma.
                log.warn("Não gravei a notificação de {}: {}. Tentando só o push.", destinatario, r.error)
                notificador.notificar(destinatario, aviso)
            }
        }
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
