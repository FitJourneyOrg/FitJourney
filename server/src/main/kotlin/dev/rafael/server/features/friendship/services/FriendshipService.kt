package dev.rafael.server.features.friendship.services

import dev.rafael.contract.friendship.FriendStatus
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.core.result.map
import dev.rafael.server.features.friendship.db.FriendshipRepository
import dev.rafael.server.features.friendship.models.PedidoRecebido
import dev.rafael.server.features.friendship.models.Pessoa
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.services.UserService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Amizades e bloqueios (ARCH #35).
 *
 * ## O vínculo é entre duas pessoas, e o servidor é o único que sabe disso
 *
 * Toda operação aqui recebe o `firebaseUid` de quem age e resolve o id interno — nenhuma rota
 * aceita "em nome de quem" pelo corpo. É o mesmo princípio do check-in: quem faz é quem está
 * autenticado, e não quem o cliente diz que é.
 *
 * ## Onde cada regra mora
 *
 * A decisão está no [FriendshipPolicy], puro e testável sem banco. Aqui fica só a ORDEM em que
 * as perguntas são feitas ao repositório — e essa ordem importa, porque cada consulta a mais é
 * uma ida ao banco, e algumas revelam informação se feitas cedo demais.
 */
class FriendshipService(
    private val userService: UserService,
    private val users: UserRepository,
    private val repository: FriendshipRepository,
    private val clock: Clock = Clock.System,
) {

    private fun agora() = clock.now().toLocalDateTime(TimeZone.UTC)

    /**
     * Pede amizade.
     *
     * A ordem das verificações é deliberada: **bloqueio antes de tudo**. Se eu checasse "já são
     * amigos" primeiro, a resposta diferente entre os casos deixaria alguém deduzir o estado da
     * relação com quem o bloqueou.
     *
     * **Pedir a quem já me pediu vira ACEITE** (decisão de 2026-08-27). Os dois manifestaram a
     * mesma intenção; exigir que um deles refaça o gesto com outro nome seria burocracia. Também
     * é o que resolve a corrida de dois toques simultâneos sem erro nenhum.
     *
     * O desvio acontece ANTES do teto porque o `aceitar` verifica o teto por conta própria — e
     * ele precisa mesmo, senão daria para furar o limite de 500 pedindo em vez de aceitando.
     */
    suspend fun pedir(quemPede: String, email: String?, alvoId: String): AppResult<Unit> =
        comAmbos(quemPede, email, alvoId) { eu, alvo ->
            repository.haBloqueioEntre(eu, alvo).flatMap { bloqueio ->
                repository.entre(eu, alvo).flatMap { existente ->
                    // O outro já me pediu: isto é um ACEITE disfarçado de pedido.
                    if (existente != null &&
                        existente.status == FriendshipPolicy.Estado.PENDENTE &&
                        !existente.mandeiEu(eu)
                    ) {
                        return@flatMap repository
                            .contarAmizades(eu)
                            .flatMap { minhas ->
                                if (minhas >= FriendshipPolicy.TETO_DE_AMIZADES) {
                                    AppError.Conflict(
                                        FriendshipPolicy.Impedimento.TETO_ATINGIDO.frase(),
                                        FriendshipPolicy.Impedimento.TETO_ATINGIDO.name,
                                    ).asFailure()
                                } else {
                                    repository
                                        .responder(eu, alvo, FriendshipPolicy.Estado.ACEITA, agora())
                                        .map { }
                                }
                            }
                    }

                    repository.contarAmizades(eu).flatMap { minhasAmizades ->
                        val impedimento = FriendshipPolicy.impedimentoParaPedir(
                            quemPede = eu,
                            alvo = alvo,
                            existente = existente?.status,
                            euPedi = existente?.mandeiEu(eu) == true,
                            haBloqueio = bloqueio,
                            amizadesDeQuemPede = minhasAmizades,
                        )
                        if (impedimento != null) {
                            return@flatMap AppError.Conflict(impedimento.frase(), impedimento.name).asFailure()
                        }

                        // Pedido RECUSADO antes: apago a linha para o `insertIgnore` poder criar
                        // a nova. Recusar não é banir (ver FriendshipPolicy), então a pessoa pode
                        // pedir de novo — e a linha velha não pode ficar no caminho.
                        val limpar =
                            if (existente != null) repository.apagar(eu, alvo) else Unit.asSuccess()

                        limpar.flatMap {
                            repository.pedir(eu, alvo, agora()).flatMap { criou ->
                                if (criou) Unit.asSuccess()
                                // Perdeu a corrida para o insert do outro lado — os dois se
                                // adicionaram no mesmo instante. Não é erro do usuário: o par
                                // existe e é isso que ele queria.
                                else Unit.asSuccess()
                            }
                        }
                    }
                }
            }
        }

    /**
     * Aceita um pedido recebido.
     *
     * **O teto de 500 é verificado AQUI**, e do lado de quem aceita. No pedido eu só olho o teto
     * de quem pede: barrar porque o ALVO está cheio revelaria quantos amigos ele tem, e deixaria
     * o pedido pendente esperando alguém sair da lista de outra pessoa.
     */
    suspend fun aceitar(quemAge: String, email: String?, outroId: String): AppResult<Unit> =
        responder(quemAge, email, outroId, FriendshipPolicy.Estado.ACEITA)

    /** Recusa. A linha fica como RECUSADA — não some, para o pedido não reaparecer sozinho. */
    suspend fun recusar(quemAge: String, email: String?, outroId: String): AppResult<Unit> =
        responder(quemAge, email, outroId, FriendshipPolicy.Estado.RECUSADA)

    private suspend fun responder(
        quemAge: String,
        email: String?,
        outroId: String,
        novo: FriendshipPolicy.Estado,
    ): AppResult<Unit> = comAmbos(quemAge, email, outroId) { eu, outro ->
        repository.entre(eu, outro).flatMap { amizade ->
            if (amizade == null || !FriendshipPolicy.podeResponder(eu, amizade.requestedBy, amizade.status)) {
                return@flatMap AppError.NotFound("Pedido não encontrado").asFailure()
            }

            val checarTeto =
                if (novo == FriendshipPolicy.Estado.ACEITA) {
                    repository.contarAmizades(eu).flatMap { minhas ->
                        if (minhas >= FriendshipPolicy.TETO_DE_AMIZADES) {
                            AppError.Conflict(
                                FriendshipPolicy.Impedimento.TETO_ATINGIDO.frase(),
                                FriendshipPolicy.Impedimento.TETO_ATINGIDO.name,
                            ).asFailure()
                        } else {
                            Unit.asSuccess()
                        }
                    }
                } else {
                    Unit.asSuccess()
                }

            checarTeto.flatMap {
                repository.responder(eu, outro, novo, agora()).flatMap { mudou ->
                    // `false` = alguém respondeu entre a leitura e o update. Devolver 404 e não
                    // 500: do ponto de vista de quem tocou, o pedido não está mais lá.
                    if (mudou) Unit.asSuccess()
                    else AppError.NotFound("Pedido não encontrado").asFailure()
                }
            }
        }
    }

    /**
     * Some com a relação — **cancela o pedido que eu mandei OU desfaz a amizade**.
     *
     * Um método, e não dois, porque do ponto de vista de quem toca é o mesmo gesto: "não quero
     * mais essa relação". Quem decide qual dos dois cabe é o ESTADO, aqui, e não a tela.
     *
     * A alternativa que eu escrevi primeiro era a rota chamar `cancelar` e, se falhasse, chamar
     * `desfazer`. Isso trata a falha do primeiro como se fosse informação — e ela também pode ser
     * uma falha de banco, que assim viraria silenciosamente "tenta o outro caminho". **Erro não é
     * canal de decisão.**
     */
    suspend fun remover(quemAge: String, email: String?, outroId: String): AppResult<Unit> =
        comAmbos(quemAge, email, outroId) { eu, outro ->
            repository.entre(eu, outro).flatMap { amizade ->
                val pode = amizade != null && (
                    FriendshipPolicy.podeCancelar(eu, amizade.requestedBy, amizade.status) ||
                        FriendshipPolicy.podeDesfazer(amizade.status)
                    )
                if (!pode) AppError.NotFound("Relação não encontrada").asFailure()
                else repository.apagar(eu, outro).map { }
            }
        }

    /**
     * Bloqueia. **Idempotente**: bloquear duas vezes não é erro.
     *
     * O pedido pendente SOME junto (decidido em 2026-08-27), na mesma transação. Deixá-lo como
     * recusado registraria que houve pedido, e quem bloqueia geralmente quer que aquilo não tenha
     * acontecido.
     */
    suspend fun bloquear(quemAge: String, email: String?, alvoId: String): AppResult<Unit> =
        comAmbos(quemAge, email, alvoId) { eu, alvo ->
            if (eu == alvo) {
                AppError.Validation("Você não pode bloquear a si mesmo").asFailure()
            } else {
                repository.bloquear(eu, alvo, agora())
            }
        }

    /** Desbloqueia. **Não** restaura a amizade — refazer é ato deliberado dos dois. */
    suspend fun desbloquear(quemAge: String, email: String?, alvoId: String): AppResult<Unit> =
        comAmbos(quemAge, email, alvoId) { eu, alvo ->
            repository.desbloquear(eu, alvo).map { }
        }

    /**
     * A relação entre duas pessoas, do ponto de vista de quem PERGUNTA — é o que o perfil público
     * consome pela porta `PublicProfileService.RelacaoCom`.
     *
     * Devolve o status E o bloqueio numa chamada porque são duas perguntas sobre o mesmo par, e
     * respondê-las separado abriria a janela em que uma diz "amigos" e a outra "bloqueado" —
     * estados que não podem coexistir, já que bloquear apaga a amizade na mesma transação.
     *
     * Falha do banco vira `NENHUMA` sem bloqueio? **Não.** O erro sobe: um perfil que mostrasse
     * "Adicionar" por causa de uma consulta que falhou levaria a pessoa a mandar pedido a quem a
     * bloqueou, e o servidor recusaria com uma frase que ela não entenderia.
     */
    suspend fun relacaoEntre(dono: Uuid, quemPergunta: Uuid): AppResult<Relacao> {
        if (dono == quemPergunta) return Relacao(FriendStatus.NENHUMA, meBloqueou = false).asSuccess()

        return repository.bloqueouMe(dono, quemPergunta).flatMap { meBloqueou ->
            repository.bloqueouMe(quemPergunta, dono).flatMap { euBloqueei ->
                if (euBloqueei) {
                    // Meu bloqueio VENCE a amizade na apresentação — mas ele já a apagou no
                    // banco, então não há o que competir. Este ramo existe para o caso de o
                    // bloqueio ter sido feito e a amizade nunca ter existido.
                    return@flatMap Relacao(FriendStatus.BLOQUEADO_POR_MIM, meBloqueou).asSuccess()
                }
                repository.entre(dono, quemPergunta).map { amizade ->
                    val status = when {
                        amizade == null -> FriendStatus.NENHUMA
                        amizade.status == FriendshipPolicy.Estado.ACEITA -> FriendStatus.AMIGOS
                        amizade.status == FriendshipPolicy.Estado.PENDENTE ->
                            if (amizade.mandeiEu(quemPergunta)) FriendStatus.PEDIDO_ENVIADO
                            else FriendStatus.PEDIDO_RECEBIDO
                        // RECUSADA não tem valor no enum de propósito: para quem olha o perfil,
                        // recusado e nunca-pedido oferecem a MESMA ação (Adicionar). Um estado a
                        // mais só serviria para a tela contar que houve uma recusa.
                        else -> FriendStatus.NENHUMA
                    }
                    Relacao(status, meBloqueou)
                }
            }
        }
    }

    /** O que a porta do perfil público devolve. Espelha `PublicProfileService.Relacao`. */
    data class Relacao(val status: FriendStatus, val meBloqueou: Boolean)

    suspend fun meusAmigos(quemPede: String, email: String?): AppResult<List<Pessoa>> =
        userService.findOrCreate(quemPede, email).flatMap { eu -> repository.amigos(eu.id) }

    suspend fun meusPedidos(quemPede: String, email: String?): AppResult<List<PedidoRecebido>> =
        userService.findOrCreate(quemPede, email).flatMap { eu -> repository.pedidosRecebidos(eu.id) }

    suspend fun meusBloqueados(quemPede: String, email: String?): AppResult<List<Pessoa>> =
        userService.findOrCreate(quemPede, email).flatMap { eu -> repository.bloqueados(eu.id) }

    /**
     * Resolve quem age e quem é o alvo, nesta ordem.
     *
     * O alvo malformado ou inexistente devolve **404**, igual ao perfil público (C.1): distinguir
     * os dois casos deixaria sondar quais ids existem.
     */
    private suspend fun comAmbos(
        firebaseUid: String,
        email: String?,
        alvoId: String,
        bloco: suspend (eu: Uuid, alvo: Uuid) -> AppResult<Unit>,
    ): AppResult<Unit> = userService.findOrCreate(firebaseUid, email).flatMap { eu ->
        val alvo = runCatching { Uuid.parse(alvoId) }.getOrNull()
            ?: return@flatMap AppError.NotFound("Usuário não encontrado").asFailure()

        users.findById(alvo).flatMap { pessoa ->
            if (pessoa == null) AppError.NotFound("Usuário não encontrado").asFailure()
            else bloco(eu.id, alvo)
        }
    }
}
