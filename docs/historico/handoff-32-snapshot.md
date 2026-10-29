# Handoff 32 — snapshot final (2026-10-07)

> **Documento encerrado.** Desmembrado nos arquivos desta pasta; o que era "onde paramos"
> virou `docs/ESTADO.md`. Preservado integral porque duas seções não foram transplantadas:
> o "Próximo passo" (listava `#35 → D → E → F` como restantes, contradizendo a própria
> declaração de Fase 6 concluída no mesmo arquivo) e o preâmbulo. Não editar.

---

# Handoff — onde paramos

> Fonte: Painel Mestre v42 + Handoff Retomada 32, atualizado **2026-10-04** (fatia J FECHADA POR COMPLETO: Progresso desmembrado, análise de progressão, filtro por programa, faixa de semanas, janela de calendário, seleção de exercícios e detalhe por sessão de um exercício (J.5) — sem migration; mais a tela de Dúvidas Frequentes, estática e sem servidor). ARCHs: #20=determinístico; #22=programa semanal; #23=value-first (+ gate de leitura); #24=idade; #25=gate edição; #26=volume; #27=multi-programa; #28=ambientes/is_base; #29=split guiado; #30=offline-first; #31=apresentação de erro; #32=conquistas; **#33=grupos e check-in (emenda o #17: sem votação; seção 6 implementada na E.2)**; **#34=perfil público e drawer (emendado pela 9.3-A)**; **#35=amizades**; **#36=notificações (F.1)**; **#37=idioma (fatia G)**.

Ler no início de cada sessão. Só o "agora"; histórico completo no git e nos fragmentos.

## Estado atual

**A fatia G fechou: G.1, G.2, G.3, G.4 e G.5. A fatia H fechou: H.1, H.2, sessão A e sessão B.
A fatia J FECHOU INTEIRA: J.1 (desmembramento do Progresso), J.2 (análise de progressão), J.3
(filtro por programa + faixa de semanas), J.4 (janela de calendário, lista de exercícios e
seleção das linhas) e J.5 (detalhe por sessão de um exercício dentro do programa).** Fora da
fatia J, a tela de **Dúvidas Frequentes** também saiu do papel — estática, sem servidor. O app
fala português e inglês, o catálogo está 1:1 com o banco nos dois idiomas, e a tela de Progresso
passou de três números a uma análise com recorte, janela, controle de quais exercícios aparecem
e detalhe por exercício.

### O que a fatia J deixou pronto, em uma olhada

| recorte | eixo de tempo | quem escolhe |
|---|---|---|
| Todos / Avulsos | janela de calendário: 8 (grátis), 26 ou 52 semanas | chips, com cadeado no free |
| Um programa | faixa de semanas DELE: `de..ate`, teto = `max(duração, última semana com dado)` | slider |

**Grátis:** totais, "último treino vs o anterior" e carga por semana (8 semanas).
**Pago:** janela de 26/52, 1RM estimado por exercício, séries por grupo e a lista de exercícios.

⚠️ **Dois números do mesmo cartão têm de medir o mesmo recorte.** Foi o defeito do `b8b1ca2`
(tonelagem filtrada ao lado da contagem global) e a mesma classe do rótulo "esta semana" numa
faixa que terminou semanas atrás. Ao tocar nesta tela, conferir isso antes de qualquer coisa.

| | |
|---|---|
| Catálogo de textos do app | **648 chaves**, paridade pt-BR × en travada por teste |
| Frases de erro sem tradução | **0** — o `TextosDeErro.SEM_TEXTO_PROPRIO` está vazio |
| Sentinelas em português no código | **0** — o `value == "Sim"` do paywall foi o último |
| Prosa vinda do servidor | **1 ponto**, enumerado: `erroDoCampo` — `rationale` saiu (fatia "rationale derivado") |
| Troca de idioma | tela própria, três portas, `LocaleManager` |
| `users.locale` (V47) | sincronizado na abertura, numa direção só |
| Catálogo de exercícios | **923 exercícios**, `description` + tradução en carregadas (V53-V55), catálogo↔banco 1:1 |
| Exercícios sem mídia | **0** — os 26 legado foram consolidados (4) ou removidos (22) |
| Ativação de programa (V60, reverte a V59) | ponteiro `users.active_program_id`, autoridade do servidor; "Meus treinos" ganha botão "Ativar programa"; treino de hoje dentro do ativo é DERIVADO (schedule × dia da semana do servidor); Home sem match em nenhum programa pede pra completar a agenda, não sugere treino avulso |
| Análise de progressão (fatia J) | `GET /me/progress` — tonelagem, carga/semana, 1RM estimado (Epley), séries por grupo, lista de exercícios. Recorte por programa, faixa de semanas, janela de 8/26/52 e seleção de quais exercícios ganham linha. **Sem migration**: todo o dado já existia em `workout_sessions` + `session_set_logs` |
| Portão da análise | **grátis**: totais, último-vs-anterior e carga/semana (8 semanas). **pago**: janela 26/52, 1RM por exercício, séries por grupo, lista. O chip travado fica visível com cadeado |
| Conquistas | 9 + **5 de carga** (`CARGA_1T` a `CARGA_250T`), alvo em TONELADAS |
| Gráficos | Canvas puro, sem biblioteca (decisão testada e confirmada — ver Painel) |
| Migrations | até **V60** (a fatia J não acrescentou nenhuma) |

⚠️ **O que os testes NÃO cobrem, e só um aparelho responde:** o `LocaleManager`, o
`attachBaseContext`, o `recreate()` e o `PATCH /me` de verdade. Os testes provam compilação,
paridade dos catálogos e que a reconciliação é *chamada* — não que ela funciona.

### ✅ J.5 — detalhe por sessão de um exercício dentro do programa (2026-10-04)

Fecha a fatia J por completo. A lista de exercícios da J.4 ganhou "ver detalhado": abre
`ExercicioDetalheScreen`, com o histórico INTEIRO do exercício dentro daquele programa — do
início até a última sessão, sem faixa. Não herda o `de`/`ate` da tela de Progress: seria um
TERCEIRO conceito de janela concorrendo com os dois que já existem (calendário e faixa de
programa). "Ver tudo que registrei" é, ao pé da letra, tudo.

| | |
|---|---|
| Servidor | `GET /me/progress/exercicio?programId=&exercicioId=` — `ProgressService.detalheDoExercicio`, já existia de uma sessão anterior. `ProgressPolicy.evolucaoPorSessaoNoPrograma` agrupa por `sessaoId`, não por data: duas sessões no mesmo dia (raro, mas possível) não podem virar um ponto só, ou `volumeKg` somaria dois treinos como se fosse um |
| Portão | **403 `ENTITLEMENT_REQUIRED`, não campo nulo** — ao contrário do `/me/progress` (J.2). Aqui não há bloco grátis pra misturar numa resposta só: é um drill-down de dentro de uma lista que já é paga, a tela inteira é profundidade paga |
| Contrato | `ExercicioDetalheDto` (`exerciseId`, `name`, `points: List<PontoDeSessaoDto>`). Cada ponto é **uma sessão**, não uma semana — `estimated1rm` (Epley, melhor série) e `volumeKg` (soma de todas as séries do exercício na sessão) viajam juntos de propósito: força subindo com volume caindo é treino de pico, não erro |
| Cliente | `ExercicioDetalheViewModel` — online-only, sem cache, modelado no `ProgramRepository.activateProgram`. Dois gráficos em Canvas reaproveitados do Progress (`CalhaDoEixo`, `CartaoDeGrafico`, `GraficoDeLinhas`, `GraficoDeBarras`): 1RM estimado em linha, volume por sessão em barra com toque |
| PR na tela | Texto estático ACIMA do gráfico, não marcador dentro do Canvas — risco e raio de impacto menores do que tocar no `GraficoDeLinhas` compartilhado por uma fatia pequena |
| Eixo X | Por ÍNDICE de sessão, não alinhado à régua de semanas do Progress — a J.5 não tem janela de calendário, é o histórico inteiro, um ponto por sessão, igualmente espaçado |
| Teste (C2) | `ExercicioDetalheViewModelTest`, 4 casos incluindo o caminho de falha: estado inicial, sucesso popula e tira o `carregando`, falha (`AppError.Forbidden`/`NotFound`) popula `erro`, recarregar depois de um erro limpa o erro antigo |
| Verificação | `:app:androidApp:testDebugUnitTest` **150** (146 + os 4 novos), `testAndroidHostTest` e `assembleDebug` — verdes, confirmados por você no XML, não no "BUILD SUCCESSFUL" |

⚠️ **`diaMes` virou `internal`** (estava `private` em `ProgressScreen.kt`) pra reaproveitar o
formatador `dd/MM` na tela nova — mesmo padrão já usado para `CalhaDoEixo`/`CartaoDeGrafico`.

### ✅ Dúvidas Frequentes — tela estática, sem servidor (2026-10-04)

Fora da fatia J. `AppRoute.Duvidas` existia como placeholder desde uma fase anterior (abria
`EmBreveScreen`) — a fase 8 (Aprender/Wiki) já tinha confirmado por escrito que FAQ ficava fora
do escopo dela. Virou tela de verdade.

Duas decisões ratificadas com você antes de codar (C1):

1. **Conteúdo fixo no app** (`strings.xml`, pt/en), não banco — ao contrário da Wiki, que é
   migration + cache + offline-first. Para 8 perguntas curtas e estáveis, aquela máquina inteira
   seria peso morto.
2. **Accordion** (expande no lugar), não navegação por pergunta — a Wiki navega porque o
   conteúdo é longo; aqui a resposta é duas frases, navegação seria demais.

