package dev.rafael.server.features.group.db

import dev.rafael.core.result.AppResult
import dev.rafael.server.features.group.models.Group
import dev.rafael.server.features.group.services.GroupPolicy
import kotlin.uuid.Uuid

/** Acesso a dados de grupo (ARCH #33, fatia A.1). */
interface GroupRepository {

    /**
     * Cria o grupo E o vínculo do criador como `ADMIN`, na MESMA transação.
     *
     * Os dois juntos porque grupo sem admin não pode existir nem por um instante: se o insert
     * do membro falhasse depois, sobraria um grupo órfão que ninguém administra e que a regra
     * do admin fantasma (2.12) trataria como "conta deletada" — um estado inventado por bug.
     *
     * O CÓDIGO é sorteado aqui dentro, com nova tentativa em colisão: [GroupPolicy.gerarCodigo]
     * só sorteia, e quem garante unicidade é o `UNIQUE` do banco.
     */
    suspend fun create(grupo: NovoGrupo): AppResult<Group>

    /** Grupos de que o usuário participa. Ordem: mais recentes primeiro. */
    suspend fun listByMember(userId: Uuid): AppResult<List<Group>>

    /** Um grupo por id, ou null. Não filtra por membro — quem decide isso é o service. */
    suspend fun findById(groupId: Uuid): AppResult<Group?>

    /** Papel do usuário no grupo, ou null se não é membro. */
    suspend fun roleOf(groupId: Uuid, userId: Uuid): AppResult<String?>
}
