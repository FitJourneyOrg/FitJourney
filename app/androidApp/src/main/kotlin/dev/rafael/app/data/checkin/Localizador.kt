package dev.rafael.app.data.checkin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Quanto detalhe a pessoa escolheu publicar.
 *
 * Não é configuração de sistema, é decisão dela, por check-in. Um desafio entre amigos pode não ter
 * problema nenhum em dizer a rua; o mesmo aparelho, num grupo aberto, tem.
 */
enum class PrecisaoDoLocal {
    /** Bairro e cidade. O PADRÃO — em privacidade, o padrão pesa mais que a opção. */
    APROXIMADA,

    /** Rua e número. Exige `ACCESS_FINE_LOCATION`, pedida só quando escolhida. */
    EXATA,
}

/** Onde a pessoa está, do jeito que a 5.2 quer: um RÓTULO sugerido e a coordenada. */
data class LocalSugerido(
    /** Sugestão para CONFIRMAR ou REESCREVER. Nunca é enviada sem passar pela pessoa. */
    val sugestao: String?,
    val latitude: Double,
    val longitude: Double,
    /**
     * A precisão que de fato saiu — **não** a que foi pedida.
     *
     * Se a permissão fina não estiver concedida, o `getCurrentLocation` com prioridade alta
     * devolve dado grosso **em silêncio**. Sem este campo, a tela mostraria um endereço de rua
     * calculado a partir de uma posição de 2 km e a pessoa publicaria uma rua onde nunca esteve.
     */
    val precisao: PrecisaoDoLocal,
)

/**
 * Captura de localização para o check-in (5.2).
 *
 * **O `Geocoder` sugere; quem nomeia é a pessoa.** Ele devolve *endereço*, não estabelecimento —
 * "Rua Silva Bueno, 1000", e não "Smart Fit Ipiranga" (isso exigiria a Places API, paga por
 * consulta). Por isso o resultado entra como sugestão editável: quem treina em casa escreve
 * "Casa". É o opt-in do #17 na forma mais forte — a pessoa decide o que aparece, não apenas se
 * aparece.
 *
 * **A coordenada gravada é sempre arredondada a 2 casas, nas duas precisões** ([INV]). O que a
 * escolha muda é o TEXTO publicado, não o dado guardado. Como não há mapa na Fase 6, guardar a
 * coordenada cheia não compraria nada visível — só criaria risco.
 */
class Localizador(private val contexto: Context) {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(contexto) }

    /** A permissão fina está concedida? A tela pergunta antes de oferecer a opção "exatamente aqui". */
    fun podeSerExata(): Boolean =
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * `null` quando não deu para obter posição — GPS desligado, sem sinal, permissão negada.
     *
     * Quem chama trata isso como "o check-in não sai neste grupo" e **diz o motivo** (5.3): botão
     * morto sem explicação é o pior desfecho possível.
     */
    @SuppressLint("MissingPermission")   // a tela confere a permissão antes de chamar
    suspend fun onde(precisao: PrecisaoDoLocal = PrecisaoDoLocal.APROXIMADA): LocalSugerido? {
        // A precisão EFETIVA é limitada pela permissão, não pelo pedido. Rebaixar aqui é o que
        // impede a tela de prometer uma exatidão que o sistema não vai entregar.
        val efetiva = if (precisao == PrecisaoDoLocal.EXATA && podeSerExata()) {
            PrecisaoDoLocal.EXATA
        } else {
            PrecisaoDoLocal.APROXIMADA
        }

        val prioridade = when (efetiva) {
            PrecisaoDoLocal.EXATA -> Priority.PRIORITY_HIGH_ACCURACY
            PrecisaoDoLocal.APROXIMADA -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        // `<Location?>` explícito: sem ele o compilador fixa o tipo no `resume(null)` do
        // `addOnFailureListener` e passa a esperar `Nothing?` no sucesso.
        val posicao = suspendCancellableCoroutine<Location?> { cont ->
            fused.getCurrentLocation(prioridade, null)
                .addOnSuccessListener { cont.resume(it) }
                // Falha aqui é rotina, não exceção: GPS desligado, sem sinal, sem serviços Google.
                // Vira `null` e a tela explica o motivo (5.3).
                .addOnFailureListener { cont.resume(null) }
        } ?: return null

        return LocalSugerido(
            sugestao = rotulo(posicao.latitude, posicao.longitude, efetiva),
            latitude = posicao.latitude,
            longitude = posicao.longitude,
            precisao = efetiva,
        )
    }

    private suspend fun rotulo(lat: Double, lng: Double, precisao: PrecisaoDoLocal): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(contexto, Locale.getDefault())
            val endereco = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Desde a API 33 a versão bloqueante é depreciada; a assíncrona é a única sem
                    // aviso. Com minSdk 24 os dois caminhos precisam existir.
                    suspendCancellableCoroutine<Address?> { cont ->
                        geocoder.getFromLocation(lat, lng, 1) { cont.resume(it.firstOrNull()) }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                }
            }.getOrNull() ?: return@withContext null

            when (precisao) {
                PrecisaoDoLocal.APROXIMADA -> endereco.bairroECidade()
                PrecisaoDoLocal.EXATA -> endereco.ruaENumero() ?: endereco.bairroECidade()
            }?.take(MAX_ROTULO)
        }

    /**
     * "Ipiranga, São Paulo" — nunca a rua.
     *
     * Começar pelo endereço completo convidaria a pessoa a apenas confirmar sem pensar, publicando
     * onde mora por inércia para até 49 pessoas.
     */
    private fun Address.bairroECidade(): String? {
        val bairro = subLocality?.takeIf { it.isNotBlank() }
        val cidade = locality?.takeIf { it.isNotBlank() } ?: subAdminArea?.takeIf { it.isNotBlank() }
        return listOfNotNull(bairro, cidade).joinToString(", ").takeIf { it.isNotBlank() }
    }

    /** "Rua Silva Bueno, 1000" — só quando a pessoa pediu exatidão E a permissão fina existe. */
    private fun Address.ruaENumero(): String? {
        val rua = thoroughfare?.takeIf { it.isNotBlank() } ?: return null
        val numero = subThoroughfare?.takeIf { it.isNotBlank() }
        return listOfNotNull(rua, numero).joinToString(", ")
    }

    private companion object {
        /** Espelha o teto do servidor (`CheckInPolicy.MAX_NOME_DO_LOCAL`). */
        const val MAX_ROTULO = 60
    }
}
