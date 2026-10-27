# Mapa de testes do FitJourney

_Estado real em 2026-09-10 (**Fase 6 concluída, débito de integração fechado, fatias G.1 e G.2 de idioma fechadas**, 717 testes em 79 arquivos); **seção 4 revisada em 2026-10-07** — 3 dos 5 itens abertos já tinham sido resolvidos por código posterior, sem ninguém atualizar este mapa. Contagem de testes atual: `gen_testes.py` (`docs/gerados/testes.md`), não este número — ele envelhece a cada teste novo e não é mais mantido à mão aqui._

> ## ⚠️ O Konsist estava dando verde sem rodar
>
> `:konsist:test` fica `up-to-date` porque lê os fontes em tempo de execução e o Gradle não os
> declara como entrada da task. Na G.1 ele passou em 7s com "12 up-to-date": **nenhum arquivo novo
> foi varrido**. Com `--rerun-tasks` rodou de verdade, e passou.
>
> É a mesma lição da V40 com outra roupa. Lá o Docker fazia a suíte de integração não rodar junto e
> a V40 quebrou 51 de 62 testes sem ninguém ver; aqui é o cache que mente.
>
> > **Suíte que não roda não cobre nada.** Vale para a que exige Docker e para a que o Gradle acha
> > que já rodou.

> ## ✅ FECHADA — a integração de V40 a V46 (2026-09-09)
>
> Era a maior lacuna da fase. Sete migrations tinham entrado com garantias que **só o Postgres
> prova**, e passavam por disciplina. Hoje cada linha desta tabela tem teste:
>
> | Migration | O que nenhum fake alcança | Onde está |
> |---|---|---|
> | V40 | PK do par canônico recusando pedido cruzado | `AmizadeIntegrationTest` (11) |
> | V41–V42 | `ON CONFLICT (token) DO UPDATE`, `INSERT` em `JSONB` | `NotificacaoIntegrationTest` (12) |
> | V44 | PK composta da reação | `SocialModeracaoIntegrationTest` (17) |
> | V45 | dois `CHECK` (arco exclusivo, motivo não-vazio), **dois índices únicos parciais**, o `CASCADE` que leva a denúncia junto com o comentário | idem |
> | V46 | PK tripla, `CHECK` do `kind`, e a consulta de `entradasNoDia` — que converte dia civil do grupo para intervalo UTC **e** exclui o criador | `AvisosDiariosIntegrationTest` (19) |
>
> O argumento que a fechou era histórico: **o defeito nº 1 da F.1 era exatamente um `INSERT` em
> `JSONB`**, e um teste de integração o teria pego em segundos em vez de horas de bateria manual.
>
> **Dois achados vieram de escrever os testes, antes de qualquer execução:**
>
> - `AcaoDeModeracao.adminId` era `Uuid` não-anulável enquanto a coluna aceita `NULL`, e o
>   `encerrarCasosPendentes` inseria `null` **direto na tabela**, contornando o próprio model.
>   Corrigido para `Uuid?`. → **Model que não representa o que a tabela permite empurra alguém a
>   contornar o model.**
> - Um teste de push que "passava" sem exercitar nada: usava um usuário fora do grupo, então a
>   guarda de filiação recusava antes. → **Teste de comportamento negativo precisa provar que
>   chegou lá.**

Este documento responde três perguntas: **o que já está coberto**, **o que não está**,
e **onde colocar o próximo teste**. Ele não é uma lista de boas práticas — é o retrato
deste sistema.

---

## 1. Como ler o mapa

Um teste é um ponto em três eixos. Chamar algo de "teste unitário" descreve só o primeiro,
e é por isso que conversas sobre teste se perdem.

| Eixo | Vai de | Até |
|---|---|---|
| **Escopo** | uma função | o sistema inteiro |
| **Propriedade** | "está certo?" | "é seguro?", "aguenta?", "dá para usar?" |
| **Custo de descoberta** | manual, uma vez | ambiente dedicado |

