package dev.rafael.server.features.checkin.routes

import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.contract.group.GroupRule
import dev.rafael.server.auth.FirebaseUser
import dev.rafael.server.features.checkin.services.CheckInService
import dev.rafael.server.features.checkin.services.FakeArmazenamento
import dev.rafael.server.features.checkin.services.FakeCheckInRepository
import dev.rafael.server.features.group.services.FakeGroupRepository
import dev.rafael.server.features.group.services.FakeUserRepository
import dev.rafael.server.features.group.services.usuario
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.plugins.FIREBASE_AUTH
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A ROTA de check-in, sobre HTTP de verdade (`ktor-server-test-host`).
 *
 * **Por que isto faltava, e por que dói.** O desmonte do multipart foi escrito sem nenhuma
 * verificação: um erro no nome da parte, no `filename` ou no tipo de conteúdo passaria por todos
 * os 385 testes existentes e só apareceria com o app na mão. E o teste de serviço não alcança
 * isso — ele começa depois do corpo já desmontado.
 *
 * Também é aqui que se prova a **autorização na fronteira**. "Quem não é do grupo não vê a foto"
 * está afirmado no serviço, mas quem decide se o serviço chega a ser chamado é a rota.
 *
 * Sem Postgres de propósito: o que se testa é HTTP, e os dublês em memória bastam. Os testes de
 * integração cobrem o banco.
 */
class CheckInRoutesTest {

    private val eu = usuario("eu")
    private val outro = usuario("outro")
    private val agora = Instant.parse("2026-08-25T18:00:00Z")
    private val relogio = object : Clock { override fun now() = agora }

    private class Montagem(
        val grupos: FakeGroupRepository,
        val checkIns: FakeCheckInRepository,
        val disco: FakeArmazenamento,
        val grupo: String,
    )

    /**
     * Sobe a aplicação com a MESMA rota de produção e um autenticador de mentira no lugar do
     * Firebase — que é infraestrutura externa e não tem nada a ver com o que se quer testar aqui.
     * O nome do provedor é o de produção (`FIREBASE_AUTH`), senão o `authenticate` da rota não o
     * encontraria e o teste passaria a testar outra coisa.
     */
    private fun ApplicationTestBuilder.montar(regras: Set<GroupRule> = emptySet()): Montagem {
        val grupos = FakeGroupRepository()
        val checkIns = FakeCheckInRepository()
        val disco = FakeArmazenamento()
        val users = UserService(FakeUserRepository(listOf(eu, outro)))
        val id = grupos.semear(
            admin = eu.id,
            outros = listOf(outro.id),
            inicio = LocalDate(2026, 8, 1),
            fim = LocalDate(2026, 9, 30),
            regras = regras,
        )
        val service = CheckInService(users, grupos, checkIns, disco, relogio)

        application {
            modulo(
                service,
                mapOf(
                    "token-eu" to FirebaseUser(eu.firebaseUid, eu.email, true),
                    "token-outro" to FirebaseUser(outro.firebaseUid, outro.email, true),
                ),
            )
        }
        return Montagem(grupos, checkIns, disco, id.toString())
    }

    /**
     * O módulo do teste, com receptor `Application` EXPLÍCITO.
     *
     * Dentro do `application { }` o `install` fica ambíguo: o `ApplicationTestBuilder` tem um
     * `install` próprio, para o cliente de teste. Uma função de extensão resolve isso e ainda
     * tira a duplicação entre os cenários.
     *
     * O autenticador é de mentira porque o Firebase é infraestrutura externa e não tem nada a ver
     * com o que se testa aqui. Mas o NOME do provedor é o de produção (`FIREBASE_AUTH`) — se
     * fosse outro, o `authenticate` da rota não o encontraria e o teste passaria a testar
     * outra coisa.
     */
    private fun Application.modulo(service: CheckInService, tokens: Map<String, FirebaseUser>) {
        install(ContentNegotiation) { json() }
        install(Authentication) {
            bearer(FIREBASE_AUTH) {
                authenticate { credencial -> tokens[credencial.token] }
            }
        }
        routing { checkInRoutes(service) }
    }

