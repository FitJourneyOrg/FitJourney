package dev.rafael.server.media

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Mídia em pasta local. O implementador da Fase 6.
 *
 * **A referência tem duas gavetas** (`a1/b2/uuid.jpg`) por um motivo prático: sistema de arquivos
 * com dezenas de milhares de entradas num diretório só fica lento para listar e para fazer
 * backup. Dois níveis de dois caracteres dão 65 mil pastas — mais do que esta fase vai precisar.
 *
 * **A referência é validada na leitura, e isso não é paranoia.** Ela vai para o banco e volta, e
 * um dia alguém vai passar uma vinda de outro lugar. `../../etc/passwd` como `ref` leria um
 * arquivo qualquer do servidor — travessia de caminho, o defeito mais banal que existe em
 * armazenamento de arquivo. O formato é fechado por regex e o caminho final é conferido contra a
 * raiz depois de resolvido.
 */
class ArmazenamentoEmDisco(private val raiz: File) : ArmazenamentoDeMidia {

    override suspend fun guardar(bytes: ByteArray, extensao: String): AppResult<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val nome = Uuid.random().toString().replace("-", "")
                val ref = "${nome.take(2)}/${nome.drop(2).take(2)}/$nome.$extensao"
                val destino = File(raiz, ref)
                destino.parentFile?.mkdirs()
                destino.writeBytes(bytes)
                ref
            }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Falha ao guardar a mídia", it).asFailure() },
            )
        }

    override suspend fun ler(ref: String): AppResult<ByteArray?> = withContext(Dispatchers.IO) {
        val arquivo = resolver(ref) ?: return@withContext null.asSuccess()
        runCatching { if (arquivo.isFile) arquivo.readBytes() else null }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { AppError.Unexpected("Falha ao ler a mídia", it).asFailure() },
        )
    }

    override suspend fun apagar(ref: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        // IDEMPOTENTE: `delete()` em arquivo inexistente devolve false, e isso não é erro. A purga
        // dos 90 dias pode rodar duas vezes sobre a mesma linha.
        resolver(ref)?.delete()
        Unit.asSuccess()
    }

    override suspend fun listarRefs(anteriorA: Instant): AppResult<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val corte = anteriorA.toEpochMilliseconds()
                raiz.walkTopDown()
                    .filter { it.isFile && it.lastModified() < corte }
                    // A ref é o caminho RELATIVO à raiz, com barra normal — a mesma forma que o
                    // `guardar` devolveu e que está no banco. Sem normalizar a barra, o
                    // recolhimento no Windows compararia `aa\bb\x.jpg` com `aa/bb/x.jpg` e
                    // acharia que TODO arquivo é órfão.
                    .map { it.relativeTo(raiz).path.replace(File.separatorChar, '/') }
                    .filter { FORMATO.matches(it) }
                    .toList()
            }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Falha ao listar a mídia", it).asFailure() },
            )
        }

    /** `null` quando a referência é malformada ou aponta para fora da raiz. */
    private fun resolver(ref: String): File? {
        if (!FORMATO.matches(ref)) return null
        val arquivo = File(raiz, ref).canonicalFile
        val dentro = arquivo.path.startsWith(raiz.canonicalFile.path + File.separator)
        return arquivo.takeIf { dentro }
    }

    private companion object {
        /** `a1/b2/<32 hex>.<ext>` — nada de `..`, nada de barra a mais, nada absoluto. */
        val FORMATO = Regex("^[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{32}\\.[a-z0-9]{2,4}$")
    }
}
