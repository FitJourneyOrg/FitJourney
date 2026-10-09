package dev.rafael.server.error

import org.slf4j.LoggerFactory

/**
 * Porta para registrar uma falha que o código **engole de propósito** (C9, 2026-10-09).
 *
 * ## Por que existe
 *
 * Push, aviso de conquista e notificação nunca derrubam a ação do usuário que já aconteceu.
 * Então a falha não sobe: vira `log.warn`. O problema é que um `log.warn` direto não é
 * testável — dá para quebrar o `catch` e nenhum teste percebe, e a falha passa a ser
 * silenciosa de verdade. Atrás desta interface, o teste assume o papel de dizer
 * "esta falha foi registrada".
 *
 * ## Quando usar
 *
 * **Só onde a exceção/erro é engolido.** Erro que sobe como `AppResult.Failure` já tem dono
 * (o `ErrorMapper`) e não passa por aqui.
 *
 * [erro] é `Any?` porque o que se engole é ora `Throwable`, ora `AppError`.
 */
interface Falhas {
    fun registrar(contexto: String, erro: Any?)
}

/** Produção: SLF4J, nível `warn`. Com `Throwable`, leva o stack trace junto. */
class FalhasSlf4j : Falhas {

    private val log = LoggerFactory.getLogger("Falhas")

    override fun registrar(contexto: String, erro: Any?) {
        if (erro is Throwable) log.warn(contexto, erro) else log.warn("{}: {}", contexto, erro)
    }
}
