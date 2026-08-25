package dev.rafael.app.data.checkin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

/**
 * Prepara a foto do check-in para subir: **rotação aplicada nos pixels**, lado maior em 1080 e
 * ~200 KB (4.10).
 *
 * **A rotação não é enfeite.** O servidor recodifica a imagem e joga fora todo o metadado — é
 * assim que o EXIF morre ([INV]). Só que a orientação da câmera também vive no EXIF: uma foto
 * tirada com o aparelho de lado chega correta na galeria e **deitada** no feed, porque lá não há
 * mais tag nenhuma dizendo como girá-la. Aplicar a rotação aqui é o que faz as duas decisões
 * conviverem.
 *
 * **O cliente comprimir não dispensa o servidor de conferir.** [REGRA] a autoridade é do servidor,
 * e um cliente modificado manda o que quiser. Isto aqui existe para economizar rede e bateria de
 * quem usa o app de verdade, não como garantia.
 */
object CompressorDeFoto {

    const val LADO_MAXIMO = 1080
    private const val ALVO_BYTES = 200 * 1024
    private const val QUALIDADE_INICIAL = 90
    private const val QUALIDADE_MINIMA = 60
    private const val PASSO = 10

    /**
     * @param graus rotação a aplicar, vinda do `ImageProxy.imageInfo.rotationDegrees` do CameraX —
     *   que já resolve orientação do sensor e do aparelho, sem precisarmos ler EXIF.
     */
    fun preparar(jpegOriginal: ByteArray, graus: Int): ByteArray {
        val original = BitmapFactory.decodeByteArray(jpegOriginal, 0, jpegOriginal.size)
            ?: return jpegOriginal   // não decodificou: sobe como veio e deixa o servidor recusar

        val girado = if (graus % 360 == 0) original else girar(original, graus)
        val reduzido = reduzir(girado)
        return comprimir(reduzido)
    }

    private fun girar(origem: Bitmap, graus: Int): Bitmap =
        Bitmap.createBitmap(origem, 0, 0, origem.width, origem.height, Matrix().apply { postRotate(graus.toFloat()) }, true)

    private fun reduzir(origem: Bitmap): Bitmap {
        val maior = maxOf(origem.width, origem.height)
        if (maior <= LADO_MAXIMO) return origem   // ampliar não recupera detalhe, só peso
        val escala = LADO_MAXIMO.toDouble() / maior
        return Bitmap.createScaledBitmap(
            origem,
            (origem.width * escala).toInt().coerceAtLeast(1),
            (origem.height * escala).toInt().coerceAtLeast(1),
            true,
        )
    }

    /**
     * Baixa a qualidade até caber em ~200 KB, com **piso**.
     *
     * O piso existe porque foto de academia é cheia de textura — equipamento, tecido, pele — e é
     * justamente o tipo de imagem que não comprime bem. Sem limite inferior, um enquadramento
     * ruim viraria um borrão para caber num número. Melhor subir 260 KB do que entregar algo que
     * a pessoa não reconhece.
     */
    private fun comprimir(bitmap: Bitmap): ByteArray {
        var qualidade = QUALIDADE_INICIAL
        var bytes = paraJpeg(bitmap, qualidade)
        while (bytes.size > ALVO_BYTES && qualidade > QUALIDADE_MINIMA) {
            qualidade -= PASSO
            bytes = paraJpeg(bitmap, qualidade)
        }
        return bytes
    }

    private fun paraJpeg(bitmap: Bitmap, qualidade: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, qualidade, it) }.toByteArray()
}
