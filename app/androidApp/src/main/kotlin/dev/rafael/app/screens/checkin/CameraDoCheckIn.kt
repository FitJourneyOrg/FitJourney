package dev.rafael.app.screens.checkin

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.rafael.app.data.checkin.CompressorDeFoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Câmera IN-APP do check-in (4.4).
 *
 * **Por que CameraX e não `ACTION_IMAGE_CAPTURE`.** A 4.4 diz "foto tirada dentro do app, sem
 * galeria". Com o intent do sistema, quem tira a foto é o app de câmera padrão do aparelho — que
 * pode ser qualquer coisa, inclusive algo que devolva imagem da galeria. A regra viraria confiança
 * no dispositivo; aqui ela é verificável, porque os bytes nascem do nosso `ImageCapture`.
 *
 * Não há botão de galeria e não existe caminho para um: é ausência de código, não uma trava.
 */
@Composable
fun CameraDoCheckIn(
    onFoto: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val dono = LocalLifecycleOwner.current
    val escopo = rememberCoroutineScope()
    var capturando by remember { mutableStateOf(false) }
    var frontal by remember { mutableStateOf(false) }

    val captura = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(contexto).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    // Religa a câmera quando a lente troca. `frontal` na chave, e não um efeito único: sem isso,
    // trocar de lente não faria nada — o binding antigo continuaria valendo.
    LaunchedEffect(frontal) {
        val provider = contexto.cameraProvider()
        val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        runCatching {
            provider.bindToLifecycle(
                dono,
                if (frontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                captura,
            )
        }
    }

    // Solta a câmera ao sair da tela. Sem isto ela fica presa ao processo, e o próximo app que
    // tentar abrir a câmera encontra o dispositivo ocupado.
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(contexto).get().unbindAll() } }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        IconButton(
            onClick = { frontal = !frontal },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        ) {
            Icon(Icons.Filled.Cameraswitch, contentDescription = "Trocar de câmera")
        }

        FloatingActionButton(
            onClick = {
                if (capturando) return@FloatingActionButton
                capturando = true
                captura.takePicture(
                    ContextCompat.getMainExecutor(contexto),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imagem: ImageProxy) {
                            val bytes = imagem.paraBytes()
                            val graus = imagem.imageInfo.rotationDegrees
                            imagem.close()
                            // A compressão sai da thread principal: redimensionar e recomprimir um
                            // bitmap de câmera trava a interface por centenas de milissegundos.
                            escopo.launch {
                                val pronta = withContext(Dispatchers.Default) {
                                    CompressorDeFoto.preparar(bytes, graus)
                                }
                                capturando = false
                                onFoto(pronta)
                            }
                        }

                        override fun onError(erro: ImageCaptureException) {
                            capturando = false
                        }
                    },
                )
            },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).size(72.dp),
        ) {
            if (capturando) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                // Círculo interno: o obturador clássico. Sem ícone de propósito — nenhum símbolo
                // diz "tirar foto" melhor do que o próprio botão redondo de câmera.
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
    }
}

/**
 * Os bytes do JPEG que o `ImageCapture` produziu.
 *
 * O plano 0 já é JPEG comprimido — não é matriz de pixels. Por isso basta copiar o buffer; não há
 * conversão de formato aqui.
 */
private fun ImageProxy.paraBytes(): ByteArray {
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

private suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({ cont.resume(futuro.get()) }, ContextCompat.getMainExecutor(this))
    }
