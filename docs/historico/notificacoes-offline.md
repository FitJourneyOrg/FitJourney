# Notificações offline (cache-first) — fechada 2026-10-22

> Pedido original: 2026-10-09 (Rafael viu em modo avião: a central de notificações **some** sem
> rede). Plano em `docs/trabalho/notificacoes-offline.md` (apagado neste fechamento).

## O que mudou

`Notificacoes.listar()` era só-rede. Agora é cache-first (ARCH #30 estendido, `ARCH-36` emenda
36.5): tabela local `notificacao` (migration `9.sqm`), `NotificacoesRepositoryImpl` por trás da
MESMA interface `Notificacoes` de sempre, `marcarComoLidas()` otimista via outbox
(`TipoOperacao.MARCAR_NOTIFICACAO_LIDA`, alvo fixo `"todas"` por ser mutação global do servidor).

`ContadorDeNaoLidas` e `NotificacoesViewModel` não tocados — zero risco de regressão nos dois
pontos que já tinham teste e invariante documentado (`[INV]` do `ARCH #36`: a lista exibida
preserva o `readAt` do snapshot ao marcar como lida).

## Decisão de desenho que não estava no plano original

O plano (`notificacoes-offline.md`) previa `Notificacoes` ganhando um `observar(): Flow<...>` e os
dois consumidores passando a coletar reativamente. Ao ler `NotificacoesViewModelTest` ANTES de
desenhar a interface nova, achei o teste `"abrir marca como lidas e zera o badge, sem apagar o
destaque da lista"` — que é exatamente o `[INV]` do `ARCH #36` escrito como asserção. Um `Flow`
movido pela mesma escrita local que marca como lida reabriria esse invariante: a tela perderia o
destaque de "novo" no instante em que `marcarComoLidas()` grava local, não quando a pessoa sai e
volta.

Troquei por manter `Notificacoes` **idêntica** (one-shot) e esconder o cache-first inteiro dentro
de `NotificacoesRepositoryImpl`. Resultado: zero linhas tocadas em `ContadorDeNaoLidas.kt`,
`NotificacoesViewModel.kt` ou nos testes dos dois — o risco da fatia ficou inteiro em código novo,
testado do zero.

> **Ler o teste existente antes de desenhar a interface nova é o que evita reabrir um invariante
> que já tinha custado uma bateria manual pra descobrir** (ver `ARCH #36`, seção 3).

## Achado incidental: doc desatualizada

`docs/regras/05-offline.md` dizia que o fallback de cache acontecia em `AppError.Unexpected`. O
código (`ProgramRepositoryImpl`, agora também `NotificacoesRepositoryImpl`) sempre checou
`AppError.Connection`. Corrigido na doc neste fechamento — código vence doc, e ninguém tinha
tocado essa linha desde que foi escrita.

## Testes (C2)

Três arquivos novos em `app/androidApp/src/test/.../notificacoes/`:
- `NotificacaoLocalDataSourceImplTest` — SQLite real (driver JDBC em memória), como
  `ProgramLocalDataSourceTest`: replace total, ordenação, round-trip de `data` (Map→JSON, incluindo
  vazio), `marcarTodasComoLidas` seletivo (`WHERE readAt IS NULL`), isolamento por uid.
- `NotificacoesRepositoryImplTest` — fakes à mão para remoto/local/stamps; `Outbox` e
  `ProcessadorDeOutbox` reais contra SQLite em memória (são classes concretas, sem interface que
  cubra `alvosPendentes()`). Cobre sucesso, falha de conexão com/sem sync anterior, falha
  não-conexão nunca serve cache, e o caso central: marcação pendente no outbox sendo reaplicada
  depois do replace do `save()`.
- `ExecutorDeNotificacaoTest` — roteamento por tipo, propagação de erro, tipo fora do executor.

Build confirmado verde pelo Rafael: `:konsist:test` e `:app:androidApp:testDebugUnitTest`, saída
silenciosa com `-q` (sem `BUILD FAILED`).

## Fora de escopo, mantido

Paginação (teto 100 já decidido no servidor), notificação local gerada offline (só o servidor
gera), listener de conectividade (decisão de 2026-10-08, já registrada em `debitos.md`).
## Bateria manual (2026-10-22) — 9/9 ✅, dois defeitos tangenciais achados no caminho

A bateria (cache local sobrevive a matar o app offline, pull offline não esvazia a tela, nova
notificação só chega com rede, marcação dupla offline colapsa numa fila só, isolamento por
`uid` entre contas) passou inteira. Dois bugs pré-existentes, NÃO relacionados ao cache-first,
bloquearam o teste e foram corrigidos no caminho:

### 1. Corrida de primeiro login (500 em `POST /me/devices` e `GET /me`/`GET /me/notifications`)

`UserRoutes.GET /me`, `DeviceRoutes.POST /me/devices` e `NotificacaoService` chamam
`UserService.findOrCreate` cada um por si -- e no primeiro acesso de uma conta nova, os três saem
quase juntos (ver `ReagirASessao` + o `GET /me` independente de alguma tela). `findOrCreate` fazia
SELECT-então-INSERT sem lidar com a corrida: o UNIQUE em `firebase_uid` rejeitava o segundo INSERT,
e isso subia como `AppError.Unexpected` (500) pro perdedor -- o próprio comentário do método já
avisava disso ("Tratamento robusto (reler no conflito) fica como refino se a corrida aparecer").

Corrigido em `UserService.findOrCreate`: ao falhar o `create`, relê por `firebase_uid` antes de
desistir -- se achar (a outra requisição venceu a corrida), devolve ela; só propaga a falha se a
releitura também vier vazia (erro de banco de verdade). Testes novos em `UserServiceTest.kt`
cobrindo os dois casos (relê e acha / relê e não acha).

### 2. Badge de notificações recarregando em toda navegação, não só troca de aba

`AppNavHost.kt` tinha `LaunchedEffect(entry?.destination?.route, uidAtual) { contador.atualizar() }`
— a chave era a ROTA EXATA, não a aba. O comentário ao lado já dizia a intenção certa ("trocar de
tela-raiz é o momento"), mas a chave usada disparava em qualquer navegação (Perfil, EditarPerfil,
Library, detalhe de treino...), porque cada tela de detalhe é uma rota própria, diferente da raiz.

Corrigido: a chave agora é `abaAtual` (mesmo cálculo de `mostrarAbas`, já existente no arquivo --
`hasRoute(tab.routeClass)` contra as 4 abas-raiz). Só refaz a pergunta ao entrar numa aba-raiz ou
trocar entre elas; fica `null` (não atualiza) em qualquer tela de detalhe, onde a barra nem
aparece para alguém olhar o número. Sem teste unitário dedicado — mesmo padrão não-testado de
`mostrarAbas`, que já existia; candidato a extração se reaparecer outro defeito aqui (mesmo motivo
que tirou `ReagirASessao` deste arquivo).

Nenhum dos dois achados exigiu mudar o desenho do cache-first em si; ambos já existiam antes
desta fatia e só ficaram visíveis testando no aparelho de verdade — mesmo padrão do `ARCH #36`.
