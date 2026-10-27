# Painel Mestre v42 — snapshot final (2026-10-07)

> **Documento encerrado.** O Painel Mestre deixou de existir: era um log cronológico por fatia,
> mesma função do Handoff, com conteúdo duplicado (fatia G, fatia H, V59/V60, débitos de 09-22).
> Convenções → `docs/CONVENCOES.md` · estado → `docs/ESTADO.md` · fases → `docs/README.md`
> · débitos → `docs/fontes/debitos.md` · migrations → `docs/migrations/INDICE.md`.
> Preservado literal para consulta; não editar.

---

# Painel Mestre — índice

> Fonte: Painel Mestre v42 + Handoff Retomada 32, atualizado **2026-10-04** (fatia J fechada por completo: análise de progressão, filtro por programa, janela de calendário, seleção de exercícios e detalhe por sessão de um exercício (J.5) — **zero migrations**, todo o dado já estava em `workout_sessions` + `session_set_logs` desde a V20; mais a tela de Dúvidas Frequentes, estática). ARCHs ratificados: #20=determinístico; #22=programa semanal (estendido: janela de semanas); #23=value-first (+ gate de leitura); #24=idade (gate + variável do motor); #26=volume; #27=multi-programa; #28=ambientes/is_base; #29=split guiado; #30=offline-first; #31=apresentação de erro; #32=conquistas; **#33=grupos e check-in (emenda o #17; seção 6 implementada na E.2)**; **#34=perfil público e drawer (emendado pela 9.3-A)**; **#35=amizades**; **#36=notificações**; **#37=idioma (fatia G)**.

Documento-índice. O conteúdo que crescia (decisões, débitos, detalhe de fase) foi recortado em fragmentos.

Estado: **🔤 FATIA G FECHADA (G.1 a G.5) e 🏋️ FATIA H FECHADA (H.1/H.2 + sessão A + sessão B)** —
2026-09-21, `:server:integrationTest` verde (144 testes). Migrations até **V55**, catálogo em
**923 exercícios**, `description` e `exercise_translations` (en) carregadas para os 923 — **catálogo
e banco 1:1, sem órfão em nenhuma direção**.

A sessão A absorveu a revisão de nomenclatura que chegou pronta (923 exercícios conferidos contra
vídeo) e consertou o que ela quebrou: **V50** os caminhos de mídia, que estavam servindo 404 em
todos os 900; **V51** os 39 duplicados; **V52** os 788 nomes revisados e os 9 travessões.

**A sessão B absorveu, numa sentada só, o que este documento previa como B + C + D separadas**
(descrições, tradução, os 26 e os 23) — a descrição virou dado carregado no banco, então parar no
meio teria deixado a tabela `exercise_translations` (V49, ociosa desde a H.1/H.2) e a `description`
de 23 exercícios inconsistentes por uma sessão a mais de espera:

- **288 descrições AJUSTE/PADRONIZAÇÃO** e **13 ERRO** (nome corrigido na sessão A, descrição
  ainda presa ao nome antigo) reescritas e revisadas — zero travessão no catálogo inteiro depois
  (achou e corrigiu 2 introduzidos pela própria reescrita, ver `debitos.md`).
- **Débito do cliente fechado**: `ExerciseDetailScreen` tinha os regex de aviso/segurança
  hardcoded em português — quebraria em silêncio assim que o locale virasse EN. Mesmo padrão do
  `TextosDeAviso` (`when` exaustivo sobre `Idioma`).
- **V53** carregou `description` (923) e `exercise_translations` en (923) do catálogo, casando por
  `name` (decisão explícita: mais rápido que persistir `chave`, risco aceito e documentado — mesmo
  padrão de fragilidade que a V33 já tinha registrado).
- A verificação achou **23 divergências reais**: nomes do catálogo sem nenhuma linha correspondente
  no banco. Investigado por `video_ref` (mais estável que nome) e por contagem total revelou
  população **disjunta nos dois sentidos** — não era renomeação: o catálogo tinha 23 exercícios
  nunca semeados, o banco tinha **26 legado** (seed antigo, `video_ref` vazio) sem correspondência
  no catálogo atual.
- ⚠️ **4 dos 26 "sem equivalente" eram, na verdade, duplicata do mesmo exercício sob outra
  capitalização/sufixo** — o match por `name` da V53 é case-sensitive e não pegou (`Stiff com
  halteres` ≠ `Stiff com Halteres` para o Postgres, mas é o MESMO exercício, um com vídeo e outro
  sem). Consolidados: `workout_exercises` migrado pro moderno, legado apagado.
- **V54**: consolidou os 4 duplicados, apagou 3 legado em uso sem equivalente (decisão do Rafael —
  ambiente de dev, usuários de teste, sem necessidade de preservar histórico) e 19 sem uso.
- **V55**: os 23 exercícios genuinamente novos, todos com vídeo/thumb confirmados **em disco**
  (não só o campo no JSON) — critério do Rafael: "se não tiver video_ref não entra".