O terceiro eixo é o que ordena o investimento:

1. **VER** — um instrumento barato que mostra o problema (a bateria manual)
2. **AFIRMAR** — um teste automatizado que impede a volta (a suíte)
3. **AMBIENTE** — infraestrutura que imita a realidade (carga, farm de dispositivos)

---

## 2. O que existe hoje

### 2.1 Arquitetura — `:konsist` (6 regras)

Roda **primeiro** no CI, e com razão: viola arquitetura é o defeito mais caro de
desfazer, porque tudo que vem depois se apoia nele.

- `shared-contract` é Kotlin puro
- `core` não sobe para feature, server nem app
- as camadas internas da feature respeitam a Clean Architecture
- todo arquivo mora no pacote da sua camada

**O que isso compra:** a [REGRA] "feature nunca depende de feature" deixa de ser
disciplina e vira falha de build.

### 2.2 Unidade e serviço — `server/src/test` (33 arquivos, 332 testes)

**Novos na G.1 (#37):** `IdiomaPolicyTest` (16) cobre a cadeia de fallback de idioma, escrita e
testada com **dois** idiomas justamente porque adicionar o terceiro só funciona se ela já existir.
`TextosDeAvisoTest` (9) cobre o que o compilador não vê: plural, possessivo, e a varredura de
travessão em todas as chaves e todos os idiomas.


Duas famílias bem distintas:

**Políticas puras** — sem banco, sem HTTP, sem relógio próprio. `GroupPolicy`,
`CheckInPolicy`, `XpPolicy`, `AchievementPolicy`, `DisplayNamePolicy`, `AgeGate`,
e o motor de treino (`SlotFiller`, `SplitSelection`, `StructureEngine`, `WeekSpread`).

É onde mora a lógica que decide, e é o teste mais barato do projeto. A regra que
fez isso funcionar: **tudo entra por parâmetro** — inclusive o relógio.

**Serviços com dublês** — `CheckInService`, `GroupMembershipService`, `ProgramService`,
`SessionService`, `ProfileService`, `UserService`.

Os dublês **implementam a regra**, não devolvem constante. O `FakeCheckInRepository`
tem o índice único de verdade; o `FakeGroupRepository` repete a condição do `DELETE`.
Sem isso, o teste do "um por dia" e o da regra 2.5-A passariam por construção.

### 2.2-A O que a F.1 acrescentou de método

**Três dos nove defeitos da F.1 eram de observabilidade** — `runCatching` engolindo falha sem log,
token apagado sem dizer a causa, e nenhuma forma de saber qual token era de qual aparelho. Não
quebravam nada; foram eles que fizeram os outros custarem horas.

Isso abre uma dimensão que este mapa não tinha: **conseguir ver o que o código faz é uma
propriedade testável**, e hoje ninguém a verifica. Não há teste que afirme "esta falha é
registrada".

> **Engolir exceção é decisão de fluxo, não de log.** O processo deve seguir; alguém precisa poder
> saber por quê.

### 2.3 Integração — `server/src/integrationTest` (13 arquivos, 132 testes, Testcontainers + Postgres)

Postgres real, subido por Docker. **A suíte inteira roda em ~50s.**

| Arquivo | Testes | Garante |
|---|---:|---|
| `IdiomaIntegrationTest` | 7 | **V47.** Compara o `CHECK` do banco com o enum `Idioma`, que são **duas fontes da mesma verdade**: adicionar um idioma de um lado só faria o `PATCH` passar por toda a validação do Kotlin e estourar no `UPDATE`. Também: a linha que não escolheu idioma nasce em pt-BR (o `DEFAULT` cobrindo quem já existia), e toda tag cabe em `VARCHAR(5)` |
| `CheckInIntegrationTest` | 28 | Índice único, os três `CHECK`, cascata e a atomicidade do `deleteIfSoleMember` (fechou a P0 da fatia B). **+ V43:** o emoji sobrevive ao round-trip (emoji não cabe num `CHAR(2)` — `👍` são 2 unidades UTF-16 e `✌️` leva seletor de variação), e check-in sem a regra grava `NULL` |
| `AvisosDiariosIntegrationTest` | 19 | **V46.** PK tripla, `CHECK` do `kind`, o encerramento idempotente, e a conversão de dia civil do grupo para intervalo UTC — verificada em São Paulo **e** em Tóquio, porque um fuso só não distingue conversão certa de nenhuma conversão |
| `SocialModeracaoIntegrationTest` | 17 | **V44+V45.** PK da reação, o arco exclusivo `check_in_id` XOR `comment_id`, os dois índices únicos parciais, e a auditoria sobrevivendo ao alvo apagado |
| `NotificacaoIntegrationTest` | 12 | **V41+V42.** Round-trip de `JSONB` (a coluna é mesmo `jsonb`, não `text`) e o `ON CONFLICT (token) DO UPDATE` |
| `AmizadeIntegrationTest` | 11 | **V40.** Par canônico, direção do bloqueio, `UNIQUE`/`CHECK` de `users.code` |
| `GroupMembershipIntegrationTest` | 11 | Entrada e papéis |
| `GroupCreationIntegrationTest` | 8 | Criação de grupo contra o schema real |
| `AchievementGrantIntegrationTest` | 6 | Concessão de conquista |
| `DisplayNameBackfillIntegrationTest` | 6 | A V35 preenche nome de linha antiga |
| `WorkoutIdempotencyIntegrationTest` | 3 | Repetir a escrita não duplica |
| `MigrationIntegrationTest` | 3 | O Flyway aplica a cadeia inteira do zero |
| `WorkoutGenerationIntegrationTest` | 1 | O motor contra o catálogo real |

**O que só esta camada pega:** índice único, `CHECK`, `ON DELETE CASCADE`, plano de
consulta. Nada disso existe num fake.

#### A estratégia de container, e por que ela mudou

**Dez das doze classes compartilham UM Postgres** (`BancoDeTeste`), limpo por `TRUNCATE` no
`@BeforeAll` de cada uma. Antes cada classe subia o próprio: ~5s de boot mais ~2,5s de schema,
oito vezes. A suíte caiu de **1m18s para 44s**.

O critério não foi velocidade por si. Esta suíte **atravessou duas fatias sem ser executada**,
porque exige Docker e não roda junto com `:server:test`; quando finalmente rodou, a V40 tinha
derrubado 51 de 62 testes.

> **Suíte lenta é suíte que ninguém roda — e suíte que ninguém roda não cobre nada.**

O que se perde: o isolamento deixa de ser por construção e passa a ser por classe. Isso impõe uma
regra a quem escreve, e ela já cobrou uma vez (um `blocks.count() == 0` que falhou por causa dos
bloqueios de testes anteriores, sem relação com a cascata que o teste afirmava):

> **Asserção sobre a tabela inteira assume um isolamento que esta suíte não dá.** Afirme sobre o
> cenário — o par, o grupo, o usuário —, nunca sobre um `count()` global.

**Duas classes ficam de fora, de propósito:** `MigrationIntegrationTest` e
`DisplayNameBackfillIntegrationTest`. Elas não testam dado, testam o **schema nascendo** — a
segunda migra só até a V34, insere linhas como existiam antes da coluna, e só então roda a V35.
Num banco já na V46 as duas passariam sem exercitar nada. Ambas falam JDBC cru e nunca chamam
`Database.connect`, o que é o que as torna inofensivas — ver abaixo.

⚠️ **A armadilha que isso custou:** `Database.connect` do Exposed é **estado global** — quem
conecta por último vira o default de todo `transaction { }` do processo. Enquanto as duas
estratégias conviviam, uma classe com container próprio fechava o datasource dela no `@AfterAll` e
a seguinte herdava um datasource morto: **39 testes falhando com `SQLException`**, e a primeira
classe da ordem passando, o que fazia o erro parecer aleatório.

> **Estado global de conexão não convive com duas estratégias de container.**

### 2.4 Cliente compartilhado — `shared/**/commonTest` (13 arquivos, 71 testes)

Mappers (`Exercise`, `Profile`, `Program`, `Workout`), ViewModels de feature,
`HttpErrorTest` (a tradução de HTTP para `AppError`) e o outbox
(`CompactadorDeOutbox`, `ProcessadorDeOutbox`) — que é o coração do offline (#30).

### 2.5 App — `app/androidApp/src/test` (9 arquivos, 46 testes)

`HomeViewModelTest`, `MenuViewModelTest`, `ContaViewModelTest`, `GruposViewModelTest`,
`PerfilPublicoViewModelTest` (C.1), `ErrorUiTest` (#31), e os três da F.1:
`NotificacoesViewModelTest`, `ContadorDeNaoLidasTest`, `SairDaContaTest`.

⚠️ Este source set **não compilava entre as fatias B.3 e A.0** e ninguém percebeu,
porque o CI não o executava. Hoje executa (`:app:androidApp:testDebugUnitTest`).
A lição está no Handoff: **um teste que não roda no CI não existe.**

**Os três da F.1 cobrem exatamente o que a bateria manual não distingue:**

| Arquivo | O que só ele pega |
|---|---|
| `NotificacoesViewModelTest` | O destaque do não-lido dura **um instante** — abrir marca como lido no servidor e a tela desenha o snapshot anterior. No olho, com rede rápida, "preservou" e "recarregou e apagou" são idênticos. Também: central sem não-lidas **não pode** chamar a marcação, ou é um POST por abertura de tela para nada |
| `ContadorDeNaoLidasTest` | Falha de rede **não zera o badge**. Um badge certo e um badge zerado por erro parecem iguais quando não há notificação nenhuma |
| `SairDaContaTest` | A **sequência inteira** do logout numa asserção só. Um assert por passo deixaria a ordem se embaralhar sem o teste notar |

O `SairDaContaTest` existe porque o compilador cobrou: a primeira versão da F.1 passou o
`RegistroDePush` inteiro (com `Context` e `FirebaseMessaging`) para o `SairDaConta`, e três testes
de sessão pararam de compilar. **Teste que não compila por excesso de dependência está denunciando
acoplamento, não pedindo um mock** — virou porta estreita (`BaixaDePush`).

### 2.6 CI — `.github/workflows/ci.yml`

Ordem: Konsist → build do server → integração → KMP host → testes do app → `assembleDebug`.

Arquitetura primeiro, e o build do Android por último porque é o mais lento.

---

## 3. Matriz de risco × cobertura

Legenda: ✅ coberto · 🟡 parcial · ❌ ausente

| Dimensão | VER | AFIRMAR | Nota |
|---|---|---|---|
| **Correção de regra** | ✅ bateria manual | ✅ políticas + serviços | O ponto forte do projeto |
| **Arquitetura** | — | 🟡 Konsist | Vira falha de build, **mas a task fica `up-to-date` e não varre** — ver o aviso no topo |
| **Texto de interface** | ✅ bateria manual | 🟡 avisos e erros | O `TextosDeAvisoTest` varre os 7 avisos do servidor; o `TextosDeErroTest` varre as 87 frases de erro do cliente e **exige texto para todo código**. Os ~500 literais de tela ainda estão cravados no código e não são varridos por nada (G.3) |
| **Schema / migrations** | ✅ boot do servidor | ✅ integração | Fechado em 2026-09-09: V38 e V40–V46 cobertas, 125 testes |
| **Contrato cliente↔servidor** | ✅ bateria manual | ✅ `shared-contract` compila | O tipo compartilhado é o teste |
| **Privacidade / autorização** | ✅ dois usuários | 🟡 só no serviço | **Nenhum teste de ROTA** |
| **Offline / sincronização** | ✅ modo avião | ✅ outbox | Check-in é online-only (10.1) |
| **Apresentação de erro** | ✅ bateria manual | ✅ `HttpErrorTest` + `ErrorUiTest` | Fechado na C.1 |
| **Estado de UI** | ✅ bateria manual | 🟡 9 ViewModels | Sem teste de Compose |
| **Ciclo de SESSÃO** | ✅ bateria (17–20) | 🟡 `SairDaContaTest` | **A maior lacuna nova — ver P0 abaixo** |
| **Entrega por push** | ✅ bateria (4–6) | ❌ | Depende de FCM, aparelho e rede: **inautomatizável aqui** |
| **Navegação / deep link** | ✅ bateria (10–12) | ❌ | Exige compor o `AppNavHost` com Splash real |
| **Observabilidade** | 🟡 lida no log | ❌ | 3 dos 9 defeitos da F.1 eram disto |
| **Volume de dados** | ❌ | ❌ | **A maior lacuna antiga** |
| **Carga** | ❌ | ❌ | Prematuro — zero usuários |
| **Armazenamento** | 🟡 percebido a olho | ❌ | Purga de notificações ✅ (F.1); a dos 90 dias de foto continua inexistente |
| **Acessibilidade** | ❌ | ❌ | Nunca avaliado |

---

## 4. Lacunas, por prioridade

### ✅ FECHADA — nada testava duas sessões seguidas (achado da F.1)

Era a lacuna mais cara da fase, e **conceitual**: dois defeitos da F.1 só existiam na transição
entre sessões — o registro do aparelho num `LaunchedEffect(Unit)`, que não redispara no login, e
`filterNotNull()` antes de `distinctUntilChanged()`, que engolia o segundo login **da mesma
conta**. Nenhum dava sintoma: o app funcionava e a pessoa parava de receber notificação.

Fechada em 2026-09-06 com `ReagirASessaoTest` (5 testes), e o caminho até lá ensina três coisas:

**Foi preciso EXTRAIR antes de testar.** A regra vivia dentro de um `@Composable`, e exercitá-la
exigiria compor a árvore inteira com Splash, Koin e navegação. Virou `ReagirASessao`, com portas
estreitas (`fun interface`) para o `RegistroDePush`, que carrega `Context` e Firebase.

> **Regra escondida dentro de UI é regra sem teste** — e foi exatamente ali que os dois defeitos
> invisíveis se esconderam.

**O dispatcher decidiu se o teste enxergava alguma coisa.** Com `StandardTestDispatcher`, os
quatro testes que esperavam registro viam **zero**: o coletor não tinha chegado ao `collect` quando
o uid mudava, e acertar o `advanceUntilIdle()` entre cada mutação é frágil. `UnconfinedTestDispatcher`
processa a emissão no instante em que acontece, que é o que se quer ao testar quem **observa**.

> **O dispatcher de teste não é detalhe: ele decide o que o teste consegue observar.**

**O teste que passou primeiro era o que não testava nada.** Dos cinco, o único verde na primeira
tentativa foi o que espera **zero** registros — ele passava pela razão errada. Um teste que
"passa" sem exercitar é pior que um que falha.

O par que dá sentido ao arquivo: `"u1" → null → "u1"` (mesma conta) **e** `"u1" → null → "u2"`
(troca de conta). O segundo é o caso que **funcionava mesmo com o defeito**, e por isso o escondia.

### ✅ FECHADA — a fatia B subiu sem integração

`CheckInIntegrationTest` existe, com **28 testes**: índice único, os três `CHECK`, cascata e a
atomicidade do `deleteIfSoleMember`. Era a P0 desde a fatia B.

### ⚠️ E a V40 QUEBROU a integração que já existia

Descoberto no GitHub Actions, **depois do merge da fatia anterior**: `users.code` virou
`NOT NULL + UNIQUE + CHECK`, e os cinco testes de integração inserem em `UsersTable` **direto**,
sem passar pelo `UserRepositoryImpl` — que é quem gera o código no app real. Com container
`PER_CLASS`, um `initializationError` derruba a classe inteira: **51 de 62**.

Escapou porque `:server:integrationTest` **exige Docker e não roda junto** com as outras tasks, e o
Painel Mestre dizia "suíte verde nas três tasks" — uma lista que omitia justamente essa.

> **Migration que aperta uma coluna quebra todo INSERT direto que já existia.** Ao mexer em
> `NOT NULL`/`UNIQUE`/`CHECK`, procure `INSERT INTO <tabela>` fora do repositório: testes de
> integração, seeds e scripts SQL.

O `seed_volume.sql` tinha o mesmo defeito e não quebrava build nenhum — só apareceria no meio de
uma investigação de performance, que é o pior momento possível.

E um teste se saiu especialmente bem: o `DisplayNameBackfillIntegrationTest` afirma que a recusa
vem do CHECK **nomeado** `users_display_name_len`. Ele pegou a mudança porque o banco passou a
recusar antes, por outro motivo. Um `assertTrue(erro != null)` teria deixado passar.

> **Recusar por outro motivo não é passar.**

### ✅ FECHADA — as V40–V46 repetiam o padrão (2026-09-09)

`friendships`, `blocks`, `device_tokens`, `notifications`, `check_in_reactions`, `group_reports`,
`moderation_actions` e `group_daily_notices` passaram a ter teste de integração: **60 testes em
quatro arquivos novos**. O que agora é afirmado, e nenhum fake alcançava:

- a PK do **par canônico** recusando o pedido cruzado no nível do banco
- `ON CONFLICT (token) DO UPDATE SET user_id` — a troca de dono do aparelho
- o `CHECK` de `friendships` e as cascatas de `ON DELETE CASCADE`
- o `INSERT` em coluna **`JSONB`** — que era exatamente o defeito nº 1 da F.1, e **um teste de
  integração o teria pego em segundos** em vez de horas de bateria manual
- o arco exclusivo da V45 e os dois índices únicos parciais
- a conversão de fuso do `entradasNoDia`, em **dois** fusos

O penúltimo item foi o argumento que fechou a lacuna: o defeito não estava na lógica, estava na
fronteira com o banco — a única camada que nenhum fake reproduz.

### ✅ FECHADA — a purga dos 90 dias não existia

`PurgaDeMidia.kt` (`server/media`) resolve as duas pontas: foto expirada (desafio encerrado há mais de `CARENCIA_EM_DIAS`, some a imagem e o dado pessoal, fica o fato) e arquivo órfão (grupo apagado deixa a cascata levar as linhas e os arquivos ficavam presos). Testado em `PurgaDeMidiaTest.kt`. **Achado nesta revisão (2026-10-07) — já estava resolvido, ninguém tinha vindo marcar.**

### 🟡 PARCIAL — teste de rota HTTP existe, mas só pro check-in

`ktor-server-test-host` saiu do catálogo pro código: `CheckInRoutesTest.kt` (13 testes) cobre exatamente o que faltava — **parsing do multipart**, código de status por rota e autorização na fronteira (não só no serviço). **Achado nesta revisão (2026-10-07).**

O que ainda falta: o mecanismo não foi estendido pras outras rotas (stats/progresso da fatia J, grupos, amizades, …) — essas seguem só com teste de serviço, que é meia garantia (a rota é quem decide se o serviço chega a ser chamado). `CheckInRoutesTest.kt` é o molde a copiar, não mais um experimento a validar.

### 🟡 PARCIAL — volume

O N+1 específico que este mapa apontava foi corrigido: `GroupService.meusGrupos` hoje busca os papéis em LOTE (`repository.rolesOf(grupos.map { it.id }, user.id)`), uma consulta pro grupo inteiro, não uma por grupo. **Achado nesta revisão (2026-10-07)** — mas sem teste que TRAVE a regressão (nenhuma "contagem de consultas por requisição" existe), então nada impede alguém reintroduzir o padrão em código novo, que é exatamente o risco que este item sempre descreveu.

O que segue genuinamente aberto: nenhum dado em escala existe. Um desafio de 50 pessoas por 90 dias são ~4.500 check-ins, e **nenhuma tela foi vista com esse número**. Precisa de: **seed determinístico** (VER) e **contagem de consultas por requisição** (AFIRMAR) — o segundo é a peça que impede QUALQUER N+1 futuro de voltar, não só o de `meusGrupos`.

### ✅ FECHADA — paginação do feed

`GrupoDetalheViewModel.kt` usa o cursor (`antesDe = cursor`, derivado do `createdAt` do último item) pra carregar mais. **Achado nesta revisão (2026-10-07) — já estava resolvido, ninguém tinha vindo marcar.** Falta só confirmar se existe teste de ViewModel cobrindo o carregamento incremental em si — não verificado aqui, é checagem separada.

### P3 — Compose e acessibilidade

Nenhum teste instrumentado. É a camada mais cara e a de menor retorno hoje, mas a
ausência deve ser **decisão consciente**, não esquecimento.

---

## 5. Onde colocar o próximo teste

| A pergunta é… | Vai para | Custa |
|---|---|---|
| "esta regra decide certo?" | `server/src/test` — política pura | Muito barato |
| "o serviço orquestra certo?" | `server/src/test` — serviço + dublês | Barato |
| "o banco garante isso?" | `server/src/integrationTest` | Médio (Docker) |
| "a rota expõe isso certo?" | `server/src/integrationTest` + `ktor-server-test-host` | Médio |
| "o estado da tela está certo?" | `app/androidApp/src/test` | Barato |
| "as camadas se respeitam?" | `:konsist` | Barato |
| "aguenta volume?" | seed + contagem de consultas | Médio |

**Duas regras de escrita que já provaram valor aqui:**

1. **Dublê implementa a regra.** Um fake que sempre aceita faz o teste passar por
   construção. O índice único mora no fake; a condição do `DELETE` também.
2. **Relógio entra por parâmetro.** Check-in é a operação mais dependente de data da
   fase; teste com o relógio da máquina falha sozinho às 21h de uma terça.

---

## 6. A regra que vale mais que este mapa

> **Todo defeito que escapou vira teste.**

É a única que não depende de adivinhar onde vai quebrar — o defeito já disse.
E é auto-corretiva: a suíte cresce na direção em que **este** sistema falha, não um
sistema genérico.

Evidência da fatia B, e do que ela ensina sobre onde investir:

| Defeito | Natureza | Virou teste? |
|---|---|---|
| Lista de grupos sumia | Estado — leitura-e-escrita sob suspensão | ✅ `GruposViewModelTest` |
| Admin sozinho preso no grupo | Regra de negócio incompleta | ✅ harness de membership |
| Coil sem token na foto | Autenticação | ❌ — precisa de teste de rota |
| 409 com mensagem errada | Apresentação | ❌ |
| Permissão lembrada em vez de relida | Estado de plataforma | ❌ — precisa de instrumentado |
| Servidor em loopback | Configuração | ❌ — não testável em unidade |
| Botão oferecido e desmentido | Produto | ✅ parcialmente (`myCheckInToday`) |

**Nenhum foi de algoritmo. Nenhum foi de carga.** Foram de estado, permissão,
autenticação, apresentação e configuração — e a maioria só apareceu porque a bateria
manual roda com **dois usuários de verdade**.

Isso diz onde o próximo esforço rende: **rota e estado de plataforma**, não mais
políticas puras — essas já estão bem cobertas.

---

_Fonte: `docs/fontes/mapa-de-testes.md`. Inventário de 2026-08-25 (pós-fatia B); seção 4 revisada em 2026-10-07._
