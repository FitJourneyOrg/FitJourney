package dev.rafael.app.di

import android.content.Context
import dev.rafael.app.data.sessao.ReagirASessao
import dev.rafael.app.data.sessao.SairDaConta
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Um só módulo raiz que INCLUI os 13 -- é o que o verify() sabe atravessar cross-módulo. */
private val grafoCompleto = module { includes(todosOsModulosDoApp) }

/**
 * Fecha o débito P2 "grafo do Koin sem verificação automática" (já causou 1 crash de boot real:
 * `Clock` no `HomeViewModel` — `viewModelOf` resolve TODO parâmetro de construtor por reflexão e
 * IGNORA valor default do Kotlin, ver KDoc do `single<Clock>` em `AppModule.kt`).
 *
 * `verify()` (koin-test) é reflexão pura: olha o construtor de cada classe registrada e confere
 * que todo parâmetro tem binding em algum módulo da lista — SEM instanciar nada de verdade. JVM
 * comum, sem emulador, sem Android real rodando. É exatamente a classe de defeito do `Clock`:
 * parâmetro de construtor sem binding, na cara.
 *
 * `todosOsModulosDoApp` é a MESMA lista que o `FitJourneyApp.onCreate()` passa pro `startKoin` —
 * um módulo novo lá entra aqui sozinho (ver KDoc dele).
 *
 * `extraTypes`:
 * - `Context::class`: os únicos `single { }` cujo construtor pede `Context` direto (via
 *   `androidContext()`) são `RegistroDePush`, `Localizador` e `SyncScheduler`, em `AppModule.kt`
 *   — o Koin android injeta isso por fora do grafo normal, e o `verify()` não vê sozinho.
 * - `Function1::class`: `SyncStampsImpl` e `Outbox` recebem `uidAtual: suspend () -> String?` no
 *   construtor, montado à mão em `AppModule.kt` (`uidAtual = { get<TokenProvider>().currentUid() }`)
 *   — não é `get()` direto, é uma lambda que só por dentro chama `get<TokenProvider>()`. O
 *   `verify()` não executa a lambda pra ver isso: só reflete sobre o TIPO do parâmetro
 *   (`Function1`) e procura um binding desse tipo puro no grafo. Não existe, porque uma função
 *   nunca é registrada como tipo — é montada inline. Falso positivo confirmado em build real
 *   (`MissingKoinDefinitionException` em `SyncStamps` antes desta lista existir).
 * - As 4 PORTAS ESTREITAS (`fun interface` de um verbo): `SairDaConta.BaixaDePush` e as três de
 *   `ReagirASessao` (`RegistrarAparelho`, `AtualizarContador`, `ReconciliarIdioma`). Todas são
 *   adaptadores montados à mão no `single { }` do `AppModule.kt`, e isso é o PADRÃO do projeto,
 *   não descuido: `RegistroDePush`/`ReconciliarIdiomaDaConta` carregam `Context`, e código de
 *   sessão não pode depender de `Context`/Firebase — então a raiz de composição adapta o objeto
 *   pesado num verbo. Registrá-las como `single` seria o contrário do que elas existem pra fazer:
 *   exporia a porta de adaptação no grafo pra qualquer um injetar. Lista completa e fechada —
 *   é o conjunto de TODAS as `fun interface` aninhadas do cliente (conferido no projeto inteiro);
 *   só cresce se alguém criar uma porta nova, que é ato deliberado.
 *
 * Sem esses, o `verify()` acusaria falta de binding onde não falta — são todos parâmetros
 * montados à mão de propósito na raiz de composição, nunca resolvidos por `get()` do tipo
 * declarado.
 *
 * NÃO entra aqui: `List`. O `verify()` DESEMBRULHA `List<T>`/`Lazy<T>` e cobra binding do `T`
 * interno — `List::class` em `extraTypes` não silencia nada (testado: erro idêntico). Quando ele
 * acusa um `List<X>`, é binding de `X` que está faltando de verdade. Foi assim que apareceram os
 * `bind ExecutorDeOperacao::class` no `AppModule.kt`.
 */
class KoinModulesVerifyTest {

    @Test
    fun `grafo do Koin resolve sem parametro de construtor sem binding`() {
        // verify() só existe em cima de UM Module (não de List<Module>) -- includes() é o jeito
        // de compor os 13 num só, e é isso que faz o verify() enxergar binding cross-módulo sem
        // precisar listar dependência de outro módulo em extraTypes.
        grafoCompleto.verify(
            extraTypes = listOf(
                Context::class,
                Function1::class,
                // Portas estreitas: adaptadores montados à mão na raiz de composição (ver KDoc).
                SairDaConta.BaixaDePush::class,
                ReagirASessao.RegistrarAparelho::class,
                ReagirASessao.AtualizarContador::class,
                ReagirASessao.ReconciliarIdioma::class,
            ),
        )
    }

    /**
     * O caminho de falha (C2): prova que o `verify()` REALMENTE acusa um parâmetro sem binding,
     * em vez de só passar sempre e dar falsa confiança. Se esta suíte um dia deixar de pegar isto,
     * o teste de cima parou de significar alguma coisa.
     */
    @Test
    fun `verify acusa parametro sem binding, nao e um teste que so passa`() {
        val moduloQuebrado = module { single { ClasseComDependenciaFaltando(get()) } }

        assertFailsWith<Exception> { moduloQuebrado.verify() }
    }

    private class ClasseComDependenciaFaltando(val dependencia: TipoNuncaRegistrado)
    private class TipoNuncaRegistrado
}