8 perguntas, todas fundamentadas em regra já implementada — nada inventado: check-in (#17,
votação 24h + ≥50% aprova + empate aprova), contestação (24h, só check-in de terceiro, via admin
do grupo), localização opt-in com PII separada, lista de recursos Premium (saída dos
`paywall_recurso_*`/`paywall_valor_*` reais), XP/nível/conquistas do perfil individual (#16),
ranking de grupo por CONTAGEM de check-in, não XP, múltiplos programas coexistindo (#27) e
autoridade do servidor (nunca o relógio do cliente).

⚠️ **Gaps deliberadamente deixados de fora, não esquecidos**: preço/plano, fluxo de
cancelamento, contato de suporte, link da política de privacidade formal — eu não tinha como
confirmar nenhum dos quatro sem inventar. Ver "Pendências ativas" abaixo.

Polimento de layout pedido depois, só visual: seta que gira (`animateFloatAsState` +
`graphicsLayer.rotationZ` — primeira vez nesse idiom específico, rotação de ícone; o
`animateFloatAsState` em si já era usado em outras telas), tingimento do card por estado
aberto/fechado, divisor entre pergunta e resposta, peso de fonte mais leve. `Card(onClick=...)`,
não `Modifier.clickable` — segue o precedente do `OutlinedCard(onClick=...)` em
`ProgramListScreen`.

Sem teste — tela 100% estática, sem ViewModel e sem lógica de negócio (nada no Tier 1/2 pra
testar; o mesmo padrão já usado para outras telas puramente de apresentação).

### ✅ J.3 e J.4 — recorte, janela e seleção (2026-10-02 e 03)

Fecham a fatia J. **Nenhuma migration**, de novo.

**J.3 — filtro por programa e faixa de semanas.** Programas COEXISTEM (V60/#27), então o gráfico
somava dois sem avisar: é correção, não conveniência. O nome do chip sai do cache LOCAL — nome de
programa é DERIVADO (V48/#37), então `availablePrograms` traz só ids e id desconhecido é PULADO em
vez de virar um chip genérico. A semana do programa conta por **dias corridos** do `started_at`
(`Math.floorDiv`, porque a divisão de `Int` trunca em direção a zero). O teto da faixa é
`max(duração declarada, última semana com dado)`: `duration_weeks` é um PLANO.

**J.4 — janela de calendário, lista de exercícios e seleção.**

| | |
|---|---|
| Janela | `?semanas=8\|26\|52`, em `JanelasDeProgresso` no **contrato** — servidor e cliente leem a mesma lista. `weeksWindow` devolve a **aplicada**, não a pedida |
| Um recorte | `naJanelaDeCalendario` espelha o `naFaixaDoPrograma`. **Duas janelas declaradas**: totais/“desde”/comparação usam todo o histórico; os três gráficos usam a janela |
| Portão | `weeklyLoad` saiu do pago. Era 100% pago, então ninguém free via 8 semanas — e “janela longa” não vendia nada |
| Scroll | acima de 12 barras o gráfico rola, com a **calha do Y fora do scroll** e começando no fim |
| Lista | `exerciseSummary` com TODOS os exercícios do recorte; a lista substituiu a legenda e virou o controle das linhas (`?exercicios=`, máximo 3 no servidor também) |
| Verificação | `:server:test` **552**, `:server:integrationTest` **174**, `:app:androidApp` **146**, `testAndroidHostTest` **29** |

⚠️ **Quatro defeitos achados olhando a TELA, não o verde — e três deles eram mentira de gráfico:**

1. **Média de séries dividindo o histórico inteiro por 8.** Volume semanal inflado ~3× em quem
   treina há meses. Passou na validação original porque o seed tem exatamente 8 semanas: o número
   certo por coincidência. A proporção entre grupos continuava honesta; só o absoluto mentia.
2. **A linha de 1RM plotava todo o histórico** ao lado de barras de 8 semanas, na mesma tela, sem
   nada dizendo isso. Inofensivo até a janela de 52 existir.
3. **O rótulo `"esta semana"` era cravado** no fim do eixo. Com faixa 10–14 em quem está na 16, a
   última barra não é esta semana.
4. **A contagem de treinos ignorava o recorte** (`stats` global ao lado de tonelagem filtrada), e
   no servidor ignorava a faixa.

> **Dois números vizinhos medindo períodos diferentes é o defeito mais barato de cometer e o mais
> caro de notar: ninguém desconfia de "30 treinos", só do gráfico.**

⚠️ **E um de processo:** o `:server:integrationTest` passou quatro commits **sem compilar** — o
construtor do `ProgressService` ganhou `programs` e ninguém rodou aquele source set. `src/test` e
`src/integrationTest` são dois source sets, e o CI roda os dois. Virou item do template de PR.

### ✅ Fatia J — Progresso desmembrado e análise de progressão (2026-10-01 e 02)

Começou com um pedido de layout e virou fatia inteira. **Nenhuma migration**: o dado que a análise
precisa já estava em `workout_sessions` + `session_set_logs` desde a V20 — faltava alguém somá-lo.

**J.1 — desmembramento (2026-10-01).** A tela de Progresso acumulava métricas, a porta das
Conquistas e o histórico inteiro. Conquistas e Histórico ganharam tela própria, alcançadas pelo
**drawer**: os dois são CONSULTA, e quem abre o app para treinar não passa por eles.

**J.2 — a análise (2026-10-02).** Quatro passos, na ordem Backend → Contrato → Cliente.

| | |
|---|---|
| Política | `ProgressPolicy` (Kotlin puro, 20 testes): tonelagem, 1RM por Epley, séries por grupo, último treino contra o anterior do MESMO treino |
| Servidor | `ProgressService` + `GET /me/progress`. O portão é **ausência**, não 403: responde 200 com os blocos pagos nulos. Para free eles nem são calculados — não há número a vazar |
| Catálogo | `ExerciseRepository.paraAnalise(ids)`: leitura crua (nome de piso + músculos primários), **sem idioma**. A tradução segue na borda |
| Conquistas | `Metrica.CARGA` com 1/10/50/100/250 **toneladas**. Atravessa servidor + contrato + `strings.xml` numa fatia só — os testes de amarração não deixam fatiar |
| Cliente | Porta `Progresso` separada de `Stats` (a Home lê stats a cada `onResume`; a análise varre o histórico e só interessa a uma tela). Cache offline-first com o **idioma na chave** |
| Gráficos | Canvas puro: barras com toque por semana, linha com área e escala compartilhada, barras horizontais ranqueadas. Cor própria (`Chart1/2/3`), validada para daltonismo |
| Tier 3 | `ProgressIntegrationTest` (9, Postgres real) + `ProgressDtoSerializacaoTest` (4, `DefaultJson` do Ktor) |
| Verificação | `:server:test` **515**, `:server:integrationTest` **173**, `:app:androidApp` **125** — todos conferidos no XML, não no "BUILD SUCCESSFUL" |

⚠️ **Quatro defeitos que só apareceram porque alguém olhou o número, não o verde:**

1. **"Mesma fórmula" era meia verdade.** `StatsService` e `ProgressService` chamavam `cargaDaSerie`,
   mas cada um repetia a condição de elegibilidade por conta própria — duas cópias de uma condição
   é como a conquista de carga e a tela de progresso discordam um dia sobre uma série de 0 kg.
   Virou `ProgressPolicy.cargaDeSerieCrua`, que faz elegibilidade E fórmula numa chamada.
2. **Um teste meu afirmava algo falso.** `e1rm(65,10)=86,7` contra `e1rm(70,8)=88,7` — eu tinha
   escrito o contrário, e a mesma conta errada estava no KDoc, a caminho de virar documentação
   mentirosa. O teste pegou o erro de raciocínio, que é para isso que ele serve.
3. **Série plana afundava na base.** Com um ponto só, `min == max`, a amplitude caía no fallback
   `1.0` e o `y` dava 0 — o ponto ia para a linha de base como se a carga fosse zero.
4. **`RAISE NOTICE` do plpgsql não tem `printf`.** O `%.1f` do script de seed imprimia o número
   com `.1f` colado.

**Verificação ponta a ponta, e esta é a parte que vale guardar:** um script em `tools/` semeou 8
semanas de treino falso numa conta de teste e o `psql` imprimiu **181,8 t em 30 sessões**. A tela
mostrou os mesmos 181,8 t e 30 treinos. Séries no Postgres → política → DTO → JSON → cache local →
Canvas, tudo conferido por um número único. ⚠️ O script **não é migration e não pode virar uma** —
mora em `tools/` (gitignored) e escolhe exercícios por GRUPO MUSCULAR, não por nome, porque nome
muda em curadoria (a V54 apagou vários).

**Aberto, aguardando você:** o filtro por PROGRAMA + faixa de semanas (J.3) está desenhado e
registrado no `debitos.md` (P4) — falta decidir se o balde de "avulsos" é selecionável.

### ✅ V60 — ativação volta a ser de PROGRAMA, reverte a V59 (2026-09-28)

Achado do Rafael testando manualmente a V59 na tela "Meus treinos": o botão "Ativar" aparecia por
TREINO dentro da semana de um programa, quando ele esperava uma escolha de PROGRAMA ("hoje eu sigo
o Push/Pull/Legs"), não um treino solto dentro dele. Duas decisões via `AskUserQuestion` (C1):

1. Ativação vira "Ativar programa" na LISTA de programas (não no detalhe, não mais por treino).
2. Sem agenda em nenhum programa ativo, a Home pede pra completar a agenda em vez de sugerir um
   treino avulso — isso substitui de vez o fallback que a entrada de 2026-09-26 (logo abaixo,
   ⚠️ **revertida**) tinha introduzido sobre a V59.

Reverte a V59 quase por inteiro, mas reaproveita a peça que ela acertou: o "treino de hoje" continua
sendo DERIVADO, só que agora cruza dois sinais em vez de um ponteiro só — o programa marcado ativo
pelo usuário, e o `dayOfWeek` de cada treino contra o dia da semana ATUAL do servidor
(`DiaDaSemanaAtual`, `TimeZone.UTC`, [REGRA] autoridade do servidor — mesmo padrão de `XpPolicy` e
`AvisosDoDia`). Programa ativo caindo num dia sem treino agendado = nenhum treino marcado, de
propósito.

| | |
|---|---|
| Desenho | `users.active_program_id` (V60, `ON DELETE SET NULL`) — mesmo padrão da V59, agora no programa. `ProgramService.activate()` valida posse via `findByIdForUser` (mesmo erro NotFound pra "não existe" e "não é seu") |
| Servidor | `POST /programs/{id}/activate` (substitui `POST /workouts/{id}/activate`, removido); `ProgramActiveWorkout` reescrito pra derivar o treino do dia via `DiaDaSemanaAtual`; `WorkoutService.get()` deriva o mesmo campo em `GET /workouts/{id}` |
| Cliente | `7.sqm` (`program.isActive`); ativação migra de `ProgramDetailViewModel` de volta pra `ProgramListViewModel` (evento `Activate(programId)` + `state.activating`); `ProgramListScreen` ganha botão "Ativar programa" + diálogo de confirmação; `ProgramDetailScreen` perde o botão por treino, mantém só o selo "· Ativo" read-only |
| Home | `HomeViewModel` perde o fallback inteiro introduzido em 2026-09-26 (`porAtivo` / `TodayWorkout.viaTreinoAtivo` / `HomeState.precisaAtivarTreino` / `CardNenhumTreinoAtivo`) — sem match de agenda em NENHUM programa, `precisaCompletarAgenda` pede pra completar a agenda em vez de sugerir um treino avulso |
| Testes (C2) | migrados, não só deletados: 2 testes de `activate` saem do `ProgramDetailViewModelTest` e entram no `ProgramListViewModelTest`; `ProgramActiveWorkoutTest`/`WorkoutServiceTest` reescritos pro novo desenho; `HomeViewModelTest` com os 2 cenários do fallback reescritos pro fim dele; `ProgramMapperTest` +1 (`program.isActive`) |
| Débito fechado | os 2 itens do P5 sobre o card "treino ativo" da Home (duração/grupo muscular ausentes, barra de progresso ausente) ficaram obsoletos — o card não existe mais. Movidos pra P6 em `debitos.md`, com data e motivo |
| Bugs achados no caminho (Gradle) | `DiaDaSemanaAtual` usou `.dayOfWeek.isoDayNumber` (API de `java.time.DayOfWeek`, não existe em `kotlinx.datetime.DayOfWeek`) — corrigido pro `when` explícito já usado em `XpPolicy`/`HomeViewModel`; comentário em `strings.xml` com `--` literal no corpo (ilegal em XML) — corrigido |
| Verificação | `:server:test`, `:server:integrationTest` (Testcontainers/Postgres), `testAndroidHostTest` (`program:data` + `program:presentation`), `:app:androidApp:testDebugUnitTest` e `:app:androidApp:assembleDebug` — todos verdes, rodados pelo Rafael |
| Migrations | V60 |

### ✅ Home: sugestão do treino ativo sem agenda — fix (2026-09-26) — ⚠️ REVERTIDA pela V60 acima (2026-09-28)

Achado do Rafael navegando no app: 4 treinos manuais, nenhum agendado, nenhum ativo — a Home
mostrava "Dia de descanso", que era mentira (`resolverTreinoDeHoje()` só conhecia a agenda
semanal, ARCH #22, nunca soube do `isActive` do V59).

| | |
|---|---|
| Regra nova | Sem match na agenda pra hoje, o treino ATIVO vira sugestão do dia (selo "· Ativo" no card, pra não parecer agendamento de verdade) |
| Card novo | Sem agenda em NENHUM programa e sem ativo → "Nenhum treino ativo" (reaproveita strings do V59) em vez de "Dia de descanso" |
| Descanso implícito (ARCH #22) | Não mudou: continua valendo quando alguma agenda existe e hoje não está nela |
| Testes | +3 em `HomeViewModelTest`, 12/12 verdes |
| Débito achado no caminho | `FakeProgramas` sem `activateWorkout()` (a interface ganhou o método na V59, o fake nunca foi atualizado) |

✅ Confirmado: o CI está rodando de fato (eu que tinha uma ref remota desatualizada e achei que
os commits não tinham sido empurrados). Rodou no PR #54 (`develop` → `main`) e pegou um problema
diferente, também real: `treino_ativo_iniciar` duplicando `comum_iniciar_treino` (V59). Corrigido
no mesmo PR, CI verde, merge feito — confirmado pelo Rafael. O gate de 2026-08-20 funciona.

### ✅ Treino ativo fechado — V59 (2026-09-25) — ⚠️ REVERTIDA pela V60 (2026-09-28, ver acima)

Veio de um mock de UI que o Rafael trouxe (tela "Meus treinos", 2 layouts: com e sem treino em
destaque). Três perguntas resolvidas antes de codar (C1): só 1 treino ativo por vez (exclusivo);
o que acontece com o progresso semanal ao trocar (resposta: NADA — sessões da semana continuam
derivadas de `workout_sessions`, não têm relação com o ponteiro); dialog de confirmação antes de
ativar. Rafael escolheu a opção A: ponteiro simples, autoridade do servidor.

| | |
|---|---|
| Desenho | `users.active_workout_id` (V59, `ON DELETE SET NULL`) — mesmo padrão do `generate()`: não é conteúdo do usuário, então **sem outbox, sem cópia otimista**. `activateWorkout()` é online-only; `invalidate()` força o próximo load a buscar a verdade do servidor |
| Escopo | TREINO, não programa — decisão deliberada pra não reviver o "1 programa ativo" que o #27 já abandonou (`ProgramDto` documenta isso no próprio KDoc) |
| Servidor | `POST /workouts/{id}/activate`; `ProgramActiveWorkout` é política pura aplicada em `GET /programs` DEPOIS do `ProgramBlur` (mesma camada, mesma ordem); `WorkoutService.get()` marca o mesmo campo em `GET /workouts/{id}` |
| `activateWorkout()` mora em `ProgramDataSource`, não em `workout` | quem CONSOME `isActive` é a tela de programa — "feature nunca depende de feature" vale também pra um endpoint HTTP só; criar a dependência só pra reaproveitar a URL teria sido pior que repetir a URL |
| Cliente | `6.sqm` (`workout.isActive`); `ProgramListViewModel` ganha o evento `Activate` + `state.activating`; `ProgramListScreen` **reescrita e substitui** a tela antiga em `AppRoute.Programs` — não é tela nova (decisão explícita do Rafael) |
| Fora de escopo (opção 1 do mock) | duração e grupo muscular no card (não existem em `WorkoutSummaryDto`) e a barra de progresso semanal. Débito registrado em `debitos.md` |
| Testes (C2) | `ProgramActiveWorkoutTest`, `WorkoutServiceTest` (+1), `ProgramMapperTest` (+1), `ProgramListViewModelTest` (+3) |
| Verificação | `:server:test`, `testAndroidHostTest` (`program:data` + `program:presentation`) e `:app:androidApp:assembleDebug` — todos verdes, rodados pelo Rafael |
| Migrations | V59 |

**Achado no meio do caminho, só no `assembleDebug` (nenhum teste de `commonTest` cobre):** dois
módulos com nomes parecidos, sortes bem diferentes. `WorkoutRepository.list()` (client,
`shared/features/workout`) está órfão desde o #27 — zero call site, substituído pelo caminho de
`ProgramRepository` — e não precisou de nenhuma mudança. Mas `WorkoutLocalDataSource`, no MESMO
módulo, é código vivo: é o cache local do `GET /workouts/{id}` e escreve na MESMA tabela `workout`
que o sync de programas popula. Faltava `isActive` em dois pontos — `salvarTreino` (erro de
compilação, `assembleDebug` pegou) e `read()` (bug silencioso: sempre devolveria `isActive = false`
mesmo com o cache certo, porque tem default e compila limpo). Corrigido nos dois.

> **Módulo sem call site ainda compila, e pode ainda escrever na tabela de outro módulo.** "Órfão"
> não é sinônimo de "inofensivo" — zero tela chamando não é zero código rodando. O critério certo
> não é "quem importa este arquivo", é "quem escreve nesta tabela".

### ✅ Fatia "rationale derivado" fechada (2026-09-22)

O `rationale` era o maior débito de arquitetura remanescente da área de idioma (ver seção "Ainda
falta" da G.4, abaixo): frase pronta em PORTUGUÊS, montada pelo `StructureEngine` e **gravada no
banco** (`programs.rationale`), exibida sem tradução no `ProgramDetail` e no `ProgramReveal` —
contável desde a G.4 no `TextoDoServidorTest`, e violando a mesma regra já aplicada a XP/nível:
**estado que é função de outra coisa é DERIVADO, não persistido** (#22, #33) — aqui a função era
`split` + `daysPerWeek` + `focusAreas`. Carregava também o último travessão do produto fora do
alcance da varredura do `strings.xml` (o `rationale` nasce no servidor, não no cliente).

Fechado do jeito que o débito sempre previu: backend estruturado → contrato tipado → cliente monta
a frase, mesma técnica da G.2 com os erros.

| | |
|---|---|
| Servidor (**V56**) | parou de gravar a frase; passa a persistir `split` como CHAVE do enum (`SplitType.name`, não mais o rótulo renderizado) e `focusMuscles` (snapshot JSON do foco NO MOMENTO da geração). Coluna `rationale` **DROPADA**, não deixada vestigial |
| Guarda na migration | `DO $$ ... RAISE EXCEPTION` se sobrar valor de `split` fora do mapeamento label→enum conhecido — a V53/V54 (mesma fatia H) não tinham essa guarda, achado de um PR review anterior |
| Cliente (`4.sqm`) | mesma troca (chave + nullable) no schema local; `split`/`focusMuscles` recriados via `INSERT...SELECT` preservando o programa manual offline não sincronizado — nunca `DROP`+`CREATE` vazio |
| Frase montada em runtime | `rationaleDoPrograma()` (`RationaleDePrograma.kt`), no idioma da tela — reaproveita `SplitType.rotulo()/.descricao()` e `MuscleGroup.rotulo()`, que já existiam desde a G.3 e só não estavam ligados a este dado |
| `split` nos DTOs/domain | virou `SplitType?`/chave `String?` em toda a cadeia (contrato → server → cliente); `null` = programa MANUAL (shell sem motor) — antes era `""`/`"Manual"`, o mesmo truque stringly-typed que esta fatia está removendo |
| `TextoDoServidorTest` | caiu de 9 para 7 pontos contáveis — `ProgramDetail`/`ProgramReveal` saíram da lista |
| Achado no review (REV, não implementação) | o KDoc do `SplitType` no `shared-contract` ainda dizia que `label`/`description` eram "chave interna do motor" e que o `WeekSpread` comparava por `.label` — exatamente o que esta fatia eliminou. Corrigido para refletir que hoje só a CHAVE (`.name`) é comparada/persistida; `label`/`description` do enum viraram documentação, não dado lido em runtime |
| Verificação | compilado e testado verde pelo Rafael (`:server:test`, `testAndroidHostTest`) — não passou pela sandbox, só pelo aparelho dele |

Detalhe completo (SQL da V56, motivo da decisão de não retroalimentar `focusMuscles` em programas
legados) em `debitos.md`.

### ✅ Débitos técnicos fechados — revisão 2026-09-22 (P0.1–P0.4 + 2/2 do P1 sem decisão pendente)

Os 77 débitos abertos tinham sido priorizados numa revisão desta mesma data (ver
`docs/fontes/debitos.md`, seção "Ordem de prioridade"). Os 6 sem decisão pendente foram fechados
numa sentada, cada um com teste (C2) e **verificado em build real pelo Rafael**, não só
implementado:

| # | Débito | Fix | Verificação |
|---|---|---|---|
| P0.1 | `exercise_translations` no `TRUNCATE` do `BancoDeTeste` apagava a carga real da V53 a cada classe | Tirado do `TRUNCATE`; `CatalogoTraduzidoIntegrationTest` reescrito p/ isolamento por CHAVE (exercício sintético por teste + `@AfterEach` + `ON DELETE CASCADE`) | `BUILD SUCCESSFUL in 1m 5s` |
| P0.2 | `:server:integrationTest` podia dar verde sem rodar (Gradle não conhecia as migrations como entrada) | `inputs.files(migrationsDoServidor)` com `withPathSensitivity`, mesmo padrão do `:konsist:test` | 3 rodadas: execução normal (1m5s) → `UP-TO-DATE` sem tocar nada (1s) → após alterar uma migration, voltou a executar (44s, 6 executed) |
| P0.3 | Teste de integração do outbox inexistente — único caminho onde uma regressão apaga dado do usuário sem erro visível | `ProgramLocalDataSourceTest` novo (3 casos) contra SQLite real via `JdbcSqliteDriver` + `Schema.create`. Infra nova: `sqldelight-sqliteDriver` (JVM) e `androidHostTest` em `program:data`, sem precedente antes | `BUILD SUCCESSFUL in 54s` |
| P0.4 | Sem guarda contra `created_at` reescrito por fora do fluxo normal em `check_ins` | Query de sanidade + 2 testes (caminho normal nunca diverge; divergência forçada é encontrada) | verde junto com P0.1 |
| P1.1 | Regex de segurança não-ancorado removia o parágrafo inteiro por menção, não só o disclaimer | Filtro por FRASE (`fimDeFrase` + `semFraseDeSeguranca`), validado antes contra os 923 `descricao_texto` reais — o disclaimer é quase sempre a ÚLTIMA frase de um parágrafo com conteúdo real, `^`-ancorar sozinho não bastaria | `:shared:features:exercise:presentation:testAndroidHostTest` verde |
| P1.2 | Tela de erro não dizia qual endereço o app tentou alcançar | Rodapé `DEBUG: ${BuildConfig.API_BASE_URL}` na `ErroDeTela`, só em debug; parte pura (`rodapeDebug`) extraída e testada | `:app:androidApp:testDebugUnitTest` verde |

**Achado no meio do caminho (P0.1):** o exercício sintético do teste usava `category = "TESTE"`,
valor inválido de `ExerciseCategory`. `ExerciseRepositoryImpl.toExercise()` chama `.valueOf()`
nesse campo **sem** `runCatching` (diferente dos campos irmãos `modality`/`movementPattern`/`level`,
todos defensivos) — a `IllegalArgumentException` virava `AppResult.Failure` genérico porque
`dbQuery` engole toda exceção, e o `ClassCastException` resultante no teste não dava pista nenhuma
da causa real.

> **Um `runCatching` genérico ao redor da query esconde a causa de qualquer exceção que não seja de
> banco — inclusive um bug no próprio teste.**

Nenhuma decisão de produto envolvida; os 71 débitos restantes (P2 em diante) seguem abertos e
priorizados no mesmo documento.

### ✅ Fatia H fechada — sessão B (2026-09-21)

Este documento (v34) previa sessão **B** (descrições), **C** (tradução) e **D** (os 26 e os 23)
como três fatias separadas. Rodaram numa sentada só, porque parar no meio teria deixado
`exercise_translations` (V49, criada na H.1/H.2 e ociosa desde então) e a `description` de 23
exercícios inconsistentes por uma sessão a mais de espera.

| | |
|---|---|
| Descrições reescritas (288 AJUSTE/PADRONIZAÇÃO + 13 ERRO) | pt-BR e en, revisadas |
| Travessão no catálogo | **0** — achou e corrigiu 2 introduzidos pela própria reescrita desta sessão |
| Débito do cliente | fechado — `ExerciseDetailScreen` não depende mais de regex em português |
| `description` carregada (V53) | **923/923** |
| `exercise_translations` en carregada (V53/V55) | **923/923** |
| Exercícios só no catálogo, sem linha no banco | eram **23** → **0** (V55, inseridos) |
| Exercícios só no banco, sem correspondência no catálogo | eram **26** → **0** (V54: 4 consolidados, 22 removidos) |
| Migrations | V53, V54, V55 |

**Achado que muda a leitura do risco aceito no matching por `name` (V53):** 4 dos 26 "órfãos" do
banco não eram órfãos — eram o MESMO exercício do catálogo sob outra capitalização/sufixo
(`Stiff com halteres` × `Stiff com Halteres`, `Levantamento terra romeno com barra` ×
`Levantamento Terra Romeno`, e mais dois pares de panturrilha/flexora). O match por `name` é
case-sensitive, então essas duplicatas nunca teriam sido pegas pela V53 sozinha — só apareceram
porque a conferência comparou os dois lados por completo, não só validou o que já batia.
`workout_exercises` migrado pro exercício moderno antes de apagar o legado; zero perda de dado
real (os 3 que tinham uso sem equivalente foram apagados com o histórico, decisão explícita do
Rafael — ambiente de desenvolvimento, usuários de teste).

`CaminhosDeMidiaIntegrationTest` (da V50) travava 900 caminhos no formato novo / 26 sem mídia /
926 exercícios como invariante numérico fixo. Os 4 asserts quebraram — esperado, o próprio
comentário do arquivo previa o dia em que a dívida seria paga. Atualizado para 923 / zero / 923.
`:server:integrationTest` verde, 144 testes.

> **Comparação de nome que ignora capitalização é o tipo de guarda que só aparece quando alguém
> lista os dois lados inteiros, nunca quando alguém só confere o que já bateu.**

Detalhe completo (23 exercícios por categoria, decisões do Rafael passo a passo) estava em
`docs/migrations/v53-traducao/V53_conferencia*.sql`; apagado em 2026-10-07 por decisão do Rafael (migration V49-V55 já aplicada, dado de descrição já carregado no banco — não há mais segunda cópia em lugar nenhum, só o histórico git se precisar recuperar o detalhe passo a passo).

### ✅ G.5 verificada no aparelho (2026-09-15)

As três coisas que teste nenhum respondia:

| | resultado |
|---|---|
| Conquistas, Perfil e Perfil público **em inglês** | ✅ os 18 textos vêm do catálogo |
| `SELECT ... FROM programs` no banco de dev | ✅ os 2 `AI` zerados, os 3 `MANUAL` intactos, inclusive um chamado *"renomeado"* |
| `:konsist:test --rerun-tasks` com a regra de insets | ✅ 487 arquivos varridos |

⚠️ **Ressalva sobre a V48, e ela é o tipo de coisa que some se não for escrita:** os três sobreviventes são `MANUAL`, então o `origin = 'AI'` sozinho já bastaria nesta amostra. As camadas do FORMATO e do TRAVESSÃO **não foram exercitadas** — nenhum programa de IA tinha sido renomeado à mão.

> **Migration que acertou porque o caso difícil não existia no banco foi testada pela sorte da amostra.**

---

## 🎨 UI: insets, movimento e a tela que faltava (2026-09-15)

Três correções que nasceram do teste no aparelho, **depois** de a G.5 fechar. Só cliente.

### O defeito dos insets teve duas metades, e a segunda foi criada pela correção da primeira

O app tem `Scaffold` aninhado — um no `AppNavHost` (menu e abas) e um em cada tela — e **ninguém tinha declarado de quem eram os insets**.

1. **Faixa preta em cima e embaixo de toda tela.** `Modifier.padding(padding)` empurra o conteúdo e não consome inset nenhum, então o `Scaffold` de dentro descontava a status bar e a navigation bar outra vez.
2. **Status bar preta acima do header.** Consumir tudo no `AppNavHost` matou a faixa dupla e tirou da `TopAppBar` de cada tela a chance de alcançar a status bar.

O arranjo final é `contentWindowInsets = WindowInsets(0)` no externo — ele deixa de reservar inset para si e passa a entregar só a **altura das barras que desenhou** — mais o `consumeWindowInsets(padding)`.

| | `mostrarAbas` = false | `mostrarAbas` = true |
|---|---|---|
| `padding` do externo | zero | altura das barras, inset embutido |
| a tela enxerga | os insets inteiros | zero |
| quem pinta a status bar | a `TopAppBar` da tela | a do `AppNavHost` |

Funciona porque `TopAppBar` e `NavigationBar` do Material 3 **já aplicam o próprio inset**. O erro era o `Scaffold` externo tirar delas essa chance.

> **[REGRA] Quem desenha a barra do topo pinta a status bar.**

Travado por regra nova no `ArchitectureKonsistTest`, com **três exceções declaradas**: `AppNavHost` (o dono), `MainActivity` (o `enableEdgeToEdge`) e **`MenuLateral`** — a gaveta fica fora do `Scaffold`, dentro do `ModalNavigationDrawer`, e é o único lugar onde esses modificadores ainda somam pixel.

### `Movimento.kt` — a escolha mora nas rotas, não nos 36 destinos

| gesto | movimento |
|---|---|
| empilhar | entra da direita; a de trás recua 1/4 e escurece |
| trocar de aba | esmaece com escala de 92%, **sem direção** |
| entrar numa tarefa (execução, check-in, paywall) | sobe de baixo |

As abas não deslizam porque a navegação entre raízes usa `popUpTo` e a pilha não guarda de onde se veio: um deslize teria de chutar um lado e acertaria metade das vezes.

Anotar os 36 `composable` seria o caminho óbvio e é o errado — **a rota 37 nasceria sem movimento**, o defeito numa tela só. Mesma lição dos insets, no mesmo dia.

> **Regra que se repete por destino não é regra: é 36 oportunidades de divergir.**

### `ExerciseLibraryScreen` ganhou `Scaffold`

Era a **única tela navegável sem um**. O `Text` de título não pintava a barra, não reservava a própria altura e não tinha botão voltar; com o edge-to-edge, passou a dividir a faixa com o relógio. Varri as outras 31 — as 7 que seguem sem `Scaffold` estão certas assim (Splash, Login, os dois passos de onboarding, as duas telas de conversão, e as duas que vivem sob as abas).

⚠️ **Não conferidos ainda:** `ProgressScreen` e `EmBreveScreen`, que vivem sob as abas.

---

---

## 🏋️ FATIA H — CATÁLOGO DE EXERCÍCIOS. H.1 e H.2 FECHADAS (2026-09-15)

Migrations até **V49**. Falta a **H.3**, que é a carga dos ~960 nomes traduzidos e depende do
Rafael.

| entrega | |
|---|---|
| **V49** `exercise_translations` | PK `(exercise_id, locale)`, `CHECK` espelhando a V47, índice por `locale` |
| Leitura por idioma | `LEFT JOIN` com fallback para `exercises.name` |
| `GET /exercises?locale=` | validado pelo `IdiomaPolicy.de`, nunca recusa |
| Cache do cliente | passou a saber **em que idioma** está guardado |
| Testes | 5 de integração + 7 unitários da decisão de rebaixar |

**Verificado no aparelho, ponta a ponta**, com uma tradução inserida à mão: em inglês o nome mudou,
em português voltou. Isso prova o join, o `?locale=` chegando, e o cache rebaixando **nas duas
direções**.

### A escolha: tabela, e o que ela custou

Era tabela contra colunas `name_en`/`name_es`. A coluna tinha uma vantagem real: o `when (idioma)`
da leitura ficaria **exaustivo**, e idioma novo quebraria o build até alguém traduzir — o mesmo
mecanismo do `TextosDeAviso`.

Perdemos isso de propósito, por causa de um número: **o app vai ter mais de quatro idiomas**. Com
dez, aquela vantagem vira vinte colunas e um build vermelho que impede lançar qualquer idioma
enquanto o último não estiver pronto.

> **A escolha entre coluna e tabela não é de modelagem, é de quantos.**

A proteção mudou de dono: saiu do compilador e virou o `CatalogoTraduzidoIntegrationTest`.

**O pt-BR não entra na tabela.** `exercises.name` é o piso, no papel que o `Idioma.PADRAO` tem no
`IdiomaPolicy` — e por isso a leitura em português **não faz join nenhum**.

⚠️ **O `LEFT` é a linha mais importante da fatia.** Com `INNER`, trocar para inglês faria o
catálogo encolher de 963 para o número de traduzidos, sem erro e sem log. Tem teste só para isso.

### ⭐ O defeito que eu introduzi consertando o defeito

O cache guardava um idioma por vez e a decisão de reusá-lo nunca perguntava qual. A correção óbvia
é carimbar por idioma (`exercises:en`) — e ela **resolve a ida e não a volta**:

| | carimbo | banco | resultado |
|---|---|---|---|
| pt-BR → en | `exercises:en` nunca marcado | — | rebaixa ✅ |
| en → pt-BR | `exercises:pt-BR` fresco de ontem | cheio, **em inglês** | pularia a rede ❌ |

> **Carimbo diz quando foi baixado. Ele não diz o que está guardado.**

São duas condições, e a segunda é a que fecha: o `ExerciseLocalDataSource` grava o idioma **na
mesma transação das linhas** — fora dela, uma falha no meio deixaria o banco afirmando português
com inglês dentro, e o app confiaria nisso para sempre.

A regra virou a função pura `precisaRebaixar`, com sete testes. Testá-la pelo repositório exigiria
`SyncStamps`, SQLDelight e um HTTP falso: três peças de infraestrutura para quatro booleanos.

### Três decisões menores que vão parecer arbitrárias depois

**O `idioma` do `ExerciseRepository` não tem valor padrão.** Um `= Idioma.PADRAO` deixaria toda
chamada antiga compilando, e é por isso que não existe — o caminho errado seria o silencioso.

**O `alternatives` traduz na BORDA**, depois do filtro, sobre os poucos ids que sobram. A
alternativa era passar o idioma para o `poolFor`, e aí o motor — que decide por taxonomia —
passaria a saber que idiomas existem para servir um caso de UI.

**O cliente manda o `?locale=` em vez de o servidor ler `users.locale`.** Aquela coluna é cópia
reconciliada numa direção só: se o `PATCH /me` falhou sem rede, ela está atrasada, e o servidor
devolveria o idioma antigo enquanto o cliente carimbaria o novo.

> **Quem cacheia a resposta tem de ser quem escolhe a pergunta.**

### ⚠️ O que a H.3 tem de desfazer

`exercise_translations` entrou no `TRUNCATE` do `BancoDeTeste`, porque hoje a tabela nasce vazia e
tudo nela é dado de teste. **Quando a carga entrar, essa linha sai**: as traduções viram dado de
referência, como os seeds do catálogo, e truncá-las faria todo teste de idioma ler o piso e passar
verde por engano. O aviso está escrito no próprio `BancoDeTeste`.

### O que ficou para depois da H.3

- **`description` e os dois regex** do `ExerciseDetailScreen` (`safetyRegex`, `warnRegex`). São
  ~960 parágrafos, e traduzi-los faz o disclaimer aparecer **duplicado** e os avisos perderem o
  destaque. Fatia própria.
- **`SAFETY_NOTE`**, literal em português no cliente, mostrado em todo exercício. Já era débito
  declarado da G.3.

---

---

## 🏋️ FATIA H — SESSÃO A FECHADA (2026-09-18)

A revisão de nomenclatura chegou pronta do Rafael (923 exercícios conferidos contra 3 quadros de
vídeo cada) e **quebrou três suposições da H.1**. A sessão A absorveu o estrago e aplicou a parte
decidida.

| migration | o que fez |
|---|---|
| **V50** | corrigiu `video_ref`/`thumb_ref` dos 900 — **as imagens estavam em 404** |
| **V51** | removeu os 39 duplicados; mídia já estava em `_removidos/` |
| **V52** | aplicou 788 nomes revisados e tirou os 9 travessões |

Verificado no banco (926 linhas), na suíte (`13 executed`) e no aparelho.

### ⭐ O defeito que ninguém tinha como ver

A revisão renomeou 1.846 arquivos de mídia para a `chave`. O banco guarda `video_ref` e
`thumb_ref` como **caminho de arquivo**, e continuou apontando para o nome por extenso. **Todos os
900 exercícios passaram a servir 404**, em silêncio, porque imagem que falta não derruba tela.

> **Caminho de arquivo guardado em banco é um acoplamento que nada no build enxerga: o dia em que
> alguém organiza a pasta, o banco fica mentindo e ninguém é avisado.**

### A ponte foi o `video_ref`, e não o nome

Casar banco × catálogo pelo NOME resolvia 93% e falhava justo onde a curadoria trabalhou. O caso
que decidiu: o banco chama `Rosca Direta com Barra no Banco Scott`; o arquivo sempre se chamou
`no colete scott` — que é o **arm blaster**, não o banco Scott. A V14 renomeou e transformou um
nome certo em errado.

> **O campo que ninguém achou importante o suficiente para curar é o que preservou a identidade.**

Cadeia usada: `video_ref` → `arquivo_gif` do backup original → `nome_original` → `chave`. Resolve
900 de 965.

### As duas lacunas opostas, e uma delas é pior do que eu disse

| | qtd | situação |
|---|---:|---|
| no banco **sem mídia** (`video_ref` vazio) | **26** | nasceram nas migrations de curadoria |
| no catálogo e **fora do banco** | **23** | têm mídia e chave; vários com sufixo `(1)`/`(2)` |

⚠️ **Eu classifiquei os 26 como cosméticos e estava errado.** A saída do
`WorkoutGenerationIntegrationTest` mostrou `Stiff com halteres` — um dos 26 — **dentro de um
programa gerado**. O motor os prescreve: 1 em cada 16 exercícios do treino de alguém chega com
miniatura em branco e nome fora do padrão.

> **Débito classificado pela lista revelou outro tamanho quando o motor o escolheu.**

### Achados de método desta sessão

**Duas guardas minhas erraram a mira.** A da V50 afirmava o formato para a tabela inteira quando a
migration tocava 900 de 965, e abortou a suíte. A mesma falha estava no teste. A correção foi
afirmar **contagem exata** em vez de propriedade universal.

> **Guarda que afirma sobre a tabela inteira quando a migration tocou uma parte dela acusa o que
> não mudou.**

**O `:server:integrationTest` deu verde sem rodar** — `BUILD SUCCESSFUL in 948ms` com Testcontainers
é impossível. Mesmo defeito que o `:konsist:test` já tinha. **Use `--rerun-tasks`.**

**Pull-to-refresh não existia.** O `ExerciseListEvent.Refresh` estava lá, com o comentário *"o
usuário pediu: fura o TTL"*, e nada o emitia. Sem ele, um deploy que corrige o catálogo leva 24h
para chegar e ninguém pode apressar — foi exatamente o que aconteceu com as mídias quebradas.

> **Evento que ninguém emite não é recurso: é a metade de um recurso que passa por pronta na
> leitura do ViewModel.**

---

### Próximo passo

**Sessão B — descrições em pt-BR.** 1,1 milhão de caracteres para 923 exercícios, mais a decisão
que arrasta cliente: o catálogo tem `descricao` (HTML) **e** `descricao_texto` (texto puro), e o
`ExerciseDetailScreen` faz `split("\n\n")` com dois regex de palavras portuguesas.

Depois: **C** (tradução: 900 nomes + 900 descrições em `exercise_translations`) e **D** (os 26 e os
23). B vem antes de C para não carregar o inglês num formato que vai mudar.

⚠️ **A G.5 nasceu de uma pergunta que nenhuma varredura respondia.** Perguntado "quais telas
faltam", o inventário respondia *nenhuma* — e respondia certo, porque ele mede LITERAIS do cliente.
As três telas de conquistas estavam em português vindas de um enum do servidor. A ordem de conferir
ficou assim, e vale repetir na fatia H:

1. varredura de literais (`inventario_strings.py`) — pega o que o cliente escreve;
2. `.name` de enum nas telas — pega identificador virando rótulo;
3. **os DTOs do contrato, campo a campo, procurando `String` que é prosa** — pega o resto, e é o
   passo que faltava.

> **Perguntar "que tela falta" devolve a resposta do medidor, não a da tela.**

---

## 🔤 FATIA G — IDIOMA. G.1, G.2, G.3 e G.4 FECHADAS (2026-09-11)

Migrations até **V47**. A G.3 é só de cliente: nenhuma migration, nenhum arquivo de servidor.

**Suíte verde nas cinco tasks, medida em 2026-09-10 com `--rerun-tasks`: 739 testes em 88 arquivos,
zero falhas, zero pulados.** A G.3 acrescentou 12 testes em 3 arquivos ao `:app:androidApp`, que foi
de 63 para **75 em 14**.

| task | arquivos | testes |
|---|---:|---:|
| `:server:test` | 42 | 450 |
| `:server:integrationTest` | 13 | 132 |
| `:app:androidApp:testDebugUnitTest` | 14 | 75 |
| `testAndroidHostTest` (18 módulos KMP) | 18 | 76 |
| `:konsist:test` | 1 | 6 |

⚠️ **Rode o Konsist com `--rerun-tasks`.** Sem isso ele fica `up-to-date` e dá verde sem varrer.

> **Número de teste que ninguém mediu é pior que número nenhum: parece verificado.**

⚠️ **Rode o Konsist com `--rerun-tasks`.** Sem isso ele fica `up-to-date` e dá verde sem varrer.

**Objetivo declarado:** quando estiver 100%, manter **100% da UI traduzida**, com ressalva de ações
humanas (comentário de usuário não se traduz). Primeira leva: **pt-BR e en**. A estrutura é aberta
para N idiomas; a lista completa chega depois, já traduzida.

**Decisão que estrutura tudo (ARCH #37):**

> **Texto que o usuário lê vive em UM lugar: o `strings.xml` do cliente.** O servidor manda dados,
> códigos e chaves, nunca prosa.

A exceção é uma só e é principiada: **a notificação**, porque push só-dados não aparece com o app
fechado (já decidido no #36). Por isso o servidor tem um catálogo, e só um: os ~14 textos dos 7
avisos, em `TextosDeAviso`.

### O que a G.1 entregou

- **V47** `users.locale`, NOT NULL, default `pt-BR`, `CHECK` de vocabulário fechado.
- **`Idioma` e `IdiomaPolicy`** no `shared-contract`, usados pelos dois lados. Cadeia de fallback:
  exato, depois só a língua ignorando a região (`pt-PT` cai em `pt-BR`), depois o piso.
- **`Aviso` virou chave mais parâmetros.** Quem renderiza é o `NotificacaoService`, que já lia o
  usuário para pegar os tokens.
- **`PATCH /me`** com `displayName` e `locale` anuláveis. `null` significa não mexer.

### Três coisas para lembrar antes de mexer nisto

1. **`locale` viaja como `String`, nunca como enum.** Precedente do `ErrorResponse`: *"código novo
   no server não quebra cliente antigo"*. Enum no fio faria um idioma novo derrubar a
   desserialização do `/me` INTEIRO num app antigo.
2. **O compilador é o `MissingTranslation` do servidor.** `ChaveDeAviso` é sealed e os `when` de
   cada idioma são exaustivos: aviso novo quebra o build em todo idioma não traduzido, idioma novo
   quebra o `render`. Trocar isso por enum mais mapa perderia a verificação sem avisar.
3. **Ninguém tem o idioma trocado por uma atualização.** Quem já usa continua em pt-BR mesmo com o
   celular em inglês. O inglês passa a existir na tela de Conta, por escolha explícita.

### Sem travessão, sem caixa alta

Nem `—` nem `–`, em nenhum idioma, nem na documentação. Verificado por teste no `TextosDeAviso` **e,
desde a G.3, no `strings.xml` inteiro** (`CatalogoDeStringsTest`). Os 15 que existiam no cliente
foram reescritos no portão do pt-BR, em 2026-09-10.

Caixa alta também não, e essa é decisão nova da mesma data: 12 cabeçalhos vinham em maiúsculas
cravadas no texto e passaram a caixa normal. **As telas mudaram de aparência de propósito** — o
Compose não tem `text-transform`, e deixar a caixa no dado obrigaria todo idioma a herdar uma
escolha tipográfica feita olhando só para o português.

### O que a G.2 entregou (2026-09-10)

As 103 frases de erro do servidor saíram de lá. **67 códigos, 87 frases distintas no cliente.**

- **Não eram 104, eram 87.** *"Programa não encontrado"* estava em 7 lugares, *"Grupo não
  encontrado"* em 5. Consolidar onde a situação é a mesma tirou 17 traduções do caminho.
- **Seis frases cobriam situações opostas** e foram desmembradas. A pior: *"Usuário não
  encontrado"* servia a "a outra pessoa sumiu" **e** a "a sua própria conta sumiu".
- **19 frases reescritas**, tirando jargão (`id`, `cursor`, `workoutId`) e o único travessão do
  catálogo.
- **`docs/gerados/erros.md`**, gerado, com o contexto de cada erro. É o documento de validação.

**Três coisas para não desfazer sem pensar:**

1. **`ErrorCodes.PORTOES_DE_PLANO`** é um CONJUNTO, e o `ErrorUi` compara com ele. Era
   `if (code == ENTITLEMENT_REQUIRED)`, e dar código próprio a cada portão **quase apagou o paywall
   sem quebrar build nem teste**.
2. **`TextosDeErro.SEM_TEXTO_PROPRIO`** lista as 9 frases que carregam número do servidor e por
   isso **não serão traduzidas**. Um teste exige que todo código tenha texto ou esteja na lista.
3. **Código desconhecido cai na `message` do servidor.** É a lição do #31 sobrevivendo: servidor
   novo com app antigo continua explicando melhor.

## ✅ G.3 CONCLUÍDA — extração dos literais (2026-09-10)

**634 literais extraídos. 605 chaves no catálogo (593 `string` + 12 `plurals`). Zero literais em
posição forte** — não sobrou um `Text("…")`, `label=` ou `title=` em português no app inteiro.

O inventário caiu de **673 candidatos em 49 arquivos** para **39 em 8**, e os 39 restantes estão
todos declarados:

| Onde | Quantos | Por quê |
|---|---:|---|
| `PaywallScreen` | 23 | dívida adiada para a **G.4** por decisão de 2026-09-10 (ver abaixo) |
| `ExerciseDetailScreen` | 5 | os dois regex e o aviso de segurança — **fatia H** |
| repositórios, `Localizador`, push | 11 | falsos positivos: chaves de cache, `@SuppressLint`, logs |

Rode `python docs/geradores/inventario_strings.py` para conferir; o número tem de continuar 39.

### A convenção (documentada no topo do `strings.xml`)

**Chave = `<area>_<elemento>`**, e a área vem do **pacote da tela**, não do que a frase diz.

> Chave que descreve o texto envelhece quando o texto muda; chave que descreve o lugar sobrevive.

Quatro prefixos especiais: `comum_` (usado em mais de uma tela), `enum_<familia>_<valor>`,
`erro_<codigo>` e `data_` (dia/mês abreviados). O resto por área (`login_`, `grupo_detalhe_`,
`checkin_`). **Todo string tem comentário de contexto, e um teste exige isso.**

### Os três mecanismos que a fatia acrescentou

**1. `ui/Rotulos.kt` — rótulo de enum num lugar só.** Cresceu de ~45 para ~90 termos:
`MuscleGroup`, `Level`, `Goal`, `TrainingEnvironment`, `ExerciseCategory`, equipamento, prescrição,
**`GroupRule`, `GroupState`, `JoinBlock`, `CheckInStatus`, `SplitType`, `BodyLimitation`** e os dias
da semana. Devolvem `@StringRes Int`, nunca `String`: continuam Kotlin puro e testáveis sem Android.

**2. `ui/Frase.kt` — a fronteira "quem escreve a frase" virou TIPO.** `Frase.Recurso(@StringRes)` ou
`Frase.DoServidor(String)`. Existe porque o `ErroVisual.texto` é bimodal: catálogo quando o código é
conhecido, `message` do servidor quando não é (a lição do #31). Guardar isso como `Int?` + `String?`
compila igual e deixa o ramo esquecido passar em silêncio.

**3. `<plurals>` — a concordância saiu do Kotlin.** Doze plurais. Havia `if (n == 1) "dia" else
"dias"` espalhado: isso é **gramática do português cravada no cliente**, e traduzir a palavra não
conserta a regra — russo tem três formas, árabe seis, e o inglês trata zero como plural.

> **Extrair a palavra e deixar a concordância no código traduz o vocabulário, não o idioma.**

### ⚠️ AS TRÊS ARMADILHAS. Custaram build quebrado, e voltaram em toda área.

**1. O import do `R` entra ANTES de qualquer substituição.** Os curingas do Compose
(`material3.*`, `layout.*`) trazem os `R` das bibliotecas para o escopo. Sem
`import dev.rafael.app.R`, você recebe ~80 erros em cascata que **não mencionam import nenhum** —
dizem *"class R does not have a companion object"*. Aconteceu no `QuizScreen`, `QuizSteps` e
`PaywallScreen`, os três arquivos com curinga.

**2. Interpolação vira placeholder posicional, nunca concatenação.** `"Também: " + lista` →
`stringResource(R.string.x, lista)` com `%1$s`. O índice vem **antes** das flags (`%1$02d`). Um
teste varre o catálogo inteiro atrás de `%s` sem posição.

**3. `map` antes de `joinToString`.** `joinToString { stringResource(...) }` **não compila**:
`joinToString` não é inline e a lambda não herda o escopo `@Composable`. `map` é inline e herda.

**Quarta, descoberta nesta rodada:** `stringResource` dentro de `try/catch` é terreno em que o
compilador do Compose já foi restritivo. O `dataCurta` do Progresso resolve o `runCatching`
primeiro e formata depois.

### As três listas de débito, e o que aconteceu com elas

A extração produziu débito enumerado por teste. **No portão do pt-BR, em 2026-09-10, duas foram
pagas e viraram proibição:**

| | Durante a G.3 | Agora |
|---|---|---|
| **Travessões** | 15 enumerados | **proibidos** — reescritos, teste falha se voltar |
| **Caixa alta** | 12 enumeradas | **proibida** — 12 cabeçalhos em caixa normal, **as telas mudaram de aparência de propósito** |
| **Textos repetidos** | 19 pares | **mantidos**, cada um com o motivo escrito |

> **Débito enumerado só tem dois finais decentes: ou é pago e o teste endurece, ou a lista vira
> mobília.**

**Travessão:** onde ele introduzia a consequência virou `:` ou `.`; onde juntava orações
coordenadas, virou `,`. O décimo quinto era o travessão **sozinho**, marcando "não preenchido" na
Conta — virou `Não informado`. Marcador tipográfico não é palavra: leitor de tela lê um traço.

**Caixa alta:** o Compose **não tem `text-transform`**. Ou a caixa está na string, ou o código chama
`.uppercase()` — e `.uppercase()` impõe a caixa a todo idioma e erra o i turco. A terceira saída é
a única que não mente, e custa a aparência de 12 cabeçalhos.

**Repetidos:** três famílias. Nove pares são enums distintos que coincidem em pt-BR
(`MuscleGroup` × `ExerciseCategory`); unificar acoplaria dois enums do contrato. **Cinco são falsos
duplicados, e são os perigosos** — cada um tem aviso cruzado nas duas pontas:

| par | não é a mesma coisa |
|---|---|
| `grupo_detalhe_sair_confirmar` × `menu_sair` | sair do DESAFIO × encerrar a SESSÃO (*Leave* × *Log out*) |
| `programa_detalhe_descanso` × `sessao_descanso` | dia sem treino × cronômetro entre séries (*Rest day* × *Rest*) |
| `grupo_detalhe_fazer_checkin` × `checkin_enviar` | ABRIR a tela × ENVIAR o check-in (*Check in* × *Submit*) |
| `home_secao_treino_de_hoje` × `home_treino_de_hoje` | o RÓTULO do card × o NOME de um treino sem nome |
| `app_name` × `login_titulo` | a marca (não se traduz) × o título da tela |

Os quatro últimos são "mesma ação, telas de contexto diferente" e ficaram separados por decisão:
`erro_acao_voltar` × `comum_voltar`, `erro_acao_tentar_de_novo` × `programa_reveal_tentar_de_novo`,
`checkin_invalidar_confirmar` × `moderacao_invalidar`, `login_email` × `conta_email`.

> **Duplicata que alguém "limpa" sem ler o comentário quebra uma tela num idioma que ninguém desta
> equipe lê.**

### Os 5 arquivos de teste que a fatia deixa (26 testes, em `app/.../ui/`)

| | O que impede |
|---|---|
| `CatalogoDeStringsTest` (10) | forma do catálogo: comentário obrigatório, placeholder posicional, plural completo, sem travessão, sem caixa alta, sem identificador vazado, e a lista dos repetidos |
| `TextosDeErroTest` (8) | todo `ErrorCode` tem frase **e a chave é derivada do código** (`PESSOA_NAO_EXISTE` → `erro_pessoa_nao_existe`) |
| `ErrorUiTest` (6) | a política do #31 sobrevive: código conhecido → cliente, desconhecido → servidor |
| `TextoDoServidorTest` (1) | **conta** os 9 pontos onde texto do servidor chega à tela, por arquivo |
| `LiteraisAdiadosTest` (1) | a dívida do paywall não cresce |

O `CatalogoDeStrings` lê o `res/values/strings.xml` **como arquivo**, num teste JVM comum: `R` num
teste devolve um `Int` sem o texto, e resolver id para frase exigiria Robolectric.

> **Invariante que só vale para a área que você está extraindo não é invariante, é revisão.**

### ⚠️ Cinco defeitos que a extração desenterrou

1. **`GroupRule` mostrava `localizacao` sem cedilha.** O detalhe do desafio derivava o rótulo de
   `it.name.lowercase().replace('_',' ')`. **Segundo caso** do mesmo padrão — o primeiro foi o
   `ExerciseCategory` mostrando "Chest" ao usuário brasileiro.
   > **Rótulo derivado do nome do enum não é rótulo, é o identificador com maiúscula.**
2. **`MuscleGroup.FOREARMS` já tinha divergido:** "Antebraço" no `QuizSteps`, "Antebraços" no
   `ExerciseDetailScreen`. Um idioma, duas cópias, e já não combinavam.
3. **Duas `AppError.Unexpected("frase")` que ninguém lia** (`WorkoutSessionViewModel`,
   `PaywallViewModel`). `Unexpected` não carrega código, o `ErrorUi` escreve o texto da família e
   **descarta a `message`**. Removidas.
4. **Quatro cópias dos nomes dos dias da semana**, em três grafias. Hoje há duas famílias, de
   propósito: `quiz_dia_*` (etiqueta de chip, capitalizada) e `data_dia_*` (linha de data compacta,
   minúscula). A ordem é ISO e é **contrato**, não apresentação — o índice vira o número enviado.
5. **`SplitType.label` está documentado como "texto de UI" e não é:** o `StructureEngine` o grava no
   `ProgramSkeleton` e o `WeekSpread` faz `split == SplitType.FULL_BODY.label`. **O servidor compara
   aquele campo.** O cliente passou a ler `SplitType.rotulo()` do catálogo e o contrato ficou
   intacto — mas o KDoc do enum precisa ser corrigido.
   > **Campo que o servidor compara não é rótulo, mesmo escrito em português.**

### ✂️ FORA DO ESCOPO (decidido, não esquecido)

**`PaywallScreen`, para a G.4.** A tabela comparativa guarda o valor de cada célula como `String?` e
faz `value == "Sim"` para escolher entre ✓, ✗ e texto. Aquele `"Sim"` **é sentinela, não texto de
tela**: mandá-lo ao catálogo quebraria a comparação em silêncio no dia em que alguém o traduzisse.
A saída é trocar o `String?` por um tipo fechado (`Sim` / `Nao` / `Texto(@StringRes)`); enquanto
isso, as 6 palavras (17 ocorrências de `"Sim"`) estão fixadas no `LiteraisAdiadosTest`.

> **Sentinela escrito em português é código ramificando em idioma, não texto de tela.**

**Terceira ocorrência do padrão nesta base.** As outras duas ficaram: o `"Não encontrado"` do
`AppError` virou sentinela **derivada** (`AppError.NotFound().message`, sem literal duplicado), e o
`SplitType.label` é dado do servidor.

**`ExerciseDetailScreen`, para a fatia H:** os dois regex que detectam aviso em português e o texto
de segurança. É lá que a descrição do exercício muda.

**`erroDoCampo`, para a G.4:** devolve a frase do servidor para um `fieldErrors` — mapa aberto, sem
código. Mesma classe do `SEM_TEXTO_PROPRIO`; a saída para os dois é o servidor mandar chave.

### ✅ `gen_strings.py` (2026-09-10)

`docs/geradores/gen_strings.py` → `docs/gerados/strings.md` → `FitJourney_STRINGS.pdf`, e já
registrado no `gen_tudo.py`. **605 chaves em 44 seções, 6 alertas.** É o documento que vai para
quem traduz, e o único momento em que alguém lê as 605 frases seguidas, fora da tela que as
justifica — que é exatamente como o tradutor vai encontrá-las.

Três coisas existem só nele, e nenhuma sai do `strings.xml`:

1. **ONDE cada frase aparece**, tirado do código e não do comentário. `conta/ContaScreen.kt:130` é
   verdade verificável; quando o comentário discorda dele, é o comentário que está velho.
2. **Conferência de parâmetros entre o XML e a chamada.** `%1$s` no catálogo com
   `stringResource(R.string.x, a, b)` na tela **não é pego pelo compilador nem pelo Lint** entre
   idiomas — estoura em runtime, no idioma de quem traduziu.
3. **Os 5 avisos cruzados em destaque**, escritos à mão porque não podem sair do código: que
   `Sair` do desafio e `Sair` da conta sejam palavras diferentes em inglês é verdade sobre o
   PRODUTO. O script confere a cada rodada que os pares ainda existem e ainda são idênticos.

⚠️ **A primeira rodada emitiu 324 alertas e três quartos eram defeito do medidor, não do
catálogo** — vale mais como aviso do que o script:

| Alerta | Era | Fim |
|---|---|---|
| Parâmetros do código × do catálogo | **51**, todos `declara N e passa N-1` | **0**. A contagem de vírgulas já excluía o id, e o script descontava de novo. **Viés constante não é ruído: é bug do medidor, e parece que está funcionando** |
| Rótulo curto sem limite | **243** de 605 | **removido**. 40% do catálogo não é dívida, é o formato de um app: botão, aba e título são curtos. Virou REGRA em "Como ler cada linha", que se lê uma vez, em vez de 243 linhas que se leem nunca |
| Sem comentário de contexto | **24** | **0**. Eram as primeiras chaves de cada seção, com só a faixa `==== Nome ====` acima. O teste aceita a faixa; **o tradutor lê a linha, não o arquivo** — os 24 comentários foram escritos |

Os **6 alertas restantes são informação, não defeito**: 3 frases longas (corpo de diálogo),
`app_name` (usada no manifest, que o script não varre) e 2 itens prefixados com `· %1$s`, que são
pedaço de frase montada pelo código.

> **"Errar para mais" tem um limite, e o número é o diagnóstico.** Alerta que acende em quatro de
> cada dez linhas ensina a pular a seção inteira — inclusive os 51 de verdade logo acima dela.

### O que ficou fora da G.3 (histórico)

- **`rationale` derivado no cliente** (débito registrado, ver abaixo).

---

## 🌐 G.4 CONCLUÍDA — o inglês (2026-09-11)

> As subseções abaixo estão em ordem cronológica, como aconteceram. O resumo do que a fatia
> entregou está em **Estado atual**, no topo; aqui ficam os PORQUÊS, que é o que não cabe numa
> tabela.

| entrega | |
|---|---|
| `values-en` com as 620 chaves | paridade travada por `CatalogoEmInglesTest` (10 testes) |
| Tela de idioma | gaveta + Conta + ajustes do sistema, um mecanismo |
| Barra de abas no catálogo | `BottomTab.label` era `String`; virou `@StringRes` |
| Paywall | sentinela `"Sim"` virou tipo fechado `Celula` |
| 9 frases com número do servidor | entraram no catálogo; `SEM_TEXTO_PROPRIO` vazio |
| `users.locale` | `PATCH /me` reconciliado na abertura |
| Grupos e Conquistas | pararam de esconder falha de rede |


**`values-en/strings.xml` está escrito: 593 `string` + 12 `plurals`, as 605 chaves, paridade
verde.** O portão do pt-BR passou, e as decisões que eram do Rafael (os quatro pares que têm de
divergir, a ordem da data, etc.) foram tomadas e aplicadas — `docs/revisao-ingles.md` (documento
de passagem) foi apagado em 2026-10-06 depois da decisão, como o próprio arquivo pedia.

### ⭐ O invariante que sustenta a fatia

> **Chave que falta no `values-en` NÃO quebra: cai no pt-BR, em silêncio.**

O Android resolve o que não achou subindo até a pasta padrão. O app roda, nada é logado, e quem
escolheu inglês lê português. É o pior modo de falha que existe — o que falha alto se conserta
hoje; o que falha calado fica até um usuário reclamar. Por isso a tradução **nasceu como arquivo
que o build confere**, e não como planilha para alguém transportar à mão depois.

`CatalogoEmInglesTest` (10 testes) tranca: conjunto de chaves nos dois sentidos, conjunto de
parâmetros posicionais por chave e por plural, `one`+`other` em todo plural, travessão e caixa
alta, frase vazia, os **4 pares cruzados obrigatoriamente divergentes**, a marca idêntica de
propósito, e as **36 chaves iguais nos dois idiomas** fixadas em lista.

O `CatalogoDeStrings` virou `open class Catalogo(caminho)` com dois objetos: `CatalogoDeStrings`
(pt-BR) e `CatalogoEmIngles`. Mesma leitura, perguntas diferentes.

### O que o inglês NÃO repete

Os comentários de contexto ficam só no pt-BR. **Duas cópias da mesma explicação são dois lugares
para atualizar e um para esquecer** — a falha que o #37 existe para impedir. No `values-en` só
sobraram ~10 comentários, todos registrando uma decisão específica do inglês que alguém
"consertaria" de volta para defeito: os pares cruzados, a inversão dia/mês, a capitalização.

### As três coisas que o inglês faz diferente, e não é engano

| | pt-BR | inglês |
|---|---|---|
| **ordem da data** | 13/09 | 09/13 — 3 chaves invertem a FRASE, não os índices |
| **caixa de dia e mês** | `seg`, `set` minúsculos | `Mon`, `Sep` — as famílias `data_dia_*` e `quiz_dia_*` ficam idênticas |
| **zero** | singular | plural, coberto pelo `other` |

### ⚠️ Achado: o teste de caixa alta tinha um buraco

`grupo_detalhe_participantes` (`PARTICIPANTES · %1$d`) e `amigos_secao_lista` (`MEUS AMIGOS · %1$d`)
atravessaram a faxina de 2026-09-10 **com o teste verde**: o invariante comparava
`v == v.uppercase()`, e `%1$d` tem um `d` minúsculo que vira `D` em caixa alta — a string nunca era
igual a si mesma. Os dois foram para caixa normal e o teste passou a **remover o placeholder antes
de comparar**. Duas telas a mais mudam de aparência, pelo mesmo motivo que as outras doze.

> **Invariante com buraco é pior que invariante ausente: o ausente você sabe que não tem.**

### ✅ Débitos fechados em 2026-09-11

Cinco linhas do `debitos.md`, e **duas delas já estavam pagas pelo código sem ninguém fechar a
linha** — o defeito espelhado do que eu fiz ontem ao subnotificar o que estava pronto.

| débito | o que era |
|---|---|
| ✅ `:konsist:test` ficava `UP-TO-DATE` sem varrer nada | O teste lê os `.kt` de todos os módulos pelo filesystem e o Gradle só conhecia `konsist/src`: dava para quebrar uma regra em qualquer módulo e a task passava sem abrir o arquivo. Os fontes viraram `inputs.files`, e a guarda de árvore vazia vive no `doFirst` (na configuração, ela fazia todo `.kt` virar entrada do build script e custava o cache de configuração de TODO build). **Provado:** `Reusing configuration cache` + `1 executed` + `varrendo 479 arquivos .kt` na mesma rodada. Custou 3 tentativas e 2 leituras erradas minhas — ver `debitos.md` |
| Reticências divergiam (`...` × `…`) | Três frases usavam `…`, uma usava três pontos. Atravessou o portão do pt-BR porque **ninguém enxerga a diferença lendo** — quem enxerga é o leitor de tela. Unificado e proibido nos dois catálogos |
| Preâmbulo velho do `erros.md` | Descrevia o cliente de ANTES da G.2 (*"o app mostra a frase do servidor literalmente"*), com o gerador rodando verde a cada `gen_tudo.py` |
| DOC: `videoRef`/`thumbRef` nullable | **Já não era verdade.** O `ExerciseDto` é non-null dos dois lados |
| DOC: "offline-first" × "offline leve" | **Já não era verdade.** O #30 foi ratificado e a escrita offline fechou em 2026-08-16 |

### ⚠️ E um que não era obsoleto: `enum.name` na tela

`ExerciseLibraryScreen` mostrava `cat.name` no chip de filtro e `ex.category.name` na linha da
lista — `FUNCTIONAL_HIT` e `LOWER_BACK`, **em inglês e em caixa alta, para o usuário brasileiro**,
numa das telas mais visitadas do app. Corrigido para `stringResource(cat.rotulo())`.

**A G.3 não tinha como pegar.** O inventário varreu LITERAIS, e `enum.name` nunca foi literal: a
varredura que declarou "zero literais em posição forte" estava certa e mesmo assim havia texto não
traduzido na tela. É a terceira ocorrência deste padrão nesta base (`MuscleGroup` e `Environment`
foram as outras duas, achadas na G.3).

> **Invariante que mede a forma errada do defeito não mede nada.**

Débito novo aberto para a guarda que falta: regex não serve aqui — ela não enxerga o TIPO do
receptor, e um teste por regex daria confiança sem base. A forma provável é uma regra de Konsist
sobre a AST. Varri o resto das telas: os outros 17 `.name` são dado legítimo.

### ✅ Tela de idioma (2026-09-11)

Alcançada por **duas portas** — item na gaveta, ao lado de Configurações da conta, e linha dentro
de Conta — e por uma terceira que não é nossa: **os ajustes do sistema**. `AppRoute.IdiomaDaInterface`.

#### [REGRA C1] `LocaleManager`, e não `AppCompatDelegate`

O caminho que a documentação do Android recomenda foi **recusado com motivo**. Ele exige
`AppCompatActivity` (a `MainActivity` é `ComponentActivity`) e, pior, um tema descendente de
`Theme.AppCompat` — este app usa `@android:style/Theme.Material.Light.NoActionBar`, um tema de
PLATAFORMA, e não tem `themes.xml`. Adotá-lo custaria dependência nova, troca da Activity raiz e um
tema escrito do zero, com risco visual em toda tela. Para dois idiomas, caro demais.

O `LocaleManager` é a API da própria plataforma (33+) e entrega o mesmo, **inclusive a porta nos
ajustes do sistema**, sem nada disso. `minSdk` é 24, então abaixo do 33 a preferência vive numa
`SharedPreferences` aplicada no `attachBaseContext` — mesmo estado, guardado onde o sistema ainda
não guardava, e ali a porta dos ajustes não existia de qualquer forma.

> **A recomendação da documentação vale para o app médio. O preço dela é do seu app.**

#### Duas portas não são dois mecanismos

Gaveta, Conta e ajustes do sistema escrevem no mesmo `IdiomaDoAparelho`. **Dois mecanismos gravando
a mesma preferência seriam duas fontes de verdade** — o defeito que a fatia G existe para remover.
A duplicação que custaria caro seria duas telas ou duas gravações, não duas entradas.

#### Conflito: o APARELHO vence (decisão de 2026-09-11)

Idioma é preferência POR APARELHO: dá para ter o app em inglês no celular do trabalho e em
português no pessoal. A escolha do aparelho manda na interface e é empurrada para `users.locale`.
Consequência aceita: trocar no celular A não troca no B, e o idioma do push segue o último aparelho
que abriu o app.

#### ⭐ Nome de idioma não se traduz

`idioma_pt_br` é *Português (Brasil)* e `idioma_en` é *English* nos DOIS catálogos, e as duas
entraram na lista fixada de chaves iguais (36 → 38). Quem precisa desta tela é quem abriu o app numa
língua que não lê, e reconhece *English*, não *Inglês*.

> **Traduzir a lista de idiomas torna ilegível a única tela que existe para sair de um idioma**
> **ilegível.**

Pelo mesmo motivo o item da gaveta leva **ícone de globo**: para quem não lê o idioma atual, a
palavra "Idioma" é tão opaca quanto o resto da gaveta. *Num app que a pessoa não consegue ler, o
ícone não é decoração: é o rótulo.* E a terceira linha, *Seguir o idioma do aparelho*, existe para
dar meia-volta — ajuste sem volta é ajuste que as pessoas têm medo de tocar.

⚠️ **O que o build verde NÃO prova.** Os testes de unidade confirmam compilação e paridade dos
catálogos. Nada exercita `LocaleManager`, `attachBaseContext` ou o `recreate()` — isso só se vê com
o app num aparelho, e nos dois lados do Android 13.

### ⚠️ A barra de abas estava em português, e o teste na mão foi quem achou

Primeiro uso do app em inglês: **tudo traduzido, menos a barra de baixo** — `Início`, `Treino`,
`Grupos`, `Progresso`. O componente mais visível do app, em português, depois de uma fatia que
declarou *zero literais em posição forte*.

A declaração estava **tecnicamente correta**. O `BottomTab` era um enum com `val label: String`, e
nenhuma das duas metades parecia texto de UI:

| onde | o que a varredura via |
|---|---|
| `INICIO("Início", ...)` | literal em argumento de construtor, longe de qualquer `Text(` |
| `Text(tab.label)` | chamada sem literal nenhum |

> **Texto guardado como propriedade de enum é invisível para quem procura texto em posição de UI:**
> **o literal não está na tela, e a tela não tem literal.**

**Quarta ocorrência da família** — antes `MuscleGroup`, `Environment` e `ExerciseCategory`. Fechada
com `@StringRes val rotulo: Int` (padrão do `ui/Rotulos.kt`) e com a guarda que faltava:

**`EnumsSemTextoTest`** — nenhum enum do módulo app pode ter propriedade `String`. A regra é grossa
de propósito. A fina ("String que chega a um `Text(`") exigiria o TIPO do receptor, que regex não
enxerga, e um teste fingindo esse alcance daria confiança sem base.

> **Invariante grosseiro e honesto vale mais que invariante fino e fingido.**

Falso duplicado novo no caminho: `nav_aba_treino` × `treino_detalhe_titulo_padrao`, ambos *Treino*
em pt-BR e **Training × Workout** em inglês. Avisos cruzados 5 → 6; duplicados fixados 19 → 22.
Catálogo em **615 chaves**.

### ✅ Paywall: o sentinela virou tipo (2026-09-11)

`value == "Sim"` **não existe mais**. O `String?` de cada célula virou:

```kotlin
private sealed interface Celula {
    data object Sim : Celula                          // ✓
    data object Nao : Celula                          // ✗  (era o `null`)
    data class Texto(@StringRes val id: Int) : Celula // isto sim é texto
}
```

O `when` do `Cell` ficou **exaustivo, sem `else`**: um quarto estado de célula quebra o build, em
vez de cair calado no ramo de texto — que era precisamente o que o `String?` permitia.

As 6 palavras adiadas foram para o catálogo (*1 programa, Vários, Só o Dia 1, Todos, Até 2*) e o
`LiteraisAdiadosTest` foi **apagado**: não sobrou literal para ele fixar. Catálogo em **620 chaves**.

**Terceira e última ocorrência do padrão nesta base.** As outras duas já estavam resolvidas: o
`"Não encontrado"` do `AppError` virou sentinela DERIVADA, e o `SplitType.label` ficou porque é
chave interna do motor, não texto.

> **A `String` é o tipo que aceita qualquer coisa, inclusive a coisa errada. Quando o código**
> **ramifica no valor dela, o tipo certo já existe e só não foi escrito.**

### ⚠️ Grupos e Conquistas escondiam a falha (2026-09-11)

Achado por teste na mão: a aba Grupos ficava em *"Carregando seus desafios"* **para sempre** e a de
Conquistas afirmava `0 de 0` numa tela em branco. Nenhum teste desta base teria pego, e a causa
estava mais fundo que a renderização.

**Grupos.** `Groups.sincronizar` devolvia `Unit`, com `is AppResult.Failure -> Unit  // mantém a
última lista conhecida`. A política de cache estava certa; jogar fora a INFORMAÇÃO de que falhou é
que não. O `GruposState` já declarava um `erroSync` que **nunca teve como ser preenchido**.

> **Campo de erro que ninguém preenche é pior que nenhum: quem lê o estado conclui que o erro**
> **está tratado.**

Agora a interface devolve `AppError?`, o ViewModel preenche e a tela ganhou um ramo — antes de
`vazio`, depois de `carregando` — com o `ErroDeTela`, o componente cujo próprio KDoc já dizia a
regra que faltava aplicar: *"use SÓ quando não há dado local"*.

**Conquistas.** O KDoc justificava não ter erro assim: *"a grade vem do cache local, então a rede
falhar não tem consequência visível"*. A conclusão vale **quando a premissa vale**, e a premissa é
*há cache local*. Na primeira execução não há — e a tela não ficava vazia, ela AFIRMAVA `0 de 0`,
em lima e negrito, dizendo que existem zero conquistas no catálogo.

> **Tela que não distingue "não tem" de "não chegou" não fica incompleta: ela mente com confiança.**

Ganhou `jaSincronizou()` e `erroSync`, e a tela passou a separar três coisas que eram uma: falhou,
ainda não chegou, e realmente não tem.

**Segunda e terceira ocorrências da família.** A primeira está no `MenuLateral.kt`, consertada em
2026-09-06: *"com o `/me` inalcançável e o cache vazio, carregando e falhou eram o mesmo pixel"*.

**Dois testes novos** no `GruposViewModelTest`, e o desenho deles importa:

- `sync que falha sem cache vira erro de tela` — o fake tem grupos NO SERVIDOR de propósito: é o que
  prova que a tela vazia não é "não tenho desafios", e sim "não consegui perguntar".
- `sync que falha COM cache nao esvazia a lista` — garante o INSUMO da decisão de silêncio (nível 1
  do #31), sem afirmar que a tela silenciou: quem decide é o ramo da tela, e um teste de ViewModel
  não alcança isso. Afirmar mais seria dar confiança sem base.

⚠️ **Lição de processo:** mudei duas interfaces e não procurei os implementadores. O pré-voo léxico
é cego para tipos — eu sabia, e ainda assim confiei nele. Antes de mudar assinatura: `grep` pelos
`override` em **main E test**.

### 🟡 Servidor em loopback: resolvido, CAUSA NÃO IDENTIFICADA (2026-09-11)

O `:server:run` subia em `Responding at http://127.0.0.1:8080` e nem o emulador nem o celular
alcançavam a API. O `application.yaml` declara `host: "$HOST:0.0.0.0"` — *"use a variável de
ambiente `HOST`, e na falta dela use `0.0.0.0`"* — e já trazia um comentário descrevendo exatamente
este sintoma, de uma passagem anterior por ele.

**Ficou funcionando. Não sabemos por quê.** Nenhuma hipótese foi confirmada: não se checou se havia
um `HOST` no ambiente do Windows, e o contorno (`--args="-host=0.0.0.0"`) não chegou a ser usado
como diagnóstico.

⚠️ **Isto fica registrado como pendência, não como fechado.** Correção sem causa identificada não é
correção: é o defeito fora de vista. Se voltar, a ordem de investigação é:

1. `echo $env:HOST` — se devolver algo, o yaml está obedecendo e o ambiente é que manda;
2. `./gradlew :server:run --args="-host=0.0.0.0"` — se isto resolve, confirma que o problema é a
   resolução do host, e não rota, porta ou firewall;
3. conferir se o IP da LAN mudou: o `API_BASE_URL` vem do `local.properties` e é cravado no build.
   Nesta ocasião ele mudou de `192.168.15.4` para `.6` no meio da investigação, o que por si só
   produz o mesmo sintoma e confunde o diagnóstico.

> **Defeito que some sem explicação não foi consertado: foi perdido de vista.**

O consolo é que o custo da reincidência caiu: as telas de Grupos e Conquistas agora **dizem** que
não falaram com o servidor, em vez de girar. Foi assim que este bug apareceu.

### ✅ As 9 frases com número do servidor entraram no catálogo (2026-09-11)

`TextosDeErro.SEM_TEXTO_PROPRIO` está **vazio**. Nenhuma frase de erro do app fica sem tradução.

**O `ErrorResponse` não mudou** — e essa é a parte que vale contar. A proposta em cima da mesa era
o envelope carregar `params: Map<String,String>` para o cliente montar a frase. O Rafael recusou por
verbosidade, e a verificação deu razão a ele por um motivo mais forte do que o alegado: **este
catálogo já cravava número de servidor em quatro strings**, todas comentadas como regra de produto
(`erro_dias_por_semana_invalido`, `erro_idade_invalida`, `erro_foco_alem_do_limite`,
`enum_bloqueio_lotado`). Mandar parâmetros no envelope teria criado **duas maneiras de fazer a mesma
coisa** no mesmo app.

> **Antes de inventar o mecanismo, olhe se a base já resolveu isso quatro vezes.**

Oito dos nove receberam o número dentro da frase, com o nome da constante do servidor no comentário
(`DisplayNamePolicy.MIN` = 2, `ProgramLimits.FREE_AI_LIMIT` = 1, `ModeracaoPolicy.PRAZO_EM_DIAS` = 7…).

O nono, `DIAS_LIVRES_INSUFICIENTES`, **não tem número para cravar**: a frase do servidor é *"precisa
de N dias livres, tem M"*, e os dois vêm das respostas da pessoa. Ele é backstop — o quiz já calcula
isso no cliente, com plural e com os dois números (`quiz_descanso_insuficiente`) — então virou frase
sem número: *"Você marcou dias de descanso demais para treinar nessa frequência."*

**O preço está registrado:** 12 frases do catálogo agora dependem de constantes do servidor que elas
não enxergam. Débito novo no `debitos.md`, com a saída conhecida (mover os números para um `Limites`
no `shared-contract`) e o motivo do adiamento.

---

## 🏅 G.5 CONCLUÍDA — conquistas e nome do programa (2026-09-15)

Migrations até **V48**. Fatia de servidor + contrato + cliente, na ordem.

| entrega | |
|---|---|
| `ConquistaIds` no `shared-contract` | 9 ids, padrão do `ErrorCodes` |
| 18 chaves novas de conquista | pt-BR e `values-en`, paridade verde |
| `programa_nome_derivado` | o nome do programa gerado passa a nascer no cliente |
| **V48** | zera o nome dos programas que a máquina já tinha nomeado |
| `AchievementDto` e `PublicAchievementDto` | perderam `title` e `description` |

### Como o defeito atravessou três invariantes verdes

A tela de Conquistas, o perfil público e a fileira de medalhas do perfil próprio estavam **inteiras
em português** para quem escolheu inglês. Nenhuma das três guardas da fatia G viu:

| guarda | por que não viu |
|---|---|
| inventário de literais (G.3) | varre literais **do cliente**, e a frase estava no servidor |
| `EnumsSemTextoTest` (G.4) | proíbe `String` em enum **do módulo app**; o enum era do `:server` |
| `TextoDoServidorTest` (G.4) | fixa os pontos que passam por `Frase.DoServidor`, e a tela fazia `Text(conquista.title)` direto |

> **Três invariantes verdes e o texto em português na tela: cada um media uma forma do defeito, e o
> defeito tinha uma quarta.**

É a **quinta** ocorrência do padrão "texto de UI onde a varredura não procura" (`ExerciseCategory`,
`MuscleGroup`, `Environment`, `BottomTab`, agora `AchievementPolicy`). As quatro anteriores foram
achadas por alguém olhando a tela; esta foi achada lendo os DTOs do contrato — que é o método que
entrou no "Próximo passo" acima.

### A decisão que a G.5 INVERTE, e o que ela custa

O KDoc do `AchievementDto` dizia, e dizia com razão para a época: *"título e descrição ficam no
servidor, então ajustar um texto ou um limiar não exige publicar uma versão nova do app"*.

Metade disso continua de pé, e é a metade que importa: **o LIMIAR não se mexeu**. Mudar `TREINOS_50`
de 50 para 40 segue sendo uma linha no `AchievementPolicy`. O que passou a exigir release é corrigir
uma PALAVRA.

> **Texto no servidor economiza um release e custa um idioma.**

### A corrente tem três elos, e cada teste guarda um

Sem os três, a falha é **silenciosa do pior jeito**: conquista sem texto **não vira cartão errado,
some da grade**. Não há fallback para cair, diferente do `TextosDeErro`.

| elo | quem guarda |
|---|---|
| enum do `:server` ↔ `ConquistaIds` | `AchievementPolicyTest` (teste novo) |
| `ConquistaIds` ↔ `TextosDeConquista` | `TextosDeConquistaTest` |
| `TextosDeConquista` ↔ `strings.xml` | `TextosDeConquistaTest`, chave derivada nos dois sentidos |

> **Medalha que o servidor concede e a tela não mostra é pior que medalha que não existe.**

### O nome do programa: `autoName` era pior que o `rationale`

`"Programa 4x — Push/Pull/Legs"` era montado no `ProgramService` e **GRAVADO**. Português,
travessão (proibido pelo #37 desde 2026-09-10), e persistido — nenhuma troca de idioma alcança dado
já escrito.

Agora o servidor grava `""`, que significa *"ninguém escolheu, derive"*, e o cliente monta o rótulo
de `daysPerWeek` + `split` no idioma da tela (`NomeDePrograma.kt`, usado nos 4 pontos que mostravam
o nome: Home, lista, detalhe e revelação).

**A coluna FICA**, diferente do que vai acontecer com o `rationale`: o usuário renomeia o programa
(ARCH #27), e o que ela guarda a partir daqui é escolha dele. Mesmo desenho do `display_name`.

⚠️ **O filtro da V48 é conservador de propósito.** Três camadas, todas obrigatórias: `origin = 'AI'`,
o formato exato `LIKE 'Programa %x — %'`, e o travessão — que é o que torna o predicado quase
infalsificável, porque ninguém o digita no teclado do celular e o projeto inteiro o proíbe.

> **Migration que apaga dado do usuário tem de errar para o lado de não apagar.**

### ❌ Notificações: NÃO foram movidas, e isso é decisão

Pedido original era "mover tudo para strings". As notificações ficaram, por um motivo que só
apareceu ao reler o contrato:

> *"`title` e `body` vêm já renderizados do servidor. Assim a notificação de ontem continua dizendo
> o que dizia ontem, mesmo que a pessoa citada troque de nome depois."*

Remontar a frase no cliente a partir do `data` faria a notificação passar a mostrar o nome **de
agora**. Ela deixaria de ser registro do que houve e viraria uma frase que se reescreve sozinha —
pior que ler o histórico no idioma antigo.

> **Registro do que aconteceu não se traduz retroativamente, porque traduzir é remontar, e remontar
> usa o dado de hoje.**

É a exceção única que o #37 já previa (o push só-dados não aparece com o app fechado, #36), agora
com uma segunda razão independente. Ratificada no `debitos.md`.

---

### ✅ `users.locale` sincronizado (2026-09-11) — G.4 fechada

`PATCH /me` com **só o `locale`**: o `displayName` fica `null`, que no `UpdateMeRequest` significa
"não mexer". Um PATCH que toca o que não foi pedido é um PUT com nome errado.

#### Onde a escrita mora, e por que não é na tela

A reconciliação vive no **`ReagirASessao`**, como quarta porta estreita ao lado de registrar o
aparelho e reler o badge. Não está na tela de idioma de propósito: **trocar o idioma destrói a
Activity** — no Android 13+ quem recria é o sistema, abaixo dele é o nosso `recreate()` — e um PATCH
disparado dali correria contra a própria recriação.

Reconciliar na abertura cobre os dois caminhos com UM ponto de escrita, e cobre também o que a tela
nunca veria: **trocar o idioma pelos ajustes do sistema com o app fechado**.

> **Quando a ação derruba quem a disparou, o lugar de gravar é no que nasce depois.**

#### Uma direção só

`ReconciliarIdiomaDaConta` empurra e nunca puxa — é a decisão "o aparelho vence", de 2026-09-11. O
contrário faria alguém que trocou pelos ajustes ver o app **desfazer a escolha sozinho** na abertura
seguinte, e o KDoc do `Idioma.PADRAO` já registrava o princípio: *idioma trocado por uma atualização
que ninguém pediu parece defeito, não recurso*.

Escreve **só quando diverge** (reescrever o mesmo valor a cada sessão é custo sem efeito), e ignora
a falha de propósito: sem rede o PATCH não sai, nada se perde, e a próxima abertura tenta de novo.
Diferente do `renomear`, cuja recusa a pessoa precisa ver.

⚠️ **Testado até onde teste de unidade alcança.** O `ReagirASessaoTest` ganhou a quarta porta e
prova que a reconciliação é chamada. **Nada exercita o PATCH de verdade, nem o `LocaleManager`, nem
o `recreate()`** — isso só num aparelho, e nos dois lados do Android 13.

### Ainda falta — lista única, revisada em 2026-09-11

⚠️ Esta lista foi reescrita do zero. A anterior tinha **itens já fechados ainda listados como
pendentes** e três cópias do mesmo item, porque cresceu por acréscimo durante a sessão. É o mesmo
defeito de livro-razão que esta sessão passou o dia caçando, cometido no documento que deveria
denunciá-lo.

> **Lista de pendências que ninguém reescreve vira lista de leitura opcional.**

| o quê | onde | tamanho |
|---|---|---|
| **12 frases cravam constantes do servidor** | `debitos.md`, aberto hoje | médio; saída conhecida (`Limites` no shared-contract) |
| **`erroDoCampo`** | `fieldErrors` é campo → FRASE em português | médio; precisa decidir se o valor vira código |
| **Varrer tela por tela com o app em inglês** | manual | contínuo, e o de maior retorno até agora |
| **Servidor em loopback, causa não identificada** | `debitos.md`, 🟡 | investigar só se voltar |

O `rationale` continua sendo o maior débito de arquitetura da área: frase em português montada pelo
`StructureEngine` e **gravada no banco**, exibida no `ProgramDetail` e no `ProgramReveal`. Carrega
também o **último travessão do produto**. Enumerado no `TextoDoServidorTest`; sai quando o servidor
mandar código mais parâmetros.

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

## Próximo passo

**Ordem restante da Fase 6:** **#35** (amizades) → **D** (motor de regras / EMOJI_DO_DIA) →
**E** (comentários, reações, denúncia) → **F** (notificação).

1. **#35 — amizades.** ADR escrito e revisado (`arch/fase-6/ARCH-35-amizades.md`). O que ele
   pede, em ordem: código de usuário de **8 caracteres, regenerável**; relação **simétrica com
   aceite** (`PENDENTE` → `ACEITA` · `RECUSADA` · `BLOQUEADA`); teto de 500; convidar para
   desafio pela lista; e — **não é refino** — uma **tela de convites pendentes** alcançável pelo
   drawer, com contador. Sem ela o recurso apenas *parece* funcionar, porque a notificação é a
   fatia F.

   O `PerfilPublicoScreen` já existe e é onde o botão **Adicionar** mora ([REGRA] #35). Ele não
   foi feito na C.1 de propósito: sem grafo, não há o que o botão faça.

2. **Copiar `gifs_exercicios/banners/default.jpg` para o servidor de produção**, quando houver —
   a pasta está fora do git. **Em desenvolvimento já está resolvido**: a imagem existe desde
   2026-08-22 e a rota responde 200 (verificado em 2026-09-06).

   O débito ficou aberto duas semanas depois de resolvido, porque ninguém conferiu.
   > **Débito só fecha quando alguém verifica, não quando alguém entrega.**

4. **Débitos ⚪ abertos:** re-encodar os 963 MP4 (197 MB, hoje 95% da mídia) · deep link do
   convite · vocabulário do 409 duplicado entre servidor e `EntrarScreen` ·
   `LocalClipboardManager` deprecado · `integrationTest` em 3m51s (um container por classe) ·
   ~24 imagens órfãs sem exercício · base da API como `var` global.

## Pendências ativas (demais em `docs/fontes/debitos.md`)

- **Dúvidas sem preço/plano, cancelamento, contato de suporte e política de privacidade** — as
  8 perguntas (2026-10-04) cobrem só regra já implementada; os quatro de cima dependem de
  decisão ou dado que só o Rafael tem, não foram esquecidos

- **Notificação de desbloqueio de conquista** — hoje a avaliação só roda na leitura, então o
  usuário descobre a medalha ao abrir a tela. Quando houver notificação, o `POST /sessions`
  precisa avaliar também
- **`stats` + `session` + `achievements` no módulo app** — os três deviam ser features próprias;
  conquistas seguiu o padrão dos irmãos de propósito, para não repartir a gamificação em dois

- **Outbox sem teste de integração** — compactador e processador têm cobertura pura, mas a
  interação outbox × `save()` (preservar pendentes) é exatamente onde estavam os bugs, e só
  tem verificação manual
- **Premium não edita conteúdo de IA offline** — consequência aceita da fronteira #30 × #25
- **Sem listener de conectividade** — a fila espera o WorkManager; a tela aberta não reage

- ~~`tools/` sem decisão de versionamento~~ — **DECIDIDO (2026-08-23): fora do git**, junto com `exercises.csv` e `gifs_exercicios/`
- `SessionApi`/`SessionSync` no módulo app (atalho) — extrair p/ `session:data`
- `fieldErrors` do nome de programa inalcançável pela UI (`enabled = name.isNotBlank()` barra
  antes) — rede de segurança, não caminho normal
- `/me/profile` sem TTL — decisão consciente (é o gate da Splash), roda de fundo
- Catálogo: nomenclatura de placas, Afundo × Avanço, `MuscleGroup` sem CALVES/TRAPEZIUS/
  LOWER_BACK, prancha isométrica ausente, 74% da base em INTERMEDIATE
- Dedup FUNCIONAL (stiff ↔ terra romeno) — precisa de decisão de produto

## Herdado do Handoff 14 (ainda válido)

- Fase 4 concluída; motor maduro, destravado e desmembrado (bíceps/tríceps/antebraço).
- Cronograma de semanas (V24): `durationWeeks` + `startedAt`, semana atual derivada no servidor.
  **Não é periodização** — treinos iguais toda semana. Ciclo rolante não implementado.
- Catálogo curado: 965 exercícios, taxonomia 100% auditada, 0 duplicatas (V28–V33).
- Gate PAR-Q fechado (V26 normaliza LOWER_BACK→LUMBAR).
- Play Billing real (trocar `DevBilling`) — Fase 10.