    private fun jpeg(): ByteArray {
        val saida = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB), "jpeg", saida)
        return saida.toByteArray()
    }

    private fun corpo(
        foto: ByteArray? = null,
        nomeDoLocal: String? = null,
        latitude: String? = null,
        longitude: String? = null,
    ) = MultiPartFormDataContent(
        formData {
            foto?.let {
                append(
                    "foto", it,
                    Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
                        append(HttpHeaders.ContentDisposition, "filename=\"checkin.jpg\"")
                    },
                )
            }
            nomeDoLocal?.let { append("nomeDoLocal", it) }
            latitude?.let { append("latitude", it) }
            longitude?.let { append("longitude", it) }
        },
    )

    private val json = Json { ignoreUnknownKeys = true }

    // ---- multipart ----

    @Test
    fun `o multipart chega inteiro no servico`() = testApplication {
        // Cada campo tem que atravessar o desmonte com o nome certo. Um erro em qualquer um deles
        // passaria por toda a suíte de serviço, que começa DEPOIS do corpo desmontado.
        val m = montar(regras = setOf(GroupRule.FOTO, GroupRule.LOCALIZACAO))

        val r = client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo(foto = jpeg(), nomeDoLocal = "Smart Fit", latitude = "-23.5505", longitude = "-46.6333"))
        }

        assertEquals(HttpStatusCode.OK, r.status)
        val dto = json.decodeFromString(CheckInDto.serializer(), r.bodyAsText())
        assertEquals("Smart Fit", dto.placeName)
        assertEquals("/checkins/${dto.id}/foto", dto.photoUrl)
        assertEquals(1, m.disco.gravacoes, "a foto tem que ter chegado como ARQUIVO, não como campo")
        // A coordenada foi arredondada no caminho — prova que o texto virou Double de verdade.
        assertEquals(-23.55, m.checkIns.guardados.single().placeLat)
    }

    @Test
    fun `coordenada malformada vira recusa de validacao, nao 500`() = testApplication {
        // `"abc".toDoubleOrNull()` devolve null e a validação do serviço explica o problema. Sem
        // isso, um parse solto viraria exceção e a pessoa leria "algo deu errado do nosso lado"
        // por ter mandado um número inválido.
        val m = montar(regras = setOf(GroupRule.LOCALIZACAO))

        val r = client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo(nomeDoLocal = "Academia", latitude = "abc", longitude = "xyz"))
        }

        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun `grupo que exige foto recusa multipart sem a parte da foto`() = testApplication {
        val m = montar(regras = setOf(GroupRule.FOTO))

        val r = client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo())
        }

        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("foto"), "o erro tem que apontar o campo")
    }

    @Test
    fun `o segundo check-in do dia responde 409`() = testApplication {
        val m = montar()
        client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo())
        }

        val r = client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo())
        }

        assertEquals(HttpStatusCode.Conflict, r.status)
        assertTrue(r.bodyAsText().contains("JA_FEZ_HOJE"), "o code viaja no envelope (#31)")
    }

    // ---- autenticação e autorização NA FRONTEIRA ----

    @Test
    fun `sem token, a rota nem chega no servico`() = testApplication {
        val m = montar()

        val r = client.post("/groups/${m.grupo}/checkins") { setBody(corpo()) }

        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertTrue(m.checkIns.guardados.isEmpty())
    }

    @Test
    fun `token desconhecido nao passa`() = testApplication {
        val m = montar()

        val r = client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-inventado")
            setBody(corpo())
        }

        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun `todas as rotas de check-in exigem autenticacao`() = testApplication {
        // Varredura: uma rota nova esquecida fora do bloco `authenticate` é o defeito mais fácil
        // de cometer e o mais caro — expõe foto de gente a quem não está logado.
        val m = montar()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/groups/${m.grupo}/checkins").status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/groups/${m.grupo}/checkins/qualquer").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/checkins/qualquer/foto").status)
    }

    // ---- feed, foto e exclusão ----

    @Test
    fun `o feed responde JSON e respeita o limite da query`() = testApplication {
        val m = montar()
        client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo())
        }

        val r = client.get("/groups/${m.grupo}/checkins?limite=10") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
        }

        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(r.bodyAsText().contains("\"mine\":true"))
    }

    @Test
    fun `a foto responde BYTES com o tipo de imagem, e nao JSON`() = testApplication {
        // A única rota do projeto cujo sucesso não é JSON. Se alguém trocar por `respondResult`,
        // o cliente passa a receber um objeto serializado onde esperava uma imagem.
        val m = montar(regras = setOf(GroupRule.FOTO))
        val criado = client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo(foto = jpeg()))
        }
        val id = json.decodeFromString(CheckInDto.serializer(), criado.bodyAsText()).id

        val r = client.get("/checkins/$id/foto") { header(HttpHeaders.Authorization, "Bearer token-eu") }

        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals(ContentType.Image.JPEG.contentType, r.contentType()?.contentType)
        assertTrue(r.headers[HttpHeaders.CacheControl]?.contains("private") == true)
    }

    @Test
    fun `quem nao e do grupo recebe 404 na foto, mesmo com o id em maos`() = testApplication {
        // A autorização é REAVALIADA a cada leitura — foi a decisão que pagamos com o custo de
        // toda foto atravessar o Ktor. Se um dia isto virar arquivo estático, este teste cai.
        val grupos = FakeGroupRepository()
        val checkIns = FakeCheckInRepository()
        val disco = FakeArmazenamento()
        val forasteiro = usuario("forasteiro")
        val users = UserService(FakeUserRepository(listOf(eu, forasteiro)))
        val id = grupos.semear(admin = eu.id, inicio = LocalDate(2026, 8, 1), fim = LocalDate(2026, 9, 30), regras = setOf(GroupRule.FOTO))
        val service = CheckInService(users, grupos, checkIns, disco, relogio)

        application {
            modulo(
                service,
                mapOf(
                    "token-eu" to FirebaseUser(eu.firebaseUid, eu.email, true),
                    "token-fora" to FirebaseUser(forasteiro.firebaseUid, forasteiro.email, true),
                ),
            )
        }

        val criado = client.post("/groups/$id/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo(foto = jpeg()))
        }
        val checkInId = json.decodeFromString(CheckInDto.serializer(), criado.bodyAsText()).id

        val r = client.get("/checkins/$checkInId/foto") {
            header(HttpHeaders.Authorization, "Bearer token-fora")
        }

        assertEquals(HttpStatusCode.NotFound, r.status, "403 contaria que o check-in existe")
    }

    @Test
    fun `apagar o proprio check-in responde 200 e some do feed`() = testApplication {
        val m = montar()
        val criado = client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo())
        }
        val id = json.decodeFromString(CheckInDto.serializer(), criado.bodyAsText()).id

        val r = client.delete("/groups/${m.grupo}/checkins/$id") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
        }

        assertEquals(HttpStatusCode.OK, r.status)
        assertTrue(m.checkIns.guardados.isEmpty())
    }

    @Test
    fun `ninguem apaga o check-in de outra pessoa pela rota`() = testApplication {
        val m = montar()
        val criado = client.post("/groups/${m.grupo}/checkins") {
            header(HttpHeaders.Authorization, "Bearer token-eu")
            setBody(corpo())
        }
        val id = json.decodeFromString(CheckInDto.serializer(), criado.bodyAsText()).id

        val r = client.delete("/groups/${m.grupo}/checkins/$id") {
            header(HttpHeaders.Authorization, "Bearer token-outro")
        }

        assertEquals(HttpStatusCode.NotFound, r.status)
        assertEquals(1, m.checkIns.guardados.size)
    }
}
