package dev.rafael.features.exercise.data

import dev.rafael.contract.exercise.ExerciseCategory
import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppError
import dev.rafael.core.result.AppResult
import dev.rafael.features.exercise.domain.model.Exercise
import dev.rafael.features.exercise.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * ## O `idiomaAtual` é uma porta estreita, não uma dependência de plataforma
 *
 * Quem sabe o idioma escolhido é o `IdiomaDoAparelho`, que vive no módulo `app` e precisa de
 * `Context`. Este arquivo é `commonMain` e não pode enxergá-lo — nem deveria: [REGRA] a dependência
 * é de sentido único, e feature nunca depende de app.
 *
 * Uma lambda resolve sem inverter nada: quem monta o grafo (Koin, no `androidApp`) fornece a
 * função, e este repositório continua Kotlin puro e testável com `{ Idioma.EN }`. Mesmo padrão do
 * `uidAtual` do [SyncStamps].
 */
class ExerciseRepositoryImpl(
    private val remote: ExerciseRemoteDataSource,
    private val local: ExerciseLocalDataSource,
    private val stamps: SyncStamps,
    private val idiomaAtual: () -> Idioma,
) : ExerciseRepository {

    override fun observeExercises(category: ExerciseCategory?): Flow<List<Exercise>> {
        val rows = if (category == null) local.observeAll()
        else local.observeByCategory(category.name)
        return rows.map { list -> list.mapNotNull { it.toDomainOrNull() } }
    }

    /**
     * Baixa o catálogo inteiro e substitui o local.
     *
     * TTL de 24h (não 5 min como o resto): o catálogo é SEMIESTÁTICO — vem de migration no
     * servidor, então só muda em deploy. Antes não havia TTL e a lista chamava `refresh()` no
     * `init`, ou seja, 965 exercícios baixados a cada entrada na aba Exercícios, mais uma vez
     * no boot pela Splash.
     *
     * O carimbo é PERSISTIDO ([SyncStamps]): quando morava em memória, essas 24h expiravam ao
     * fechar o app e todo cold start rebaixava o catálogo inteiro.
     *
     * Catálogo local vazio ignora o TTL: sem dado não há o que preservar.
     *
     * ## ⭐ O CACHE PASSA A CONHECER IDIOMA, e sem isso nada da fatia H funciona
     *
     * Até 2026-09-15 a condição era só carimbo fresco mais tabela não vazia, e o escopo `GLOBAL`
     * não conhece idioma. Como o `replaceAll` guarda **um idioma por vez**, trocar para inglês
     * **não invalidava nada**: a pessoa continuava lendo "Supino reto" por até 24h, sem erro e sem
     * log.
     *
     * > **Cache de texto traduzido cuja chave ignora o idioma serve a resposta do idioma anterior,
     * > e serve calado.**
     *
     * ## Duas perguntas, e a primeira versão desta correção só respondia uma
     *
     * Carimbar por idioma (`exercises:en`) resolve a ida e **não resolve a volta**. Voltando ao
     * português: o carimbo `exercises:pt-BR` ainda está fresco dos dias anteriores, a tabela não
     * está vazia — e o app pularia a rede com 963 linhas em inglês dentro. O mesmo defeito, agora
     * só no caminho de volta, que é onde ninguém testa.
     *
     * > **Carimbo diz QUANDO foi baixado. Ele não diz O QUE está guardado.**
     *
     * Por isso são duas condições, e a segunda é a que fecha: o [ExerciseLocalDataSource] grava o
     * idioma **na mesma transação das linhas**, e aqui a gente compara. Uma responde "está velho?",
     * a outra "é o idioma certo?".
     *
     * O custo aceito é rebaixar 963 linhas em toda troca de idioma, nas duas direções. A
     * alternativa seria guardar um catálogo por idioma no SQLDelight — mais schema, mais espaço, e
     * resolveria um caso que acontece raríssimas vezes na vida de um usuário.
     *
     * @param forcar pull-to-refresh do usuário — ele pediu, então vai.
     */
    override suspend fun refresh(forcar: Boolean): AppResult<Unit> {
        val idioma = idiomaAtual()
        // GLOBAL: o catálogo é do aparelho, não da conta — não entra no escopo do uid. Mas ele NÃO
        // é igual em todo idioma, e é isso que o sufixo da chave diz.
        val chave = "${SyncStamps.CATALOGO}:${idioma.tag}"

        val rebaixar = precisaRebaixar(
            forcar = forcar,
            carimboFresco = stamps.fresco(chave, TTL_MS, SyncStamps.Escopo.GLOBAL),
            idiomaGuardado = local.idiomaGuardado(),
            idiomaAtual = idioma.tag,
            catalogoVazio = local.isEmpty(),
        )
        if (!rebaixar) return AppResult.Success(Unit)

        return httpResult {
            local.replaceAll(remote.getExercises(category = null, idioma = idioma), idioma.tag)
        }.also { if (it is AppResult.Success) stamps.marcar(chave, SyncStamps.Escopo.GLOBAL) }
    }

    override suspend fun alternatives(exerciseId: String): AppResult<List<Exercise>> =
        httpResult { remote.getAlternatives(exerciseId, idiomaAtual()).map { it.toDomain() } }

    /**
     * CACHE-FIRST ([REGRA] ARCH #30). Antes isto baixava o CATÁLOGO INTEIRO (965 exercícios)
     * para filtrar um id em memória — a cada abertura de tela de detalhe, e sem nem olhar o
     * banco local, que já tinha o mesmo dado desde o boot.
     *
     * Só vai à rede se o exercício não estiver local (catálogo desatualizado após um deploy
     * que adicionou exercícios novos).
     */
    override suspend fun getDetail(exerciseId: String): AppResult<Exercise> {
        local.readById(exerciseId)?.toDomainOrNull()?.let { return AppResult.Success(it) }

        return when (val r = refresh(forcar = true)) {
            is AppResult.Success ->
                local.readById(exerciseId)?.toDomainOrNull()
                    ?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(AppError.NotFound("Exercício não encontrado"))
            is AppResult.Failure -> AppResult.Failure(r.error)
        }
    }

    private companion object {
        /** 24h: catálogo vem de migration, muda só em deploy. */
        const val TTL_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * ⭐ **A decisão de ir à rede, isolada do banco para poder ser testada** (fatia H).
 *
 * Os três colaboradores do repositório — `SyncStamps`, `ExerciseLocalDataSource` e o HTTP — todos
 * exigem infraestrutura. A regra em si não exige nada, e é ela que erra: o defeito que esta função
 * existe para fixar não é de I/O, é de **condição incompleta**.
 *
 * ## As três razões para rebaixar, e por que nenhuma é dispensável
 *
 * | razão | o que acontece sem ela |
 * |---|---|
 * | carimbo vencido | o catálogo nunca atualiza depois de um deploy |
 * | catálogo vazio | primeira abertura não baixa nada e a tela fica vazia |
 * | **idioma diferente** | a pessoa troca de idioma e o app não percebe |
 *
 * A terceira é a fatia H, e ela tem uma metade que passa despercebida: carimbar por idioma resolve
 * a IDA e não a VOLTA. Voltando para o português, o carimbo `exercises:pt-BR` ainda está fresco de
 * ontem e a tabela não está vazia — só que o que está nela é inglês. Por isso a condição compara o
 * idioma GUARDADO, e não só o carimbo.
 *
 * > **Carimbo diz quando foi baixado. Ele não diz o que está guardado.**
 *
 * `idiomaGuardado == null` é o app vindo de uma versão anterior a esta fatia: ninguém registrou
 * idioma, então a única resposta honesta é rebaixar.
 */
internal fun precisaRebaixar(
    forcar: Boolean,
    carimboFresco: Boolean,
    idiomaGuardado: String?,
    idiomaAtual: String,
    catalogoVazio: Boolean,
): Boolean =
    forcar || !carimboFresco || catalogoVazio || idiomaGuardado != idiomaAtual