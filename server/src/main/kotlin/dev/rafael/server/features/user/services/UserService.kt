package dev.rafael.server.features.user.services

import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.core.result.asFailure
import dev.rafael.core.result.asSuccess
import dev.rafael.core.result.flatMap
import dev.rafael.contract.i18n.IdiomaPolicy
import dev.rafael.server.features.user.db.UserRepository
import dev.rafael.server.features.user.models.User
import kotlin.uuid.Uuid
import dev.rafael.contract.error.ErrorCodes

class UserService(private val repository: UserRepository) {

    /**
     * Garante o usuário no Postgres a partir da identidade do Firebase.
     * 1o acesso: cria. Demais: retorna o existente. (find-or-create)
     */
    suspend fun findOrCreate(firebaseUid: String, email: String?): AppResult<User> {
        return when (val found = repository.findByFirebaseUid(firebaseUid)) {
            is AppResult.Failure -> found
            is AppResult.Success -> {
                val existing = found.value
                if (existing != null) {
                    AppResult.Success(existing)
                } else {
                    // Não existe -> cria. Corrida (2 requests do mesmo uid novo): o UNIQUE
                    // em firebase_uid faz o 2o insert falhar -> cai em Unexpected. Tratamento
                    // robusto (reler no conflito) fica como refino se a corrida aparecer.
                    //
                    // O NOME nasce aqui, não no onboarding (V35, #33). Este método roda no
                    // `GET /me` do splash, ANTES do quiz: se o nome esperasse o fim do
                    // onboarding, haveria uma janela com a linha criada e a coluna NOT NULL
                    // sem valor. O onboarding confirma/edita; ninguém fica sem nome.
                    //
                    // O CÓDIGO também nasce aqui (V40, #35), pelo mesmo motivo do nome: a
                    // coluna é NOT NULL, e gerá-lo "quando alguém precisar" exigiria que ela
                    // fosse nullable — o que a A.0 já pagou para evitar.
                    val id = Uuid.random()
                    repository.create(
                        id = id,
                        firebaseUid = firebaseUid,
                        email = email,
                        displayName = DisplayNamePolicy.inicial(email, id),
                        code = UserCodePolicy.gerar(),
                    )
                }
            }
        }
    }

    /**
     * Por id interno. `null` = não existe.
     *
     * Existe para quem chega a uma PESSOA e não à própria conta: o `NotificacaoService` precisa do
     * idioma do destinatário, e ele só tem o `Uuid`. Passar o `UserRepository` direto para lá
     * resolveria igual e daria ao serviço de notificação acesso de escrita a `users`, que ele não
     * tem nenhum motivo para ter.
     */
    suspend fun porId(userId: Uuid): AppResult<User?> = repository.findById(userId)

    /**
     * O `PATCH /me` inteiro (G.1, ARCH #37). `null` em um campo significa **não mexer nele**.
     *
     * ## [INV] As duas validações acontecem ANTES de qualquer escrita
     *
     * Não é só a regra de sempre (*validação primeiro, recusa não deve custar consulta*). Com dois
     * campos aparece um caso que com um só não existia: **nome válido e idioma inválido**.
     *
     * Validando na hora de escrever, o nome já teria sido gravado quando o idioma fosse recusado, e
     * o `PATCH` devolveria erro tendo mudado metade da coisa. A pessoa veria a mensagem de falha,
     * tentaria de novo, e encontraria o nome já alterado sem entender por quê.
     *
     * > **Requisição que falha não pode ter mudado metade.** Ou vale inteira, ou não vale.
     *
     * Uma transação resolveria igual e seria mais caro: validar antes não precisa do banco.
     *
     * ## Tag de idioma não suportada é RECUSA, não fallback
     *
     * Quem pede `es` e recebe `200 OK` acredita que escolheu espanhol, e vai atribuir a falta de
     * tradução a um defeito do app em vez de saber que o idioma não existe.
     *
     * É a diferença entre `IdiomaPolicy.valida`, usada aqui, e `IdiomaPolicy.de`, usada ao ler a
     * coluna: **entrada de usuário recusa, entrada de sistema tolera.**
     */
    suspend fun atualizarMe(
        firebaseUid: String,
        email: String?,
        displayName: String?,
        locale: String?,
    ): AppResult<User> {
        val nome = if (displayName == null) null else {
            when (val r = DisplayNamePolicy.normalizar(displayName)) {
                is AppResult.Failure -> return r
                is AppResult.Success -> r.value
            }
        }

        val idioma = if (locale == null) null else {
            IdiomaPolicy.valida(locale) ?: return AppError.Validation(
                "Idioma não suportado.",
                mapOf("locale" to "Idioma não suportado."),
                code = ErrorCodes.IDIOMA_NAO_SUPORTADO,
            ).asFailure()
        }

        return findOrCreate(firebaseUid, email).flatMap { user ->
            var atual: AppResult<User> = user.asSuccess()
            if (nome != null) {
                atual = repository.updateDisplayName(user.id, nome).flatMap { naoEncontrado(it) }
            }
            if (idioma != null && atual is AppResult.Success) {
                atual = repository.updateIdioma(user.id, idioma).flatMap { naoEncontrado(it) }
            }
            atual
        }
    }

