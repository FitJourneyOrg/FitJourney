# Plano — RIR chega do cliente e acorda a progressão (fatia "rir-chega-e-registra")

> Documento de handoff escrito no fechamento de "declaração de custo do motor" (2026-10-31), a
> pedido do Rafael: achado durante a investigação de fechamento, não planejamento vindo de fora.
> **Não é a fatia em si** — é o que a sessão seguinte precisa pra abrir com a mão na massa, sem
> precisar de uma sessão só de descoberta. Nenhum código desta fatia foi tocado.

## O que é

Débito (a) de "o motor sabe, o app não mostra" (`debitos.md`) diz "RIR não chega do cliente".
Investigando o fechamento, achei algo que o debitos.md ainda não registra: **mesmo que o RIR
chegasse hoje, nada no servidor chamaria `Progressao.decidir()`/`calibrar()`/`registrar()`**. São
dois gapes empilhados, não um:

1. **(a) Cliente não envia RIR.** `SetLogDto` (contrato) não tem campo `rir`. `SetEntry`
   (cliente, `WorkoutSessionState.kt`) não captura RIR na tela de execução. `session_set_logs.rir`
   existe desde a V69 mas nunca é escrito — `SessionRepositoryImpl.save()` nunca faz `it[rir] = ...`.
2. **(b) NOVO — o servidor nunca liga o motor de progressão à sessão salva, com ou sem RIR.**
   `SessionService.record()` salva a sessão e chama `avaliarConquistas` — só isso. Busquei
   `Progressao.decidir(`, `Progressao.calibrar(` e `ProgressaoRepository.registrar(` em todo
   `server/src/main/kotlin`: **zero chamadas em código de produção.** Só aparecem em
   `AppModule.kt` (DI do bean) e em teste (`ProgressaoIntegrationTest`). `PromotionService` só
   **lê** de `ProgressaoRepository` (`historico`, `exerciciosComEventoNoMesociclo`,
   `primeiroUmRmNoMesociclo`, `reducoesDesde`) — nunca escreve. Resultado: a tabela
   `progression_event` está vazia em produção hoje, então toda âncora do `PromotionService` já
   está com "histórico vazio" **antes mesmo** de o RIR entrar em cena.

(b) é mais sério que (a) sozinho: resolver só o contrato/DTO do RIR não acorda nada, porque não
existe o fio que lê o resultado salvo e chama o núcleo puro. As duas lacunas precisam fechar
juntas pra qualquer coisa mudar em produção.

## Por que ainda está dormente (achado, não suposição)

- `ProgressaoRepository.registrar()` (D-2) é desenhado pra ser chamado **uma vez por sessão
  salva**, na mesma transação do novo `Estado` — mas não existe o chamador.
- `Progressao.decidir()` exige, por EXERCÍCIO da sessão: `Papel` (COMPOSTO/ACESSORIO/ISOLAMENTO,
  atribuído por `SessionComposition.Vaga` em tempo de GERAÇÃO do programa), `incremento` (passo de
  carga do equipamento), `barraLivre: Boolean`, e o `Estado` anterior (`ProgressaoRepository.buscar`).
  **Nenhum desses três primeiros é hoje parte do `WorkoutDto`/sessão que chega no servidor** — o
  gerador de produção (não-v3) não carrega `Papel` pra frente, e `incremento`/`barraLivre` vivem
  (se vivem) no catálogo de exercício, não no snapshot da sessão.
- Primeira exposição de um exercício é `calibrar()` (precisa de `cargaInicial` e lista de RIRs
  informados por série), não `decidir()` — o `SessionService` precisaria saber se é a 1ª vez
  (`ProgressaoRepository.buscar() == null`) pra escolher qual dos dois chamar.

## Perguntas para o Rafael abrir a sessão (em vez de eu decidir)

