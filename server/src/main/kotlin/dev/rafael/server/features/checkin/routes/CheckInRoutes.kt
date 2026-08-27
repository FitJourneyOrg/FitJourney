package dev.rafael.server.features.checkin.routes

import dev.rafael.core.result.AppResult
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.error.respondResult
import dev.rafael.server.error.toHttp
import dev.rafael.server.features.checkin.services.CheckInService
import dev.rafael.server.features.checkin.services.PedidoDeCheckIn
import dev.rafael.server.media.Foto
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

/**
 * Check-in (fatia B).
 *
 * **Multipart e não JSON com base64.** Base64 infla o corpo em 33% e obriga o servidor a segurar
 * a imagem inteira como texto antes de decodificar — numa foto de 200 KB é bobagem, mas o formato
 * também some com o streaming e com o `Content-Type` da parte. Multipart é o que o Android já
 * sabe montar e o que um proxy sabe limitar.
 *
 * A rota **não decide nada**: desmonta o corpo e entrega ao service. Regra aqui seria regra fora
 * de teste, porque rota só se testa subindo HTTP.
 */
fun Route.checkInRoutes(service: CheckInService) {
    authenticate(FIREBASE_AUTH) {

        post("/groups/{id}/checkins") {
            val p = call.principal<FirebaseUser>()!!
            val groupId = call.parameters["id"].orEmpty()

            var foto: ByteArray? = null
            var nomeDoLocal: String? = null
            var latitude: Double? = null
            var longitude: Double? = null

            call.receiveMultipart(formFieldLimit = Foto.BYTES_MAXIMOS.toLong())
                .forEachPart { parte ->
                    when (parte) {
                        is PartData.FileItem -> if (parte.name == "foto") {
                            // Lê no MÁXIMO o teto + 1 byte. O byte a mais é de propósito: com ele
                            // o `Foto.normalizar` enxerga que passou do limite e recusa como
                            // validação, em vez de receber um arquivo truncado e reclamar que a
                            // imagem é inválida — que é verdade, mas é a mensagem errada.
                            //
                            // O teto vem do mesmo lugar que a normalização usa: um número só,
                            // combinado entre o transporte e a regra.
                            foto = parte.provider()
                                .readRemaining((Foto.BYTES_MAXIMOS + 1).toLong())
                                .readByteArray()
                        }
                        is PartData.FormItem -> when (parte.name) {
                            "nomeDoLocal" -> nomeDoLocal = parte.value
                            // Coordenada malformada vira `null` e cai na validação do service como
                            // "não consegui localizar você" — e não num 500 de parsing.
                            "latitude" -> latitude = parte.value.toDoubleOrNull()
                            "longitude" -> longitude = parte.value.toDoubleOrNull()
                        }
                        else -> Unit
                    }
                    parte.release()
                }

            call.respondResult(
                service.criar(
                    p.uid,
                    p.email,
                    groupId,
                    PedidoDeCheckIn(foto, nomeDoLocal, latitude, longitude),
                ),
            )
        }

        /** O FEED do grupo (8.0). `?limite=30&antesDe=<ISO>` — cursor, não página. */
        get("/groups/{id}/checkins") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.feed(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.request.queryParameters["limite"]?.toIntOrNull(),
                    call.request.queryParameters["antesDe"],
                ),
            )
        }

        /**
         * O RANKING do grupo (7.2). Só para membro.
         *
         * Sem paginação de propósito: o teto de 50 pessoas (2.2) é o teto da lista. Paginar
         * cinquenta linhas seria complexidade para um problema que a regra já impede de existir.
         */
        get("/groups/{id}/ranking") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(service.ranking(p.uid, p.email, call.parameters["id"].orEmpty()))
        }

        /** Apagar o próprio check-in, no mesmo dia (4.11). */
        delete("/groups/{id}/checkins/{checkInId}") {
            val p = call.principal<FirebaseUser>()!!
            call.respondResult(
                service.apagar(
                    p.uid,
                    p.email,
                    call.parameters["id"].orEmpty(),
                    call.parameters["checkInId"].orEmpty(),
                ),
            )
        }

        /**
         * A FOTO. Sem `groupId` no caminho de propósito: quem diz a que grupo ela pertence é o
         * check-in, e confiar num id vindo da URL seria deixar o cliente escolher a própria
         * permissão. O service carrega o check-in e confere a filiação a partir dele.
         *
         * Não usa `respondResult` porque a resposta é BINÁRIA — o envelope de erro continua JSON,
         * mas o sucesso são bytes de imagem.
         */
        get("/checkins/{id}/foto") {
            val p = call.principal<FirebaseUser>()!!
            when (val r = service.foto(p.uid, p.email, call.parameters["id"].orEmpty())) {
                is AppResult.Success -> {
                    // Cabeçalho ANTES do corpo — depois de responder não há mais o que cabeçalhar.
                    //
                    // Cache curto e `private`: a foto é imutável (recodificada uma vez, nunca
                    // reescrita), mas a PERMISSÃO muda — quem sai do grupo não pode continuar
                    // vendo por causa de um cache longo. `private` também a mantém fora de proxy
                    // compartilhado, que é onde uma foto de usuário nunca deveria encostar.
                    call.response.headers.append(HttpHeaders.CacheControl, "private, max-age=300")
                    call.respondBytes(r.value, ContentType.Image.JPEG)
                }
                is AppResult.Failure -> {
                    val (status, corpo) = r.error.toHttp()
                    call.respond(status, corpo)
                }
            }
        }
    }
}
