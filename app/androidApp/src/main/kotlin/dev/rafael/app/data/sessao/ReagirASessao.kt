package dev.rafael.app.data.sessao

import dev.rafael.core.network.TokenProvider
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/**
 * O que acontece TODA VEZ que uma sessão começa (boot com sessão, login, troca de conta).
 *
 * ## Por que isto saiu do `AppNavHost`
 *
 * A regra morava dentro de um `@Composable`, e por isso não tinha teste: exercitá-la exigiria
 * compor a árvore inteira do Compose, com Splash, Koin e navegação. **Regra escondida dentro de UI
 * é regra sem teste** — e foi exatamente ali que os dois piores defeitos da F.1 se esconderam,
 * nenhum dos dois dando sintoma na tela:
 *
 * 1. `LaunchedEffect(Unit)` dispara **uma vez por composição**, e login não recompõe o
 *    `AppNavHost`. Sair e entrar sem matar o app deixava o aparelho fora do `device_tokens`.
 * 2. `filterNotNull()` **antes** de `distinctUntilChanged()` engolia o segundo login **da mesma
 *    conta** — o `null` do logout era a única coisa que separava as duas sessões.
 *
 * ## A ORDEM DOS DOIS OPERADORES É O COMPORTAMENTO
 *
 * `distinctUntilChanged()` vem antes de `filterNotNull()`, e isso não é estilo. A sequência real de
 * quem sai e volta na mesma conta é `"u1"` → `null` → `"u1"`. Filtrando primeiro, o `null` some e
 * sobra `"u1"` seguido de `"u1"` — iguais consecutivos, que o `distinct` descarta.
 *
 * O detalhe que torna isto perigoso: **entrar em OUTRA conta funcionava**. Só a mesma conta
 * falhava, que é o caso comum e o menos provável de alguém testar por curiosidade.
 *
 * ## Portas estreitas, não os objetos inteiros
 *
 * [RegistrarAparelho] e [AtualizarContador] são verbos. O `RegistroDePush` real carrega `Context` e
 * `FirebaseMessaging`, que não existem num teste de unidade — a mesma lição que o `SairDaConta`
 * cobrou quando três testes de sessão pararam de compilar.
 */
class ReagirASessao(
    private val sessao: TokenProvider,
    private val registrarAparelho: RegistrarAparelho,
    private val atualizarContador: AtualizarContador,
) {

    /** Registra o aparelho para push (F.1). */
    fun interface RegistrarAparelho {
        suspend fun registrar()
    }

    /** Relê o badge de notificações não lidas (F.1). */
    fun interface AtualizarContador {
        suspend fun atualizar()
    }

    /**
     * Coleta para sempre — quem chama decide o escopo.
     *
     * `filterNotNull` depois do `distinct`: logout emite `null` e não há o que registrar (a baixa
     * já foi feita pelo `SairDaConta`, com o token do Firebase ainda válido, que é a única janela
     * em que ela funciona).
     */
    suspend fun observar() {
        sessao.uidFlow()
            .distinctUntilChanged()
            .filterNotNull()
            .collect {
                registrarAparelho.registrar()
                atualizarContador.atualizar()
            }
    }
}
