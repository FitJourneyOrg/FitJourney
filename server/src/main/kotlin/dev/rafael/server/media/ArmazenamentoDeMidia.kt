package dev.rafael.server.media

import dev.rafael.core.result.AppResult

/**
 * Onde a mídia mora (decisão 10.5). Três operações, e nenhuma pista de onde os bytes estão.
 *
 * **Por que interface se hoje só existe disco.** Não é abstração especulativa: três lugares
 * diferentes já vão chamar isto — a rota autenticada que serve a foto, a purga dos 90 dias (4.8)
 * e a exclusão em cascata do grupo. Sem a interface, `File(...)` apareceria no meio de cada um, e
 * a troca por object storage viraria uma caçada. Com ela, é uma classe nova no Koin.
 *
 * **A [Ref] é opaca de propósito.** Quem chama nunca monta nem interpreta o valor — não é caminho
 * de arquivo, não é URL. É o que permite a foto migrar para R2 sem tocar em `check_ins`.
 *
 * **Bytes em memória, não stream.** A foto chega com ~200 KB (4.10) e sai recodificada com teto
 * rígido; carregar isso na memória é irrelevante e mantém a interface trivial. Se um dia entrar
 * vídeo, aí sim vale pagar o preço do stream — e a interface muda com um implementador só.
 */
interface ArmazenamentoDeMidia {

    /** Guarda os bytes e devolve a referência para gravar no banco. */
    suspend fun guardar(bytes: ByteArray, extensao: String): AppResult<String>

    /** Os bytes, ou `null` se a referência não existe mais (purgada, ou nunca existiu). */
    suspend fun ler(ref: String): AppResult<ByteArray?>

    /** Apaga. **Idempotente**: apagar o que já não existe não é erro — a purga dos 90 dias pode
     *  rodar duas vezes, e a cascata do grupo pode encontrar foto já expirada. */
    suspend fun apagar(ref: String): AppResult<Unit>
}
