package dev.rafael.server.features.checkin.services

import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.server.features.checkin.db.CheckInRepository
import dev.rafael.server.features.checkin.models.CheckIn
import dev.rafael.server.features.checkin.models.CheckInComAutor
import dev.rafael.server.features.checkin.models.NovoCheckIn
import dev.rafael.server.features.checkin.models.toDto
import dev.rafael.contract.group.RankingEntryDto
import dev.rafael.server.features.group.db.GroupRepository
import dev.rafael.server.features.group.models.Group
import dev.rafael.server.features.group.services.GroupPolicy
import dev.rafael.server.features.user.models.User
import dev.rafael.server.features.user.services.UserService
import dev.rafael.server.media.ArmazenamentoDeMidia
import dev.rafael.server.media.Foto
import dev.rafael.contract.checkin.CheckInStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * O que a pessoa manda no check-in. Vem do multipart, já desmontado pela rota.
 *
 * A rota não decide nada — só traduz HTTP em dados. Toda regra mora aqui.
 */
data class PedidoDeCheckIn(
    val foto: ByteArray?,
    val nomeDoLocal: String?,
    val latitude: Double?,
    val longitude: Double?,
) {
    // `equals`/`hashCode` gerados por data class comparam o ByteArray por referência. Não usamos
    // igualdade aqui, e sobrescrever seria mentira útil a ninguém — mas o aviso do compilador é
    // legítimo, então fica explícito que a comparação não vale.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * CHECK-IN (fatia B, ARCH #33 emendado).
 *
 * **Ordem das validações não é estilo, é economia e higiene.** Tudo o que recusa vem ANTES de
 * gravar a foto: normalizar uma imagem custa CPU, e guardá-la para depois recusar o check-in
 * deixaria arquivo órfão em disco a cada tentativa inválida. Quando a gravação já aconteceu e o
 * `INSERT` ainda assim falha (corrida do "um por dia"), a foto é apagada na volta.
 */
class CheckInService(
    private val userService: UserService,
    private val groups: GroupRepository,
    private val repository: CheckInRepository,
    private val midia: ArmazenamentoDeMidia,
    private val clock: Clock = Clock.System,
) {

    suspend fun criar(
        firebaseUid: String,
        email: String?,
        groupId: String,
        pedido: PedidoDeCheckIn,
    ): AppResult<CheckInDto> = comMembro(firebaseUid, email, groupId) { grupo, user ->
        val agora = clock.now()
        val fuso = grupo.timezone

        // 1. O grupo aceita check-in agora? [INV] só com ATIVO.
        CheckInPolicy.impedimento(GroupPolicy.estado(grupo.startDate, grupo.endDate, agora, fuso))
            ?.let { return@comMembro recusa(it) }

        // 2. As regras do grupo estão cumpridas? Checagem ESTRUTURAL (5.3) — existe foto, existe
        //    local. O conteúdo não é conferido por ninguém até alguém denunciar (fatia E).
        val temLocal = !pedido.nomeDoLocal.isNullOrBlank()
        val faltando = CheckInPolicy.regrasNaoCumpridas(grupo.rules, pedido.foto != null, temLocal)
        if (faltando.isNotEmpty()) {
            return@comMembro AppError.Validation(
                "Este desafio exige mais do que você enviou.",
                faltando.associate { it.name.lowercase() to "Este desafio exige ${it.name.lowercase().replace('_', ' ')}." },
            ).asFailure()
        }

        // 3. O local veio inteiro? Nome sem coordenada não passa no CHECK do banco, e um 500 por
        //    isso esconderia um erro de cliente atrás de "algo deu errado do nosso lado".
        validarLocal(pedido)?.let { return@comMembro it.asFailure() }

        // 4. Só AGORA a foto vira bytes em disco.
        val ref = when (val r = guardarFoto(pedido.foto)) {
            is AppResult.Failure -> return@comMembro r
            is AppResult.Success -> r.value
        }

        val novo = NovoCheckIn(
            id = Uuid.random(),
            groupId = grupo.id,
            userId = user.id,
            localDate = CheckInPolicy.diaDoGrupo(agora, fuso),   // dia do GRUPO (4.6)
            createdAt = agora.toLocalDateTime(TimeZone.UTC),     // relógio do SERVIDOR (4.5)
            photoRef = ref,
            placeName = pedido.nomeDoLocal?.trim(),
            placeLat = pedido.latitude?.let(CheckInPolicy::arredondar),
            placeLng = pedido.longitude?.let(CheckInPolicy::arredondar),
        )

        repository.criar(novo).flatMap { criou ->
            if (!criou) {
                // Perdeu a corrida do índice único. A foto que acabou de ser gravada não tem mais
                // dono — apagar aqui é o que impede o disco de acumular lixo a cada toque duplo.
                ref?.let { midia.apagar(it) }
                return@flatMap recusa(CheckInBlock.JA_FEZ_HOJE)
            }
            paraDto(novo, user, agora, fuso).asSuccess()
        }
    }

    /**
     * APAGAR o próprio check-in (4.11). Só o dono, só no MESMO dia do grupo.
     *
     * Apagar libera o slot do dia — é o que faz a regra conversar com o índice único. Sem isso,
     * arrepender-se seria uma armadilha: a pessoa apagaria a foto tremida e ficaria sem poder
     * refazer o check-in.
     */
    suspend fun apagar(
        firebaseUid: String,
        email: String?,
        groupId: String,
        checkInId: String,
    ): AppResult<Unit> = comMembro(firebaseUid, email, groupId) { grupo, user ->
        val id = runCatching { Uuid.parse(checkInId) }.getOrNull() ?: return@comMembro naoEncontrado()

        repository.porId(id).flatMap { achado ->
            val alvo = achado?.checkIn
            // Não é meu, ou não é deste grupo: 404. Dizer "sem permissão" contaria que existe.
            if (alvo == null || alvo.groupId != grupo.id || alvo.userId != user.id) {
                return@flatMap naoEncontrado()
            }
            if (!CheckInPolicy.podeApagar(alvo.localDate, clock.now(), grupo.timezone)) {
                return@flatMap AppError.Conflict(
                    "Só dá para apagar um check-in no mesmo dia em que ele foi feito.",
                    CODE_PRAZO_DE_EXCLUSAO,
                ).asFailure()
            }
            // [PROPOSTA — a ratificar na fatia E] check-in INVALIDADO não se apaga.
            //
            // A 4.11 diz "apagar libera o slot" e o invariante diz "invalidado nunca volta a
            // contar". Juntas, permitiriam apagar-e-refazer para desfazer a decisão do admin — e
            // "decisões do admin são imutáveis". Hoje não morde (invalidação só existe na E), mas
            // deixar a brecha aberta seria plantá-la.
            if (alvo.status != CheckInStatus.VALIDO) {
                return@flatMap AppError.Conflict(
                    "Este check-in está em análise e não pode ser apagado.",
                    CODE_EM_ANALISE,
                ).asFailure()
            }

            // A LINHA primeiro, o arquivo depois — e nessa ordem de propósito.
            //
            // Se o arquivo saísse antes e o `DELETE` falhasse, sobraria uma linha apontando para
            // uma foto que não existe: o feed mostraria um item quebrado. Na ordem inversa, o pior
            // caso é um arquivo órfão em disco — invisível, e que a purga dos 90 dias não alcança,
            // mas que não mente para ninguém. Preferir o lixo silencioso ao dado inconsistente.
            repository.apagar(id).flatMap {
                alvo.photoRef?.let { midia.apagar(it) }
                Unit.asSuccess()
            }
        }
    }

    /**
     * O FEED do grupo (8.0) — só para quem está dentro.
     *
     * `antesDe` é cursor e não página: com polling de 10s (8.3) chegando item novo por cima,
     * paginar por deslocamento faria a segunda página repetir ou pular linhas.
     */
    suspend fun feed(
        firebaseUid: String,
        email: String?,
        groupId: String,
        limite: Int?,
        antesDe: String?,
    ): AppResult<List<CheckInDto>> = comMembro(firebaseUid, email, groupId) { grupo, user ->
        val agora = clock.now()
        val cursor = antesDe?.let { texto ->
            runCatching { Instant.parse(texto).toLocalDateTime(TimeZone.UTC) }.getOrNull()
                ?: return@comMembro AppError.Validation("Cursor inválido.").asFailure()
        }
        repository.doGrupo(grupo.id, (limite ?: PAGINA_PADRAO).coerceIn(1, PAGINA_MAXIMA), cursor)
            .flatMap { itens ->
                itens.map { it.toDto(user.id, agora, grupo.timezone) }.asSuccess()
            }
    }

    /**
     * O RANKING do grupo (7.2, fatia C) — só para quem está dentro.
     *
     * **A posição é atribuída aqui, sobre a ordem que o banco devolveu, e não é guardada.** Mesma
     * escolha do estado do grupo: derivar é sempre correto, e não existe posição persistida
     * divergindo da contagem porque alguém esqueceu de recalcular.
     *
     * Não há empate na lista final: a consulta desempata por quem atingiu a pontuação primeiro,
     * então duas pessoas com a mesma contagem recebem posições diferentes — e a de cima é a de
     * quem chegou lá antes.
     */
    suspend fun ranking(
        firebaseUid: String,
        email: String?,
        groupId: String,
    ): AppResult<List<RankingEntryDto>> = comMembro(firebaseUid, email, groupId) { grupo, user ->
        repository.ranking(grupo.id).flatMap { linhas ->
            linhas.mapIndexed { indice, linha ->
                RankingEntryDto(
                    position = indice + 1,
                    userId = linha.userId.toString(),
                    displayName = linha.displayName,
                    checkIns = linha.checkIns,
                    mine = linha.userId == user.id,
                )
            }.asSuccess()
        }
    }

    /**
     * A FOTO, servida atrás de autenticação e **conferindo filiação a cada leitura**.
     *
     * Foto de check-in é gente. URL aleatória servida como estático é o padrão da indústria e é
     * mais barato, mas quem tiver o link vê para sempre — inclusive quem saiu do grupo. Aqui a
     * permissão é reavaliada em cada requisição, que é o único jeito de "saiu, não vê mais" ser
     * verdade. O custo é toda foto atravessar o Ktor; com o volume da Fase 6, é aceitável, e a
     * troca por URL assinada mexe num componente só (10.5).
     */
    suspend fun foto(firebaseUid: String, email: String?, checkInId: String): AppResult<ByteArray> =
        userService.findOrCreate(firebaseUid, email).flatMap { user ->
            val id = runCatching { Uuid.parse(checkInId) }.getOrNull() ?: return@flatMap naoEncontrado()
            repository.porId(id).flatMap { achado ->
                val alvo = achado?.checkIn ?: return@flatMap naoEncontrado()
                // O check-in NÃO diz quem pode vê-lo; o vínculo com o grupo diz.
                groups.roleOf(alvo.groupId, user.id).flatMap { papel ->
                    if (papel == null) return@flatMap naoEncontrado()
                    val ref = alvo.photoRef.takeIf { alvo.fotoViva } ?: return@flatMap naoEncontrado()
                    midia.ler(ref).flatMap { bytes ->
                        // Referência viva no banco e arquivo ausente: 404 e não 500. Para quem
                        // pede, "não está mais aqui" é a verdade — e é o que acontece depois da
                        // purga se a marcação falhar.
                        bytes?.asSuccess() ?: naoEncontrado()
                    }
                }
            }
        }

    // ---- caminho comum ----

    /**
     * Resolve usuário e grupo, e recusa quem não é membro com **404, nunca 403**.
     *
     * Responder "sem permissão" contaria que aquele grupo existe. Para quem está de fora, ele não
     * existe — mesma escolha do `GET /groups/{id}`.
     */
    private suspend fun <T> comMembro(
        firebaseUid: String,
        email: String?,
        groupId: String,
        bloco: suspend (Group, User) -> AppResult<T>,
    ): AppResult<T> = userService.findOrCreate(firebaseUid, email).flatMap { user ->
        val id = runCatching { Uuid.parse(groupId) }.getOrNull() ?: return@flatMap naoEncontrado()
        groups.roleOf(id, user.id).flatMap { papel ->
            if (papel == null) return@flatMap naoEncontrado()
            groups.findById(id).flatMap { grupo ->
                if (grupo == null) return@flatMap naoEncontrado()
                bloco(grupo, user)
            }
        }
    }

    private fun validarLocal(pedido: PedidoDeCheckIn): AppError? {
        val nome = pedido.nomeDoLocal?.trim()
        if (nome.isNullOrEmpty()) {
            return if (pedido.latitude != null || pedido.longitude != null) {
                AppError.Validation("Diga o nome do lugar.", mapOf("nomeDoLocal" to "Diga o nome do lugar."))
            } else {
                null   // sem local nenhum: legítimo quando o grupo não exige
            }
        }
        if (nome.length > CheckInPolicy.MAX_NOME_DO_LOCAL) {
            val msg = "Use até ${CheckInPolicy.MAX_NOME_DO_LOCAL} caracteres."
            return AppError.Validation(msg, mapOf("nomeDoLocal" to msg))
        }
        if (pedido.latitude == null || pedido.longitude == null) {
            val msg = "Não consegui localizar você. Tente de novo."
            return AppError.Validation(msg, mapOf("nomeDoLocal" to msg))
        }
        return null
    }

    private suspend fun guardarFoto(bytes: ByteArray?): AppResult<String?> {
        if (bytes == null) return AppResult.Success(null)
        // Recodifica: é aqui que o EXIF deixa de existir ([INV]) e que o teto de 1080px é imposto
        // pelo SERVIDOR, porque "o app comprime" é expectativa, não garantia.
        return Foto.normalizar(bytes).flatMap { limpa -> midia.guardar(limpa, Foto.EXTENSAO) }
    }

    private fun paraDto(novo: NovoCheckIn, autor: User, agora: Instant, fuso: TimeZone): CheckInDto =
        CheckInComAutor(
            checkIn = CheckIn(
                id = novo.id,
                groupId = novo.groupId,
                userId = novo.userId,
                localDate = novo.localDate,
                createdAt = novo.createdAt,
                status = CheckInStatus.VALIDO,
                photoRef = novo.photoRef,
                photoPurgedAt = null,
                placeName = novo.placeName,
            ),
            displayName = autor.displayName,
        ).toDto(quemPede = autor.id, agora = agora, fusoDoGrupo = fuso)

    private fun <T> recusa(bloqueio: CheckInBlock): AppResult<T> =
        AppError.Conflict(bloqueio.frase(), bloqueio.name).asFailure()

    private fun <T> naoEncontrado(): AppResult<T> =
        AppError.NotFound("Grupo não encontrado").asFailure()

    /**
     * A frase vai em `message` e o enum em `code` — a lição do 409 que dizia "dados
     * desatualizados" para uma regra de negócio. A tela pode escrever a própria frase pelo code
     * (#31); enquanto não escreve, a do servidor já é útil.
     */
    private fun CheckInBlock.frase(): String = when (this) {
        CheckInBlock.NAO_COMECOU -> "Este desafio ainda não começou."
        CheckInBlock.ENCERRADO -> "Este desafio já terminou."
        CheckInBlock.JA_FEZ_HOJE -> "Você já fez check-in neste desafio hoje."
    }

    private companion object {
        const val PAGINA_PADRAO = 30
        /** Teto do que uma requisição pode pedir: cliente não decide a carga do servidor. */
        const val PAGINA_MAXIMA = 100

        const val CODE_PRAZO_DE_EXCLUSAO = "PRAZO_DE_EXCLUSAO"
        const val CODE_EM_ANALISE = "EM_ANALISE"
    }
}
