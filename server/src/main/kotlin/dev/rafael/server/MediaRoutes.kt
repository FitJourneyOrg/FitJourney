package dev.rafael.server

import io.ktor.server.application.Application
import io.ktor.server.application.log
import java.io.File

/**
 * Mídia dos exercícios (png/mp4) servida como estáticos em /media — SÓ DEV.
 *
 * O QUE: os arquivos moram numa pasta local (default `gifs_exercicios/` na raiz do projeto,
 * ignorada no git, ~360MB). O cliente recebe refs relativos (`Glúteos/Nome.png`) no ExerciseDto
 * e monta a URL final: MEDIA_BASE_URL + "/" + ref → http://10.0.2.2:8080/media/Glúteos/Nome.png.
 *
 * POR QUE dev-only: em produção a mídia vai pra um CDN/object storage (R2/Firebase/S3) e o cliente
 * só troca a base URL. O Ktor servir estáticos é conveniência pra destravar o emulador sem infra.
 *
 * Diretório vem de `media.dir` (application.yaml / env MEDIA_DIR). Como o :server:run pode rodar
 * com working-dir em server/, tenta o path e o mesmo relativo à raiz; loga o absoluto + se existe.
 */
fun Application.resolveMediaDir(configured: String): File {
    val candidates = listOf(File(configured), File("..").resolve(configured))
    val dir = candidates.firstOrNull { it.isDirectory } ?: File(configured)
    val abs = dir.absoluteFile
    if (abs.isDirectory) {
        log.info("Mídia: /media -> ${abs.path} (ok)")
    } else {
        log.warn("Mídia: pasta não encontrada em ${abs.path} — /media vai retornar 404. " +
            "Ajuste media.dir (ou a env MEDIA_DIR).")
    }
    return abs
}
