# Herdado do Handoff 14 e anteriores

> **Histórico.** Fatia fechada — preservado literal do antigo `handoff-retomada.md`.
> Estado atual em `docs/ESTADO.md`; convenções em `docs/CONVENCOES.md`;
> regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`.

---

## Desde o Handoff 14 (sessão 2026-08-15)

Sessão longa, dirigida por teste manual. Quase tudo que entrou saiu de defeito observado no
emulador ou no log do servidor — vale ler nessa chave.

### ARCH #31 (novo) — política única de apresentação de erro

O sintoma: wifi ligado, Ktor parado, e o app dizia "Sem conexão". Puxando o fio, não havia
política nenhuma — cada ViewModel decidia sozinho.

- **`AppError.Connection`** separa falha de transporte de 500. Antes os dois eram `Unexpected`,
  e como `is AppError.Unexpected` era o gatilho do fallback de cache, **erro interno do servidor
  fazia o app servir dado velho e esconder a falha**.
- **`app/ui/ErrorUi.kt`** é o ponto único onde `AppError` vira pixel. Quatro formas: silêncio
  (há dado local), `ErroDeTela`, `ErroInline` (formulários), `ErroEmSnackbar`. O componente
  **não navega** — devolve `ErroAcao` e a tela executa.
- **11 states** deixaram de guardar `String` e passaram a guardar `AppError`. `ErrorMessage.kt`
  removido.
- **`install(Auth)` não tinha `refreshTokens`** — o Ktor cacheava o token e nunca recarregava.
  Token do Firebase dura ~1h: passada a hora, **todo** request virava 401 permanente, com o
  usuário logado e sem saída. Corrigido; 401 que sobrevive ao refresh emite `SessionExpiryBus`,
  e o NavHost desloga.
- **`ErroContexto.AUTENTICANDO`**: 401 na tela de login é "senha errada", não "sessão expirada".
  Sem isso o handler global deslogaria quem apenas errou a senha.
- **`fieldErrors`** trafegava vazio: 30 pontos de validação no servidor, nenhum preenchia.
  Marcados os 11 que o usuário consegue corrigir; os ~19 de rota/id ficam fora por decisão.

### ARCH #23 — gate de leitura (falha de segurança)

O blur estava aplicado **só** em `GET /programs`. O `GET /workouts/{id}` entregava o treino
completo de um dia trancado a quem pedisse pelo id — no emulador, tocar no cadeado mostrava os
exercícios e o "Iniciar treino".

`ProgramAccess` passou a ser a definição **única** de quem vê o quê; o blur e o novo
`requireReadable` (gêmeo do `requireEditable`) leem a mesma função. O corte vivia inline no
blur, e foi por isso que o outro endpoint nunca soube dele.

No cliente, `WorkoutLocalDataSource.save()` só deixa `locked` **subir**: a Home, que buscava o
treino do dia só para estimar duração, gravava `locked = false` por cima e o dia trancado
aparecia liberado.

### ARCH #30 — política de frescor persistida (SyncStamps)

Todo TTL vivia em memória e valia zero entre aberturas. O caso mais irônico: catálogo com TTL
de 24h (só muda em deploy) expirando a cada fechamento do app — 965 exercícios por boot.

`SyncStamps` grava o carimbo no `kv_cache` com o **uid na chave**. Substituiu quatro pares
`sincronizadoEm` + `donoDoCache` escritos à mão, e tornou o isolamento por conta consequência da
chave em vez de um `if` — foi esquecendo esse `if` num lugar que dado de uma conta apareceu em
outra.

Ganhos medidos no log do servidor, navegando em ciclo:

| antes | depois |
|---|---|
| 6 `GET /me/stats` em 16s | 1 (TTL 2 min) |
| catálogo completo a cada entrada na aba | 1× no boot, TTL 24h |
| catálogo completo por exercício aberto | nenhuma requisição |
| `GET /workouts/{id}` por navegação | 1 (TTL 5 min) |
| cold start: 6 requisições | 2 |

**Dois bugs de cache que só apareceram medindo:**

- `ExerciseRepositoryImpl.getDetail()` baixava o **catálogo inteiro** para achar um exercício, sem
  olhar o banco local que já o tinha.
- `ProgramLocalDataSource.read()` devolvia `null` tanto para "não tenho programas" quanto para
  "nunca sincronizei" — então **toda conta sem programa furava o cache-first**, indo à rede em
  toda abertura, para sempre.

Também: **invalidar cache é consequência de mutação, não de navegação**. O NavHost invalidava em
toda volta do detalhe de treino e em todo fechamento do paywall, inclusive no "Agora não".
`onBack` virou `(alterou: Boolean)` e `onClose` virou `(assinou: Boolean)`.

E a **Splash ficou cache-first**: `onboardingCompleted` é monotônico, então um `true` cacheado
dispensa esperar a rede. `false`/`null` continuam esperando — pular o onboarding por engano é
pior que 2s de spinner.

### ARCH #32 (novo) — Conquistas: fecha a Fase 5

**A decisão que importa: regra derivada, desbloqueio PERSISTIDO.** XP é 100% recalculado; a
conquista não pode ser. Se um limiar de streak subir de 7 para 10, o cálculo puro faria a
medalha SUMIR do perfil de quem já a viu — e o código continuaria "correto". `unlocked_at`
torna o desbloqueio fato histórico, não consequência da versão atual do código. Concedida uma
vez, nunca é retirada (o repositório nem expõe `delete`).

- `AchievementPolicy` — Kotlin puro, 10 testes. Catálogo de 9 conquistas em 3 métricas.
- V34 `user_achievements`, PK composta `(user_id, achievement_id)` — é ela, com
  `ON CONFLICT DO NOTHING`, que dá a idempotência. 6 testes de integração contra Postgres real.
- **A avaliação dispara na LEITURA** (`GET /me/achievements`), não no `POST /sessions`: dá o
  retroativo de graça (quem já tinha 60 treinos recebe na primeira abertura, sem backfill) e
  mantém o registro de sessão com uma responsabilidade só.
- Cliente: `data/achievements` + `screens/achievements`, tela em grade com bloqueadas em cinza
  e progresso. Cache-first (#30). `lime` aqui é permitida — é o lugar dela ([REGRA] #16).

**Consequência aceita:** `current` pode ficar ABAIXO de `target` numa conquista desbloqueada
(streak quebra, medalha fica). A UI lê `unlockedAt` e nunca infere de `current >= target`.

**VALIDADO no emulador (2026-08-17):** grade em cinza numa conta zerada; "Começou" ao registrar
o 1º treino; sair e voltar não duplica nem move a data; 10 sessões injetadas no Postgres
concederam TRÊS medalhas de uma vez (retroativo sem backfill); e — o teste que valida a decisão
inteira — apagar as sessões recentes quebrou o streak e **`Uma semana` continuou desbloqueada**.
Offline com cache quente mostra a grade completa.

Três defeitos, todos de APRESENTAÇÃO: barra de progresso invertida (faltava `trackColor`; o
default `secondaryContainer` do Material3 é roxo neste tema e dominava o progresso cinza —
11 de 30 parecia 90%); "Meio century" (jargão em inglês sem sentido em musculação); e ordem
instável entre medalhas do mesmo lote (desempatava por progresso, que muda com o tempo).

**Armadilha de diagnóstico:** o Database Inspector sem "Live updates" mostra SNAPSHOT
CONGELADO. Um "0 de 0" offline parecia bug de cache, mas era o cache frio daquela instalação —
e a consulta ao `kv_cache` mostrava a chave ausente porque o snapshot era velho. O que impediu
uma correção errada foi a CONTRADIÇÃO entre duas evidências: a tela só tem uma fonte (o cache),
então "3 de 9 online" e "chave ausente" não podiam ser verdade juntos.

**Nada de "treinou N dias seguidos" literal:** o app prescreve descanso (#22/#26), e premiar
treino diário empurraria o usuário contra o próprio programa. A métrica reusa o streak do
`XpPolicy`, em que dia de descanso agendado conta como cumprido.

### ARCH #30 — ESCRITA offline (outbox genérico, B.1–B.4)

Último item aberto do #30, fechado. Detalhe completo na emenda do
`arch/fase-5/ARCH-30-offline-first.md`; o essencial:

**Um caminho só, com ou sem rede:** grava local → enfileira → pede o envio. A alternativa
("com rede manda direto, sem rede enfileira") dá dois comportamentos e deixa o caminho offline
exercitado só quando algo já deu errado.

- **B.1** — id gerado no CLIENTE. `insertIgnore` + releitura no servidor: reenviar depois de uma
  resposta perdida devolve o recurso existente em vez de duplicar. Id de outro usuário → 409.
- **B.2** — tabela `outbox` (3.sqm) guarda **comandos**, não "estado + flag de sujo" (um flag não
  sabe dizer que o usuário criou, editou duas vezes e excluiu). `CompactadorDeOutbox` é lógica
  pura, 7 testes. Compacta na LEITURA: a fila preserva a intenção bruta.
- **B.3** — repositórios escrevem pela fila. `AgendadorDeSync` (interface no core, WorkManager no
  app) mantém os módulos KMP livres de Android.
- **B.4** — `ProcessadorDeOutbox` + um `ExecutorDeOperacao` por feature (inversão: core não
  depende de feature). `FilaDeSaida` torna o processador testável sem SQLDelight — 8 testes.

**A regra que mais importa — temporário vs. permanente.** 400/403/404/409 = o servidor recusou,
retry dá o mesmo para sempre: marca e a UI avisa. Sem rede / 5xx / **401** = temporário (o 401 é
renovado pelo `refreshTokens`; descartar a escrita de quem ficou 1h sem abrir o app seria o pior
desfecho). E na primeira falha temporária o processamento **PARA** — se o POST do treino falhar
por rede e o PUT da agenda subir assim mesmo, o servidor devolve 400, o 400 é permanente, e a
agenda é descartada por causa de uma queda momentânea.

**Fronteira com o #25 (mudança de plano no meio da fatia).** O desenho original — toda escrita
pela fila — quebrava o gate de entitlement: o free editaria treino de IA, veria "salvo", o
paywall nunca abriria, e a edição sumiria minutos depois. Regra adotada: **o outbox cobre o que
o usuário POSSUI**. `MANUAL` → otimista; `AI` (treino, programa, agenda) → online-only e
síncrono. Preço: premium não edita conteúdo de IA offline.

**Três armadilhas de SQL** que custaram tempo e valem lembrar:

- proteger o pendente só na LIMPEZA não basta — o re-insert logo abaixo regrava a versão do
  servidor por cima. São dois lugares.
- `limparTreinosExceto` precisa proteger por `id` **e** por `programId`: renomear programa
  offline deixava o programa vazio na tela.
- `programId` é NULLABLE e `NULL NOT IN ('a')` vale NULL, não TRUE — sem `IS NULL OR …` nenhum
  treino avulso seria limpo pelo sync, nunca.

**A bateria manual achou 4 defeitos, nenhum no compactador ou no processador** — as duas peças
COM teste. Todos nas costuras: treino offline invisível (faltava gravar `program_schedule`);
selo "Pendente" grudado (nada em foreground chamava o processador — a redundância prometida no
KDoc não existia); worker falhando em silêncio (Koin não resolvia `List<ExecutorDeOperacao>`, e
o `runCatching` engolia o rastro); e alvo anulado pela compactação que nunca saía da tabela,
travando `contarPendentes()` acima de zero para sempre. Detalhe no ADR. **Lição: lógica pura
testada, integração não — é para lá que a próxima cobertura deve ir.**

**Diagnóstico:** `adb logcat -s FitJourneySync:*` mostra cada etapa do sync e a exceção.

**Selo na UI:** escrita otimista sem selo é desonesta. `PendenciaDeSync` mora no DOMÍNIO (a
presentation não enxerga `core:database`); "Pendente" discreto, "Não sincronizou" em `error`.

**Decisão:** logout **não** limpa a fila — é chaveada por uid, e apagar descartaria o treino de
quem saiu e voltou.

### Onboarding — primeiro programa opcional

O `ProgramRevealViewModel` chamava `generate()` no `init`: o programa era criado e gravado no
servidor no instante em que a tela abria, sem ninguém pedir. E o "Agora não" do paywall fazia
`popBackStack()` de volta para o Reveal — a própria tela que oferece premium.

Nova tela **`ProgramOffer`** no fim do quiz. A pergunta fica **depois** do quiz, não antes: o
quiz não é sobre o programa, é sobre o **perfil** (ambiente #28, idade #24, limitações). Pular
deixaria `onboardingCompleted = false` (a Splash devolveria ao quiz em toda abertura) e quebraria
`GET /exercises` por falta de ambiente.

`AppRoute.Paywall` ganhou `voltarParaHome` para distinguir os dois contextos de abertura.

### Home testável (10 testes, de zero)

Era a única tela sem cobertura, e num único dia saíram dela três defeitos. Três coisas travavam,
todas resolvidas: interfaces `HistoricoDeSessoes`/`Stats` (as implementações recebiam
`FitJourneyDatabase` no construtor), `Clock` injetado (`Clock.System` cravado fazia o teste do
"treino de hoje" depender do dia do build) e o source set `src/test/` que não existia.


## Herdado do Handoff 14 (ainda válido)

- Fase 4 concluída; motor maduro, destravado e desmembrado (bíceps/tríceps/antebraço).
- Cronograma de semanas (V24): `durationWeeks` + `startedAt`, semana atual derivada no servidor.
  **Não é periodização** — treinos iguais toda semana. Ciclo rolante não implementado.
- Catálogo curado: 965 exercícios, taxonomia 100% auditada, 0 duplicatas (V28–V33).
- Gate PAR-Q fechado (V26 normaliza LOWER_BACK→LUMBAR).
- Play Billing real (trocar `DevBilling`) — Fase 10.