    /**
     * A conta de **quem está pedindo** sumiu, logo depois de o servidor tê-la lido.
     *
     * ## Desmembrado de "Usuário não encontrado" na G.2
     *
     * A mesma frase servia a dois lugares opostos: aqui, e no `FriendshipService`, onde ela quer
     * dizer *"a pessoa que você procurou não existe"*. Dizer que o usuário não foi encontrado para
     * alguém que está logada é o app negando a existência de quem está olhando.
     *
     * Na prática isto é **estado impossível**: o `findOrCreate` acabou de devolver a linha, e o
     * `UPDATE` seguinte não a achou. O código `MINHA_CONTA_SUMIU` deixa o cliente tratar como
     * falha nossa em vez de mandar a pessoa procurar o que fez de errado.
     */
    private fun naoEncontrado(u: User?): AppResult<User> =
        u?.asSuccess() ?: AppError.NotFound(
            "Não consegui carregar sua conta agora. Tente de novo em instantes.",
            code = ErrorCodes.MINHA_CONTA_SUMIU,
        ).asFailure()

    /**
     * Gera um código novo e mata o anterior (35.5).
     *
     * **Só uma tentativa de colisão, e ela vira erro.** Com 32⁸ ≈ 1 trilhão de códigos, colidir
     * é evento de loteria; um laço de retry aqui seria código que nunca roda e por isso nunca é
     * testado. Melhor falhar alto e a pessoa tocar de novo — o botão está na frente dela.
     */
    suspend fun regenerarCodigo(firebaseUid: String, email: String?): AppResult<User> =
        findOrCreate(firebaseUid, email).flatMap { user ->
            repository.updateCode(user.id, UserCodePolicy.gerar()).flatMap { atualizado ->
                atualizado?.asSuccess() ?: naoEncontrado(null)
            }
        }

    /**
     * Ativa o premium do usuário (compra simulada — Fase 7 dev). O passo de compra REAL
     * (Play Billing + verificação) fica no cliente atrás da interface Billing; aqui só liga
     * o flag. Idempotente. Depois, a verificação server-side de recibo entra na frente disto.
     */
    suspend fun activatePremium(firebaseUid: String, email: String?): AppResult<User> =
        findOrCreate(firebaseUid, email).flatMap { user ->
            repository.setPremium(user.id, true).flatMap { updated ->
                updated?.asSuccess() ?: naoEncontrado(null)
            }
        }

    /**
     * Renomeia o usuário. Onboarding e tela de perfil usam o MESMO caminho.
     *
     * A validação vem PRIMEIRO, antes de tocar no banco: nome inválido é recusa, e recusa não
     * deve custar uma consulta. [REGRA] quem decide validade é o servidor, não a UI.
     *
     * Continua existindo depois do [atualizarMe] porque o onboarding chama exatamente isto e nada
     * mais. Fazê-lo passar por um método de dois campos anuláveis para mexer num só transformaria
     * uma chamada clara numa com metade dos argumentos em `null`.
     */
    suspend fun updateDisplayName(
        firebaseUid: String,
        email: String?,
        displayName: String,
    ): AppResult<User> = atualizarMe(firebaseUid, email, displayName, locale = null)
}