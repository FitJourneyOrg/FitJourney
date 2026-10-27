# Motor de geração — como funciona (walkthrough de código)

> **Para quem está lendo o código do motor agora.** Traçado em 2026-10-11 lendo os fontes, não a
> documentação. Onde este arquivo e um PDF discordarem, vale o código — e corrija o PDF.
>
> **O que este arquivo é:** o passo a passo do pipeline, as fórmulas com os números reais, as
> constantes, o que cada campo do catálogo faz (e o que não faz), e as armadilhas de quem vai mexer.
>
> **O que ele NÃO é:** não é a regra de comportamento (essa está em `docs/regras/01-motor-de-geracao.md`,
> com os `[REGRA]`/`[INV]` e os gates da rota) nem a decisão arquitetural (ARCH #20, #22, #23, #24,
> #26, #27, #28, #29 em `docs/arch/`). Aqui não se decide nada: só se explica o que está escrito.
>
> **Não está registrado em nenhum gerador** — `gen_regras.py` tem lista explícita de arquivos, então
> este .md não entra em PDF nenhum até alguém registrá-lo.

---

## 1. Mapa de arquivos

Tudo em `server/src/main/kotlin/dev/rafael/server/features/exercise/`:

| arquivo | linhas | papel |
|---|---|---|
| `engine/WorkoutGenerator.kt` | 13 | a interface. Uma função: `generate(profile, prompt)` |
| `engine/DeterministicWorkoutGenerator.kt` | 106 | o orquestrador. Chama os três abaixo em ordem |
| `engine/StructureEngine.kt` | 223 | **passo 1–4**: volume → split → séries → papéis. Produz o esqueleto |
| `engine/VolumeModel.kt` | 105 | as tabelas: `TargetMuscle`, `SlotRole`, `AgeBand`, `VolumeTable`, `RoleParams` |
| `engine/WorkoutStructure.kt` | 29 | os data classes do esqueleto: `Slot`, `DaySkeleton`, `ProgramSkeleton` |
| `engine/ExercisePreFilter.kt` | 58 | **passo 5a**: a query que reduz o catálogo ao pool viável |
| `engine/EquipmentEnvironmentMap.kt` | 37 | equipamento → ambiente. Regra em código, sem migration |
| `engine/SlotFiller.kt` | 107 | **passo 5b**: escolhe UM exercício por slot |
| `engine/WeekSpread.kt` | 48 | distribui os treinos nos dias da semana (fora do pipeline de escolha) |
| `models/Exercise.kt` | — | o exercício como o motor vê |
| `models/Taxonomy.kt` | — | `Modality`, `MovementPattern`, `PrescriptionType`. **Vive só no server** |
| `db/ExerciseRepositoryImpl.kt` | — | `toExercise()`: linha do banco → `Exercise` |
| `services/ExerciseService.kt` | — | `alternatives()`: a substituição manual, fora da geração |

Fora do server, em `shared-contract` (`src/commonMain/kotlin/dev/rafael/contract/`):
`profile/ProfileDto.kt`, `profile/Level.kt`, `profile/Goal.kt`, `profile/MuscleGroup.kt`,
`profile/TrainingEnvironment.kt`, `profile/BodyLimitation.kt`, `profile/SplitType.kt`,
`profile/SplitCatalog.kt`, `exercise/ExerciseCategory.kt`, `workout/WorkoutExerciseDto.kt`.

Testes: `server/src/test/.../engine/` — `StructureEngineTest`, `SlotFillerTest`, `RoleParamsTest`,
`SplitSelectionTest`, `EquipmentEnvironmentMapTest`, `WeekSpreadTest`.

---

## 2. Os tipos que você precisa ter na cabeça

**Duas taxonomias de músculo, de propósito.** Não as confunda:

- `MuscleGroup` (shared-contract, 9 valores): CHEST, BACK, BICEPS, TRICEPS, FOREARMS, SHOULDERS,
  LEGS, GLUTES, CORE. É o vocabulário do **quiz** e do campo `primary_muscles` do catálogo.
  **Não tem CALVES, TRAPEZIUS nem LOWER_BACK** — isso tem consequência, ver §10.
- `ExerciseCategory` (shared-contract, 17 valores): CORE, FUNCTIONAL_HIT, MOBILITY, CALISTHENICS,
  LEGS, SHOULDERS, CHEST, BACK, CROSSFIT, BICEPS, TRICEPS, TRAPEZIUS, GLUTES, CALVES, FOREARMS,
  CARDIO, LOWER_BACK. É a **categoria do catálogo**. (O KDoc dela diz "As 16 categorias" e são 17.)
- `TargetMuscle` (server, 11 valores): CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS, QUADS,
  POSTERIOR, CALVES, GLUTES, CORE. É o **alvo do slot**, perna e braço desmembrados. Só existe
  dentro do motor.

**`Modality`** = STRENGTH, MOBILITY, CARDIO, PLYOMETRIC, SKILL. Só STRENGTH entra no pool.

**`MovementPattern`** = NONE, SQUAT, HINGE, LUNGE, HORIZONTAL_PUSH, VERTICAL_PUSH, HORIZONTAL_PULL,
VERTICAL_PULL, ROTATION, CARRY, GAIT, STRIKE, STRETCH, KNEE_FLEXION, KNEE_EXTENSION.

**`SlotRole`** = COMPOSTO_PESADO, COMPOSTO_ACESSORIO, ISOLAMENTO. Define reps/descanso/RIR.

**`Slot`** = (target, role, sets, repRange, restSeconds, rir). **`DaySkeleton`** = (label, slots).
**`ProgramSkeleton`** = (days, split).

---

## 3. O que chega ao motor

Os gates da rota `/programs/generate` rodam **antes** e barram com Forbidden (detalhe em
`docs/regras/01-motor-de-geracao.md`): saúde (PAR-Q) → idade → teto por plano → blur/posse.

Do `ProfileDto`, o motor lê **oito** campos:

| campo | onde pesa |
|---|---|
| `goal` | `VolumeTable.weeklySets` (GENERAL_HEALTH usa o piso) e `RoleParams` (modo conservador) |
| `level` | volume semanal · RIR conservador · **filtro do pool** · **bônus +2.0 no score** · gate do foco |
| `daysPerWeek` | nº de dias (coagido a 2..6) e, via `SplitCatalog`, o split |
| `splitPreference` | o split, se for válido pro nº de dias; senão cai no recomendado |
| `focusAreas` | +3 séries/semana no alvo e **+4.0 no score** |
| `age` | fator de volume e RIR conservador em 60+ |
| `environment` | filtro de equipamento. **Obrigatório** — `requireNotNull`, a rota traduz em 400 |
| `limitations` | remove do pool quem declara a contraindicação |

O `prompt` da assinatura é **ignorado** — motor determinístico não lê texto livre (ARCH #20).

O usuário **não** escolhe exercício, padrão de movimento nem carga.

**Foco tem gate próprio** (`DeterministicWorkoutGenerator.effectiveFocus`): vale só para
INTERMEDIATE/ADVANCED e nunca para `GENERAL_HEALTH`; no máximo 2 grupos (`focusAreas.take(2)`).
Fora disso, `emptySet()`. O foco usado é gravado no `ProgramDto` como snapshot — ninguém relê o
perfil depois.

---

## 4. Passo 1 — alvo semanal por músculo (`StructureEngine`, linhas 46–52)

```
alvo[m] = min( round(VolumeTable.weeklySets(m, level, goal) * ageVolumeFactor(age)) + bônusFoco,
               VolumeTable.mrv(m) )
```

`bônusFoco` = `FOCUS_BONUS` = **3**, se `m` está nos alvos do foco. O bônus é somado **depois** do
fator de idade, de propósito: a faixa etária reduz a carga sistêmica sem apagar a prioridade.

`ageVolumeFactor`: `<40` → 1.0 · `40–59` → 0.9 · `60+` → 0.75 · **idade nula → 1.0** (não penaliza).

`VolumeTable.targetsForFocus` traduz o grupo grosso do quiz para os alvos finos — e
**`LEGS` do quiz vira QUADS + POSTERIOR + CALVES** (não GLUTES).

**Tabela de volume** (séries efetivas/semana; `GENERAL_HEALTH` usa a coluna iniciante seja qual for o nível):

| alvo | iniciante | intermediário | avançado | MRV |
|---|---|---|---|---|
| CHEST | 8 | 14 | 18 | 22 |
| BACK | 10 | 16 | 20 | 25 |
| SHOULDERS | 6 | 12 | 16 | 22 |
| BICEPS | 5 | 9 | 12 | 16 |
| TRICEPS | 6 | 10 | 14 | 18 |
| FOREARMS | 3 | 6 | 9 | 12 |
| QUADS | 6 | 12 | 16 | 20 |
| POSTERIOR | 6 | 10 | 14 | 18 |
| CALVES | 5 | 9 | 12 | 16 |
| GLUTES | 4 | 8 | 12 | 16 |
| CORE | 5 | 8 | 10 | 16 |

Os números de BICEPS/TRICEPS/FOREARMS estão marcados **[PROPOSTA]** no código (Fatia B) — tríceps
acima de bíceps porque é músculo maior.

---

## 5. Passo 2 — split e frequência (linhas 54–62)

`SplitCatalog.resolve(days, splitPreference)`: usa a escolha do usuário **se ela for válida para o
número de dias**; senão, o recomendado. Isso blinda o motor contra combinação impossível (Arnold
em 3 dias). Catálogo ratificado (★ = recomendado):

| dias | opções |
|---|---|
| 2 | Full Body ★ |
| 3 | Full Body ★ · Upper/Lower/Full · PPL |
| 4 | Upper/Lower ★ · Full Body |
| 5 | Upper/Lower + PPL ★ |
| 6 | PPL ×2 ★ · Arnold |

Bro-split não entra: fere a frequência (~2×/semana, Schoenfeld).

`splitTemplate` expande o split em `List<Pair<rótulo, Set<TargetMuscle>>>`. Conjuntos:

- **UPPER** = CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, FOREARMS (6)
- **LOWER** = QUADS, POSTERIOR, CALVES, GLUTES, CORE (5)
- **PUSH** = CHEST, SHOULDERS, TRICEPS · **PULL** = BACK, BICEPS, FOREARMS
- **LEGS** = QUADS, POSTERIOR, CALVES, GLUTES
- **CHEST_BACK** (Arnold d1) · **SHOULDERS_ARMS** (Arnold d3) · **FULL** = todos os 11

`cycle()` repete o ciclo base até `days` e sufixa " A"/" B" quando há mais de uma rodada
(4 dias Upper/Lower → `Upper A`, `Lower A`, `Upper B`, `Lower B`).

**Frequência** = em quantos dias do template o músculo aparece, com piso 1.

---

## 6. Passo 3 — séries e nº de exercícios por sessão (linhas 64–90)

As constantes, todas privadas no `StructureEngine`:

| constante | valor | o que faz |
|---|---|---|
| `SESSION_MIN_SETS` | 3 | piso de séries por exercício |
| `MAX_SETS_PER_EXERCISE` | 5 | teto de séries por exercício |
| `SESSION_MAX_SETS` | 8 | teto por músculo por sessão (antes de junk volume) |
| `SESSION_MAX_EXERCISES` | 6 | teto de exercícios por sessão (usabilidade) |
| `SESSION_MAX_SETS_TOTAL` | 20 | teto de séries totais por sessão |
| `SETS_PER_EXERCISE` | 3 | divisor da contagem "natural" de exercícios |

Na ordem:

1. **Músculos do dia** (`trainedMuscles`). Se `muscles.size <= 6`, todos entram, em `canonicalOrder`
   (grandes → pequenos: CHEST, BACK, SHOULDERS, QUADS, POSTERIOR, GLUTES, BICEPS, TRICEPS,
   FOREARMS, CALVES, CORE). Se não cabem (Full Body = 11), mantém os **grandes** (CHEST, BACK,
   QUADS, POSTERIOR, SHOULDERS) e **rotaciona os pequenos** por `dayIndex` — cada dia cobre
   pequenos diferentes, o resto vem indireto. Músculo pequeno **de foco entra sempre**, antes da
   rotação (correção do defeito #1 do ARCH #26: senão o bônus de foco nunca virava exercício).
2. **Séries da sessão** = `round(alvo / frequência)`, preso em **3..8**.
3. **Nº de exercícios** (`allocateExercises`): "natural" = `round(sériesSessão / 3)`, mínimo 1.
   Começa com **1 por músculo** e distribui a sobra até o teto de 6, sempre para quem tem a maior
   razão `sériesSessão / jáAlocado`, e nunca acima do "natural".
4. **Séries por exercício** = `round(sériesSessão / nºExercícios)`, preso em **3..5**.
5. **Teto de volume total**: enquanto a soma `Σ exercícios × séries > 20`, tira 1 série de quem
   tem mais séries (empate → o primeiro na ordem canônica), com piso 3.

> ⚠️ **Os passos 3 e 5 brigam.** Ver §10, defeitos 1 a 3 — não mexa nesse bloco sem ler.

---

## 7. Passo 4 — papel e prescrição (linhas 92–101, 160–165)

`roleFor(target, index)`:

- **CALVES, CORE, BICEPS, TRICEPS, FOREARMS → sempre `ISOLAMENTO`.** O comentário do código diz o
  porquê: não há composto-âncora confiável para esses no modelo.
- Os outros: índice `0` → `COMPOSTO_PESADO`, `1` → `COMPOSTO_ACESSORIO`, `2+` → `ISOLAMENTO`.

`RoleParams.paramsFor(role, level, goal, age)` — `conservative` = `GENERAL_HEALTH` **ou**
`BEGINNER` **ou** `60+`:

| papel | normal | conservador |
|---|---|---|
| COMPOSTO_PESADO | 5-8 · 150s · RIR 2 | 6-10 · 150s · RIR 3 |
| COMPOSTO_ACESSORIO | 8-12 · 105s · RIR 1 | 8-12 · 105s · RIR 3 |
| ISOLAMENTO | 12-15 · 75s · RIR 1 | 10-15 · 75s · RIR 3 |

Os slots do dia são ordenados por `role.ordinal` — **compostos pesados primeiro**. É `sortedBy`,
que é estável, então dentro do mesmo papel vale a ordem canônica de músculo.

---

## 8. Passo 5a — o pool (`ExercisePreFilter.poolFor`)

Uma query, em `transaction { }` próprio (o motor em si não abre transação):

```
WHERE modality IS NOT NULL AND modality = 'STRENGTH'
  AND level IN (<levelsUpTo(level)>)
  AND (equipment IN (<HOME_EQUIPMENT>))      -- só quando o ambiente é CASA
```

e depois, **em código**: `.filter { ex -> blocked.none { b -> ex.contraindications.any { it.name == b } } }`.

- `levelsUpTo`: BEGINNER → {BEGINNER} · INTERMEDIATE → {BEGINNER, INTERMEDIATE} · ADVANCED → os três.
  É **cumulativo**: o avançado vê tudo.
- `EquipmentEnvironmentMap.equipmentsFor`: **CASA** → `{DUMBBELL, BODYWEIGHT, BAND}` ·
  **ACADEMIA** → `null`, ou seja, sem filtro de equipamento. Equipamento desconhecido só roda na
  academia (conservador). Essa regra é **código, não banco**, de propósito: "onde esse equipamento
  roda" é julgamento nosso e muda sem migration.
- O filtro de limitação roda em memória, não em SQL — débito anotado no próprio arquivo
  ("mover p/ `&&` ao escalar").

`toExercise()` (em `ExerciseRepositoryImpl`) converte a linha. **Todos os enums são lidos com
`runCatching { ... }.getOrNull()` e as listas com `mapNotNull`** — valor inválido no banco é
descartado **em silêncio**, sem log e sem erro. É a origem do defeito 4 da §10.

---

## 9. Passo 5b — a escolha (`SlotFiller.fillDay`)

Para cada slot, em ordem:

**(a) Candidatos por alvo** — `matchesTarget`, a tabela completa:

| alvo | casa por |
|---|---|
| CHEST / BACK / SHOULDERS | `MuscleGroup.X in primaryMuscles` |
| BICEPS / TRICEPS / FOREARMS | `category == ExerciseCategory.X` (braço desmembrado, Fatia B) |
| QUADS | `movementPattern ∈ {SQUAT, LUNGE, KNEE_EXTENSION}` |
| POSTERIOR | `movementPattern ∈ {HINGE, KNEE_FLEXION}` **E** `LEGS in primaryMuscles` |
| CALVES | `category == CALVES` |
| GLUTES | `GLUTES in primaryMuscles` |
| CORE | `CORE in primaryMuscles` **ou** `category == CORE` |

Três observações que explicam muita coisa:
- **QUADS e POSTERIOR são os únicos que casam por padrão de movimento**, porque é o dado confiável
  na perna. Exercício com `movementPattern = null` **nunca** casa esses dois.
- O `E LEGS in primaryMuscles` do POSTERIOR existe para **excluir o hip thrust** (primário GLUTES).
- GLUTES casa por `primaryMuscles`, **não por category** — o fallback HINGE foi removido porque
  roubava o hip thrust do posterior.

**(b) Fora o que já foi usado**: `it.id !in alreadyUsed`. O `alreadyUsed` é **por programa inteiro**,
não por dia — nenhum exercício se repete na semana.

**(c) Papel**: `COMPOSTO_*` exige `isCompound == true`; `ISOLAMENTO` exige `isCompound == false`.
Comparação com `Boolean?`, então **`isCompound = null` não entra em slot nenhum**.

**(d) Base primeiro** (ARCH #28): `pooled.filter { it.isBase }.ifEmpty { pooled }`. Se existe
qualquer base, as variações **não são consideradas** — só quando as bases acabam no programa.

**(e) Pontuação** (`score` + penalidade + jitter):

```
score = 4.0  se algum primaryMuscles está no foco
      + 2.0  se ex.level == userLevel
      + 2.0  se o slot não é ISOLAMENTO e isCompound == true
      + equipmentWeight(equipment)
      - 2.5  se ex.movementPattern já foi usado NESTE dia
      + Random.nextDouble() * 0.35
```

`equipmentWeight`: **BARBELL / MACHINE / CABLE → 1.0** · DUMBBELL / KETTLEBELL → 0.8 ·
BAND / GYMSTICK → 0.4 · BODYWEIGHT / IMPROVISED → 0.2 · desconhecido → 0.5.

Constantes: `ROTATION_JITTER = 0.35`, `PATTERN_REPEAT_PENALTY = -2.5`.

Vence o maior. Escolhido entra em `alreadyUsed` e seu padrão em `usedPatterns` (do dia).

**(f) Slot sem candidato**: `?: continue` — o slot é **silenciosamente descartado**. O dia volta com
menos exercícios e ninguém é avisado.

> Aritmética que vale memorizar: o spread máximo de `equipmentWeight` é **0,8** e o jitter vai só
> até **0,35**. Então uma diferença de equipamento **nunca** é desfeita pelo sorteio: entre dois
> candidatos iguais no resto, BARBELL (1.0) vence BODYWEIGHT (0.2) sempre. Já o bônus de nível
> (2.0) atropela qualquer diferença de equipamento — **na prática o nível é o critério primário**.

---

## 10. O que o motor lê do catálogo — e o que ignora

| campo | o motor usa? |
|---|---|
| `modality` | **sim** — filtro do pool (só STRENGTH) |
| `level` | **sim** — filtro do pool + bônus no score |
| `equipment` | **sim** — filtro de ambiente + peso no score |
| `primaryMuscles` | **sim** — casa CHEST/BACK/SHOULDERS/GLUTES/CORE e dá o bônus de foco |
| `movementPattern` | **sim** — casa QUADS/POSTERIOR e alimenta a penalidade de repetição |
| `isCompound` | **sim** — filtro de papel |
| `category` | **sim** — casa BICEPS/TRICEPS/FOREARMS/CALVES/CORE |
| `isBase` | **sim** — base antes de variação |
| `contraindications` | **sim** — filtro de limitação |
| `secondaryMuscles` | **não** — nenhuma leitura no pacote `engine/` |
| `secondaryPattern` | **não** — zero ocorrências fora do data class |
| `unilateral` | **não** — só vai ao DTO pelo `ExerciseMapper` |
| `prescriptionType` | **não** — só vai ao DTO |
| `description`, `videoRef`, `thumbRef` | **não** — vitrine |

Ou seja: **mexer em `unilateral`, `secondaryPattern` ou `prescriptionType` não muda treino gerado.**

---

## 11. A saída

`toExerciseDto` monta `WorkoutExerciseDto(exerciseId, orderIndex, restSeconds, rir, sets)`, com
`reps = midReps(slot.repRange)`.

**Não existe campo de carga.** O motor prescreve séries, reps, descanso e RIR — a carga é do usuário
e do log de progressão (fatia J). Isso é central: o motor não tem como regular dificuldade por peso,
só por exigência de execução e prescrição.

`ProgramDto` sai com `name = skeleton.split.name` (placeholder — o `ProgramService` descarta e
persiste `SEM_NOME`; quem monta o rótulo é a tela, no idioma dela, ARCH #37), `focusMuscles`
(snapshot) e `locked = false` (posse/blur é decidido na rota, ARCH #23).

---

## 12. Determinismo — o que é e o que não é

- **A estrutura é determinística pelo perfil.** Mesmo perfil → mesmo split, mesmos músculos por dia,
  mesmas séries, reps, descanso e RIR. Nada de aleatório no `StructureEngine`.
- **A seleção não é.** `DeterministicWorkoutGenerator` faz `val seed = kotlin.random.Random.nextLong()`
  por geração: **"gerar de novo" com o mesmo perfil dá exercícios diferentes.** Isso revisa o
  ARCH #20 e está comentado na linha 41.
- O `SlotFiller` **é** determinístico dada a seed. O KDoc dele diz "mesma seed + perfil = mesmo
  treino", o que é verdade dele e **não** do gerador. **DOC a corrigir.**
- Zero LLM em todo o caminho.

---

## 13. Substituição manual (`ExerciseService.alternatives`) — fora da geração

Mesmo pool (`poolFor`), e alternativa é quem satisfaz **as quatro**:

```
c.id != exerciseId
c.movementPattern == target.movementPattern
c.isCompound       == target.isCompound
target.primaryMuscles.any { it in c.primaryMuscles }
```

Note que isso é **mais estrito** que o `matchesTarget` da geração: exige padrão idêntico. E se
`target.primaryMuscles` estiver vazio, a quarta condição é sempre falsa → **zero alternativas**.

---

## 14. Exemplo traçado — intermediário · ganhar massa · 4 dias · foco Costas · 28 anos · academia

Pool do exemplo = as 79 da lista principal (pós-V64). Scores sem o jitter; "sorteio entre N" = os
que estão dentro dos 0,35 do topo.

**Passo 1:** CHEST 14 · **BACK 19** (16+3) · SHOULDERS 12 · BICEPS 9 · TRICEPS 10 · FOREARMS 6 ·
QUADS 12 · POSTERIOR 10 · CALVES 9 · GLUTES 8 · CORE 8.
**Passo 2:** Upper/Lower → `Upper A`, `Lower A`, `Upper B`, `Lower B`. Frequência 2× em tudo.

**Upper A** — séries/sessão: CHEST 7 · BACK 8 · SHOULDERS 6 · BICEPS 5 · TRICEPS 5 · FOREARMS 3.
Exercícios: **1 para cada** (6 músculos = o teto; o `while` do `allocateExercises` não executa).
O teto de 20 séries corta CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, CHEST, BACK, SHOULDERS →
**CHEST 1×3 · BACK 1×3 · SHOULDERS 1×3 · BICEPS 1×4 · TRICEPS 1×4 · FOREARMS 1×3** (= 20).

| slot | papel | prescrição | cand. | topo | sorteio |
|---|---|---|---|---|---|
| CHEST | COMPOSTO_PESADO | 3×5-8 · 150s · RIR2 | 9 | 5.0 | 6 |
| BACK | COMPOSTO_PESADO | 3×5-8 · 150s · RIR2 | 15 | **9.0** | 7 |
| SHOULDERS | COMPOSTO_PESADO | 3×5-8 · 150s · RIR2 | 4 | 5.0 | 2 |
| BICEPS | ISOLAMENTO | 4×12-15 · 75s · RIR1 | 12 | 2.8 | 2 |
| TRICEPS | ISOLAMENTO | 4×12-15 · 75s · RIR1 | 9 | 0.5 | 2 |
| FOREARMS | ISOLAMENTO | 3×12-15 · 75s · RIR1 | 2 | **−1.5** | 2 |

O 9.0 das costas = 4 (foco) + 2 (nível) + 2 (papel) + 1 (equipamento). O −1.5 do antebraço =
0 + 0 + 0 + 1.0 − 2.5 (padrão NONE já usado no dia). **Score negativo é inofensivo**: `maxByOrNull`
é relativo.

**Lower A** — QUADS 2×3 · POSTERIOR 1×3 · GLUTES 1×3 · CALVES 1×4 · CORE 1×4. Slots: QUADS pesado
(4 cand.) · POSTERIOR pesado (3) · GLUTES pesado (4) · QUADS acessório (2) · CALVES (2) ·
**CORE sem candidato → descartado**.

**Upper B** herda o que sobrou: SHOULDERS cai a 1 candidato, FOREARMS a 1.
**Lower B** é o pior: QUADS pesado com **1** candidato, **QUADS acessório sem nenhum**, CORE sem
nenhum → **4 exercícios em vez de 6**.

**Volume entregue × alvo na semana:** BACK 6/19 (32%) · CHEST 6/14 (43%) · SHOULDERS 6/12 (50%) ·
POSTERIOR 6/10 (60%) · GLUTES 6/8 · QUADS 9/12 · TRICEPS 8/10 · CALVES 8/9 · BICEPS 8/9 (89%) ·
FOREARMS 6/6 (100%) · CORE 0/8.

---

## 15. Defeitos conhecidos — leia antes de mexer

1. **[INV violado] O bônus de foco é somado no passo 1 e destruído no passo 3.** O corte do teto de
   20 séries percorre a ordem canônica, que começa nos músculos grandes, então CHEST/BACK/SHOULDERS
   levam 2 cortes cada e os pequenos 1. No exemplo acima, **costas é o foco e entrega 6 séries/semana
   contra alvo de 19, enquanto o bíceps entrega 8 contra alvo de 9**. O músculo priorizado pelo
   usuário recebe menos volume que o bíceps.
2. **O volume entregue fica muito abaixo do alvo e com a hierarquia invertida** (§14): músculo
   pequeno bate o alvo, grande não chega perto. É consequência direta do teto de 20 combinado com o
   teto de 6 exercícios.
3. **Em Upper/Lower o `allocateExercises` é inerte.** UPPER tem exatamente 6 músculos =
   `SESSION_MAX_EXERCISES`, então `alloc` fica 1 para cada e o laço nunca roda — o "natural"
   (CHEST 2, BACK 3, SHOULDERS 2) é calculado e descartado. Consequência: **em dia Upper o papel
   `COMPOSTO_ACESSORIO` nunca existe**; todo peito/costas/ombro é composto pesado a 5-8 reps.
4. **Enum inválido no banco desaparece em silêncio.** `primary_muscles = {TRAPEZIUS}` não existe em
   `MuscleGroup` → `mapNotNull` devolve lista vazia. O exercício fica sem primário, não casa
   CHEST/BACK/SHOULDERS/GLUTES/CORE, e se a `category` dele também não for alvo de nenhum
   `TargetMuscle` (TRAPEZIUS não é), ele **entra no pool e nunca é escolhido** — e `alternatives`
   devolve zero para ele. É o débito "`MuscleGroup` sem CALVES/TRAPEZIUS/LOWER_BACK" do ESTADO.md
   cobrando. Vale o mesmo para `LOWER_BACK`.
5. **A penalidade de padrão repetido distorce o isolamento.** `NONE` é tratado como padrão real:
   depois do primeiro isolamento, todos os outros `NONE` levam −2,5 igualmente — inofensivo, porque
   é uniforme. **Mas um isolamento que TEM padrão real é punido contra os `NONE`.** Num dia com
   isolamento de peito depois do supino (HORIZONTAL_PUSH), Crucifixo, Crossover e Pec Deck levam
   −2,5 e o **Pullover ganha só por ser VERTICAL_PULL**.
6. **`alreadyUsed` é por programa.** Alvo com poucos compostos não aguenta 2 dias de treino: o
   segundo dia encontra o pool esgotado e perde slots (§14, Lower B).
7. **`equipmentWeight` não expressa "livre vs guiado" — e o inverte.** BARBELL = MACHINE = CABLE = 1.0
   enquanto DUMBBELL = 0.8, isto é, **máquina vence halter**. O que o peso mede é "quanta carga
   externa cabe". Há `[PROPOSTA]` aberta de trocar isso por exigência técnica, em
   `docs/trabalho/motor-lista-principal.md`.
8. **Slot vazio é descartado em silêncio** (`?: continue`). Hoje o usuário recebe um dia mais curto
   sem saber. A fatia da lista principal quer trocar isso por falha explícita da geração.

---

## 16. Como rodar os testes

`gradlew` não roda no ambiente do Claude — ele pede, você roda e cola. As **cinco** tasks:

```
:konsist:test                  (use --rerun-tasks: fica up-to-date sem varrer)
:server:test
:server:integrationTest        (Postgres real, exige Docker)
:<módulo>:testAndroidHostTest
:app:androidApp:testDebugUnitTest
```

Mexeu no motor → `:server:test` cobre `StructureEngineTest`, `SlotFillerTest`, `RoleParamsTest`,
`SplitSelectionTest`, `EquipmentEnvironmentMapTest`, `WeekSpreadTest`.

Sem biblioteca de mock de objeto: fakes à mão em `commonTest`, `kotlin.test`, nomes em crases. Não é
estilo — os targets iOS estão declarados e MockK é JVM-only. Mock de transporte é permitido.
