package dev.rafael.app.data.checkin

import dev.rafael.contract.checkin.CheckInDto
import dev.rafael.core.result.AppResult

/**
 * Check-in como as TELAS o enxergam (fatia B).
 *
 * **Tudo online-only, sem cache e fora do outbox** (10.1) — e aqui o motivo é mais forte que no
 * nome do usuário: o check-in depende do relógio e do fuso do SERVIDOR para saber em que dia cai,
 * e o "um por dia" é decidido por um índice único no banco. Um check-in otimista local seria um
 * registro que talvez o servidor recuse por já existir — e a pessoa veria o próprio check-in
 * desaparecer da tela depois de comemorar.
 *
 * O feed também não tem cache nesta fatia: ele muda por ação de OUTRAS pessoas o tempo todo, que
 * é exatamente o caso em que TTL é atraso e não economia (a mesma lição da lista de grupos).
 */
interface CheckIns {

    /**
     * Faz o check-in. A foto vai crua daqui: quem comprime é o [CompressorDeFoto], chamado pela
     * tela logo depois da captura, porque é lá que a rotação do CameraX é conhecida.
     */
    suspend fun fazer(
        groupId: String,
        foto: ByteArray?,
        nomeDoLocal: String?,
        latitude: Double?,
        longitude: Double?,
    ): AppResult<CheckInDto>

    suspend fun feed(groupId: String, antesDe: String? = null): AppResult<List<CheckInDto>>

    suspend fun apagar(groupId: String, checkInId: String): AppResult<Unit>
}

/** Sem cache: o repositório é uma casca fina sobre a API, e isso é decisão, não preguiça. */
class CheckInsRepository(private val api: CheckInsApi) : CheckIns {

    override suspend fun fazer(
        groupId: String,
        foto: ByteArray?,
        nomeDoLocal: String?,
        latitude: Double?,
        longitude: Double?,
    ): AppResult<CheckInDto> = api.criar(groupId, foto, nomeDoLocal, latitude, longitude)

    override suspend fun feed(groupId: String, antesDe: String?): AppResult<List<CheckInDto>> =
        api.feed(groupId, antesDe)

    override suspend fun apagar(groupId: String, checkInId: String): AppResult<Unit> =
        api.apagar(groupId, checkInId)
}
