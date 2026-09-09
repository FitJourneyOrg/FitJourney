package dev.rafael.server.avisos

import dev.rafael.contract.checkin.ReportTarget
import dev.rafael.contract.group.MemberRole
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.server.features.checkin.db.GroupReportsTable
import dev.rafael.server.features.checkin.db.ModerationActionsTable
import dev.rafael.server.features.checkin.models.TipoDeAcao
import dev.rafael.server.features.group.db.GroupMembersTable
import dev.rafael.server.features.group.db.GroupsTable
import dev.rafael.server.features.group.services.AvisosDiarios
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AvisosDiariosRepositoryImpl(private val clock: Clock = Clock.System) : AvisosDiariosRepository {

    /**
     * Grupos que ainda importam.
     *
     * O `encerrado` é derivado aqui, e não no laço, porque a consulta já tem `end_date` na mão.
     * Carregar em memória e filtrar depois é seguro nesta escala — a alternativa (dois `SELECT`
     * com `WHERE` de data) duplicaria a regra de "o que é encerrado" no SQL, onde ela não vive.
     */
    override suspend fun gruposVivos(): AppResult<List<GrupoParaAvisar>> = dbQuery {
        val hoje = clock.now()
        transaction {
            GroupsTable.selectAll().map { linha ->
                val fuso = runCatching { TimeZone.of(linha[GroupsTable.timezone]) }
                    .getOrDefault(TimeZone.UTC)
                val fim = linha[GroupsTable.endDate]
                GrupoParaAvisar(
                    id = linha[GroupsTable.id],
                    titulo = linha[GroupsTable.title],
                    fuso = fuso,
                    // Encerrado quando o dia do GRUPO já passou do fim. Mesma conta do
                    // `GroupPolicy.estado`, e o laço não pode importá-la sem virar feature→feature.
                    encerrado = AvisosDiarios.diaDoGrupo(hoje, fuso) > fim,
                    criadorId = linha[GroupsTable.createdBy],
                )
            }
        }
    }

    /**
     * Entradas no dia civil do grupo, convertido para o intervalo UTC correspondente.
     *
     * `joined_at` é um instante em UTC; o dia é do calendário do grupo. Comparar
     * `joined_at::date = dia` contaria o dia do SERVIDOR — o mesmo erro que a V38 evita ao
     * persistir `local_date`.
     *
     * ## O CRIADOR não conta, e isso foi achado na bateria
     *
     * A primeira versão contava toda linha de `group_members` com `joined_at` no dia — inclusive a
     * do criador, que nasce junto com o grupo (ele entra como `ADMIN` e ocupa uma vaga das 50).
     * Sintoma observado: criar um desafio e receber, segundos depois, *"1 pessoa entrou no seu
     * desafio hoje"* — sobre si mesmo.
     *
     * > **Criar não é entrar.** A linha do criador é consequência da criação, não uma adesão.
     *
     * O filtro é por `created_by` e **não por papel `ADMIN`**: o cargo é transferível (2.12), e
     * depois de uma transferência o admin da vez seria alguém que de fato entrou — e sumiria da
     * contagem. **Quem fundou o grupo nunca muda.**
     *
     * O criador chega por parâmetro, e não é buscado aqui: o `gruposVivos` já o leu para o mesmo
     * grupo. Buscá-lo de novo seria uma consulta por grupo por ciclo, e o laço acorda de hora em
     * hora.
     */
    override suspend fun entradasNoDia(
        groupId: Uuid,
        dia: LocalDate,
        fuso: TimeZone,
        exceto: Uuid?,
    ): AppResult<Int> = dbQuery {
        val inicio = dia.atStartOfDayIn(fuso).toLocalDateTime(TimeZone.UTC)
        val fim = dia.plus(DatePeriod(days = 1)).atStartOfDayIn(fuso).toLocalDateTime(TimeZone.UTC)
        transaction {
            GroupMembersTable.selectAll().where {
                (GroupMembersTable.groupId eq groupId) and
                    (GroupMembersTable.joinedAt greaterEq inicio) and
                    (GroupMembersTable.joinedAt less fim) and
                    // O `neq` só entra quando há quem excluir: sem a guarda, um grupo sem
                    // `created_by` deixaria de contar TODA entrada em vez de uma a mais.
                    (if (exceto != null) GroupMembersTable.userId neq exceto else Op.TRUE)
            }.count().toInt()
        }
    }

    override suspend fun membros(groupId: Uuid): AppResult<List<Uuid>> = dbQuery {
        transaction {
            GroupMembersTable.selectAll()
                .where { GroupMembersTable.groupId eq groupId }
                .map { it[GroupMembersTable.userId] }
        }
    }

    override suspend fun admins(groupId: Uuid): AppResult<List<Uuid>> = dbQuery {
        transaction {
            GroupMembersTable.selectAll()
                .where {
                    (GroupMembersTable.groupId eq groupId) and
                        (GroupMembersTable.role eq MemberRole.ADMIN.name)
                }
                .map { it[GroupMembersTable.userId] }
        }
    }

    /**
     * Conta CASOS (alvos distintos), não denúncias — a aritmética do badge (6.11).
     *
     * O agrupamento acontece em memória pelo mesmo motivo do `ModeracaoRepositoryImpl`: o alvo mora
     * em duas colunas (arco exclusivo da V45), e um `GROUP BY` teria de coalescê-las. A fila de um
     * grupo tem dezenas de linhas, não milhares.
     */
    override suspend fun filaAberta(groupId: Uuid): AppResult<FilaResumida> = dbQuery {
        transaction {
            val linhas = GroupReportsTable.selectAll()
                .where {
                    (GroupReportsTable.groupId eq groupId) and GroupReportsTable.resolvedAt.isNull()
                }
                .orderBy(GroupReportsTable.createdAt to SortOrder.ASC)
                .map {
                    val alvo = it[GroupReportsTable.checkInId] ?: it[GroupReportsTable.commentId]!!
                    alvo to it[GroupReportsTable.createdAt]
                }

            FilaResumida(
                casos = linhas.map { it.first }.distinct().size,
                // `Instant.DISTANT_FUTURE` quando não há fila: o laço já sai antes pelo `casos == 0`,
                // e um valor impossível é melhor que um `null` que obrigaria todo chamador a tratar.
                maisAntigoEm = linhas.minOfOrNull { it.second.toInstant(TimeZone.UTC) }
                    ?: Instant.DISTANT_FUTURE,
            )
        }
    }

    /**
     * `insertIgnore` e releitura, como o check-in do dia (V38) e a denúncia (V45).
     *
     * Quem decide o empate é a PK `(group_id, day, kind)`. Um `SELECT` antes deixaria duas
     * instâncias do servidor passarem pela checagem e mandarem o aviso duas vezes.
     */
    override suspend fun registrarAviso(
        groupId: Uuid,
        dia: LocalDate,
        tipo: AvisosDiarios.Tipo,
        quando: LocalDateTime,
    ): AppResult<Boolean> = dbQuery {
        transaction {
            val antes = GroupDailyNoticesTable.selectAll().where {
                (GroupDailyNoticesTable.groupId eq groupId) and
                    (GroupDailyNoticesTable.day eq dia) and
                    (GroupDailyNoticesTable.kind eq tipo.name)
            }.empty()

            if (!antes) return@transaction false

            GroupDailyNoticesTable.insertIgnore {
                it[GroupDailyNoticesTable.groupId] = groupId
                it[day] = dia
                it[kind] = tipo.name
                it[sentAt] = quando
            }
            // Relê: se outra instância inseriu no meio, o `insertIgnore` não fez nada e a linha que
            // está lá é dela. Sem esta segunda pergunta, as duas achariam que ganharam.
            !GroupDailyNoticesTable.selectAll().where {
                (GroupDailyNoticesTable.groupId eq groupId) and
                    (GroupDailyNoticesTable.day eq dia) and
                    (GroupDailyNoticesTable.kind eq tipo.name) and
                    (GroupDailyNoticesTable.sentAt eq quando)
            }.empty()
        }
    }

    /**
     * Fecha os casos abertos e grava a auditoria — **uma linha por alvo**, não por denúncia.
     *
     * `admin_id` fica nulo de propósito: não houve decisão de ninguém. É o que permite a quem ler o
     * histórico distinguir "o admin manteve" de "o desafio acabou antes".
     */
    override suspend fun encerrarCasosPendentes(groupId: Uuid, quando: LocalDateTime): AppResult<Int> =
        dbQuery {
            transaction {
                val abertos = GroupReportsTable.selectAll()
                    .where {
                        (GroupReportsTable.groupId eq groupId) and GroupReportsTable.resolvedAt.isNull()
                    }
                    .map {
                        val checkIn = it[GroupReportsTable.checkInId]
                        if (checkIn != null) {
                            checkIn to ReportTarget.CHECK_IN
                        } else {
                            it[GroupReportsTable.commentId]!! to ReportTarget.COMMENT
                        }
                    }
                    .distinct()

                if (abertos.isEmpty()) return@transaction 0

                abertos.forEach { (alvo, tipo) ->
                    ModerationActionsTable.insert {
                        it[id] = Uuid.random()
                        it[ModerationActionsTable.groupId] = groupId
                        it[adminId] = null
                        it[targetType] = tipo.name
                        it[targetId] = alvo
                        it[action] = TipoDeAcao.ENCERRADO_SEM_JULGAMENTO.name
                        it[createdAt] = quando
                    }
                }

                GroupReportsTable.update({
                    (GroupReportsTable.groupId eq groupId) and GroupReportsTable.resolvedAt.isNull()
                }) { it[resolvedAt] = quando }

                abertos.size
            }
        }

    private suspend fun <T> dbQuery(block: () -> T): AppResult<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.fold(
                onSuccess = { it.asSuccess() },
                onFailure = { AppError.Unexpected("Erro de banco", it).asFailure() },
            )
        }
}
