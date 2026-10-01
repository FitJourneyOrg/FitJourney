package dev.rafael.features.wiki.data

import dev.rafael.contract.i18n.Idioma
import dev.rafael.core.database.SyncStamps
import dev.rafael.core.network.httpResult
import dev.rafael.core.result.AppResult
import dev.rafael.features.wiki.domain.model.WikiArticle
import dev.rafael.features.wiki.domain.repository.WikiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * O acervo do "Aprender" (Fase 8), cache-first.
 *
 * O `idiomaAtual` é a mesma **porta estreita** do `ExerciseRepositoryImpl`: quem sabe o idioma
 * escolhido é o `IdiomaDoAparelho`, que vive no módulo `app` e precisa de `Context`. Este arquivo é
 * `commonMain` e não pode enxergá-lo — [REGRA] a dependência é de sentido único. Uma lambda
 * resolve sem inverter nada, e mantém este repositório testável com `{ Idioma.EN }`.
 */
class WikiRepositoryImpl(
    private val remote: WikiRemoteDataSource,
    private val local: WikiLocalDataSource,
    private val stamps: SyncStamps,
    private val idiomaAtual: () -> Idioma,
) : WikiRepository {

    override fun observeArticles(): Flow<List<WikiArticle>> =
        local.observeAll().map { linhas -> linhas.mapNotNull { it.toDomainOrNull() } }

    override fun observeArticle(slug: String): Flow<WikiArticle?> =
        local.observeBySlug(slug).map { linha -> linha?.toDomainOrNull() }

    /**
     * Baixa o acervo inteiro e substitui o local.
     *
     * TTL de 24h, igual ao catálogo e pela mesma razão: o conteúdo é SEMIESTÁTICO — entra por
     * migration repetível, então só muda em deploy. Acervo vazio ignora o TTL, porque sem dado não
     * há o que preservar.
     *
     * ⚠️ **A chave do carimbo leva o idioma, e mesmo assim ela sozinha não basta** — esta é a
     * lição inteira da fatia H, e ela vale idêntica aqui porque o acervo também guarda **um idioma
     * por vez**:
     *
     * > **Carimbo diz QUANDO foi baixado. Ele não diz O QUE está guardado.**
     *
     * Carimbar por idioma resolve a ida e não a volta: voltando ao português, o carimbo
     * `wiki:pt-BR` ainda está fresco de ontem e a tabela não está vazia — com o acervo em inglês
     * dentro. Por isso [precisaRebaixar] compara também o idioma GUARDADO, que o
     * [WikiLocalDataSource] grava na mesma transação das linhas.
     */
    override suspend fun refresh(forcar: Boolean): AppResult<Unit> {
        val idioma = idiomaAtual()
        // GLOBAL: o acervo é do aparelho, não da conta. O sufixo diz que ele não é igual em todo idioma.
        val chave = "${SyncStamps.WIKI}:${idioma.tag}"

        val rebaixar = precisaRebaixar(
            forcar = forcar,
            carimboFresco = stamps.fresco(chave, TTL_MS, SyncStamps.Escopo.GLOBAL),
            idiomaGuardado = local.idiomaGuardado(),
            idiomaAtual = idioma.tag,
            acervoVazio = local.isEmpty(),
        )
        if (!rebaixar) return AppResult.Success(Unit)

        return httpResult {
            local.replaceAll(remote.getArticles(idioma), idioma.tag)
        }.also { if (it is AppResult.Success) stamps.marcar(chave, SyncStamps.Escopo.GLOBAL) }
    }

    private companion object {
        /** 24h: o conteúdo vem de migration repetível, muda só em deploy. */
        const val TTL_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * A decisão de ir à rede, isolada para poder ser testada sem infraestrutura.
 *
 * Duplica a forma do `precisaRebaixar` do módulo de exercício de propósito: **feature nunca depende
 * de feature** ([REGRA]), e importar uma função `internal` de outro módulo de feature só para não
 * repetir cinco linhas custaria o acoplamento que a regra existe para impedir. É o mesmo julgamento
 * que manteve a extensão `idiomaPedido` copiada entre duas rotas do servidor.
 *
 * As três razões para rebaixar, e o que acontece sem cada uma:
 *
 * | razão | o que acontece sem ela |
 * |---|---|
 * | carimbo vencido | o acervo nunca atualiza depois de um deploy |
 * | acervo vazio | primeira abertura não baixa nada e a tela fica vazia |
 * | **idioma diferente** | a pessoa troca de idioma e o app não percebe, nas duas direções |
 *
 * `idiomaGuardado == null` é aparelho que nunca baixou: a única resposta honesta é rebaixar.
 */
internal fun precisaRebaixar(
    forcar: Boolean,
    carimboFresco: Boolean,
    idiomaGuardado: String?,
    idiomaAtual: String,
    acervoVazio: Boolean,
): Boolean =
    forcar || !carimboFresco || acervoVazio || idiomaGuardado != idiomaAtual
