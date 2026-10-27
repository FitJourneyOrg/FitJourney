# V59 e V60 — treino ativo vira programa ativo (2026-09-25 a 09-28)

> **Histórico.** Fatia fechada — preservado literal do antigo `handoff-retomada.md`.
> Estado atual em `docs/ESTADO.md`; convenções em `docs/CONVENCOES.md`;
> regra de comportamento em `docs/regras/`; débitos em `docs/fontes/debitos.md`.

---

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