1. **De onde vem `Papel`/`incremento`/`barraLivre` por exercício, hoje, em produção?** Se vêm do
   catálogo (`ExerciseRepository`) e não da sessão, o `SessionService.record()` precisa buscar
   isso por `exerciseId` no momento de salvar — ok, mas é uma dependência nova do `session` no
   `exercise` (checar se fere "feature nunca depende de feature": `session` → `exercise` é feature
   → feature, salvo se `exercise` expuser isso por uma porta em `core` ou for puxado por
   `services/` sem acoplar módulo a módulo).
2. **O motor v3 continua dormente nesta fatia, ou isso é o gatilho pra ligá-lo?** Se a progressão
   só roda com `Papel` do v3 e o v3 ainda não é o gerador de produção (`debitos.md`, "Troca de
   produção do StructureEngine"), rodar `decidir()`/`registrar()` em produção vai operar sobre
   `Papel` inferido fora do fluxo real do programa — precisa decidir se isso é aceitável como
   "motor roda em paralelo, sem efeito ainda" ou se trava até a troca.
3. **Granularidade do registro: por exercício da sessão, uma vez — mas quando tem mais de uma
   vaga do mesmo exercício no mesmo treino (bi-set, etc)?** `registrar()` tem PK
   `(usuário, exercício, sessão)` — duas vagas do mesmo exercício na mesma sessão colidiriam.
4. **RIR na UI: por série ou só a 1ª série do exercício?** A Regra 4 (`reducaoIntrasessao`) só usa
   o RIR da 1ª série do dia; `decidir()`/`estimarUmRm()` usam RIR de TODAS as séries. A tela
   precisaria capturar RIR série a série (como já faz com `repsDone`/`weight`), não só a primeira.

## Achado de 2026-11-05 — resposta real à pergunta 1 (lido no código, não suposição)

> A pergunta 1 original ("de onde vêm `Papel`/`incremento`/`barraLivre` hoje, catálogo ou
> sessão?") presumia que os três já existiam em algum lugar e só precisavam ser encontrados e
> ligados. **Não existem.** Verificado por grep em `server/src/main/kotlin` e em
> `shared/features/{exercise,session}` (domain + data):

- **`Papel`** (`SessionComposition.Papel`, `engine/v3/SessionComposition.kt`) é um conceito que só
  existe DENTRO do motor v3, atribuído em tempo de geração do programa. Não está em
  `WorkoutSessionDto`/`SetLogDto`, não está persistido em nenhuma tabela de sessão, e o gerador de
  produção hoje (`StructureEngine`/`SlotFiller`, não-v3) não carrega esse conceito pra frente.
- **`incremento` e `barraLivre`** (por exercício) **não existem em lugar nenhum do código.** Os dois
  nomes só aparecem como parâmetros de `Progressao.decidir()`/`calibrar()` — nenhum produtor em
  `Exercise` (catálogo, campo a campo verificado: `equipment` é só `String?` solto, sem estrutura
  pra derivar "é barra livre" ou "incremento de carga"), nenhum em `ExerciseRepository`, nenhum em
  qualquer DTO de sessão. O único "incremento" real no código é o passo fixo de **2,5 kg** da UI
  (`WorkoutSessionViewModel.ajustarPeso()`, botão `-`/`+`) — igual pra todo mundo, sem relação com
  o exercício, e que NÃO serve como substituto: `Progressao` precisa do incremento típico daquele
  equipamento específico (a diferença entre halter de 2 kg em 2 kg e barra com anilha de 1,25 kg).

**O que isso muda:** esta não é uma fatia de "ligar um fio que falta" — é uma fatia de **modelar dois
dados novos no catálogo** (`incremento: Double` e `barraLivre: Boolean` por exercício, provavelmente
migration nova + curadoria manual, já que não é derivável do que existe) **e decidir como `Papel`
sai do v3 e chega no momento do `record()`**, antes de sequer cogitar chamar `decidir()`/`calibrar()`
em produção. As perguntas 2–4 originais continuam valendo como estavam.

**Decisão em aberto, nomeada aqui pra não virar suposição na próxima sessão:** talvez valha quebrar
em duas fatias — (1) catálogo: `incremento`/`barraLivre` por exercício, curadoria, migration; (2) o
fio RIR → `Progressao` → `registrar()`, que já depende de (1) estar pronto. Decidir ao abrir a
sessão, não antes.

## ⚠️ Aviso de 2026-11-05 — o RIR chegando NÃO conserta a qualidade da progressão

> Medido na simulação da fatia do catálogo (`tools/motor_sim/sim_incremento.py`, tabelas em
> `trabalho/catalogo-incremento-e-semantica-de-carga.md`). **Esta fatia não pode ser planejada
> como se resolvesse a progressão** — ela destrava o dado, e o que aparece embaixo são dois
> defeitos de comportamento que já estão lá:

- **A Regra 3 não protege no caso para que foi escrita.** O KDoc diz que a tolerância pós-salto
  existe para evitar "o halter de 8 → 10 kg em loop de subir e devolver". Mas ela exige
  `rirNoAlvo`, e quem acabou de receber um aumento volta com RIR 0 — a tolerância não ativa e o
  DESCE acontece. Resultado medido: oscilação permanente sempre que o passo passa de ~20% da
  carga (halter leve, crossover leve, máquina leve com incremento 5).
- **MANTEM em RIR 0: motor imóvel com a pessoa treinando à falha.** Halter a 13 kg, sessões 3–10:
  8–9 reps com RIR 0 (falha) oito sessões seguidas. Está dentro da faixa 8–12, então não é
  `abaixo`; não bate o topo, então não é `topo` → MANTEM. A Regra 4 seria o freio, mas está
  anulada (limiar 12,5 × incremento = 13 kg; a carga É 13 kg). **Hoje isso é invisível porque o
  motor está dormente; com o RIR chegando, passa a ser comportamento de produção.**
- **[BLOQUEIA O FECHAMENTO DESTA FATIA] A troca de sinal de `cargaInvertida` no `decidir()`.**
  A coluna `carga_invertida` nasceu na V74 (fatia do catálogo) com 4 exercícios marcados, mas
  **o motor ainda não a lê**. Hoje é inofensivo porque nada chama `decidir()` em produção; **no
  dia em que este fio ligar sem a troca de sinal, a Barra Fixa Assistida regride calada** — o
  `SOBE` faz `carga + incremento`, e na assistida mais peso é mais ajuda. Direção errada é pior
  que magnitude errada. Custo conhecido e aceito: mexer em `decidir()` obriga a atualizar
  `server/src/test/oracle/progressao_v3.py` e regerar `referencia_progressao.txt` (847 KB).
  **Caso de teste nomeado, obrigatório na C2 desta fatia:** exercício com `cargaInvertida = true`,
  séries no topo da faixa com RIR no alvo, decisão `SOBE` → **a carga devolvida tem de ser MENOR
  que a anterior**. Caminho de falha do mesmo caso: `cargaInvertida = false` no mesmo cenário →
  carga maior (prova que o sinal não foi trocado para todo mundo).
- **Decisão B (Rafael, 2026-11-05): o contador da Regra 4 conta o EFEITO, não a intenção.** Com o
  arredondamento, a regra computa e é apagada (`anulada = true`). Se o disparo contar a execução,
  a medição mostra freio agindo onde não age. **Corrigir nesta fatia**, que é quem instrumenta a
  Regra 4 pela primeira vez.

## Desenho — Servidor → Contrato → Cliente (esqueleto, a confirmar na sessão)

**Servidor** (depende das respostas 1–3):
- `SessionService.record()`, depois de `repository.save(session)` ter sucesso (mesmo `also` que
  já existe para `avaliarConquistas`): para cada exercício único da sessão, resolver
  `Papel`/`incremento`/`barraLivre`, buscar `Estado` anterior, chamar `calibrar()` (1ª exposição)
  ou `decidir()` (demais), e `ProgressaoRepository.registrar(...)` com o `AncoraDoIniciante.Evento`
  resultante e o `mesociclo` atual (`MesocicloRepository.buscar`).
- `SessionRepositoryImpl.save()`: adicionar `it[rir] = setLog.rir` no insert de
  `SessionSetLogsTable` (arquivo já lido, linhas 1–65 — o insert de hoje não toca `rir`).

**Contrato**:
- `SetLogDto` (`WorkoutSessionDto.kt`) ganha `val rir: Int? = null` — nullable, sem default
  quebrar quem já chama (D-3 do motor: `null` é "não informado", caminho seguro já implementado
  no núcleo).

**Cliente**:
- `SetEntry` (`WorkoutSessionState.kt`) ganha `val rir: String = ""` (mesmo padrão de texto livre
  de `repsDone`/`weight`, convertido com `toIntOrNull()` no `finish()`).
- `SessionEvent` ganha `RirChanged(index, value)`, tratado no `WorkoutSessionViewModel.onEvent`
  igual a `RepsChanged`/`WeightChanged` (linha 62–63 hoje).
- `finish()` (linha ~261) passa `rir = e.rir.toIntOrNull()` pro `SetLogDto`.
- UI de execução (tela de série, não lida nesta investigação): campo de RIR por série, com rótulo
  explicando o que é pra quem nunca usou (iniciante pode deixar em branco: D-3 cobre isso).
- **Sem migration de cache local**: `session_log` do cliente não foi inspecionado ainda — checar
  se há uma tabela de log de série local antes de assumir que basta mandar no DTO de sync.

## Testes (C2, a prever)

- Lógica pura: já coberta (`ProgressaoContraOOraculoTest`, `ProgressaoTest` — não tocar).
- **Novo**: teste de serviço com fake de `SessionRepository`/`ProgressaoRepository`/
  `MesocicloRepository` provando que `record()` chama `registrar()` exatamente uma vez por
  exercício distinto da sessão, com o `Estado`/`Evento` certo — caminho de falha: sessão salva mas
  `registrar()` falha (idempotência, reenvio) não deve derrubar o `record()` (mesmo padrão do
  `avaliarConquistas`, que é `also` e não decide o resultado).
- Rota/repositório: `ProgressaoIntegrationTest` existente provavelmente precisa de um caso novo
  "sessão real com RIR chega e gera progression_event" (Testcontainers).
- Cliente: `WorkoutSessionViewModelTest` (se existir) ganha caso de `RirChanged` + `finish()`
  carregando `rir` no DTO.

## Fora de escopo (nomeado, não resolvido aqui)

- Ligar o v3 como gerador de produção (débito já aberto, "Troca de produção do StructureEngine").
- UI/texto para mostrar ao usuário o resultado da progressão (subiu carga, manteve, etc.) — é a
  MESMA família de "o motor sabe, o app não mostra" que esta sessão acabou de fechar (c)/(d) de.
- Recalibrar os dois limiares heurísticos marcados em `debitos.md` como dependentes de RIR real
  chegando em produção (2 âncoras pra promoção iniciante, maioria estrita pra avançado).
- Tabela/cache local de log de sessão no cliente — não inspecionada; primeiro passo da próxima
  sessão antes de tocar `SetEntry`.

## Ao abrir a próxima sessão

1. Ler este arquivo (não precisa reler `debitos.md` inteiro — as duas lacunas já estão resumidas
   aqui com as referências de linha).
2. Responder as 4 perguntas acima com o Rafael ANTES de codar (C1 — decisão arquitetural nova).
3. Backend → Contrato → Cliente, nessa ordem, como sempre.

## Achado de 2026-11-05 (sessão "login sem backend", antes de abrir esta fatia) — pergunta 2 tem resposta nova

A V74 (fatia do catálogo, fechada hoje) já criou `incremento`/`barraLivre`/`cargaInvertida` em
`exercises` — a pergunta 1 original ("de onde vêm esses três?") está **parcialmente resolvida**:
os dois primeiros existem agora no catálogo, server-side (`Exercise.incremento`/`barraLivre`,
não vão ao DTO). Falta só `Papel`.

E sobre `Papel` (pergunta 2), uma leitura nova do gerador de PRODUÇÃO (não-v3) muda o quadro:

- `SessionComposition.Papel` (v3) é atribuído por VAGA, não por exercício — depende de quantas
  séries aquele músculo recebe na semana e da posição na distribuição (`papeisPara()`). Não é
  propriedade do `Exercise`, é propriedade do PLANO daquela semana. Confirma que não dá pra
  "adicionar uma coluna no catálogo" pra isto, ao contrário de `incremento`/`barraLivre`.
- **Mas a produção já calcula o equivalente.** `StructureEngine`/`VolumeModel` têm `SlotRole`
  (`COMPOSTO_PESADO`/`COMPOSTO_ACESSORIO`/`ISOLAMENTO`) — mesma tripla de `Papel`, calculada do
  mesmo jeito (posição na distribuição de séries do músculo). E uma FATIA do `role` já sobrevive
  até o template persistido: `DeterministicWorkoutGenerator` grava `slot.rir` (RIR alvo, "por
  papel", ARCH #26) em `WorkoutExerciseDto.rir`. O que NÃO sobrevive é o `role`/`Papel` em si
  (nem a faixa de reps — só o `WorkoutSetDto.reps` resolvido, um número, não a faixa) — ninguém
  grava `SlotRole` em lugar nenhum do contrato ou da persistência.

**Isto muda a pergunta 2.** Não é "liga o v3 ou não" — é mais barato: **persistir o `role` que a
produção JÁ calcula**, independente de qual motor gera (v3 dormente continua dormente). Dá pra
desacoplar esta fatia da decisão de troca de produção inteira.

**Pergunta 2 revisada, pra decidir com o Rafael antes de codar:**
- `WorkoutExerciseDto` ganha um campo novo (`papel`? reaproveitar o nome `Papel` do v3, ou um
  nome próprio da produção, já que `SlotRole` e `Papel` são conceitos gêmeos mas de módulos
  diferentes — mesclar os dois enums ou manter dois e mapear na borda?), preenchido por
  `DeterministicWorkoutGenerator` a partir de `slot.role`, da mesma forma que `rir` já é hoje.
- Falta decidir se vale a pena já sair com `SlotRole`→`Papel` unificado (um enum só, movido pra
  `core`/`contract`, os dois motores apontando pra ele) ou se é cedo pra essa unificação e cada
  motor mantém o seu, convertendo na borda do `record()`.

## Decisões tomadas com o Rafael (2026-11-05, antes de codar)

- **Unificar `Papel`/`SlotRole` num enum só**, movido pra `shared-contract` (onde `WorkoutExerciseDto`
  já mora) — os dois motores (v3 e produção) passam a apontar pro mesmo tipo. Nome: `Papel`
  (não `SlotRole`) — é o nome que `Progressao.decidir()`/`calibrar()` já consomem, e não tocar a
  assinatura deles evita custo de regerar o oráculo. Mapeamento ao apagar `SlotRole`:
  `COMPOSTO_PESADO → COMPOSTO`, `COMPOSTO_ACESSORIO → ACESSORIO`, `ISOLAMENTO → ISOLAMENTO`.
  Checado antes de propor: os três papéis da produção (`VolumeModel.kt`: COMPOSTO_PESADO
  5-8/RIR2, COMPOSTO_ACESSORIO 8-12/RIR1, ISOLAMENTO 12-15/RIR1) são a MESMA classificação
  estrutural das faixas do v3 (COMPOSTO 6-9/RIR2, ACESSORIO 8-12/RIR1, ISOLAMENTO 12-16/RIR1) —
  pequena diferença de calibração numérica entre os dois motores, mas o mesmo papel. Isso não
  precisa igualar: `decidir()`/`calibrar()` usam a FAIXA PRÓPRIA do v3 (`PrescricaoV3`) pra
  avaliar abaixo/no-alvo/topo, não a faixa que a produção prescreveu — o `Papel` é só o rótulo
  que escolhe qual faixa consultar. A produção continua livre pra calibrar os PRÓPRIOS números
  de reps/descanso/RIR por `Papel`, sem acoplar ao v3.
- **Bi-set/vaga duplicada: só a 1ª ocorrência do exercício na sessão conta** pra `registrar()`.
  Sem mudança de schema (PK de `progression_event` continua `usuário, exercício, sessão`). As
  ocorrências seguintes do mesmo exercício na mesma sessão são ignoradas pela progressão (ainda
  contam normalmente pra tudo o resto — XP, histórico de execução, etc.).
- **RIR por série**, não só a 1ª — mesmo padrão de `repsDone`/`weight` na tela de execução.
  `decidir()`/`calibrar()` usam RIR de todas as séries; a Regra 4 (`reducaoIntrasessao`) continua
  usando só a 1ª, sem mudança nela.

## Desenho final (Backend → Contrato → Cliente)

### Servidor
1. `Papel` nasce em `shared-contract` (novo arquivo, `@Serializable`), com o KDoc explicando a
   origem dupla (v3 + produção) e por que os valores não mudam (contrato com o oráculo).
2. `v3/SessionComposition.kt`: `Papel` deixa de ser `enum class` local, passa a importar do
   contrato. Nenhum uso interno muda de nome.
3. `VolumeModel.kt`/`StructureEngine.kt`/`SlotFiller.kt`/`DeterministicWorkoutGenerator.kt`:
   `SlotRole` apagado, trocado por `Papel` (import do contrato) nos três pontos acima.
4. `WorkoutExerciseDto` ganha `val papel: Papel? = null` — nullable (treino manual, sem
   prescrição automática, não tem papel). `DeterministicWorkoutGenerator` passa a mandar
   `papel = slot.role` (já renomeado) junto do `rir` que já manda hoje.
5. Persistência do treino (tabela de `workout_exercises` ou equivalente — **checar o schema
   antes de assumir coluna nova**, mesmo padrão de toda migration aditiva do projeto): nova
   coluna pro `papel`, nullable.
6. `SessionService.record()`, depois de `repository.save(session)` (mesmo `also` de
   `avaliarConquistas`): por exercício ÚNICO da sessão (1ª ocorrência só, decisão acima), ler
   `papel` do `WorkoutExercise` do treino de origem + `incremento`/`barraLivre`/`cargaInvertida`
   do catálogo (`ExerciseRepository`, já existem desde a V74) + `Estado` anterior
   (`ProgressaoRepository.buscar`), chamar `calibrar()` (1ª exposição) ou `decidir()` (demais),
   `ProgressaoRepository.registrar(...)`.
7. `SessionRepositoryImpl.save()`: `it[rir] = setLog.rir` no insert de `SessionSetLogsTable`
   (coluna já existe desde V69, nunca escrita).

### Contrato
- `SetLogDto` ganha `val rir: Int? = null`.
- `WorkoutExerciseDto` ganha `val papel: Papel? = null` (acima).

### Cliente
- `SetEntry` ganha `val rir: String = ""`; `SessionEvent.RirChanged(index, value)`; `finish()`
  manda `rir = e.rir.toIntOrNull()`.
- UI de execução: campo de RIR por série (mesma forma de `repsDone`/`weight`).
- Checar se há tabela/cache local de log de sessão antes de assumir que basta mandar no DTO —
  **não inspecionado ainda, primeiro passo ao abrir o cliente**.

## Porte da fatia — fora do tamanho de uma sessão só

Esta investigação (sem codar nada) já cobriu: unificação de enum tocando DOIS motores
(v3 + produção), DTO novo em dois lugares, persistência nova em pelo menos duas tabelas, o fio
`record()` → `decidir()`/`calibrar()`/`registrar()`, e a UI de captura de RIR no cliente. É
Backend → Contrato → Cliente com migration em cada ponta — maior que qualquer fatia fechada
nesta sessão (catálogo V74 foi só servidor; login-sem-backend foi só cliente). Recomendação:
abrir em sessão nova, com este plano pronto, começando pela etapa 1–4 do servidor (unificação do
enum + `WorkoutExerciseDto.papel`) como primeiro commit/teste verde, antes de tocar
`SessionService.record()`.
