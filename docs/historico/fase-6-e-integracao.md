# Fase 6 e o débito de integração V40–V46 (2026-09-09)

> **Histórico.** Fatia fechada — preservado literal do antigo `handoff-retomada.md`.
> Estado atual em `docs/ESTADO.md`; convenções em `docs/CONVENCOES.md`;
> regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`.

---

## ✅ FASE 6 CONCLUÍDA (2026-09-09)

Todas as fatias entregues: **A.0 · A · B · C · C.1 · D · E.1 · E.2 · F**. Migrations até **V46**.
Suíte verde nas **cinco** tasks — incluindo `:server:integrationTest`, que **exige Docker e não roda
junto com as outras**. **677 testes em 75 arquivos**. Baterias manuais: F.1 com 28 passos, D com 7,
E.1 com 13, E.2 com 30 e F com 32 — todas completas.

**E o débito de integração da fase foi pago (2026-09-09)**, depois do merge: 59 testes em quatro
arquivos novos cobrindo V40–V46, e a suíte de integração passou de 66 para **125 testes em 12
arquivos**. Ver a seção logo abaixo.

O app está **social e completo no ciclo**: dá para criar desafio, entrar, fazer check-in com foto
(com emoji do dia, se o desafio exigir), ver feed e ranking, **reagir e comentar**, abrir o perfil
de qualquer pessoa, adicionar como amiga e bloquear. Existe **correção** — qualquer membro
denuncia, o admin julga numa fila própria, e a decisão fica registrada para sempre. E existe
**aviso**: seis gatilhos de notificação, com push e central, inclusive com o app fechado.

**Próximo: Fase 7 em diante** (trial/assinatura já feito, falta Play Billing real) ou o débito de
prioridade alta que sobrou: **volume de dados**. Nenhuma tela foi vista com os ~4.500 check-ins de
um desafio de 50 pessoas por 90 dias, e o N+1 do `meusGrupos` (uma consulta de papel POR grupo) já
foi identificado por leitura, sem ferramenta. Precisa de duas peças: seed determinístico (VER) e
contagem de consultas por requisição (AFIRMAR) — a segunda é a que impede o N+1 de voltar, e ele
volta, porque código novo imita código existente.

## ✅ Integração V40–V46 — o débito da fase, pago (2026-09-09)

Sete migrations tinham entrado com garantias que **só o Postgres prova**. Entraram quatro arquivos:
`AmizadeIntegrationTest` (11), `NotificacaoIntegrationTest` (12), `SocialModeracaoIntegrationTest`
(17) e `AvisosDiariosIntegrationTest` (19).

**Dois defeitos apareceram só de escrever os testes, antes de rodar:**

- `AcaoDeModeracao.adminId` era `Uuid` não-anulável enquanto a coluna aceita `NULL` — e o
  `encerrarCasosPendentes` inseria `null` direto na tabela para contornar o próprio model.
  → **Model que não representa o que a tabela permite empurra alguém a contornar o model.**
- Um teste de push "passava" sem exercitar nada: usava usuário fora do grupo, e a guarda de
  filiação recusava antes. → **Teste de comportamento negativo precisa provar que chegou lá.**

**A suíte caiu de 1m18s para 44s.** Dez das doze classes passaram a compartilhar um Postgres
(`BancoDeTeste`), limpo por `TRUNCATE` no `@BeforeAll`. Não foi vaidade de benchmark: esta suíte
**atravessou duas fatias sem ser executada**, porque exige Docker e não roda com `:server:test` —
e quando rodou, a V40 tinha derrubado 51 de 62.

> **Suíte lenta é suíte que ninguém roda.**

⚠️ **Duas coisas para lembrar antes de escrever a próxima classe de integração:**

1. O `TRUNCATE` roda por **classe**, nunca por teste. **Asserção sobre a tabela inteira assume um
   isolamento que esta suíte não dá** — afirme sobre o cenário (o par, o grupo, o usuário).
2. `Database.connect` do Exposed é **estado global**. Uma classe com container próprio que o chame
   deixa a seguinte com um datasource morto — foi o que causou 39 falhas com `SQLException` durante
   a conversão, com a primeira classe da ordem passando, o que fazia o erro parecer aleatório.
   `MigrationIntegrationTest` e `DisplayNameBackfillIntegrationTest` mantêm container próprio de
   propósito (testam o schema **nascendo**) e são inofensivas porque falam JDBC cru.

### A E fechou o único mecanismo de correção do produto

Sem votação (o #17 foi emendado), **nada é verificado no momento do check-in** — foto, local e
emoji são autodeclarações. A denúncia é a totalidade da correção: se ela falha, a única resposta
do produto à fraude é sair do grupo. É por isso que a seção 6 do ARCH #33 não era opcional.

### ⚠️ Antes de rodar qualquer coisa nesta bancada

**Leia `docs/erros/README.md`.** Metade do tempo da bateria da F.1 foi gasto em
problemas de ambiente que imitavam defeitos de código: IP da LAN reatribuído pelo DHCP a outro
aparelho (que respondia **403**, indistinguível de erro de autorização nosso), conexão do FCM caída
no celular, modo Não Perturbe ligado. Nenhum deles deixa rastro no log do app.

Verificação de 60 segundos: `ipconfig` → `type local.properties` → `/health` no navegador **do
celular**. O `apiBaseUrl` mudou de casa: agora vive em **`local.properties`** na raiz (ignorado
pelo git), com default `10.0.2.2` comitado no `build.gradle.kts`. Todo build imprime
`FitJourney: API_BASE_URL = <url>`.

### Fatias fechadas nesta rodada

**A.4 — detalhe do grupo e membros.** Bateria manual verde.

**B — check-in + foto + feed (V38).** `check_ins` com UNIQUE `(group_id, user_id, local_date)`
— o "um por dia" é do banco, não do código. Camada de mídia própria (`ArmazenamentoDeMidia`,
disco em dois níveis, guarda contra path traversal); a foto é **re-encodada**, não repassada, e
o guard de bomba de descompressão lê as dimensões do cabeçalho antes de decodificar. Purga
agendada com carência de 30 dias. Validações acontecem **antes** de gravar a foto, e a foto
órfã é apagada se o INSERT perder a corrida.

**C — pontuação e ranking.** Contagem de check-ins, nunca XP ([REGRA] #18). Desempate resolvido
no servidor e não persistido: `total DESC`, `ultimo ASC NULLS LAST`, `joined_at`, `user_id`.
O critério de desempate saiu de uma correção do Rafael — no dia 1 há 50 pessoas empatadas em
zero, então empate não é caso raro, é o caso comum.

**Detalhe do grupo reestruturado:** `TabRow` + `HorizontalPager` com 4 abas — Sobre, Ranking,
Posts, Membros. Só a aba visível faz polling.

**C.1 — perfil público de terceiro (2026-08-27).** `GET /users/{id}/profile`, autenticada.
Bateria manual de 11 passos, verde.

**#35 — amizades (V40, 2026-08-27).** Código de usuário de 8 caracteres, regenerável, com limite
de 10 tentativas/hora. Par canônico (`user_a` = menor uuid) como PK: pedido cruzado é impossível no
banco. **Pedido cruzado vira amizade na hora** — os dois manifestaram a mesma intenção. Bloqueio
directional; quem foi bloqueado vê perfil indisponível. Bateria manual de 14 passos, verde.

**F.1 — notificações e push (V41 + V42, 2026-08-30).** Ver **ARCH #36**. `notifications` grava
**antes** de despachar (o push é o aviso, a central é a verdade), com `title`/`body` já
renderizados e retenção de 6 meses. `device_tokens` tem o token como PK — identidade de
**instalação**, não de usuário — e o logout apaga o deste aparelho. Cliente: badge na barra das
telas-raiz, central própria, deep link, e `AvisosDePush` (bus) ligando "chegou push" a "recarregue".

**A bateria da F.1 achou 9 defeitos** e vale ler a seção 8 do ARCH #36 antes da próxima fatia.
Dois deles **não davam sintoma nenhum**: o app funcionava, a tela não reclamava, e a pessoa
simplesmente parava de receber notificação.

- **Registro de push num `LaunchedEffect(Unit)`** — a baixa era explícita (`SairDaConta`), o
  registro dependia de composição, e login não recompõe o `AppNavHost`. Sair e entrar sem matar o
  app deixava o aparelho fora da tabela.
  > **Uma ponta explícita e a outra implícita não ficam sincronizadas por muito tempo.**
- **`filterNotNull()` antes de `distinctUntilChanged()`** — o `null` do logout era o único
  separador entre duas sessões da mesma conta. Entrar em OUTRA conta funcionava; só a própria
  falhava.
  > **A ordem dos operadores de `Flow` é o comportamento.**
- **Deep link com o app morto** era apagado pelo `popUpTo(Splash)` — "app morto" e "app aberto"
  chamam o mesmo código, mas só um concorre com o Splash.
  > **Dois caminhos que terminam no mesmo lugar não são o mesmo caminho.**
- **Três defeitos eram de observabilidade**, não de comportamento: `runCatching` engolindo falha
  sem log, token apagado sem dizer a causa, e nenhuma forma de saber qual token era de qual
  aparelho. Não quebravam nada — e foram eles que fizeram os outros custarem horas.
  > **Engolir exceção é decisão de fluxo, não de log.**

**[ARCH] O driver Postgres saiu de `runtimeOnly`** para `implementation`: a F.1 precisou de
`PGobject` (o Postgres não faz cast implícito de `varchar` para `jsonb`), e a nota "código não
referencia o driver" descrevia uma independência de banco que as migrations já não tinham.
> **Restrição que mente é pior que dependência declarada.**

**D — emoji do dia (V43, 2026-09-06).** Regra ratificada em `regras/07-checkin-e-moderacao.md` (`fase-6-decisoes.md` apagado em 2026-10-07, decisão já promovida). Sorteio **derivado**
de `(groupId, dia)` — sem tabela, sem job — e resultado **gravado** em `check_ins.emoji`, porque a
lista curada cresce. Mora em `features/group` (é regra do grupo, não do check-in). Lista de **29**
emojis, revisada de uma proposta de 44.

**Primeira fatia da fase sem defeito de comportamento na bateria.** Dois achados vieram de tentar
o que a regra não permite: não se entra em grupo já `ATIVO` (só `AGENDADO`), e o bloco de local não
aparece em grupo sem `LOCALIZACAO` — os dois corretos.

O passo mais difícil saiu por acaso, no pior cenário: a lista foi trocada **e** a meia-noite virou
no mesmo intervalo, e o check-in antigo manteve o 😎. Um sorteio recalculado na leitura teria
errado por qualquer uma das duas razões.

> **Uma mão só.** O critério que derrubou 🙌 👏 🙏 🤝 da lista só apareceu quando alguém olhou
> pensando em FAZER o gesto — selfie se tira segurando o celular. Vale para qualquer decisão de
> produto: ler a lista e executar a lista são revisões diferentes.

**E.1 — comentários e reações (V44, 2026-09-07).** Duas tabelas e não uma: comentário é
**muitos por pessoa, texto livre, ordem importa**; reação é **uma por pessoa, vocabulário fechado,
o que importa é a contagem**. Uma tabela só com `tipo` teria de carregar o índice único da reação
sem aplicá-lo ao comentário — e regra que vale para metade das linhas é regra que o banco não
garante. A PK composta `(check_in_id, user_id)` **é** o invariante "uma reação por pessoa".

A 8.2 dizia "qualquer emoji" e foi **emendada para seis**: com 50 membros escolhendo livremente o
agrupamento perde sentido e "12 👍" nunca acontece. Comentário ganhou **tela própria** em vez de
expandir no card — expandir faria o feed virar uma lista de alturas imprevisíveis que salta a cada
polling, com o teclado tapando o que se lê.

**Segunda fatia seguida sem defeito de comportamento.** O único susto foi de leitura: com dois
comentários numa tela vazia, o novo pareceu estar no topo.
> **Lista curta não distingue ordenação.** São precisos pelo menos três itens.

**E.2 — denúncia e moderação (V45, 2026-09-07).** Regra ratificada em `regras/07-checkin-e-moderacao.md` (`fase-6-decisoes.md` apagado em 2026-10-07, decisão já promovida). A máquina
`VALIDO → EM_ANALISE → INVALIDADO`, que existia na V38 sem ninguém que a movesse, agora se move.

**Duas tabelas com políticas de integridade OPOSTAS, e a diferença é o ponto:**

| Tabela | Política | Por quê |
|---|---|---|
| `group_reports` | CASCADE em tudo | é o **pedido**; alvo apagado, nada a moderar |
| `moderation_actions` | **sem FK no alvo** | é a **decisão** (6.6, append-only); precisa sobreviver ao alvo |

Dar CASCADE às duas faria a auditoria evaporar exatamente nos casos em que ela é mais necessária.
E uma FK obrigaria escolher entre isso e nunca mais poder apagar o alvo.

O alvo da denúncia é um **arco exclusivo** (`check_in_id` XOR `comment_id`, com `CHECK`), não um
`target_id` opaco: opaco custaria a integridade referencial inteira e encheria a fila do admin de
itens que não abrem.

**Decisões de produto tomadas nesta fatia:**

- **O invalidado FICA no feed, marcado.** Sumir apagaria a única evidência de que a moderação agiu
  — e a publicidade da decisão é o freio contra o abuso de quem decide.
- **O admin não vê quem denunciou.** O banco guarda (sustenta "uma por pessoa" e a auditoria), o
  DTO não carrega. Num grupo de conhecidos, denúncia identificada é denúncia que ninguém faz.
- **Motivo obrigatório.** É o atrito que separa a denúncia pensada do toque irritado — e como o
  admin julga sem saber quem reclamou, a frase é tudo o que ele tem.
- **Fila em tela própria**, com badge só na barra do admin. Uma quinta aba estaria visível para os
  49 membros que não podem abri-la.

**A bateria de 30 passos achou 2 defeitos, e os dois eram o mesmo tipo:**

- **O erro da denúncia não tinha onde aparecer.** Ia para `state.erro`, renderizado só na aba
  Sobre; quem denuncia está na aba Posts com um diálogo por cima. A mensagem existia, estava
  correta, e nunca foi lida.
  > **Erro precisa aparecer onde o gesto aconteceu.**
- **`canDelete` não olhava o status.** A regra "sob moderação não se apaga" vivia só no serviço; o
  mapper calculava só pela data. O menu oferecia "Apagar" num check-in invalidado.
  > **Duas fontes de verdade para a mesma pergunta divergem na primeira que alguém esquecer.**

**Nos dois casos o servidor estava certo e a tela não perguntou** — e o teste automatizado passava.
É a terceira vez na Fase 6 (o `MeRepository` no menu, a lista de membros da A.4, e estes).

**[REGRA ratificada] Check-in sob moderação não se apaga** — nem `INVALIDADO`, nem `EM_ANALISE`.
A 4.11 diz que apagar libera o slot; sem a guarda, quem fosse invalidado apagaria e refaria,
desfazendo a decisão do admin. Bloquear só o invalidado deixaria a fuga um passo mais cedo:
esvaziar a fila antes do julgamento.

**F — os outros gatilhos de notificação (V46, 2026-09-09).** A F.1 tinha construído a infraestrutura
inteira com **um** caso de uso; a F pluga os outros cinco. **A aposta se confirmou**: os quatro
gatilhos que nascem de ação entraram por **porta estreita**, sem `checkin` importar `notificacao`,
e nenhum defeito da bateria foi na infraestrutura.

| Gatilho | Para quem | Decisão que carrega |
|---|---|---|
| Comentário no meu check-in | dono | sem "seguir a thread" — 10 comentários gerariam 55 avisos |
| Denúncia contra mim | dono do conteúdo | **não diz quem denunciou nem o motivo** |
| Nova denúncia no grupo | só admin | **uma por CASO**, não por denúncia (6.11) |
| Check-in invalidado | dono | mesmo aviso pelos dois caminhos (julgamento e 6.10) |
| Entradas do dia, agregadas | todos | uma por grupo por dia; o teto não depende do tamanho |
| **Fila parada há 3+ dias** | só admin | **emenda** — ver abaixo |

**O quinto gatilho é de outra natureza:** não nasce de requisição nenhuma. Virou o `AvisosDoDia`,
um laço no boot como as duas purgas — mas **acordando de hora em hora**, porque o dia civil de cada
grupo vira num instante diferente conforme o fuso. Quem garante o "uma vez por dia" é a **PK
`(grupo, dia, tipo)` da V46**, não o intervalo.

Mora em `server.avisos`, **fora de `features/`**: lê membros (`group`), lê denúncias (`checkin`) e
escreve notificação. Mesma escolha do `PurgaDeMidia` — um varredor não é feature, é composição.

### Duas emendas à seção 6, decididas nesta fatia

**Expiração automática de denúncia foi RECUSADA.** A proposta era o caso sumir sozinho em 24h se o
admin não julgasse. O problema é quem ganha: **a inação do admin viraria absolvição** — bastaria
não abrir o app, e num grupo de conhecidos o admin pode ser amigo de quem fraudou. A 6.12 já
resolve pelo outro lado: ele não tem prazo **porque** o `EM_ANALISE` continua contando ponto (6.8),
então o caso parado não prejudica ninguém.

> **O remédio para fila parada é fazer o admin ver, não apagar o que ele não viu.** Daí o lembrete
> de 3 dias, contado do caso **mais antigo** — pelo mais recente, o grupo com denúncias frequentes
> nunca geraria lembrete, e é o que mais precisa.

**Denúncia em desafio ENCERRADO fecha sozinha** — e aqui resolver automaticamente é honesto: o
ranking congelou e invalidar mudaria um resultado já comemorado (6.5). Grava
`ENCERRADO_SEM_JULGAMENTO` com `admin_id` **nulo**, e é esse nulo que distingue "o admin manteve"
de "o desafio acabou antes".

### A bateria de 32 passos achou 3 defeitos — todos no gatilho novo ou no TEXTO

- **O aviso falava de uma denúncia inexistente.** *"O admin avaliou **a denúncia**"* era reusado na
  invalidação direta (6.10), onde não houve denúncia. A decisão de os dois caminhos mandarem o
  mesmo aviso continua certa; o erro era afirmar um fato que só valia num deles.
  > **Mensagem compartilhada só pode afirmar o que é verdade em todos os caminhos que a usam.**
- **O criador era contado como entrada.** Criar um desafio disparava *"1 pessoa entrou no seu
  desafio hoje"* — sobre si mesmo.
  > **Criar não é entrar.**
- **"Seu desafio" para todo mundo.** Para os 49 que entram num desafio alheio, o possessivo é falso.

Os dois últimos corrigidos por `created_by` e **não** por papel `ADMIN`: o cargo é transferível
(2.12), o fundador não muda.

**⚠️ Corrigido junto:** o cabeçalho do menu lateral **girava para sempre** quando o `/me` não
chegava — "carregando" e "falhou" eram o mesmo pixel. Agora cai para o e-mail da sessão do
Firebase, que existe offline. É a **quarta** regressão do `MenuViewModel` ligada a atravessar a
sessão; ver a seção nova do Painel Mestre.

### Higiene de armazenamento (2026-08-23)

App + cache no aparelho: **356 MB → 51,5 MB**. Mídia no servidor: 372 MB → 213 MB. PNG → WebP
(V39 reescreve os `thumb_ref`), `allowBackup="false"` e `dataExtractionRules`.

O defeito que ensinou mais: depois de converter tudo para WebP o app **continuava pedindo
`.png`**, e sobreviveu a `pm clear` e a reinstalar. O sintoma decisivo foi a **ausência** de
`GET /exercises` no log — o catálogo vinha do backup em nuvem, restaurado sem passar pela rede.

> **Backup de cache restaura mentira.** E: mudar dado que o cliente cacheia não é uma migration,
> são duas — uma no banco e uma no tempo.

Também apareceu que o **release estava quebrado desde junho** (`material-icons-core` sem
versão, resolvido por acaso no debug). `assembleRelease` entrou na CI.

### Decisões de regra desta rodada

- **2.5-A** — admin sozinho que sai **apaga o desafio**.
- **4.8 emendada** — a foto vive durante `ATIVO` + 30 dias após `ENCERRADO`. Os "90 dias a
  partir do check-in" conflitavam com desafio de duração ilimitada.
- **9.3 → 9.3-A** — **o perfil é público**: nome, nível, XP e conquistas, e nada além. Histórico
  de treino, programas, peso, altura, idade, objetivo e triagem de saúde continuam privados;
  desafios concluídos seguem só do dono (#34).
- **ARCH #35 — amizades**, escrito e **revisado no mesmo dia**, porque a 9.3-A derrubou metade
  dele. Custou meia hora de texto por ter sido escrito **antes** do código.
- **Comunidade (grupo sem fim) adiada para a v2.**

### O que a C.1 decidiu, e que vale além dela

**Duas telas de perfil, não uma com `if (souEu)`.** A tela do dono mostra "12 treinos · 3 nesta
semana" e a barra de XP do nível — privado pela 9.3-A. Uma tela só faria o isolamento depender
de alguém lembrar do `if`. Duas telas, dois tipos (`UserStatsDto` × `PublicProfileDto`): o
compilador garante o que dependia de disciplina. A **rota** continua uma só e despacha.

**Porta estreita `GamificacaoDe`**, no molde do `CheckInDeHoje`. O `PublicProfileService` recebe
`(Uuid) -> AppResult<Gamificacao>`, não o `StatsService` — que arrasta `ProgramService` e
`WorkoutGenerator` por causa do streak, que não é público.

**404 para id inexistente E malformado.** Nunca 403: "sem permissão" confirmaria a existência da
conta, e o código permanente do #35 já é superfície de enumeração.

**Ler perfil de terceiro não concede conquista.** O `AchievementService` grava ao ler, o que faz
sentido para o dono. Efeito aceito: quem treina e nunca abriu a própria tela de perfil aparece
sem medalha para os outros. A alternativa faria as medalhas de alguém depender de quantas
pessoas espiaram o perfil dele.

### Instrumentos que passaram a existir

- **`tools/seed_volume.sql`** (fora do git) — 20 grupos × 50 membros × 60 dias, idempotente,
  com `seed_entrar(email)`. Foi ele que revelou o **N+1 em `meusGrupos`** — por leitura do log,
  não por teste de carga.
- **`docs/gen_testes.py`** — varre os source sets e gera o catálogo de testes.

> **Instrumento vence adivinhação.** O N+1 apareceu porque havia volume para ler, não porque
> alguém suspeitou dele.

### Lições de método registradas

- **Todo defeito que escapou vira teste.**
- **O fake tem que implementar a REGRA, não devolver constante.** Um teste de integração passou
  a mentir porque todos os check-ins compartilhavam o mesmo `photoRef`.
- **Par de asserções duvidoso mora no MESMO teste**, senão "consertar" uma quebra a outra.
- **Texto que descreve posição envelhece a cada mudança de layout** — a frase "apague o check-in
  abaixo" quebrou quatro vezes.
- **`MutableStateFlow` de ViewModel só se escreve com `update`.**
- **Reiniciar o Ktor faz parte de rodar a bateria manual.**
- **Modo avião não testa a tela B quando a tela A, que vem antes, já trata o offline** (achado
  da bateria da C.1). Caminho que a mão não alcança precisa de teste automatizado.
- **Antes de suspeitar do código, elimine o ambiente** (F.1). Aparelho, rede e IP produzem
  sintomas idênticos aos de defeito real e custam segundos para descartar.
- **Engolir exceção é decisão de fluxo, não de log** (F.1). O processo deve seguir; alguém precisa
  poder saber por quê.
- **Ação destrutiva sem motivo registrado não é diagnosticável** (F.1).
- **A ordem dos operadores de `Flow` é o comportamento** (F.1). `filterNotNull` e
  `distinctUntilChanged` parecem comutativos e não são.
- **Dois caminhos que terminam no mesmo lugar não são o mesmo caminho** (F.1).
- **Restrição que mente é pior que dependência declarada** (F.1).
- **Teste que não compila por excesso de dependência denuncia acoplamento, não pede mock** (F.1).
- **O dispatcher de teste decide o que o teste consegue observar** (F.1). `UnconfinedTestDispatcher`
  foi o que tornou visível a emissão que o `Standard` engolia.
- **Débito só fecha quando alguém verifica, não quando alguém entrega.**
- **Lista curta não distingue ordenação** (E.1). São precisos ao menos três itens.
- **Erro precisa aparecer onde o gesto aconteceu** (E.2). Terceira vez na fase que um erro correto
  fica invisível porque a tela que o exibe não é a que está na frente.
- **Duas fontes de verdade para a mesma pergunta divergem na primeira que alguém esquecer** (E.2).
- **Teste que passa por dois motivos não prova nenhum dos dois** (E.2). Um cenário em que o admin
  também era dono do check-in deixava o teste da autoridade dele verde por acidente.
- **Fake sem o índice do banco prova o oposto do que se quer** (E.2). Os dublês reproduzem os
  índices únicos de propósito — sem isso, "uma denúncia por pessoa" passaria contando 2.
- **Não aplique correção em massa sem medir antes** (bancada, E.2). Um `UPDATE` de teste sem `id`
  atingiu 42.784 linhas do `seed_volume`; reverter às cegas teria criado o erro na direção oposta.
- **Regenerar não é atualizar** (2026-09-07). O `gen_tudo.py` lê `docs/fontes/`; fonte velha vira
  PDF com data nova, que é pior que PDF velho. **Convenção: as fontes são atualizadas ANTES de
  rodar o gerador.**
- **Mensagem compartilhada só pode afirmar o que é verdade em todos os caminhos que a usam** (F).
  Um aviso reusado herda a obrigação de ser verdadeiro em cada um — e nenhum teste pega isso,
  porque o teste afirma *que* avisou, não *o que* o texto diz.
- **Criar não é entrar** (F). A linha do criador em `group_members` é consequência da criação, não
  uma adesão.
- **Reiniciar o Ktor faz parte de cada correção de servidor.** Custou três investigações entre a
  E.2 e a F. `installDebug` atualiza o app; o Ktor precisa ser derrubado à mão.
- **`current_date` do Postgres é UTC** (F). Depois das 21h em São Paulo, `- 1 day` ainda é *hoje*
  para um grupo em `America/Sao_Paulo`. Comando de teste que depende disso mente sobre o código.
- **Teste de comportamento negativo precisa provar que chegou lá** (F). Um `isFailure` sozinho é
  satisfeito por qualquer falha, inclusive a que acontece antes do trecho em teste.
- **Nome de teste em backticks não aceita `"` no Windows** — o `.class` gerado por lambda usa o
  nome do teste, e o compilador quebra com `InvalidPathException` sem dizer qual é o culpado.

