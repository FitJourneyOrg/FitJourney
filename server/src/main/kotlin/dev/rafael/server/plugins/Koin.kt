package dev.rafael.server.plugins

import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import io.ktor.server.application.install
import io.ktor.server.application.log
import dev.rafael.server.di.appModule
import dev.rafael.server.di.midiaModule
import io.ktor.server.application.Application
import java.io.File

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule, midiaModule(pastaDeUploads()))
    }
}

/**
 * Onde as fotos de check-in são gravadas — **separada** da pasta de mídia dos exercícios.
 *
 * São coisas de natureza oposta: `gifs_exercicios/` é asset estático, versionado à parte, servido
 * público e nunca apagado; isto aqui é **dado de usuário**, privado, servido só a membro do grupo
 * e purgado aos 90 dias (4.8). Misturar as duas faria um backup levar 360 MB de asset junto e um
 * `rm -rf` de asset levar foto de gente.
 */
private fun Application.pastaDeUploads(): File {
    val configurado = environment.config.propertyOrNull("media.uploads")?.getString() ?: "uploads"
    val alvo = File(configurado)

    // Caminho ABSOLUTO manda (é o que produção usa). Relativo é ancorado na RAIZ do projeto.
    //
    // A primeira versão fazia como o `resolveMediaDir`: "o primeiro candidato que já existe".
    // Isso funciona para `gifs_exercicios/`, que está lá; para uma pasta que ainda não existe,
    // caía no relativo ao working-dir — e o `:server:run` roda em `server/`. Resultado: as fotos
    // nasceram em `server/uploads`, fora do `/uploads/` do .gitignore. Dado de usuário a um
    // `git add .` de distância do repositório.
    val dir = if (alvo.isAbsolute) alvo else File(raizDoProjeto(), configurado)

    // Criada no BOOT, e não na primeira foto: falha de permissão é problema de ambiente, e
    // ambiente se descobre subindo o servidor — não no primeiro check-in de um usuário.
    if (!dir.isDirectory && !dir.mkdirs()) {
        log.warn("Uploads: não consegui criar ${dir.path} — o upload de foto vai falhar.")
    } else {
        log.info("Uploads: fotos de check-in -> ${dir.path}")
    }
    return dir
}

/** Sobe até achar o `settings.gradle.kts` — é o arquivo que DEFINE a raiz, não um palpite. */
private fun raizDoProjeto(): File {
    var atual: File? = File("").absoluteFile
    while (atual != null) {
        if (File(atual, "settings.gradle.kts").isFile) return atual
        atual = atual.parentFile
    }
    return File("").absoluteFile
}