- `CaminhosDeMidiaIntegrationTest` (da V50) travava 900/926/26 como invariante fixo — quebrou 4
  testes de propósito, o próprio comentário do arquivo previa isso ("se ele DESCE, a dívida está
  sendo paga"). Atualizado para 923/923/zero.

> **Match por `name` sem `chave` persistida vai quebrar nesse formato de novo: dois exercícios
> iguais escritos com capitalização diferente passam pela igualdade de string do Postgres como se
> fossem exercícios distintos.** A V33 já tinha documentado a mesma família de risco por outro
> caminho (rename por ID). `chave` continua no catálogo sem uso em migration nenhuma — candidata
> natural a virar coluna persistida se isso quebrar uma terceira vez.

⚠️ **Existem DOIS números de teste, e eles contam coisas diferentes.** Não é divergência para
resolver escolhendo um: é para parar de somar à mão.

| fonte | valor | o que conta |
|---|---|---|
| `docs/gerados/testes.md` (`gen_testes.py`) | **751 em 84 arquivos** | `@Test` encontrados varrendo os fontes, a cada `gen_tudo.py` |
| execução com `--rerun-tasks` (2026-09-10) | **739 em 88 arquivos** | o que o Gradle rodou nas cinco tasks |

A diferença de arquivos é do método — o gerador não enxerga o que o Gradle enxerga, e vice-versa.
**O número a citar é o do documento gerado**, porque ele se atualiza sozinho; o da execução vale
para dizer "passou", não "quantos". Até 2026-09-15 este parágrafo trazia um terceiro valor, somado
à mão a partir do medido, e um documento gerado o contradisse na primeira rodada.

> **Contagem mantida à mão ao lado de uma contagem gerada só sobrevive até alguém rodar o gerador.**

Para verificar que a suíte PASSA (e aí sim usar `--rerun-tasks`):
`:konsist:test` · `:server:test` · `:server:integrationTest` (exige Docker) · `testAndroidHostTest`
· `:app:androidApp:testDebugUnitTest`.

### O que a fatia G entregou

| | |
|---|---|
| **G.1** | V47 (`users.locale` com `CHECK` fechado), `Idioma`/`IdiomaPolicy` no contrato com cadeia de fallback, `Aviso` como chave+parâmetros renderizado na borda, `PATCH /me` com campos anuláveis |
| **G.2** | Tirou 103 frases de erro do servidor: **67 `ErrorCodes`, 87 frases distintas, 6 desmembramentos, 19 reescritas**. Descobriu que não eram 104 e sim 87 (*"Programa não encontrado"* em 7 lugares) e que seis cobriam situações OPOSTAS — a pior servia a *"a outra pessoa sumiu"* e a *"a sua própria conta sumiu"* |
| **G.3** | Extraiu **634 literais**, inventário de 673 → 39, **zero em posição forte**. Três mecanismos: `Rotulos.kt` (~90 rótulos de enum como `@StringRes`), `Frase.kt` (TIPO para a fronteira catálogo × servidor) e `<plurals>` (tirou do Kotlin a concordância cravada em `if (n == 1)`) |
| **Portão pt-BR** | Travessão, caixa alta e reticências deixaram de ser listas de débito e viraram **proibição com teste** |
| **G.4** | `values-en` com o catálogo inteiro, tela de idioma, barra de abas no catálogo, paywall sem sentinela, as 9 frases com número do servidor, `users.locale` sincronizado |
| **G.5** | Tirou a última prosa não enumerada do servidor: **18 frases de conquista** (três telas em português, invisíveis para as três guardas da fatia) e o `autoName` do programa, que era gravado em português e com travessão. `ConquistaIds` no contrato, **V48** zerando os nomes já gerados. Catálogo em **648 chaves**. Verificada no aparelho em 15/09, com ressalva escrita sobre o que a V48 **não** provou |
| **H.1 / H.2** | **V49** `exercise_translations` (tabela, e não coluna `name_en`, porque serão mais de quatro idiomas), leitura com `LEFT JOIN` e piso em `exercises.name`, `GET /exercises?locale=`, e o cache do cliente passando a saber **em que idioma** está guardado. 12 testes novos |
| **H sessão A** | **V50/V51/V52**: caminhos de mídia, 39 duplicados removidos, 788 nomes revisados. Mais o pull-to-refresh que faltava na biblioteca e duas guardas novas (travessão e nome duplicado em `exercises.name`) |
| **H sessão B** | **V53/V54/V55**: `description`+tradução en carregadas para 923 exercícios, 4 duplicatas por capitalização consolidadas, 22 legado sem mídia removidos, 23 exercícios novos inseridos. Fix do débito de cliente `ExerciseDetailScreen` (regex de aviso preso em português). Fatia H **fechada** |
| **UI (pós-G.5)** | Insets padronizados — o `Scaffold` externo deixou de reservar inset e passou a entregar só a altura das barras que desenha; `Movimento.kt` classificando a animação pelas rotas; `Scaffold` na biblioteca de exercícios, a última tela navegável sem um |

### [REGRA] do ARCH #37 e da G.4

- **Texto que o usuário lê vive num lugar só: o `strings.xml` do cliente.** O servidor manda dados,
  códigos e chaves. Única exceção: a notificação, porque push só-dados não aparece com o app fechado
  **e porque ela é registro do que houve** — remontar a frase no cliente usaria o dado de hoje, e a
  notificação de ontem passaria a dizer outra coisa (ratificado na G.5).
- **Chave que falta no `values-en` NÃO quebra: cai no pt-BR em silêncio.** O app roda, nada é
  logado, e quem escolheu inglês lê português. Por isso a tradução nasce como arquivo que o build
  confere, travado pelo `CatalogoEmInglesTest`.
- **Os comentários de contexto não são duplicados** no `values-en`: duas cópias da mesma explicação
  são dois lugares para atualizar e um para esquecer.
- **Idioma é preferência POR APARELHO**, e o aparelho vence o servidor. O contrário faria o app
  desfazer sozinho uma escolha feita nos ajustes do sistema.
- **Nome de idioma não se traduz.** `Português (Brasil)` e `English` são idênticos nos dois
  catálogos e estão fixados: quem precisa dessa tela é quem não lê o idioma atual.
- **Cinco pares idênticos em pt-BR divergem obrigatoriamente em inglês** — *Leave × Log out*,
  *Rest day × Rest*, *Check in × Submit check-in*, *Today × Today's workout*,
  *Training × Workout* — e o teste reprova se colapsarem. **38 chaves são idênticas nos dois
  idiomas e estão fixadas**, porque chave nova nessa lista é quase sempre tradução esquecida.

### ⚠️ Cinco invariantes que passavam verdes sem olhar (G.4 e G.5)

O padrão dominante da fatia, e vale mais que qualquer entrega dela:

| | |
|---|---|
| Caixa alta | comparava `v == v.uppercase()`, e o `d` minúsculo de `%1$d` deixava passar dois cabeçalhos gritados |
| `:konsist:test` | ficava `UP-TO-DATE` sem varrer nada: lia os fontes por filesystem, e o Gradle só conhecia `konsist/src` |
| Inventário da G.3 | mediu LITERAIS e declarou "zero em posição forte", com a barra de abas inteira em português dentro de um enum |
| Preâmbulo do `erros.md` | gerador rodando verde há uma fatia descrevendo um sistema que a G.2 desfez |
| **As três guardas de texto juntas (G.5)** | inventário (literais do cliente), `EnumsSemTextoTest` (enums do app) e `TextoDoServidorTest` (pontos com `Frase.DoServidor`) — **as três verdes**, com três telas de conquistas inteiras em português vindas de um enum do `:server` |

> **A varredura mede a forma do defeito que ela conhece.**

Três dos defeitos da G.4 foram achados **abrindo o app e olhando**, não por teste. O da G.5 foi
achado de um terceiro jeito, que virou procedimento: **ler os DTOs do contrato campo a campo,
procurando `String` que é prosa**. Perguntado "quais telas faltam", o inventário respondeu *nenhuma*,
e respondeu certo dentro do que mede.

> **Perguntar "que tela falta" devolve a resposta do medidor, não a da tela.**

### Próximo

**Fatia H fechada** (H.1/H.2 + sessões A e B) — catálogo e banco 1:1 em 923 exercícios, com
tradução. Débitos abertos da área, sem relação com a fatia H: 12 frases do catálogo cravam
constantes do servidor (mais os números das conquistas, acrescentados na G.5); `erroDoCampo` ainda
devolve frase pronta.

Próxima fatia: a definir com o Rafael.

**2026-09-28 — V60: ativação volta a ser de PROGRAMA, reverte a V59.** Achado do Rafael testando
manualmente a V59: o botão "Ativar" aparecia por TREINO dentro da semana do programa, quando ele
pensa em termos de PROGRAMA ("hoje eu sigo o Push/Pull/Legs"). Duas decisões via `AskUserQuestion`:
(1) ativação vira "Ativar programa" na lista de programas, não mais um botão por treino no detalhe;
(2) sem agenda em NENHUM programa, a Home pede pra completar a agenda em vez de sugerir um treino
avulso — substitui de vez o fallback que a entrada de 2026-09-26 (abaixo, ⚠️ **revertida**) tinha
introduzido.

Reverte a V59 quase por inteiro, mas mantém a peça que ela acertou: o "treino de hoje" continua
DERIVADO, só que agora cruza dois sinais em vez de um ponteiro só — o programa marcado ativo pelo
usuário, e o `dayOfWeek` de cada treino contra o dia da semana ATUAL do servidor (`DiaDaSemanaAtual`,
`TimeZone.UTC`, [REGRA] autoridade do servidor). Programa ativo num dia sem treino agendado = nenhum
treino marcado, de propósito.

| | |
|---|---|
| Servidor | `users.active_program_id` (substitui `active_workout_id`); `POST /programs/{id}/activate` (substitui `POST /workouts/{id}/activate`, removido); `ProgramActiveWorkout` reescrito pra derivar via `DiaDaSemanaAtual`; `WorkoutService.get()` deriva o mesmo campo em `GET /workouts/{id}` |
| Cliente | `7.sqm` (`program.isActive`); ativação migra de `ProgramDetailViewModel` de volta pra `ProgramListViewModel`; `ProgramListScreen` ganha botão "Ativar programa" + diálogo; `ProgramDetailScreen` perde o botão por treino, mantém só o selo "· Ativo" read-only |
| Home | `HomeViewModel` perde o fallback inteiro (`porAtivo`/`TodayWorkout.viaTreinoAtivo`, introduzidos em 2026-09-26) — `precisaCompletarAgenda` pede pra completar a agenda em vez de sugerir um treino avulso |
| Testes (C2) | migrados, não deletados: 2 testes de `activate` saem do `ProgramDetailViewModelTest` e entram no `ProgramListViewModelTest`; `ProgramActiveWorkoutTest`/`WorkoutServiceTest` reescritos; `HomeViewModelTest` com 2 cenários reescritos pro fim do fallback; `ProgramMapperTest` +1 |
| Débito fechado | os 2 itens do P5 sobre o card "treino ativo" da Home (duração/grupo muscular, barra de progresso) ficaram obsoletos — o card não existe mais. Movidos pra P6 em `debitos.md` |
| Verificação | `:server:test`, `:server:integrationTest`, `testAndroidHostTest` (`program:data` + `program:presentation`), `:app:androidApp:testDebugUnitTest` e `:app:androidApp:assembleDebug` — todos verdes, rodados pelo Rafael |
| Migrations | V60 |

**2026-09-26 — ⚠️ REVERTIDA pela V60 acima — Home: sugestão do treino ativo quando falta agenda (fix sobre o V59).** Achado
do Rafael navegando no app: com 4 treinos manuais, nenhum agendado e nenhum ativo, a Home mostrava
"Dia de descanso" — mentira, porque `HomeViewModel.resolverTreinoDeHoje()` só conhecia a agenda
semanal (ARCH #22) e nunca soube do ponteiro `isActive` do V59, ortogonal por desenho ("sessões da
semana continuam derivadas de `workout_sessions`, não muda nada" — verdade pra CONTAGEM de sessão,
não pra qual card a Home escolhe mostrar).

Resolvido em duas perguntas (C1, ambas confirmadas pelo Rafael): (1) sem agenda pra hoje, o treino
ATIVO vira a sugestão do dia — reaproveita `CardTreinoDeHoje` com um selo "· Ativo" pra não parecer
um agendamento que não existe; (2) sem agenda em NENHUM programa e sem ativo, um card novo pede pra
ativar um treino em vez de "descanso" — reaproveita as strings que já existiam
(`treino_ativo_nenhum_titulo`/`corpo`) e o CTA leva pra "Meus treinos". Descanso implícito (ARCH
#22) continua valendo exatamente como antes, quando alguma agenda existe e hoje não está nela.

**Débito solto do fechamento do V59, achado no caminho:** `FakeProgramas` (testes da Home) não
implementava `activateWorkout()`, que `ProgramRepository` ganhou naquela fatia —
`testDebugUnitTest` da Home não rodava desde então (ver nota abaixo sobre o CI).

| | |
|---|---|
| Cliente | `HomeViewModel` (+`TodayWorkout.viaTreinoAtivo`, +`HomeState.precisaAtivarTreino`), `HomeScreen` (+`CardNenhumTreinoAtivo`, selo no card de hoje) |
| Testes | +3 em `HomeViewModelTest` (sugere ativo, agenda prevalece sobre ativo, sem agenda e sem ativo pede pra ativar) — 12/12 verdes |
| Verificação | `testDebugUnitTest` (Home) e `:app:androidApp:assembleDebug`, ambos verdes pelo Rafael |
| Migrations | nenhuma — só leitura do `isActive` (V59) que já existia |

✅ **Resolvido.** Não era falha do CI — era eu com uma referência remota desatualizada (não tinha
dado `git fetch` antes de afirmar "23 commits sem push"). `origin/develop` já estava em dia. O CI
rodou de verdade no PR #54 (`develop` → `main`, os 23 commits + os dois fixes desta sessão) e pegou
um problema REAL, diferente do `activateWorkout`: `CatalogoDeStringsTest > os textos repetidos sao
exatamente os declarados` falhou porque a V59 introduziu `treino_ativo_iniciar` duplicando
`comum_iniciar_treino` (mesmo texto, mesmo botão de iniciar treino). Corrigido, empurrado, CI verde,
PR #54 mergeado — confirmado pelo Rafael. O gate de 2026-08-20 está funcionando.

**2026-09-25 — ⚠️ REVERTIDA pela V60 (2026-09-28, ver acima) — treino ativo (V59) fechado.** Fatia pequena, veio de um mock de UI (tela "Meus
treinos"): qual treino fica em destaque quando o usuário tem mais de um. Desenhada como ponteiro
simples e exclusivo, **autoridade do servidor** (`users.active_workout_id`, `ON DELETE SET NULL`),
o mesmo padrão já usado pelo `generate()` — não é conteúdo do usuário, então sem outbox nem cópia
otimista: `ProgramRepository.activateWorkout()` é online-only, `invalidate()` força o próximo load
a buscar a verdade do servidor. `ProgramActiveWorkout` é política pura aplicada em `GET /programs`
DEPOIS do `ProgramBlur` (mesma camada, mesma ordem); `WorkoutService.get()` marca o mesmo campo em
`GET /workouts/{id}`. Escopo em TREINO, não em programa — decisão deliberada pra não reviver o "1
programa ativo" que o #27 já abandonou.

`activateWorkout()` (o POST) mora no `ProgramDataSource`, não no módulo `workout`: quem CONSOME
`isActive` é a tela de programa, e a regra "feature nunca depende de feature" vale também pra um
único endpoint HTTP — criar a dependência só pra reaproveitar uma URL teria sido pior que repetir
a URL.

**Achado no meio do caminho, só no `assembleDebug`:** o `WorkoutRepository.list()` do módulo
`shared/features/workout` está órfão desde o #27 (zero call site, substituído pelo caminho de
`ProgramRepository`) — mas o `WorkoutLocalDataSource` do MESMO módulo não está: é o cache local do
`GET /workouts/{id}` e escreve na MESMA tabela `workout` que o sync de programas popula. Faltava o
campo novo em dois lugares: no `salvarTreino` (erro de compilação) e no `read()` (bug silencioso —
devolveria `isActive = false` sempre, mesmo com o cache certo). Nenhum teste de `commonTest` cobre
esse arquivo; só apareceu porque o `assembleDebug` compila o módulo inteiro, chamado ou não.

> **Módulo sem call site ainda compila, e ainda escreve na tabela de outro módulo.** "Órfão" não é
> sinônimo de "inofensivo": zero tela chamando não é zero código rodando.

Fora de escopo por decisão do Rafael (opção 1 do mock): duração e grupo muscular no card do treino
(não existem em `WorkoutSummaryDto`) e a barra de progresso semanal (o card fica só com nome +
contagem de exercícios). Débito registrado em `debitos.md`.

| | |
|---|---|
| Servidor | V59: `users.active_workout_id`; `POST /workouts/{id}/activate`; `ProgramActiveWorkout` (política pura, pós-blur) |
| Cliente | `6.sqm`: `workout.isActive`; `ProgramListScreen` reescrita — **substitui** a tela antiga (`AppRoute.Programs`), não é tela nova |
| Testes | `ProgramActiveWorkoutTest`, `WorkoutServiceTest` (+1), `ProgramMapperTest` (+1), `ProgramListViewModelTest` (+3) — C2 cumprido |
| Verificação | `:server:test`, `testAndroidHostTest` (program:data + presentation) e `:app:androidApp:assembleDebug`, todos verdes pelo Rafael |
| Migrations | V59 |

**2026-09-22 — fatia "rationale derivado" fechada.** O `rationale` era o maior débito de
arquitetura remanescente da área de idioma: frase em português, com travessão, montada pelo
`StructureEngine` e **gravada no banco** (`programs.rationale`) — estado que é função de
`split`+`daysPerWeek`+`focusAreas`, mas persistido, violando a mesma regra já aplicada a XP/nível
(#22, #33). Fechado do jeito que o débito sempre previu — backend estruturado, contrato tipado,
cliente monta: **V56** parou de gravar a frase e passou a persistir `split` como CHAVE do enum
(`SplitType.name`, não mais o rótulo renderizado) e `focusMuscles` (snapshot do foco no momento da
geração); a coluna `rationale` foi **DROPADA**, não deixada vestigial. Cliente (`ProgramDto`/
`Program`/`4.sqm`) monta a frase em runtime, no idioma da tela, via `rationaleDoPrograma()` —
reaproveitando `SplitType.rotulo()/.descricao()` e `MuscleGroup.rotulo()` que já existiam desde a
G.3. Migration com guarda que falha alto se sobrar valor fora do mapeamento label→enum conhecido
(a V53/V54 não tinham essa guarda — achado do PR review anterior). `TextoDoServidorTest` caiu de 9
para 7 pontos contáveis; o KDoc de `SplitType` no `shared-contract`, que dizia que `label` era
"chave interna do motor", foi corrigido — não é mais lido em lugar nenhum do motor. Compilado e
testado verde pelo Rafael (server + `testAndroidHostTest`). Migrations até **V56**.

**2026-09-22 — 6 débitos técnicos fechados** (P0.1–P0.4 + 2/2 do P1 sem decisão pendente, dos 77
priorizados na revisão desta data): TRUNCATE de `exercise_translations` no `BancoDeTeste` (achou e
corrigiu um companheiro — bug de categoria inválida escondido atrás de `AppResult.Failure`
genérico); `inputs.files` do `:server:integrationTest`, que podia dar verde sem rodar; teste de
integração do outbox `save()`×`alvosPendentes()` contra SQLite real (infra nova no projeto:
`sqldelight-sqliteDriver` + `androidHostTest`); query de sanidade `created_at`×`local_date`; regex
de segurança por FRASE em vez de parágrafo inteiro (validado contra os 923 textos reais antes de
implementar); rodapé DEBUG na `ErroDeTela`. Todos com teste (C2) e verificados em build real pelo
Rafael, não só implementados. Detalhe completo em `debitos.md` e no Handoff.

Antes: **✅ FASE 6 CONCLUÍDA (2026-09-09)** — todas as fatias entregues (A.0 · A · B · C · C.1 · D · E.1 · E.2 · F). Migrations até **V46**; schema local até **3.sqm**. Suíte verde nas cinco tasks (incluindo integração), **677 testes em 75 arquivos**. **Débito de integração V40–V46 pago em 2026-09-09** — 59 testes novos, suíte de integração de 66 → 125 testes em 12 arquivos, e de 1m18s → **44s** com container compartilhado. Cinco baterias manuais completas (F.1 28 passos, D 7, E.1 13, E.2 30, F 32). **O app está social**: criar desafio, entrar, check-in com foto, feed, ranking, perfil de qualquer pessoa, amizade, bloqueio e **push com o app fechado**. Fatias fechadas nesta rodada: **A.4** (detalhe + membros) · **B** (check-in, foto, feed — V38, camada de mídia própria com purga agendada) · **C** (ranking por contagem de check-ins, desempate resolvido no servidor) · **C.1** (perfil público de terceiro). Detalhe do grupo reestruturado em `TabRow` + `HorizontalPager` (Sobre · Ranking · Posts · Membros), com polling só na aba visível. **Higiene de armazenamento (2026-08-23):** app + cache 356 MB → **51,5 MB**; mídia no servidor 372 MB → 213 MB (PNG→WebP, V39 reescreve `thumb_ref`, `allowBackup="false"`). Descoberto de quebra: o **release estava quebrado desde junho** (`material-icons-core` sem versão, resolvido por acaso no debug) — `assembleRelease` entrou na CI. Regras novas: **2.5-A** (admin sozinho que sai apaga o desafio), **4.8 emendada** (foto vive em `ATIVO` + 30 dias após `ENCERRADO`), **9.3 → 9.3-A** (o perfil é PÚBLICO: nome, nível, XP e conquistas, e nada além). **Comunidade adiada para a v2.** Ver HANDOFF.

## Mapa dos documentos

- **HANDOFF** — onde paramos e próximo passo.
- **README.md (tabela "Por fase")** — estado por fase, é a fonte única agora (a antiga `fases/RESUMO-FASES.md` saiu, era duplicata desatualizada). Fechamento de fase com detalhe próprio, quando existir, mora em `arch/fase-N/` (ex.: `arch/fase-4/fase-4-treino.md`).
- **arch/README.md** — índice das decisões (um ADR por arquivo em `arch/fase-N/`).
- **`docs/fontes/debitos.md`** — backlog técnico consolidado (aberto + pago, por fase).
- Docs canônicos originais (PDF): Regras, Contrato da API, Documentação Completa, Adendo Stack.

## Convenções fixadas

- Pacote raiz `dev.rafael.*`; core `dev.rafael.core.*`; features `dev.rafael.features..*`; server `dev.rafael.server`; contrato `dev.rafael.contract`.
- Ordem: Backend → Contrato → Cliente (Data → Domain → Presentation). Feature nunca depende de feature; domain é Kotlin puro.
- Autoridade do servidor (XP, validação, IA, entitlement); cliente só cópia otimista; nunca o relógio do cliente. Gate de edição/entitlement decide no server (`requireEditable`); UI só antecipa p/ não dar erro na cara.
- C1 explicar antes de construir · C2 testar ao fechar (fakes sem mock; kotlin.test; Testcontainers; coroutines-test).
- **Nome de teste em backticks não aceita `"` no Windows** (2026-09-08): o Kotlin gera um `.class` por lambda usando o nome do teste, e aspas duplas são caractere ilegal em caminho — o compilador quebra com `InvalidPathException` e um stack trace de 80 linhas que não menciona o teste culpado. Vale para `:` `*` `?` `<` `>` `|` também. Usar palavras em vez de citar o texto testado.
- **Nome de task nos módulos KMP:** o plugin novo de `androidLibrary` não gera `compileKotlinAndroid` nem `testDebugUnitTest`. Use `testAndroidHostTest` (compila main + test e roda). `testDebugUnitTest` só existe no `:app:androidApp`.
- **Suíte completa são CINCO tasks:** `:konsist:test` · `:server:test` · **`:server:integrationTest`** (Postgres real via Testcontainers — **precisa de Docker**) · `testAndroidHostTest` (módulos KMP) · `:app:androidApp:testDebugUnitTest` (ViewModels do app). Esta lista já mentiu **duas vezes**, e das duas o custo foi real:
  - dizia "duas" e esquecia a do `app`: entre a B.3 e a A.0 esse source set não compilava e ninguém percebeu. A CI passou a rodá-lo em 2026-08-20.
  - dizia "três" e esquecia a **de integração**, que não roda junto por exigir Docker. Fechei o #35 e a F.1 sem ela, e a V40 (`users.code` NOT NULL) quebrou **51 dos 62** testes de integração — descoberto só no GitHub Actions, depois do merge da fatia anterior.

  > **Migration que aperta uma coluna quebra todo INSERT direto que já existia** — e teste de integração, seed e script SQL são exatamente isso. Ao mexer em `NOT NULL`/`UNIQUE`/`CHECK`, procure `INSERT INTO <tabela>` fora do repositório.
- **Todos os módulos em JVM 17**, o `app` inclusive (corrigido em 2026-08-20; estava em 11, o que impedia inlinar `AppResult.map`/`flatMap` do core lá dentro). Ao criar módulo novo, alinhe — divergência de `jvmTarget` aparece como erro de bytecode incompreensível, não como erro de configuração.
- **Leitura é cache-first, sempre** (#30): a tela lê do banco local; a rede é sincronizador. Erro de rede não vira erro de tela quando há dado local.
- **Frescor:** TTL para o que você NÃO controla; `invalidate()` para o que você controla. Invalidar cache é consequência de mutação, nunca de navegação.
- **Emenda (2026-08-21, fatia A.3):** dado que muda por ação de **TERCEIROS** vai à rede **ao entrar na tela**, ignorando o TTL. O TTL protege bem o que muda pela ação do próprio usuário (o XP da Home), porque aí a invalidação na mutação cobre tudo. A contagem de membros de um grupo muda porque **outra pessoa entrou** — esperar cinco minutos ali não é economia, é atraso, e o usuário não tem o que fazer além de esperar. O custo é uma requisição pequena por entrada na aba, não as seis que motivaram o TTL.
- **Todo dado e todo carimbo local são chaveados por uid** — o isolamento por conta deve ser consequência da CHAVE, não de um `if` que alguém precisa lembrar de escrever.
- **Escrita é um caminho só** (#30): grava local → enfileira no outbox → agenda o envio, com ou sem rede. Dois caminhos ("online manda direto") deixam o offline mal testado.
- **O outbox cobre o que o usuário POSSUI**: conteúdo `MANUAL` é otimista; conteúdo `AI` é travado por entitlement (#25) e continua online-only — a recusa tem de chegar no momento da ação, não pela fila minutos depois.
- **Falha temporária × permanente** é a regra de maior consequência da fila: 4xx de recusa para de tentar; sem rede / 5xx / 401 continua. Na primeira falha temporária o processamento PARA (a ordem entre alvos é dependência real).
- **Recompensa concedida NUNCA é retirada** (#32): XP e nível podem ser 100% derivados porque são números; conquista não pode, porque mudar um limiar faria a medalha sumir do perfil de quem já a viu. Regra derivada e pura, desbloqueio persistido com data.
- **Escrita otimista exige selo na UI** — sem ele o usuário não distingue "está no servidor" de "está só neste aparelho".
- **Fronteira de privacidade é o DTO, nunca uma condicional** (#34): dado privado não existe no contrato público. Isolamento que depende de alguém lembrar de escrever um `if` falha — já falhou com o uid no cache local.
- **Identidade em contexto social é `display_name`, nunca e-mail** (#33): a coluna é NOT NULL com backfill, para não espalhar `?: "Usuário"` por toda tela que mostra gente. O e-mail não atravessa a fronteira do grupo.
- **Estado que é função de datas é DERIVADO, não persistido** (#22, #16, #33): sem coluna `status`, sem job de virada — job é mais uma peça falhando em silêncio.
- **Erro é `AppError` até a UI** (#31): nunca `String` no state. Quem escolhe o texto é a camada que conhece a plataforma.
- DTO nunca vaza p/ o domínio (mapper na data). NavHost puro. Datas no server: `kotlin.time.Clock`.
- Trabalho fire-and-forget que não pode morrer com a tela vai no appScope (singleton em appModule), não no viewModelScope.
- Ritual de fechar fatia: main → commit → atualizar HANDOFF + fragmentos + ADR novo → regenerar PDF. **As FONTES são atualizadas antes do gerador** (2026-09-07): `gen_tudo.py` lê `docs/fontes/`, então rodar sem editar produz PDF com data de hoje e conteúdo de anteontem — pior que PDF velho, porque parece confiável. > **Regenerar não é atualizar.**
- **Fake sem o índice do banco prova o oposto do que se quer** (E.2): os dublès reproduzem os índices únicos reais — `(grupo, usuário, dia)` no check-in, `(checkIn, user)` na reação, `(alvo, denunciante)` na denúncia. Um fake que guardasse em lista deixaria "uma por pessoa" passar contando 2, e o defeito só apareceria em produção.
- **Ramificar por um código só e depois multiplicar os códigos quebra a ramificação sem quebrar o build** (#37, G.2): o `ErrorUi` abria o paywall com `if (code == ENTITLEMENT_REQUIRED)`, e dar código próprio a cada portão de plano quase o apagou em silêncio. O conjunto `ErrorCodes.PORTOES_DE_PLANO` mora no CONTRATO, e um teste percorre ele inteiro. Vale para toda ramificação por valor único que um dia pode virar vários.
- **Guarda que barra por dois motivos precisa de duas frases** (#37, G.2): `podeResponder` é uma condição só e recusa por "já foi respondido" **ou** "é o seu próprio pedido". Dar a mesma frase aos dois fez o app afirmar algo falso, e foi o erro que a fatia cometeu enquanto o corrigia. Quem apontou foi um teste escrito muito antes.
- **Desmembrar por caminho de código, e não por situação do usuário, multiplica trabalho sem mudar uma tela** (#37, G.2): as 104 ocorrências de erro eram 87 frases distintas; *"Programa não encontrado"* aparecia em 7 lugares que são a MESMA coisa para quem usa. O critério é a situação de quem lê, nunca a linha que percebeu.
- **Débito que ninguém consegue enumerar não é débito, é surpresa** (#37, G.2): as 9 frases de erro que carregam número do servidor não serão traduzidas, e por isso estão LISTADAS em `TextosDeErro.SEM_TEXTO_PROPRIO`, com teste exigindo que todo código tenha texto ou apareça ali.
- **Texto que o usuário lê vive em UM lugar: o `strings.xml` do cliente** (#37, 2026-09-09). O servidor manda dados, códigos e chaves, nunca prosa. Duas fontes de tradução divergem na primeira que alguém esquecer de atualizar, e um catálogo só significa uma verificação só (o `MissingTranslation` do Lint). **Exceção única: a notificação**, porque push só-dados não aparece com o app fechado (#36) e o texto precisa sair pronto do servidor.
- **Sem travessão em texto de usuário** (#37): nem `—` nem `–`, em nenhum idioma, nem na documentação. **Verificado por teste**, não combinado, porque quem traduzir o sétimo idioma não vai ler o ADR. Já havia uma violação anterior à regra, no `StructureEngine.rationale`, achada por acaso na saída de um teste que não é sobre texto. Desde a G.3 a varredura alcança o `strings.xml` inteiro, e os 15 que existiam no cliente foram reescritos.
- **Sem caixa alta cravada no texto** (#37, G.3): caixa é decisão de DESENHO, e desenho morando no dado quebra em quem traduz — alemão capitaliza substantivos por gramática, turco tem duas letras i, e há escritas sem caixa nenhuma. O Compose **não tem `text-transform`**, e `.uppercase()` imporia a caixa a todo idioma; a única saída que não mente custou a aparência de 12 cabeçalhos, mudada de propósito em 2026-09-10.
- **Débito enumerado só tem dois finais decentes** (#37, G.3): ou é pago e o teste ENDURECE (a lista vira proibição), ou a lista vira mobília. Travessão e caixa alta foram enumerados durante a extração e proibidos no portão; a única lista que sobrou é a dos textos repetidos, porque lá a decisão foi MANTER.
- **Duplicata de texto sobrevive num idioma e diverge no segundo** (#37, G.3): é o argumento da `Rotulos.kt`, e a fatia o viu se cumprir — `MuscleGroup.FOREARMS` era "Antebraço" numa tela e "Antebraços" noutra **antes de qualquer tradutor entrar**. Um teste enumera os 19 pares idênticos que restam, cada um com o motivo. **Cinco são falsos duplicados** (`Sair` do desafio × da conta, `Descanso` dia × cronômetro, `Fazer check-in` abrir × enviar, `Treino de hoje` rótulo × nome, `FitJourney` marca × título) e têm aviso cruzado nas duas pontas: unificá-los quebra uma tela num idioma que ninguém desta equipe lê.
- **Rótulo derivado do nome do enum não é rótulo, é o identificador com maiúscula** (#37, G.3): aconteceu duas vezes — `ExerciseCategory` mostrando "Chest" ao usuário brasileiro, e `GroupRule` mostrando `localizacao` sem cedilha. Rótulo de enum mora em `ui/Rotulos.kt` e devolve `@StringRes Int`, nunca `String`: assim não precisa de `Context` nem de `@Composable`, e o teste compara ids sem Android.
- **Campo que o servidor compara não é rótulo, mesmo escrito em português** (#37, G.3): `SplitType.label` está documentado como "texto de UI" e o `WeekSpread` faz `split == SplitType.FULL_BODY.label`. O cliente passou a ler o catálogo e o contrato ficou intacto. Mesma família do sentinela `value == "Sim"` do paywall, adiado para a G.4. > **Sentinela escrito em português é código ramificando em idioma.**
- **Extrair a palavra e deixar a concordância no código traduz o vocabulário, não o idioma** (#37, G.3): `if (n == 1) "dia" else "dias"` é gramática do português cravada no cliente. Doze `<plurals>`, e um teste exige `one` e `other` em cada — russo tem três formas, árabe seis, e o inglês trata zero como plural.
- **⚠️ Curinga de import + `R` = 80 erros que não mencionam import nenhum** (#37, G.3): `material3.*` e `layout.*` trazem os `R` das bibliotecas para o escopo, e sem `import dev.rafael.app.R` explícito o compilador diz *"class R does not have a companion object"*. Mordeu em três arquivos. **O import do `R` entra ANTES de qualquer substituição.**
- **`joinToString { stringResource(...) }` não compila** (#37, G.3): `joinToString` não é inline e a lambda não herda o escopo `@Composable`; `map` é inline e herda. Resolver, depois juntar. Da mesma família: `stringResource` dentro de `try/catch` é terreno em que o compilador do Compose já foi restritivo — resolva o `runCatching` primeiro e formate depois.
- **Idioma no fio é `String`, nunca enum** (#37): mesma razão já escrita no `ErrorResponse` sobre o `code`. Enum serializado faria um idioma novo derrubar a desserialização do `/me` INTEIRO num cliente antigo, e a pessoa perderia a tela por causa de um idioma que não escolheu.
- **A cadeia de fallback de idioma existe ANTES da lista** (#37): exato, depois só a língua ignorando a região, depois o piso `pt-BR`. Escrita e testada com dois idiomas justamente porque adicionar o terceiro só funciona se ela já existir. > **Idioma que não existe precisa cair em algum lugar decidido, ou cai onde der.**
- **Entrada de sistema tolera, entrada de usuário recusa** (#37): `IdiomaPolicy.de` nunca falha (coluna do banco, locale do aparelho); `IdiomaPolicy.valida` devolve `null` (corpo de `PATCH`). Quem pede `es` e recebe `200 OK` acredita que escolheu espanhol, e atribui a falta de tradução a um defeito do app.
- **Requisição que falha não pode ter mudado metade** (#37): o `PATCH /me` valida os dois campos ANTES de qualquer escrita. Com um campo só o caso não existia; com dois, nome válido mais idioma inválido gravaria o nome e devolveria erro.
- **Verificação que o compilador faz não precisa de teste, e não pode ser esquecida** (#37): `ChaveDeAviso` é sealed e os `when` por idioma são exaustivos. Aviso novo quebra o build em todo idioma não traduzido; idioma novo quebra o `render`. É o `MissingTranslation` do lado do servidor, que lá não vem de graça.
- **⚠️ `:konsist:test` fica `up-to-date` sem rodar** (2026-09-09): o Konsist lê os fontes em tempo de execução e o Gradle não os conhece como entrada da task. Um verde de 7s com "12 up-to-date" **não varreu nada**. Use `--rerun-tasks` ao fechar fatia. Mesma lição da V40 com outra roupa: **suíte que não roda não cobre nada.**
- **A suíte de integração compartilha UM Postgres** (`BancoDeTeste`, 2026-09-09), limpo por `TRUNCATE` no `@BeforeAll` — o isolamento é por **classe**, nunca por teste. Duas consequências que valem como regra:
  - **Asserção sobre a tabela inteira assume um isolamento que esta suíte não dá.** Afirme sobre o cenário (o par, o grupo, o usuário), nunca sobre um `count()` global. Já cobrou: um `blocks.count() == 0` falhou por bloqueios de testes anteriores, sem relação com a cascata afirmada.
  - **Estado global de conexão não convive com duas estratégias de container.** `Database.connect` do Exposed define o default de todo `transaction { }` do processo; uma classe com container próprio que o chame deixa a seguinte com um datasource morto — 39 falhas com `SQLException`, e a primeira da ordem passando, o que fez o erro parecer aleatório. `MigrationIntegrationTest` e `DisplayNameBackfillIntegrationTest` mantêm container próprio de propósito (testam o schema **nascendo**) e falam JDBC cru justamente por isso.

  > **Suíte lenta é suíte que ninguém roda.** O critério da mudança não foi elegância: esta suíte atravessou duas fatias sem ser executada, e quando rodou a V40 tinha derrubado 51 de 62.
- **Model que não representa o que a tabela permite empurra alguém a contornar o model** (2026-09-09): `AcaoDeModeracao.adminId` era `Uuid` não-anulável enquanto a coluna aceita `NULL`, e o `encerrarCasosPendentes` inseria `null` **direto na tabela**. Duas verdades sobre a mesma coluna, e a do Kotlin era a errada. Achado pelo compilador ao escrever o teste de integração — ele recusou um caso que o banco aceita todo dia.
- **Teste que passa por dois motivos não prova nenhum dos dois** (E.2): o cenário em que o admin também era dono do check-in deixava o teste da autoridade dele verde por acidente — continuaria passando se a regra do admin sumisse.
- **Reiniciar o Ktor faz parte de cada correção de servidor** — três investigações entre a E.2 e a F custaram isso. `installDebug` atualiza o app; o processo Ktor precisa ser derrubado à mão, e o sintoma de esquecer é o mais enganoso possível: o comportamento antigo continua e parece que a correção não funcionou.
- **Notificação gravada nunca muda de texto** (#36). Corrigir uma frase não altera as que já estão na bandeja ou na central — só as novas. É o invariante funcionando, e engana quem está testando.
- **`current_date` do Postgres é UTC** (F): depois das 21h em São Paulo, `- 1 day` ainda é *hoje* para um grupo em `America/Sao_Paulo`. Comando de teste que depende disso acusa o código sem motivo.
- **Teste de comportamento negativo precisa provar que chegou lá** (F): um `isFailure` sozinho é satisfeito por qualquer falha, inclusive a que acontece **antes** do trecho em teste. Foi o que fez um teste passar sem exercitar nada.
- **Não aplique correção em massa sem medir antes** (bancada, E.2): um `UPDATE` de teste com `where status = 'VALIDO'` em vez de `where id = ...` atingiu 42.784 linhas do `seed_volume`. Reverter às cegas teria criado o erro na direção oposta, sem um "antes" para comparar.
- **Backup de cache restaura mentira** (2026-08-23): `allowBackup="true"` devolveu ao app um catálogo com caminhos `.png` que já não existiam, sobrevivendo a `pm clear` e a reinstalar. Cache local do app fica **fora do backup** — e mudar dado que o cliente cacheia **não é uma migration, são duas**: uma no banco e uma no tempo.
- **Instrumento vence adivinhação**: o N+1 de `meusGrupos` apareceu porque o seed criou volume para LER no log, não porque alguém suspeitou dele ou rodou teste de carga.
- **O fake tem que implementar a REGRA, não devolver constante** — um teste de integração passou a mentir porque todos os check-ins compartilhavam o mesmo `photoRef`.
- **Par de asserções duvidoso mora no MESMO teste**, senão "consertar" uma delas quebra a outra em silêncio.
- **Texto que descreve posição envelhece a cada mudança de layout** ("apague o check-in abaixo" quebrou quatro vezes).
- **`MutableStateFlow` de ViewModel só se escreve com `update`** — read-modify-write através de uma suspensão perdeu a lista de grupos inteira.
- **Reiniciar o Ktor faz parte de rodar a bateria manual.**
- **Todo defeito que escapou vira teste** — e o caminho que a MÃO não alcança também: modo avião não testa a tela B quando a tela A, que vem antes, já trata o offline (achado da bateria da C.1).
- **Migration no TÍTULO do PR quando ela toca dado que já existe** (`ALTER`, backfill, `DELETE`, mudança de constraint). `CREATE TABLE` puro fica só no checklist — não há o que dar errado com dado de ninguém. O critério é "o que eu ia querer ler primeiro se algo quebrasse em produção": tabela nova não é suspeita; coluna `NOT NULL` acrescentada a `users` é a primeira. O título é o que sobra no `git log` depois do squash.

## O que ATRAVESSA a sessão — a classe de defeito mais cara do cliente

Componentes criados junto com o `AppNavHost` vivem enquanto a Activity viver e **atravessam logout
e login sem serem recriados**. O `MenuViewModel` já acumulou **quatro** defeitos por isso, todos
com o mesmo formato: um valor resolvido uma vez, num instante em que a resposta ainda era outra.

| Defeito | O que ficava preso | Corrigido com |
|---|---|---|
| "?" eterno no cabeçalho (A.0) | a chave do cache, resolvida no início da coleta | `TokenProvider.uidFlow()` |
| Nome da conta anterior sobrando | idem | idem |
| Segunda saída não disparava | `saiu = true` para sempre — evento modelado como estado | `consumirSaida()` |
| Esqueleto girando para sempre (D) | "carregando" e "falhou" eram o mesmo estado | fallback para o e-mail local do Firebase |

A F.1 repetiu o padrão em outro lugar: o registro de push num `LaunchedEffect(Unit)`, que não
redispara no login, e o `filterNotNull()` antes do `distinctUntilChanged()`, que engolia o segundo
login da mesma conta.

> **Antes de resolver um valor, pergunte se ele sobrevive a um logout.** Se o componente nasce com
> o `AppNavHost`, a resposta é quase sempre não — e o que resolve é observar a sessão, não ler uma
> vez.

**Coberto desde 2026-09-06.** A regra saiu do `AppNavHost` para `ReagirASessao`, e o
`ReagirASessaoTest` atravessa a fronteira que nenhum teste atravessava: `"u1" → null → "u1"`.
Duas lições do caminho:

- **Regra escondida dentro de UI é regra sem teste.** Enquanto o código vivia num `@Composable`,
  exercitá-lo exigiria compor a árvore inteira — e foi ali que os dois defeitos invisíveis moraram.
- **O dispatcher de teste decide o que o teste consegue observar.** Com `StandardTestDispatcher` os
  quatro testes que esperavam registro viam zero; para quem **observa** um `Flow`, o certo é
  `UnconfinedTestDispatcher`.

## Decisões recentes (resumo; detalhe nos ADRs)

- **Docs de conferência/histórico sem mais pendência apagados (2026-10-07):** `fase-6-decisoes.md` (zero `[ABERTO]`, decisões já em `regras/07-checkin-e-moderacao.md` e débitos já em `debitos.md`) e `relatorio-testes.md` (histórico desde 2026-08-17, substituído por `gen_testes.py` + `mapa-de-testes.md`). Geradores `gen_fase6.py`/`gen_relatorio.py` e os dois PDFs correspondentes removidos junto.

- **Regras: "Especificação de Lógica" virou pasta, uma área por arquivo, e trocou de nome
  (2026-10-07):** `docs/fontes/especificacao-logica.md` (~1000 linhas, um arquivo único) →
  `docs/fontes/especificacao/` (15 arquivos + índice, mesmo dia) → `docs/regras/` (renomeada de
  novo, mesmo dia — nome final). PDF: `FitJourney_Especificacao_Logica.pdf` →
  `FitJourney_Regras.pdf`. Objetivo: quem for tocar numa área abre o arquivo certo direto, sem
  procurar num arquivo único. Nenhuma regra mudou de conteúdo — checado por contagem de
  `[REGRA]`/`[INV]`/`[PROPOSTA]` antes/depois. **Convenção daqui pra frente:** área nova com
  regra própria ganha arquivo novo na pasta, nunca empilha em "regras gerais" por economia de
  arquivo. `gen_especificacao.py` lê a lista fixa de arquivos (não glob) — área nova precisa
  entrar nela também.

- **J.1 — desmembramento do Progresso (2026-10-01):** a tela acumulava métricas, a porta das conquistas e o histórico inteiro. Três coisas numa tela não é uma tela de três coisas, é uma tela sem assunto. **Conquistas** e **Histórico** foram para o drawer, com tela própria cada um — os dois são CONSULTA, e quem abre o app para treinar não passa por eles.
- **J.2 — análise de progressão (2026-10-02):** `GET /me/progress` com tonelagem, carga por semana, 1RM estimado (Epley) e séries por grupo. **Portão como ausência, não como 403**: responde 200 com os blocos pagos nulos, porque aqui não há ação bloqueada e um erro que não é erro custaria uma 2ª chamada. `analysisLocked` separa "é pago" de "você ainda não treinou com carga" — sem ele, premium recém-chegado veria o paywall do que acabou de pagar.
- **J.2 — o volume por grupo conta SÉRIES, e a decisão veio do número:** em kg, 8 semanas reais deram pernas 105 t × ombro 6,6 t (16×) por causa do leg press; em séries, 6×. **Comparar grupo muscular por quilo é comparar alavanca, não esforço** — e o gráfico em kg daria diagnóstico errado com cara de dado.
- **J.2 — `Metrica.CARGA` com alvo em TONELADAS:** a frase da medalha recebe o `target` no `%1$d` (débito "números de conquista cravam constante do servidor"), então alvo em quilos imprimiria "250000" no cartão. `valorDe` trunca: 999 kg são zero toneladas. Fatia **atômica nas três camadas** — `ConquistaIds.TODOS` é escrito à mão e dois testes o amarram ao enum do servidor e ao `strings.xml`, então separar deixaria o build vermelho no meio do caminho.
- **J.2 — `ExerciseRepository.paraAnalise(ids)`:** leitura CRUA do catálogo (nome de piso + músculos primários), **sem `idioma`**. Agrupar série por músculo é DECIDIR, não exibir; a tradução continua na borda, pelo `nomesTraduzidos`, sobre os poucos ids que sobram — o mesmo arranjo do `alternatives`. **Não dá para snapshotar nome de programa/exercício na sessão**: a V48 tirou nome derivado do banco de propósito.
- **J.2 — gráficos em Canvas, sem biblioteca:** três formas simples (barras, linha, lista ranqueada). Uma lib traria tema próprio e gramática visual que não é a do app, e **a lista ranqueada de séries por grupo continuaria custom de qualquer jeito** — duas gramáticas na mesma tela é o que lê como inacabado. Compromisso explícito: se depois do polimento ainda parecesse cru, o Vico entrava. Depois do polimento, não entrou. **O Canvas desenha MARCA; todo rótulo é `Text` de verdade** — texto dentro de Canvas ignora o tamanho de fonte do sistema e some para o leitor de tela.
- **J.3 — filtro por PROGRAMA, e é correção, não conveniência (2026-10-02):** programas COEXISTEM (V60/#27), então o gráfico somava X e Y sem avisar. O nome do chip sai do cache LOCAL, nunca do servidor: nome de programa é DERIVADO de `daysPerWeek` + `split` no idioma da tela (V48/#37), então `availablePrograms` carrega só ids — e id que o cache ainda não conhece é PULADO em vez de virar um chip genérico. Sessão de programa APAGADO cai no balde "avulsos" junto com a sem programa, porque o segundo não tem como ser nomeado.
- **J.3 — a semana do programa conta por DIAS CORRIDOS do `started_at`, não por semana ISO:** quem começou numa quarta teria uma "semana 1" de cinco dias. Usa `Math.floorDiv` porque a divisão de `Int` em Kotlin trunca em direção a zero, e isso poria uma sessão 3 dias ANTES do início dentro da semana 1.
- **J.3 — o teto da faixa é `max(duração declarada, última semana com dado)`:** `duration_weeks` é um PLANO, não um limite. Quem programou 8 semanas e treinou até a 10 não pode ver as duas últimas sumirem do gráfico.
- **J.4.1 — uma JANELA, e duas declaradas de propósito (2026-10-03):** totais, "desde" e a comparação usam TODO o histórico do recorte (o `sinceDate` é a primeira sessão da vida e as conquistas de carga acumulam); os três gráficos usam a janela escolhida. **O que não pode voltar a acontecer é cada GRÁFICO escolher a sua** — era o que fazia a média de séries dividir o histórico inteiro por 8 (volume inflado ~3× em quem treina há meses) e a linha de 1RM plotar dois anos ao lado de barras de dois meses. `naJanelaDeCalendario` é o espelho do `naFaixaDoPrograma`: um corte, um lugar.
- **J.4.1 — o portão mudou: paga-se a PROFUNDIDADE, não o gráfico.** `weeklyLoad` era 100% pago, o que significava que ninguém free via oito semanas — e por isso "janela longa" não vendia nada: não existia quem visse 8 e quisesse 26. Free vê a janela curta; 26/52, o 1RM e o volume por grupo são pagos. Value-first (#23): a tela de Progresso do free deixou de ser só um paywall. O chip travado fica **visível com cadeado** e leva ao paywall — esconder não vende.
- **J.4.2 — a seleção de exercícios NÃO mora no filtro:** o filtro é o recorte (que dado responde); a seleção é o que se desenha sobre ele, e qualquer recorte aceita qualquer seleção. Dois eixos, não um. Mas os dois entram na CHAVE do cache, porque o servidor só manda os PONTOS dos escolhidos. **Três linhas continua sendo o teto**, e é da escala COMPARTILHADA: leg press a 200 kg e rosca a 20 kg no mesmo eixo achatam a rosca numa reta. Quem responde "e os outros vinte exercícios?" é a LISTA, que dá o número sem desenhar nada.
- **J.4 — a assimetria das entradas do `/me/progress`:** só `programId` malformado vira 400. Janela fora da lista, faixa fora do teto e id de exercício que a pessoa não treina **encaixam em silêncio**. A regra: `programId` muda QUAL dado responde; os outros só mudam o que é desenhado, e recusar a requisição inteira deixaria a tela sem gráfico.
- **J.2 — família de cor `Chart1/2/3` + `ChartGrid`:** dado não é ação nem recompensa. Barra em `ActionBlue` parece botão, em `Volt` promete recompensa — e o lime é exclusivo do perfil individual (#16). Os três foram escolhidos JUNTOS e validados contra a superfície `Ink1` para daltonismo (pior par ΔE 12,1 em deuteranopia, 23,7 em visão normal, contraste ≥ 3:1): **trocar um sozinho quebra a garantia.**

- **J.5 — detalhe por sessão de um exercício dentro do programa (2026-10-04), fecha a fatia J:**
  "ver detalhado" na lista da J.4 abre o histórico INTEIRO do exercício naquele programa, sem
  faixa (não herda `de`/`ate` do Progress — seria um terceiro conceito de janela). Portão é
  **403 `ENTITLEMENT_REQUIRED`, não campo nulo**: ao contrário do `/me/progress`, aqui não há
  bloco grátis pra misturar, é drill-down de dentro de uma lista já paga.
- **J.5 — `evolucaoPorSessaoNoPrograma` agrupa por `sessaoId`, não por data:** duas sessões no
  mesmo dia (raro, mas possível) não podem virar um ponto só, ou `volumeKg` somaria dois treinos
  como se fosse um. Cada ponto é uma SESSÃO, não uma semana.
- **Dúvidas Frequentes — tela estática, sem servidor (2026-10-04):** conteúdo fixo em
  `strings.xml` (não banco, ao contrário da Wiki) e accordion (não navegação por pergunta) —
  duas decisões ratificadas antes de codar. 8 perguntas fundamentadas em regra já implementada;
  preço, cancelamento, suporte e política de privacidade ficaram de fora de propósito (débito em
  `handoff-retomada.md`).

- **Precificação — faixa ratificada (2026-09-26):** US$ 9,99–14,99/mês internacional (US$
  79,99–99,99/ano); R$ 24,90–34,90/mês ou R$ 199,90–249,90/ano no Brasil. Posiciona o app entre
  Jefit e Fitbod (motor de IA + comunidade + offline-first, combinação que nenhum concorrente
  pesquisado tem). **Decisões 2–5 da seção 5 de `docs/propostas/precificacao-PROPOSTA.md` seguem
  abertas** (moeda fixa vs. conversão automática, trial por tempo, tetos do freemium, mensal+anual)
  — nenhuma toca em código de billing até elas fecharem; schema hoje é só `is_premium` booleano
  (V6), sem tabela `subscriptions`.

- **Fase 0 — motor destravado (V21):** extensoras de quadríceps estavam invisíveis (perna só composto); reativadas + fix is_compound da nórdica.
- **Desmembramento de braço (Fatia B/B.2):** ARMS → BICEPS/TRICEPS/FOREARMS. Motor casa por `category` (V22 reativa antebraços/tríceps casa); contrato/quiz com MuscleGroup fino (V23 re-tag catálogo + perfis). Push→tríceps, Pull→bíceps+antebraço.
- **Cronograma de semanas (V24, #22 estendido):** programa ganha duration_weeks (mín 8 = 2 meses) + started_at; currentWeek derivado no servidor. Cliente: "Semana Y de X" + banner de conclusão. Sem periodização; sem ciclo rolante (só fixo curado).
- **Calendário-âncora (WeekSpread):** default curado ancorado na segunda quando não há dias off; espaçamento vira fallback.
- **Dedup (V25):** 15 grupos de nome idêntico mesclados (preserva mídia + repoint FK). Funcional (stiff/romeno) fora de propósito.
- **Fix lombar (V26):** LOWER_BACK→LUMBAR nas contraindicações (gate PAR-Q furado).
- **Idade (V27, #24):** gate no `/programs/generate` (<18 aceite / ≥69 informativo) + ampliado como variável do motor (volume por faixa + RIR conservador).
- **#29 Split guiado:** split escolhido na onboarding (livre; recomendado por dias via SplitCatalog). Migration V18.
- **#23 Value-first (impl):** ProgramBlur — Dia 1 livre, resto trancado p/ não-premium; revelação na onboarding.
- **#25 Gate de edição (estendido):** programa IA só é mutável por premium em TODAS as ações (rename/add/edit/remove treino), via `requireEditable`. Excluir programa inteiro fica livre.
- **#30 Offline-first (leitura completa):** banco local é a fonte da verdade da UI; `Flow` do local + sync de fundo. Tabelas reais para programa/treino (saíram do `kv_cache`). `SyncStamps` persiste os carimbos no `kv_cache` com uid na chave — substituiu 4 pares `sincronizadoEm`/`donoDoCache` manuais. TTLs não uniformes de propósito: catálogo 24h (GLOBAL, muda só em deploy), programas/treinos/histórico 5min, XP 2min, perfil sem TTL. Cold start caiu de 6 requisições para 2. Splash cache-first (onboardingCompleted é monotônico). **Escrita offline FECHADA (2026-08-16, B.1–B.4):** id gerado no cliente + `insertIgnore` no servidor (idempotência); tabela `outbox` guarda COMANDOS, não estado+flag; `CompactadorDeOutbox` reduz a sequência do usuário ao menor conjunto de requisições e preserva a ordem entre alvos; `ProcessadorDeOutbox` + um `ExecutorDeOperacao` por feature (core não depende de feature); `AgendadorDeSync` mantém os módulos KMP livres de WorkManager. O `save()` do sync protege os alvos pendentes na limpeza E no re-insert. Logout não limpa a fila (é chaveada por uid).
- **#31 Apresentação de erro:** `AppError.Connection` separa transporte de 500 — antes os dois eram `Unexpected`, e o fallback de cache disparava em erro interno do servidor. `app/ui/ErrorUi.kt` é o ponto único (4 níveis: silêncio / tela / inline / snackbar); o componente devolve `ErroAcao` e NÃO navega. 401 tem contexto (login = senha errada; logado = sessão expirada). `refreshTokens` no Ktor — sem ele, após ~1h todo request virava 401 permanente. `SessionExpiryBus` no core, porque o 401 pode nascer no SyncWorker.
- **#23 gate de LEITURA (emenda 2026-08):** o blur existia só na listagem; `GET /workouts/{id}` entregava dia trancado completo. `ProgramAccess` virou a definição única (blur + `requireReadable` leem a mesma função). Recusar o premium NÃO apaga o programa — Dia 1 continua livre.
- **Onboarding: primeiro programa opcional:** tela `ProgramOffer` no fim do quiz. A pergunta fica DEPOIS do quiz porque o quiz é sobre o PERFIL (ambiente #28, idade #24, limitações), não sobre o programa. `AppRoute.Paywall(voltarParaHome)` distingue os dois contextos de abertura.
- **#32 Conquistas:** `AchievementPolicy` pura (9 conquistas, 3 métricas) + V34 `user_achievements` com PK composta dando idempotência. A avaliação dispara na LEITURA de `/me/achievements` — retroativo de graça, sem migration de backfill. `achievement_id` é TEXT para conquista nova não exigir migration; id é contrato e nunca muda de significado. Sem "treinou N dias seguidos" literal: premiar treino diário contraria o descanso prescrito (#22/#26), então a métrica reusa o streak do XpPolicy. Débito: notificação exige avaliar também no POST /sessions.
- **#33 Grupos e check-in — confiança, não verificação (EMENDA o #17):** votação de 24h, apuração ≥50% e contestação por terceiro estão **REVOGADAS** — com 50 membros seriam 50 votações/dia que ninguém faz, e "silêncio aprova" viraria aprovação automática com 50 notificações. **O check-in nasce VÁLIDO**; foto, local e emoji são autodeclarações; a correção vem por **denúncia** julgada pelo admin (`VALIDO` → `INVALIDADO`, append-only). Também caíram: EXIF (forjável em dois toques e removido por apps de mensagem) e geofence (excluiria quem treina em casa, #28). Estado do grupo é **derivado** de datas + fuso, sem coluna `status` nem job. Localização exibida é **nome escolhido pelo usuário** (`Geocoder` sugere), coordenada só gravada arredondada a 2 casas. Enum de um valor só (`tipo_grupo`, `modelo_pontuacao`) pela técnica do `achievement_id`. **Pré-requisito descoberto: `display_name` em `users`** — o `UserDto` não tem nome, e nunca fez falta porque o app era individual até a Fase 5.
- **#34 Perfil público e drawer:** `AppRoute.Perfil` era `EmBreve` e o `display_name` da A.0 não tinha onde ser editado. **Duas telas**: `Perfil(userId: String?)` (pública, a mesma renderiza qualquer pessoa) e `Configurações da conta` (privada). **[INV] dado privado não existe no `PublicProfileDto`** — "desafios concluídos" vem de `/me/challenges`, porque um campo anulável preenchido por `if (souEu)` vaza no dia em que alguém esquece o `if`. Público mostra nome, inicial colorida, nível, conquistas e totais; **não** mostra e-mail, XP bruto, streak, nem PII de saúde — XP e streak virariam placar paralelo, contra a [REGRA] do #18. **Perfil sai do bottom nav** (4 abas) e vira item do **drawer global**; `Sair` sai da saudação da Home. Conquistas viram a vitrine do perfil (um dono só). **Tema claro descartado**: o app é dark-only por construção e o `Volt` só tem contraste sobre escuro.
- **Agendamento por dia real (G.2):** day_of_week (1=Seg..7=Dom) é fonte da verdade; `PUT /programs/{id}/schedule` (SetScheduleRequest) define o dia de cada treino. Descanso implícito (dia sem treino); ProgramDetail é visão de semana. Motor WeekSpread espaça os treinos; criação manual escolhe o dia. Livre p/ manual, premium p/ IA (não fura o blur #23).
- **#33 fatias B e C (impl):** `check_ins` (V38) com UNIQUE `(group_id, user_id, local_date)` — o "um por dia" é do BANCO, não do código — e três CHECKs (vocabulário de status, local completo exige nome + coordenadas juntos, purga exige foto). Camada de mídia própria: disco em dois níveis, guarda contra path traversal por regex + caminho canônico, foto **re-encodada** (nunca repassada) e guard de bomba de descompressão lendo dimensões do cabeçalho antes de decodificar. Purga agendada, carência de 30 dias, arquivo apagado ANTES de marcar. Validações rodam antes de gravar a foto; foto órfã é apagada se o INSERT perder a corrida. **Ranking (C):** contagem de check-ins, nunca XP ([REGRA] #18); posição atribuída sobre a ordem do banco e **não persistida**; desempate `total DESC`, `ultimo ASC NULLS LAST`, `joined_at`, `user_id` — critério que nasceu de uma correção do Rafael, porque no dia 1 há 50 pessoas empatadas em zero e empate é o caso comum, não o raro.
- **Emenda 9.3-A (2026-08-26) — o perfil é público:** nome, nível, XP e conquistas visíveis a **qualquer usuário autenticado**. Histórico de treino, programas, peso, altura, idade, objetivo e triagem de saúde continuam privados; desafios concluídos seguem só do dono. **XP e nível ficam fora das superfícies do grupo** — não por privacidade, por FOCO: o ranking é por check-in ([REGRA] #18) e dois placares na mesma tela confundem qual é o jogo.
- **#35 Amizades (ADR escrito ANTES do código, e revisado no mesmo dia):** amizade **não é permissão, é atalho** — serve para convidar sem digitar código. Código de usuário de **8 caracteres** (não 6 como o do grupo: este é permanente e sempre resgatável), **regenerável**, com limite de tentativas. Relação **simétrica com aceite**; bloquear existe porque "remover" que não impede novo pedido é gesto vazio. Teto de 500. A fatia **precisa entregar a tela de convites pendentes** — sem ela o recurso apenas parece funcionar, porque notificação é a fatia F. A revisão pela 9.3-A **simplificou** o desenho em vez de complicá-lo (o "preview mínimo antes de adicionar" virou o próprio perfil), o que costuma ser sinal de que a decisão de produto está certa.
- **#34 emendado pela C.1 — DUAS telas de perfil, não uma com `if (souEu)`:** a tela do dono mostra treinos da semana e a barra de XP do nível, privados pela 9.3-A. Uma tela só faria o isolamento depender de alguém lembrar do `if` — e a fronteira nunca foi a UI. Duas telas, dois TIPOS (`UserStatsDto` × `PublicProfileDto`): o compilador garante o que dependia de disciplina. A **rota** continua uma só (`Perfil(userId)`) e despacha. No servidor: porta estreita `GamificacaoDe` (o perfil não arrasta `ProgramService`/`WorkoutGenerator` por causa do streak, que não é público); **404 para id inexistente E malformado**, nunca 403, porque "sem permissão" confirmaria a existência da conta; e **ler perfil de terceiro não concede conquista** — senão as medalhas de alguém dependeriam de quantas pessoas espiaram o perfil dele.

- **F.1 Notificações (impl) — a gravada é a verdade, o push é o aviso:** `notifications` (V42, retenção 6 meses com índice parcial) grava **antes** de despachar; se a gravação falha o push sai mesmo assim, e se o push falha a central mostra assim mesmo. `title`/`body` gravados **já renderizados** — notificação é registro do que houve, não consulta ao presente, e a de ontem não pode passar a dizer o nome novo de quem trocou de nome hoje. `device_tokens` (V41) tem o token como PK: identidade de INSTALAÇÃO, não de usuário; **o logout apaga o token deste aparelho** (`SairDaConta` em três passos, baixa do push ANTES do `signOut`, com o token do Firebase ainda válido). `NotificadorFcm` apaga token morto na resposta do envio — e **loga a causa**, porque `UNREGISTERED` (aparelho fora) e `INVALID_ARGUMENT` (credencial de outro projeto) têm o mesmo sintoma e correções opostas. Cliente: badge na `TopAppBar` das telas-raiz, central própria, abrir marca tudo como lido **preservando o destaque do snapshot já carregado**, e `AvisosDePush` (bus no molde do `SessionExpiryBus`) liga "chegou push" a "recarregue" — sem ele o badge só subia quando a pessoa saía da tela e voltava. **Driver Postgres saiu de `runtimeOnly`** (ARCH): `JsonbText` precisa de `PGobject`, e a restrição "código não referencia o driver" descrevia uma independência de banco que as migrations já não têm.
- **D — emoji do dia (V43, 2026-09-06):** sorteio **derivado** de `(groupId, dia)` — sem tabela, sem job, sem migration para o sorteio — e resultado **gravado** em `check_ins.emoji`, porque a lista curada cresce e recalcular na leitura faria o feed antigo mostrar um emoji que ninguém imitou. `EmojiDoDia` mora em `features/group`: o emoji é propriedade da REGRA do grupo, e pô-lo em `checkin` faria o `GroupMapper` importar de outra feature — o ciclo que o `UserCodePolicy` custou no #35. O emoji aparece **antes** da foto (é instrução, não enfeite) e ninguém confere o gesto (autodeclaração, #33). Lista curada de **29** emojis com cinco critérios, e o mais instrutivo é **"uma mão só"** — 🙌 👏 🙏 🤝 caíram porque selfie se tira segurando o celular, e o critério só apareceu quando alguém olhou pensando em FAZER o gesto. Teste automatizado impede emoji de Unicode 14/15 na lista: eles viram **caixa vazia** antes do Android 14, e nada no app detectaria.
- **Cabeçalho do menu não gira mais para sempre (2026-09-06):** com o `/me` inalcançável e o cache vazio, "carregando" e "falhou" eram o mesmo pixel. Cai para o **e-mail da sessão do Firebase**, que existe offline — dado real, diferente do antigo "Você". `AuthRepository.usuarioLocal()` é o `isLoggedIn()` devolvendo dado em vez de booleano.
- **⚪ F.2 proposta — recarregar por gesto (`PullToRefreshBox`):** cobre o que o push não cobre — "eu duvido do que estou vendo" (push não chegou, rede voltou, quero confirmar). Só nas telas cujo conteúdo muda por **ação de terceiros**: Amigos, Notificações, feed/ranking do grupo. Fora de Conta/Perfil próprio/Configurações, onde o gesto ensinaria a desconfiar de dado que nunca fica velho. **Exige separar `recarregando` de `carregando`** no state: reusar o mesmo campo faria o indicador de pull aparecer junto com o spinner da primeira carga, dois indicadores para o mesmo estado — e essa distinção vale fazer de uma vez em todas as telas.
- **E.1 — comentários e reações (V44, 2026-09-07):** **duas tabelas, não uma.** Comentário é muitos por pessoa, texto livre, ordem cronológica importa; reação é uma por pessoa, vocabulário fechado, o que importa é a contagem. Uma tabela com `tipo` + texto anulável teria de carregar o índice único da reação sem aplicá-lo ao comentário — e regra que vale para metade das linhas é regra que o banco não garante. A PK composta `(check_in_id, user_id)` **é** o invariante 8.2; trocar de reação é `ON CONFLICT DO UPDATE`, sem consultar antes. A **8.2 foi emendada de "qualquer emoji" para SEIS**: com 50 membros escolhendo livremente o agrupamento perde sentido e "12 👍" nunca acontece, emoji recente vira caixa vazia em Android antigo, e abrir teclado custa mais que o conteúdo vale. Vocabulário **aberto no banco, fechado no Kotlin** (técnica do `achievement_id`): a lista é decisão de produto e vai mudar. Comentário ganhou **tela própria** — expandir no card faria o feed virar lista de alturas imprevisíveis saltando a cada polling, com o teclado tapando o que se lê. `canDelete` resolvido no servidor: autor, **dono do check-in** (emenda de 07/09 — é a foto dele) e admin. Sem `updated_at` e sem `PUT`: **comentário não se edita**, e a ausência da coluna é a regra.
- **E.2 — denúncia e moderação (V45, 2026-09-07) — a máquina de estados ganhou quem a move:** `VALIDO → EM_ANALISE → INVALIDADO` existia na V38 desde a fatia B, com o vocabulário fechado e nenhum caminho que a percorresse. **Duas tabelas com políticas de integridade OPOSTAS, e a diferença é o ponto:** `group_reports` é o PEDIDO e leva CASCADE em tudo (alvo apagado, nada a moderar); `moderation_actions` é a DECISÃO (6.6, append-only) e **não tem FK no alvo** — uma FK obrigaria escolher entre a auditoria evaporar junto com o alvo (CASCADE) e o alvo nunca mais poder ser apagado (RESTRICT), e as duas são piores que um UUID solto. O alvo da denúncia é um **arco exclusivo** (`check_in_id` XOR `comment_id`, com `CHECK`), não um `target_id` opaco: opaco custaria a integridade referencial inteira. **6.8 é a regra que impede a denúncia de virar arma** — `EM_ANALISE` continua contando ponto, senão bastaria denunciar quem lidera na véspera do fim. Decisões de produto: o invalidado **fica no feed marcado** (sumir apagaria a evidência de que a moderação agiu, e a publicidade da decisão é o freio contra o abuso de quem decide); **o admin não vê quem denunciou** (o banco guarda, o DTO não carrega — num grupo de conhecidos, denúncia identificada é denúncia que ninguém faz); **motivo obrigatório** (é o atrito que separa a denúncia pensada do toque irritado, e o admin julga sem saber quem reclamou); fila em **tela própria** com badge só na barra do admin. **A guarda de admin devolve 403 e não 404** — única exceção da fatia: quem chega já é membro e está olhando para o grupo. **[REGRA ratificada] check-in sob moderação não se apaga**, nem `INVALIDADO` nem `EM_ANALISE` — a 4.11 libera o slot, e sem a guarda quem fosse invalidado apagaria e refaria; bloquear só o invalidado deixaria a fuga um passo mais cedo, esvaziando a fila antes do julgamento.
- **A bateria da E.2 achou 2 defeitos, do MESMO tipo — o servidor certo e a tela sem perguntar:** (1) o erro da denúncia ia para `state.erro`, renderizado só na aba Sobre, enquanto quem denuncia está na aba Posts com um diálogo por cima — a mensagem existia, estava correta e nunca foi lida; (2) `canDelete` era calculado só pela data no mapper, com a regra de moderação vivendo apenas no serviço — o menu oferecia "Apagar" num check-in invalidado e o servidor recusava depois do toque. Corrigido unificando em `CheckInPolicy.impedimentoParaApagar`, que serviço e DTO leem. **Terceira vez na Fase 6** (o `MeRepository` no menu, a lista de membros da A.4, e estes), e nas três o teste automatizado passava. > **Erro precisa aparecer onde o gesto aconteceu.** > **Duas fontes de verdade para a mesma pergunta divergem na primeira que alguém esquecer.**
- **F — os outros gatilhos de notificação (V46, 2026-09-09), e a aposta da F.1 confirmada:** a F.1 dizia que "tipo novo entra só plugando", e cinco tipos depois isso se provou — os quatro gatilhos de ação entraram por **porta estreita** (`fun interface` / `data class` de lambdas), sem `checkin` importar `notificacao`, e **nenhum dos três defeitos da bateria foi na infraestrutura**. Gatilhos: comentário no meu check-in (só o dono, sem "seguir a thread" — 10 comentários gerariam 55 avisos), denúncia contra mim (**sem dizer quem nem o motivo** — quem foi denunciado sabendo por quem tem oportunidade de retaliar dentro do grupo), nova denúncia no grupo (só admin, **uma por CASO** e não por denúncia — 6.11 valendo também no push), check-in invalidado (**mesmo aviso pelos dois caminhos**: quem perde o ponto não precisa saber por qual rota o admin passou) e entradas do dia agregadas (10.6). **Manter uma denúncia não avisa ninguém**: o denunciado já soube quando ela chegou, e um segundo push dizendo "não deu em nada" ensina que metade dos avisos não pede ação.
- **O quinto gatilho é de outra natureza — virou o `AvisosDoDia`, em `server.avisos`:** não nasce de requisição nenhuma. Laço no boot como as duas purgas, mas **acordando de hora em hora** e não uma vez por dia, porque o dia civil de cada grupo vira num instante diferente conforme o fuso — dormir 24h atrasaria o aviso em até um dia para quem está do outro lado do mundo. **Quem garante o "uma vez por dia" é a PK `(grupo, dia, tipo)` da V46, não o intervalo do laço**, e é isso que torna acordar mais vezes seguro. Mora **fora de `features/`** porque lê membros (`group`), lê denúncias (`checkin`) e escreve notificação: um varredor não é feature, é composição — mesma escolha do `PurgaDeMidia`.
- **A V46 é UMA tabela onde a V44 insistiu em duas, e o critério é o mesmo:** lá comentário e reação tinham **formatos opostos** (muitos por pessoa × um por pessoa), e uma tabela só teria de carregar o índice único de um sem aplicá-lo ao outro. Aqui "entradas do dia" e "fila parada" têm o **mesmo formato exato** — um aviso, por grupo, por dia — e o índice é idêntico. **O critério nunca foi "uma" nem "duas": é se a regra do banco cabe em todas as linhas.**
- **Duas emendas à seção 6 (2026-09-08):** **(1) expiração automática de denúncia foi RECUSADA** — se o caso sumisse sozinho em 24h, a inação do admin viraria absolvição, e num grupo de conhecidos o admin pode ser amigo de quem fraudou. A 6.12 já resolve pelo outro lado: ele não tem prazo **porque** o `EM_ANALISE` continua contando ponto (6.8). O remédio virou o **lembrete de 3 dias**, contado do caso **mais antigo** — pelo mais recente, o grupo com denúncias frequentes nunca geraria lembrete, e é o que mais precisa. **(2) Denúncia em desafio ENCERRADO fecha sozinha**, e aqui automático é honesto: o ranking congelou e invalidar mudaria resultado já comemorado (6.5). Grava `ENCERRADO_SEM_JULGAMENTO` com **`admin_id` nulo** — o único registro da tabela sem autor, e é esse nulo que distingue "o admin manteve" de "o desafio acabou antes". > **Um caso que não pode produzir efeito não deveria continuar pedindo decisão.**
- **A bateria da F achou 3 defeitos, todos no gatilho novo ou no TEXTO:** (1) o aviso de invalidação dizia *"o admin avaliou **a denúncia**"* e era reusado na invalidação direta (6.10), onde não houve denúncia — a decisão de os dois caminhos mandarem o mesmo aviso continua certa, o erro era **afirmar um fato que só valia num deles**; (2) o **criador era contado como entrada**, e fundar um desafio disparava "1 pessoa entrou no seu desafio hoje" sobre si mesmo; (3) *"**seu** desafio"* ia para todos, e para os 49 que entram num desafio alheio o possessivo é falso. Os dois últimos corrigidos por `created_by` e **não** por papel `ADMIN` — o cargo é transferível (2.12), o fundador não muda. > **Mensagem compartilhada só pode afirmar o que é verdade em todos os caminhos que a usam.** > **Criar não é entrar.**
- **⚪ Dívida — lista que recarrega debaixo do dedo:** com o bus de push, um pedido chegando enquanto a pessoa está na aba Pedidos move os itens e ela pode tocar em "Aceitar" do pedido errado. Hoje é pequeno (lista curta, botões explícitos); com os pushes da fatia E (comentários, reações) vira real. Saída conhecida: não recarregar a lista visível e mostrar **"1 pedido novo — toque para ver"** no topo.

## Arquivados

- LLM ao vivo → #20. Sessão avulsa (`/workouts/generate`) → #22. Geração só em Treinos e gate na geração (#19 orig) → #22/#23.

---
_Reconstruído do PDF canônico, 2026-08-06; atualizado 2026-09-21 (v35). Gerado por `docs/geradores/gen_painel.py`._
