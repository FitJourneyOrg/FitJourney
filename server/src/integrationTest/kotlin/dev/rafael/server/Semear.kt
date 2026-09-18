package dev.rafael.server

import dev.rafael.contract.checkin.CheckInStatus
import dev.rafael.server.features.checkin.db.CheckInsTable
import dev.rafael.server.features.group.db.GroupMembersTable
import dev.rafael.server.features.group.db.GroupsTable
import dev.rafael.server.features.user.db.UsersTable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

/**
 * Dados mínimos para os testes de integração, inseridos **direto na tabela**.
 *
 * ## Por que não passar pelos repositórios
 *
 * O que está sob teste aqui é o **schema** — constraint, índice, cascata. Montar o cenário pelos
 * repositórios faria o teste depender do código que ele deveria poder contradizer: se o repositório
 * tiver um bug que evita violar a constraint, o teste nunca chegaria a exercitá-la.
 *
 * O custo disso já foi cobrado uma vez: a **V40** deixou `users.code` NOT NULL e derrubou **51 de
 * 62** testes de integração, exatamente porque eles inserem sem passar pelo repositório.
 *
 * > **Migration que aperta uma coluna quebra todo INSERT direto que já existia.**
 *
 * Este arquivo existe para que a próxima migration assim quebre em **um** lugar.
 */
object Semear {

    /**
     * Um usuário válido, com o `code` derivado do id (ver [CodigoDeTeste]).
     *
     * ⚠️ **NÃO escreve `locale` de propósito** (V47). Assim esta linha é idêntica à de quem existia
     * antes da migration, e é o `DEFAULT` da coluna que decide o idioma. O
     * `IdiomaIntegrationTest.linha que nao escolheu idioma nasce em portugues` depende disso:
     * acrescentar `locale` aqui faria aquele teste passar sem provar nada.
     */
    fun usuario(nome: String = "Atleta"): Uuid {
        val id = Uuid.random()
        transaction {
            UsersTable.insert {
                it[UsersTable.id] = id
                it[firebaseUid] = "uid-$id"
                it[email] = "$id@teste.local"
                it[displayName] = nome
                it[code] = CodigoDeTeste.de(id)
            }
        }
        return id
    }

    /**
     * Um grupo com o criador dentro como `ADMIN`, como manda a regra de criação.
     *
     * O membro do criador é inserido aqui e não pelo repositório porque **a linha dele faz parte
     * do cenário**: é dela que depende o "criar não é entrar" da V46.
     */
    fun grupo(
        criador: Uuid,
        titulo: String = "Desafio",
        inicio: LocalDate = LocalDate.parse("2026-09-01"),
        fim: LocalDate = LocalDate.parse("2026-12-31"),
        fuso: String = "America/Sao_Paulo",
        entrouEm: LocalDateTime = LocalDateTime.parse("2026-09-01T10:00:00"),
    ): Uuid {
        val id = Uuid.random()
        transaction {
            GroupsTable.insert {
                it[GroupsTable.id] = id
                it[code] = CodigoDeTeste.de(id).take(6)
                it[type] = "DESAFIO"
                it[scoringModel] = "CONTAGEM_CHECKINS"
                it[title] = titulo
                it[description] = null
                it[startDate] = inicio
                it[endDate] = fim
                it[timezone] = fuso
                it[createdBy] = criador
                it[createdAt] = entrouEm
                it[updatedAt] = entrouEm
            }
            GroupMembersTable.insert {
                it[groupId] = id
                it[userId] = criador
                it[role] = "ADMIN"
                it[joinedAt] = entrouEm
            }
        }
        return id
    }

    /** Põe alguém no grupo. [entrouEm] é o que decide "entrou hoje?" na V46. */
    fun membro(grupo: Uuid, usuario: Uuid, entrouEm: LocalDateTime, papel: String = "MEMBRO") {
        transaction {
            GroupMembersTable.insert {
                it[groupId] = grupo
                it[userId] = usuario
                it[role] = papel
                it[joinedAt] = entrouEm
            }
        }
    }

    /** Um check-in válido, sem foto nem local — o mínimo que os `CHECK` da V38 aceitam. */
    fun checkIn(
        grupo: Uuid,
        usuario: Uuid,
        dia: LocalDate = LocalDate.parse("2026-09-10"),
        criadoEm: LocalDateTime = LocalDateTime.parse("2026-09-10T12:00:00"),
        status: CheckInStatus = CheckInStatus.VALIDO,
    ): Uuid {
        val id = Uuid.random()
        transaction {
            CheckInsTable.insert {
                it[CheckInsTable.id] = id
                it[groupId] = grupo
                it[userId] = usuario
                it[localDate] = dia
                it[createdAt] = criadoEm
                it[CheckInsTable.status] = status.name
                it[photoRef] = null
                it[placeName] = null
                it[placeLat] = null
                it[placeLng] = null
                it[emoji] = null
            }
        }
        return id
    }
}
