package dev.rafael.server.media

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Normaliza a foto do check-in: **recodifica** para JPEG, com teto de lado e sem metadado nenhum.
 *
 * **Recodificar, e não "remover o EXIF".** Existe biblioteca que apaga os segmentos APP1 de um
 * JPEG, e ela erra: metadado também mora em APP0, APP13 (IPTC), XMP e em campos de fabricante que
 * ninguém cataloga. O invariante é *"nenhum metadado EXIF sobrevive ao upload"* — e a única forma
 * de garantir isso é jogar fora o arquivo original e escrever um novo a partir dos **pixels**.
 * Decodificar produz uma matriz de cor; ela não tem onde guardar coordenada de GPS.
 *
 * **Por que o servidor refaz o que o cliente já fez.** A 4.10 manda o cliente comprimir para
 * 1080px/~200 KB, e ele faz. Mas [REGRA] a autoridade é do servidor: um cliente modificado manda
 * o que quiser, e "o app comprime" não é garantia — é expectativa. A mesma passada que remove o
 * metadado impõe o teto, então a checagem sai de graça.
 *
 * **Consequência para o cliente (fatia B.4):** como o EXIF morre aqui, a ROTAÇÃO precisa vir
 * aplicada nos pixels. Foto que depende da tag de orientação chega deitada no feed.
 */
object Foto {

    const val LADO_MAXIMO = 1080
    const val EXTENSAO = "jpg"

    /** Teto do upload cru. Barra o arquivo antes de decodificar — ver [PIXELS_MAXIMOS]. */
    const val BYTES_MAXIMOS = 8 * 1024 * 1024

    /**
     * Teto de pixels, conferido ANTES de decodificar.
     *
     * Um JPEG de 200 KB pode declarar 30000×30000 e virar 3,6 GB de matriz na memória — é a
     * "bomba de descompressão", e o limite de bytes sozinho não protege contra ela, porque o
     * arquivo comprimido é pequeno de verdade. Por isso as dimensões são lidas do cabeçalho e
     * julgadas antes de qualquer alocação.
     */
    const val PIXELS_MAXIMOS = 50_000_000

    fun normalizar(bytes: ByteArray): AppResult<ByteArray> {
        if (bytes.isEmpty()) return recusa("Envie uma foto.")
        if (bytes.size > BYTES_MAXIMOS) return recusa("A foto é grande demais.")

        val original = runCatching { decodificar(bytes) }.getOrNull()
            ?: return recusa("O arquivo enviado não é uma imagem válida.")

        val redimensionada = redimensionar(original)
        return runCatching { escreverJpeg(redimensionada) }.fold(
            onSuccess = { it.asSuccess() },
            onFailure = { AppError.Unexpected("Falha ao processar a foto", it).asFailure() },
        )
    }

    /** Lê as dimensões pelo cabeçalho, decide, e só então decodifica. */
    private fun decodificar(bytes: ByteArray): BufferedImage? {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { entrada ->
            val leitor = ImageIO.getImageReaders(entrada).asSequence().firstOrNull() ?: return null
            try {
                leitor.input = entrada
                val largura = leitor.getWidth(0)
                val altura = leitor.getHeight(0)
                if (largura.toLong() * altura.toLong() > PIXELS_MAXIMOS) return null
                return leitor.read(0)
            } finally {
                leitor.dispose()
            }
        }
    }

    private fun redimensionar(origem: BufferedImage): BufferedImage {
        val maior = maxOf(origem.width, origem.height)
        val escala = if (maior <= LADO_MAXIMO) 1.0 else LADO_MAXIMO.toDouble() / maior
        val largura = (origem.width * escala).toInt().coerceAtLeast(1)
        val altura = (origem.height * escala).toInt().coerceAtLeast(1)

        // TYPE_INT_RGB sempre, mesmo sem redimensionar: descarta canal alfa (um PNG transparente
        // viraria fundo preto sujo no JPEG) e garante uma matriz nova, sem nada herdado do arquivo.
        val destino = BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB)
        destino.createGraphics().run {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(origem, 0, 0, largura, altura, null)
            dispose()
        }
        return destino
    }

    private fun escreverJpeg(imagem: BufferedImage): ByteArray {
        val escritor = ImageIO.getImageWritersByFormatName("jpeg").next()
        val saida = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(saida).use { fluxo ->
            escritor.output = fluxo
            val params = escritor.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = QUALIDADE
            }
            // `IIOImage(imagem, null, null)`: o terceiro argumento são os METADADOS, e ele vai
            // nulo de propósito. É aqui, literalmente, que o EXIF deixa de existir.
            escritor.write(null, IIOImage(imagem, null, null), params)
            escritor.dispose()
        }
        return saida.toByteArray()
    }

    private fun recusa(mensagem: String): AppResult<ByteArray> =
        AppError.Validation(mensagem, mapOf("foto" to mensagem)).asFailure()

    /** 0.8 é o joelho da curva: abaixo disso aparece artefato em pele e em texto de camiseta. */
    private const val QUALIDADE = 0.8f
}
