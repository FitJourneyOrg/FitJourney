# Lista principal do motor — plano

> Criado 2026-10-09 como "favoritos"; **redefinido em 2026-10-09** para "lista principal" (ver "O que mudou").
> **Fatia nova: sessão própria.**

## O que mudou em relação ao plano de "favoritos"

| | Favoritos (descartado) | Lista principal (vale) |
|---|---|---|
| Papel | prioridade: favorito → base → pool | **filtro duro**: só a lista principal entra na geração |
| Resto do catálogo | continuava no pool da geração | **só para substituição** (troca pelo usuário) |
| Sem candidato no slot | caía em base/pool | **falha a geração** (decisão do Rafael, 2026-10-09) |
| Coluna | `is_favorite` | `is_principal` |
| `is_base` | segundo nível | **sem efeito na geração** (vira débito, ver abaixo) |

## Como retomar (sessão nova)

**Estado em 2026-10-09:** plano e decisões fechados; **nenhum código escrito**. Quem tem a bola é o
Rafael: ele está curando a lista principal (nomes exatos dos exercícios STRENGTH) e vai mandá-la
pronta (CSV com coluna `principal`, xlsx ou lista de nomes). `tools/cache/favoritos.csv` é o export
de partida (o nome do arquivo ficou do plano antigo; a coluna a preencher agora é `principal`).

**Primeiros passos da sessão:**
1. Ler este arquivo e `docs/ESTADO.md`. Ler `SlotFiller.kt` (hoje `?: continue` quando o slot não
   acha candidato: ver pergunta 1) e `ExercisePreFilter.kt`/`ExerciseService.alternatives` (trechos).
2. Conferir a lista: nomes que não existem ou duplicados; **cobertura por padrão × equipamento**
   (< 3 principais = avisar o Rafael; agora é requisito, não aviso).
3. Responder as perguntas abertas abaixo.
4. Dizer o plano em 3 linhas, esperar o ok, só então implementar (V64 → motor → contrato → cliente → testes).

**Comandos do Rafael ao fechar:** `:server:test` · `:server:integrationTest` (Docker) · `:konsist:test` ·
`:app:androidApp:testDebugUnitTest` · `:<módulo>:testAndroidHostTest` (o contrato ganha um erro novo).
Depois da V64 aplicada via `:server:run`: conferir contagem no psql (`SELECT COUNT(*) FROM exercises
WHERE is_principal;`) e rodar `docs/migration_verify/`.

**Dados medidos em 2026-10-09 (banco de dev):** 945 exercícios; 583 sem `modality`; motor vê 270
STRENGTH (BODYWEIGHT 63, DUMBBELL 48, MACHINE 45, BARBELL 39, CABLE 38, BAND 28, LANDMINE 4,
STABILITY_BALL 3, KETTLEBELL 2). Base por padrão (STRENGTH): H_PUSH 26/30, V_PUSH 12/14, H_PULL 20/26,
SQUAT 12/17, LUNGE 10/14, KNEE_FLEX 3/5, HINGE 10/22, V_PULL 10/25, KNEE_EXT 2/6. Reconferir antes de
usar: o banco pode ter mudado.

## Medido em 2026-10-09: lista do Rafael × catálogo × mídia

Lista recebida: 10 grupos de ~71 itens (peito, costas, pernas, ombros, bíceps, tríceps, abdômen).
Cruzada com os 270 STRENGTH (`tools/cache/favoritos.csv`) e com `gifs_exercicios/catalogo.json`
(923 entradas; mídia = `arquivo_video` .mp4 + `arquivo_thumb` .webp, conferida em disco).

- **Mídia dos 270: 269/270 têm vídeo e thumb em disco.** A exceção é a Prancha (V58, sem mídia por
  decisão nomeada). Conferido pelo `catalogo.json` + disco, **não** pelo `video_ref`/`thumb_ref` do banco:
  falta rodar a conferência no banco (V50 reconstruiu esses caminhos).
- **~54 itens da lista têm equivalente nos 270, com mídia** (alguns por aproximação: Remada Baixa
  Triângulo ≈ Remada Sentada no Cabo Pegada Fechada; Pulldown Barra Reta; Rosca na Polia Baixa ≈
  Rosca Direta no Cabo com Barra; Elevação Lateral na Polia Baixa ≈ Unilateral Inclinado no Cabo).
- **6 exercícios clássicos existem no catálogo, com mídia, mas FORA dos 270** (o motor não os vê
  hoje; provável `modality` NULL ou diferente de STRENGTH, confirmar no banco): Leg Press 45°,
  Cadeira Adutora, Elevação Lateral com Halteres, Remada Alta (barra, halteres, cabo), Rosca Spider
  (unilateral), Extensão de Tríceps na Máquina. Também: Crossover na Polia Média, Remada Cavalinho
  (T-Bar) com Barra, Voador no Pec Deck.
  **Consequência: a decisão "escopo só os 270" cai.** A lista principal precisa poder incluir esses,
  o que exige corrigir a `modality` deles (migration de DADO) ou o pré-filtro.
- **Abdômen: o catálogo não tem.** Não há categoria Abdômen em `gifs_exercicios/`. Dos 10 itens da
  lista, nenhum existe (Crunch, Elevação de Pernas, Cable Crunch, Bicicleta, Máquina, Remador, Russian
  Twist, Declinado, Infra). Só há, nos 270: Ativação Abdominal Deitado, Abdominal Grupado com Bola
  Suíça, Rollout na Bola Suíça, V-Up com Bola Suíça (+ Prancha sem mídia).
- **[RESOLVIDO em 2026-10-09, opção (a)] Era um bloqueio para a regra "slot sem principal falha a geração":** o `StructureEngine` tem
  `TargetMuscle.CORE` (volume 5–16 séries/semana) nos splits e o `SlotFiller` casa CORE por
  `category`/músculo. Com o catálogo atual, CORE sem bola suíça não tem candidato: ligar a falha
  rígida derrubaria a geração para quem não tem bola. Opções eram: (a) CORE fora da regra
  de falha, (b) novos exercícios de abdômen com mídia entram no catálogo antes, (c) CORE fica sem
  slot por enquanto. **Rafael escolheu (a).**

## Medido no banco em 2026-10-09 (`tools/cache/principal_midia.csv`, 284 linhas)

- **270 STRENGTH:** `video_ref`/`thumb_ref` preenchidos em 269 (a Prancha, sem mídia, é a exceção da V58).
  **1 caminho quebrado:** `Crucifixo Declinado com Halteres (Visão 2)` aponta para
  `Peitoral/dumbbell_flat_fly.mp4/.webp`, que não existe em disco (nem em `_removidos/`) → serve 404
  hoje. O catálogo tem `dumbbell_decline_fly_view_2`. Corrigir na V64 (ou migration própria) e **o Rafael
  confirmou (2026-10-09) que NÃO é duplicata**: repontar para `dumbbell_decline_fly_view_2`, sem remover nada.
  (A checagem só pelo `catalogo.json` tinha dito 269/270 ok: o banco achou o que o catálogo escondia.)
- **Os 14 de fora dos 270 existem todos, com mídia no banco e em disco**, mas com **`modality` NULL e
  `movement_pattern` NULL** (category, equipment, primary_muscles, is_compound e level preenchidos).
  Sem os dois campos o motor não os enxerga nem os casa em slot de padrão. **A V64 passa a ser também
  curadoria de taxonomia desses 14**, espelhando análogos que já estão nos 270 (**confirmados pelo Rafael em
  2026-10-09: "ok pode entrar"**): Leg Press 45° → SQUAT · Voador no Pec Deck → HORIZONTAL_PUSH · Crossover na Polia
  Média → HORIZONTAL_PUSH · Remada Cavalinho (T-Bar) com Barra → HORIZONTAL_PULL · Elevação Lateral com
  Halteres, Elevação Lateral Unilateral no Cabo, Crucifixo Inverso Apoiado no Banco Inclinado,
  Extensão de Tríceps na Máquina, Rosca Spider (2) → NONE (isolamento) · Cadeira Adutora → NONE, category
  como a Abdutora (hoje GLUTES; a Adutora está LEGS) · **Remada Alta (3: barra, halteres, cabo): decisão do
  Rafael em 2026-10-09 = category BACK (dorso) e `movement_pattern` VERTICAL_PULL** (hoje TRAPEZIUS). Conferir se têm relação com os
  "23 sem taxonomia" do Grupo D.

## Cobertura da lista pré-marcada (2026-10-09, `tools/cache/curadoria-lista-principal.xlsx`)

Planilha com os 284 exercícios (270 + os 14), `principal = S` pré-preenchido nos 79 que casam com a
lista do Rafael, aba Cobertura (principais por slot × equipamento, com fórmulas) e aba Leia-me.
Resultado com a lista como veio (é rascunho; o Rafael edita a coluna `principal`):

- **O ambiente CASA só vê DUMBBELL + BODYWEIGHT + BAND** (`EquipmentEnvironmentMap`). A lista veio com
  cara de academia: em CASA, `SQUAT`, `KNEE_EXTENSION`, `KNEE_FLEXION` e as isolações de panturrilha,
  glúteo e antebraço têm **0** principais; `HINGE`, `HORIZONTAL_PULL` e `LUNGE` têm 1. Com a regra de
  falha rígida, **quem treina em casa ficaria sem programa**. Decidir: ampliar a lista para CASA,
  ou tratar CASA de outra forma. Em academia: SQUAT×DUMBBELL 0, SQUAT×BARBELL 1, KNEE_EXTENSION 1,
  LUNGE×BARBELL 1.
- Slots sem nenhum exercício no motor hoje (não dá para curar, é buraco do catálogo): GAIT, ROTATION
  (só BODYWEIGHT/BAND/KETTLEBELL), ISOL de CALISTHENICS, FUNCTIONAL_HIT, LEGS, LOWER_BACK, MOBILITY.

## Decisões tomadas

- **Lista principal = curadoria editorial do Rafael, global** (não por usuário). Por usuário seria
  outra feature (tabela por uid + sync) e fica de fora.
- **Único pool da geração.** O `SlotFiller` só considera exercícios `is_principal`. O resto do
  STRENGTH existe para a **troca** (`GET /exercises/{id}/alternatives`) e para a biblioteca.
- **Slot sem principal elegível → a geração FALHA** (decisão do Rafael, 2026-10-09; eu recomendei
  cair no catálogo e registrar, ele preferiu o rígido). Consequências aceitas e que viram trabalho:
  erro novo no contrato, tratamento no cliente (mensagem pt/en) e a cobertura da lista virar
  requisito testado (abaixo).
- **Exceção CORE (Rafael, 2026-10-09):** a regra de falha NÃO vale para slots `CORE`. Slot CORE sem
  principal elegível mantém o comportamento de hoje (`isBase` → pool do STRENGTH) e registra via
  `Falhas`, para o buraco aparecer. O catálogo não tem abdômen (ver "Medido"); trazer abdominais com
  GIF e imagem é débito próprio, fora desta fatia. Quando o catálogo tiver abdômen, a exceção pode cair.
- **A lista principal NUNCA fura filtro:** equipamento, nível, contraindicação, idade, saúde,
  `alreadyUsed` e `PATTERN_REPEAT_PENALTY` valem antes. Principal só define quem pode concorrer;
  entre os elegíveis o jitter continua sorteando (variedade depende de ≥ 3 por padrão × equipamento).
- **O filtro mora no `SlotFiller`, NÃO no `ExercisePreFilter`.** `ExerciseService.alternatives` usa
  `preFilter.poolFor`; se o filtro entrasse lá, a troca encolheria para a lista curada, o oposto da
  intenção. Teste obrigatório: `alternatives` continua devolvendo exercício fora da lista.
- **Escopo: os 270 STRENGTH + os 14 acima** (que ganham `modality` e `movement_pattern` na V64;
  confirmado pelo Rafael em 2026-10-09). Os 76 com padrão e `modality` NULL (≈10 parecem força comum)
  ficam para depois, decisão do Rafael.
- `exercises.is_principal BOOLEAN NOT NULL DEFAULT false`.
- **`is_base` fica no banco, sem efeito na geração** e vira débito (remover ou reaproveitar). Não
  apagar nesta fatia.
- **Regra a atualizar ao fechar (autorizado pelo Rafael em 2026-10-09):** [INV] de
  `docs/regras/01-motor-de-geracao.md` ("`is_base` primeiro; variação só se a base acabar") e ARCH #28.

## Perguntas abertas (responder antes de codar)

1. **O que hoje cai no `continue` silencioso do `SlotFiller`?** Hoje slot sem candidato é pulado em
   silêncio. Transformar isso em falha pode quebrar casos que hoje "funcionam" com programa menor
   (ex.: perfis com limitações pesadas). Medir no banco real, com a lista pronta, **quantos
   perfis × ambiente × nível deixam algum slot vazio** antes de ligar a falha.
2. **Matriz de cobertura:** quais combinações de perfil entram no teste de cobertura (ambientes ×
   níveis × limitações comuns)? Proposta: gerar do `StructureEngine` todos os slots possíveis e
   exigir ≥ 1 principal elegível em cada combinação, com o `ExercisePreFilter` real.
3. **Erro novo:** código e HTTP (proposta: 422 com `ErrorCodes.LISTA_PRINCIPAL_INSUFICIENTE`),
   seguindo a convenção dos outros erros de geração. Conferir `ErrorCodes` e o mapeamento no cliente.
4. **O catálogo é semeado por migrations/pipeline de curadoria?** Ver como `is_base` foi escrito
   (V-algo) para seguir o mesmo caminho e não ser sobrescrito pela próxima carga.

## Desenho (ordem: Backend → Contrato → Cliente)

### Servidor
`Exercise.isPrincipal`; `ExercisesTable.isPrincipal`; `SlotFiller.fillDay` passa a devolver falha
quando o slot não tem principal elegível (hoje `?: continue`), **exceto CORE**, que cai no
`isBase` → pool e registra via `Falhas`; o serviço de geração propaga como
`AppResult.Failure` com o erro novo. `ExercisePreFilter` e `alternatives` **não mudam**.

### Contrato
Erro novo de geração (código estável em `shared-contract`). Nenhum campo novo no `ExerciseDto`: o
cliente não precisa saber quem é principal.

### Cliente
Tratar o erro novo na tela de gerar programa (mensagem pt/en, mesmo padrão dos outros erros de
geração). Troca de exercício: nada muda.

### Curadoria (o trabalho do Rafael)
Planilha gerada do banco (`tools/cache/favoritos.csv` → xlsx): uma linha por exercício STRENGTH,
ordenada por padrão e equipamento, coluna `principal`. Eu devolvo um **resumo de cobertura por
padrão × equipamento** junto com a planilha para ele ver os buracos antes de fechar a lista.

## Migration
Duas migrations de dado, um assunto cada: **`V64`** = taxonomia dos 14 (`modality`, `movement_pattern`,
category da Remada Alta) + mídia da Visão 2; **`V65__exercises_is_principal.sql`**: `ADD COLUMN is_principal` + `UPDATE ... SET is_principal = true
WHERE name IN (...)` com contagem esperada conferida. É migration de DADO (não apaga nada): rodar
`docs/migration_verify/` depois, contra o banco real.

## Testes (C2)
- `SlotFillerTest`: só principal concorre; principal fora do pool (outro equipamento) NÃO entra;
  não-principal elegível NÃO entra mesmo sem concorrente; entre principais o sorteio varia por seed;
  **slot sem principal elegível devolve falha** (caminho de falha); **slot CORE sem principal não
  falha**, cai no pool e registra em `FalhasFake`.
- `ExerciseServiceTest`: `alternatives` devolve exercício não-principal (trava o risco do filtro no
  lugar errado).
- Serviço de geração: propaga o erro novo; rota devolve o código/HTTP definidos.
- Integração (Testcontainers, lista real): coluna existe, default false, contagem dos marcados bate,
  **cobertura da matriz** (pergunta 2) sem slot vazio.
- Cliente: ViewModel de geração mapeia o erro novo para a mensagem.
- Comandos: `:server:test`, `:server:integrationTest`, `:konsist:test`, testes do cliente.

## Fora de escopo, por decisão
- Lista por usuário; os 76 sem `modality`; remoção do `is_base`; ordenar principais primeiro nas
  alternativas da troca (ideia possível, não pedida); ajuste de bônus (não há: é filtro).

## Ao fechar — promover antes de apagar
Ver checklist no fim de `MODELO.md`. Regra do motor → `docs/regras/01-motor-de-geracao.md` (trocar o
[INV] do `is_base`); ARCH #28 ganha "lista principal é o único pool"; `fontes/debitos.md` (`is_base`
sem efeito; os 76 sem `modality`); `migrations/INDICE.md` (V64); `ESTADO.md`.

## REV da lista principal (79 linhas) contra o motor — 2026-10-10

Fonte: `tools/cache/lista-principal-detalhada.xlsx` (79 linhas) cruzada com `SlotFiller.kt`,
`ExercisePreFilter.kt`, `StructureEngine.kt`, `EquipmentEnvironmentMap.kt`, `MuscleGroup.kt` e
`tools/cache/principal_midia.csv`. Simulação do `poolFor()` + `matchesTarget()` + papel do slot.
Nada codado. Os números abaixo são medidos na planilha, não estimados.

### 1. Bloqueadores da regra "slot sem principal → falha a geração"

Candidatos por alvo × papel, **pós-V64**, formato `composto/isolamento`:

| | CHEST | BACK | SHOU | QUAD | POST | GLUT | BICE | TRIC | FORE | CALV | CORE |
|---|---|---|---|---|---|---|---|---|---|---|---|
| ACADEMIA · BEGINNER | 2/3 | 5/**0** | 2/3 | 2/1 | **0**/2 | 3/1 | 11 | 7 | 1 | 2 | **0** |
| ACADEMIA · INTERMEDIATE | 9/6 | 14/2 | 4/4 | 4/1 | 3/2 | 6/1 | 13 | 9 | 1 | 2 | **0** |
| CASA · BEGINNER | 2/**0** | **0**/**0** | 1/2 | 1/**0** | **0**/**0** | 1/**0** | 5 | 3 | **0** | **0** | **0** |
| CASA · INTERMEDIATE | 5/2 | 3/2 | 2/3 | 1/**0** | 1/**0** | 1/**0** | 7 | 3 | **0** | **0** | **0** |

- **[INV candidato] CASA não gera.** Para iniciante em casa: zero costas (composto e isolamento),
  zero posterior, zero panturrilha, zero antebraço, zero isolamento de peito/quadríceps/glúteo.
  Causa: as únicas costas caseiras da lista (Barra Fixa #15, Chin-Up #16) são INTERMEDIATE;
  panturrilha (#41, #42) e antebraço (#62) só existem em MACHINE/BARBELL. **A lista tem 0
  exercícios BAND** — os 270 têm 28. CASA = DUMBBELL+BODYWEIGHT+BAND, e a lista só tem 24+5.
- **[INV candidato] POSTERIOR composto = 0 para BEGINNER**, mesmo na academia: Terra (#25), Stiff
  com Barra (#33) e Stiff com Halteres (#34) são todos INTERMEDIATE. Sobram Mesa/Cadeira Flexora
  (isolamento). Split de iniciante com slot composto de posterior falha.
- **BACK isolamento = 0 para BEGINNER.** Vira slot vazio quando o dia tem 3+ slots de costas.
- **Nenhum exercício ADVANCED na lista** (46 BEGINNER + 33 INTERMEDIATE). Pool do avançado é
  idêntico ao do intermediário e o bônus `level == userLevel` (+2.0 no `score`) nunca dispara
  para ele.
- CORE = 0 é esperado (decisão (a), 2026-10-09). Consequência a assumir: o volume CORE de
  5–16 séries/semana do `StructureEngine` simplesmente não é preenchido.

### 2. Linhas que entram na lista e o motor nunca escolhe

- **Cadeira Adutora (#39) — a V64 como está planejada NÃO resolve.** O plano manda `category
  LEGS → GLUTES`, mas `TargetMuscle.GLUTES` casa por `MuscleGroup.GLUTES in ex.primaryMuscles`,
  **não** por category. O primary dela é LEGS e o padrão é NONE → não casa GLUTES, não casa QUADS
  (exige SQUAT/LUNGE/KNEE_EXTENSION) e não casa POSTERIOR (exige HINGE/KNEE_FLEXION). Trocar a
  category é cosmético. **[PROPOSTA]** `primary_muscles → GLUTES`, como a Abdutora (#40), que é
  alcançável exatamente por isso.
- **Face Pull (#49) — dead row.** Está `STRENGTH / HORIZONTAL_PULL / CABLE` no banco (confirmado
  no `principal_midia.csv`), entra no pool e não casa slot nenhum: `primary_muscles = TRAPEZIUS`
  não existe no enum `MuscleGroup`, e `toExercise()` usa
  `mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }` → o valor é **descartado em
  silêncio** e `primaryMuscles` fica vazio. A category TRAPEZIUS também não é alvo de nenhum
  `TargetMuscle`. É o débito "MuscleGroup sem CALVES/TRAPEZIUS/LOWER_BACK" do ESTADO.md cobrando.
  A V64 decidiu `TRAPEZIUS → BACK` só para as Remadas Altas. **[PROPOSTA]** estender ao #49
  (primary → BACK, category → BACK) e alinhar o #48, que hoje se salva só pelo primary BACK e
  fica com category TRAPEZIUS órfã.

### 3. Contradição a resolver ANTES de escrever a V64

- **#34 Stiff com Halteres.** A planilha diz `Modality hoje = (NULL)` e `Padrão hoje no banco =
  (vazio)`, mas a coluna Origem **da mesma linha** diz "STRENGTH (já no motor)" e o
  `principal_midia.csv` (medido no banco em 2026-10-09) diz `Stiff com Halteres,STRENGTH,HINGE,
  DUMBBELL`. Duas fontes contra uma → **o gerador da planilha errou nessa linha**, provável lookup
  furado depois da V63 (`dedup_stiff_terra_romeno`). Daí as três contagens divergentes: 13 linhas
  NULL na planilha × 12 laranja × 14 no plano. Se a V64 sair da planilha, ela escreve numa linha
  que não precisa.
- **12 laranja × 14 do plano: não é erro.** Os 2 que faltam são "Remada Alta com Halteres" e
  "Elevação Lateral Unilateral no Cabo" — e esta última é exercício **diferente** do #51
  "Elevação Lateral Unilateral **Inclinado** no Cabo", que já está STRENGTH/NONE. 14 fora dos 270,
  12 escolhidos.
- **"Remada Alta com Halteres" tem `equipment = BARBELL`** no banco e vídeo
  `Ombros/dumbbell_upright_row.mp4`. Se entrar na lista, nasce invisível em CASA. Corrigir na V64,
  que já vai tocar as 3 Remadas Altas.

### 4. Requisito "< 3 principais por padrão × equipamento" — medido (pós-V64)

| padrão | BARBELL | DUMBBELL | MACHINE | CABLE | BODYWEIGHT | total |
|---|---|---|---|---|---|---|
| HORIZONTAL_PUSH | 4 | 4 | 2 | 2 | 1 | 13 |
| HORIZONTAL_PULL | 2 | 1 | 2 | 4 | – | 9 |
| VERTICAL_PULL | 1 | 1 | – | 3 | 2 | 7 |
| HINGE | 3 | 1 | 1 | – | – | 5 |
| VERTICAL_PUSH | 1 | 2 | 1 | – | 1 | 5 |
| SQUAT | 1 | – | 1 | – | – | **2** |
| LUNGE | 1 | 1 | – | – | – | **2** |
| KNEE_FLEXION | – | – | 2 | – | – | **2** |
| KNEE_EXTENSION | – | – | 1 | – | – | **1** |
| NONE | 7 | 14 | 6 | 5 | 1 | 33 |

Reprovam o limiar: **KNEE_EXTENSION (1), SQUAT (2), LUNGE (2), KNEE_FLEXION (2)** — todos de perna.
HINGE passa no total mas não tem CABLE nem BODYWEIGHT.

### 5. Taxonomia incoerente dentro da lista

- **#62 Rosca Inversa com Barra = FOREARMS; #63 Rosca Inversa com Barra W = BICEPS.** Mesmo
  movimento, alvos diferentes no motor (BICEPS/TRICEPS/FOREARMS casam por **category**). FOREARMS
  tem exatamente 1 candidato na lista (o #62): se a decisão for "é bíceps", FOREARMS vai a 0 e o
  slot falha a geração. **Precisa de decisão de produto.**
- **#71 Supino Pegada Fechada e #75 Mergulho nas Paralelas estão category CHEST.** O motor nunca
  os usa em slot de TRÍCEPS. Não quebra nada (TRICEPS é sempre ISOLAMENTO no `StructureEngine`),
  mas não dá para contá-los como cobertura de tríceps.
- **#25 Levantamento Terra: category BACK, primary GLUTES+LEGS** → não casa `TargetMuscle.BACK`;
  casa POSTERIOR e GLUTES. Coerente com o motor (é hinge), mas na lista ele está no grupo Costas.
  Confirmar que é intencional.
- **#67 Rosca Spider Unilateral em Pé: `unilateral = não`**, enquanto o #66 (Deitado) é `sim`.
  `unilateral`, `secondaryPattern` e `prescriptionType` **não são lidos pelo motor** (só vão ao
  DTO via `ExerciseMapper`), então é defeito de vitrine, não de geração.
- `ExerciseCategory` tem **17** entradas e o KDoc diz "As 16 categorias". DOC a corrigir ao tocar.

### 6. O que está íntegro (não reabrir)

- Mídia 79/79: `.mp4` + `.webp` conferidos em disco pelo `video_ref` do banco, **nenhuma ref
  duplicada**, 79 pastas de cópia distintas. Prancha e Crucifixo Declinado (Visão 2) ficaram fora
  da lista — são justamente os dois de mídia quebrada.
- Chave, ID, nome PT e nome EN: nenhum vazio, nenhum duplicado nas 79.
- Descrição PT e EN em todas as 79 (769–1831 caracteres, nenhuma repetida).
- Cobertura da lista do Rafael: **61 itens distintos em 80 ocorrências / 79 linhas**. Só UMA linha
  carrega dois itens (#26). A #79 (Tríceps Francês na Polia com Corda) carrega um item só —
  "Tríceps Francês (Halter ou Polia)" está coberto pelas #73 e #74. 19 itens têm 2 linhas cada
  (variações). Com os 10 de abdômen que o catálogo não tem, fecha com os ~71 do plano.

### Perguntas que travam o início do código

1. **CASA entra nesta fatia ou fica fora?** Como está, a lista principal é uma lista de academia.
   Opções: (a) `TrainingEnvironment.CASA` fora da regra de falha rígida, como o CORE; (b) curar uma
   lista principal caseira (precisa de BAND e de costas/posterior/panturrilha corporais);
   (c) a falha rígida valer só para ACADEMIA.
2. **POSTERIOR composto para BEGINNER:** rebaixar um dos Stiffs para BEGINNER, ou aceitar que
   iniciante não tem composto de posterior?
3. **Rosca Inversa é BICEPS ou FOREARMS?** (decide se o slot FOREARMS tem 1 candidato ou nenhum)
4. **Adutora e Face Pull:** confirma as duas propostas da seção 2 para entrarem na V64?
5. **Nível ADVANCED:** a lista principal fica sem ADVANCED de propósito?

### Respostas do Rafael — 2026-10-10

- **Q1 (CASA):** vai **fornecer exercícios caseiros depois**. A lista principal de casa fica
  pendente de curadoria; não decidir a regra de falha para CASA nesta sessão.
- **Q2 e Q5 (nível):** proposta de remover nível **RETIRADA pelo Rafael em 2026-10-10:
  "não retire o nível".** O sistema de nível fica inteiro (filtro do pool, bônus de score, volume e
  gate do foco). **Consequência: os furos de iniciante continuam de pé e são bloqueadores.**

**Onde `level` é lido hoje (para escopar a remoção):**

1. `ExercisePreFilter.poolFor` → `ExercisesTable.level inList levelsUpTo(level)` — **filtro duro do pool**.
2. `SlotFiller.score` → `if (ex.level?.name == userLevel) s += 2.0` — **bônus de ranking**.
3. `StructureEngine` → `VolumeTable.weeklySets(m, level, goal)` e `RoleParams.paramsFor(role, level, goal, age)`
   — **volume semanal e reps/descanso/RIR**. Nada a ver com escolha de exercício.
4. `DeterministicWorkoutGenerator.effectiveFocus` → `profile.level != Level.BEGINNER` —
   **gate do foco** (foco só vale para INTER/ADVANCED).

**[PROPOSTA] Remover só (1) e (2); manter (3) e (4).** (3) é a calibragem de volume, que não tem
relação com filtrar catálogo; (4) é regra de produto do foco. Remover (3) seria outra fatia e
derrubaria o `VolumeTable` inteiro.

**Risco a ratificar junto:** sem (1), iniciante pode receber Levantamento Terra e Agachamento Livre
no primeiro treino. O filtro de nível era a única rede contra isso — é decisão de produto, não
técnica.

### Decisões do Rafael — 2026-10-10 (2ª rodada)

- **Nível FICA.** `ExercisePreFilter.levelsUpTo`, o bônus `+2.0` do `SlotFiller.score`, o
  `VolumeTable` e o gate do foco permanecem como estão. Os furos de iniciante voltam a ser
  bloqueadores da falha rígida.
- **[REGRA] Rosca Inversa é antebraço.** Rafael, 2026-10-10: "focada principalmente no antebraço
  (com destaque para o braquiorradial), mas também trabalha o braquial e, em menor grau, o bíceps".
  → **#63 Rosca Inversa com Barra W muda de `category BICEPS` para `FOREARMS`**, primary
  `{FOREARMS}`, secondary `{BICEPS}`, igualando a #62 (que já está FOREARMS). `MuscleGroup` não tem
  braquiorradial nem braquial; FOREARMS é o mais próximo. BICEPS cai de 13 → 12 candidatos.
- **V64 confirmada** para as duas correções da seção 2: Cadeira Adutora `primary_muscles → GLUTES`
  (não só a category) e Face Pull `primary` + `category → BACK`.
- **[REGRA] O requisito de cobertura é por ALVO × PAPEL, não por padrão × equipamento.** Rafael
  ratificou o raciocínio em 2026-10-10: o que derruba a geração é o par (TargetMuscle, SlotRole) que
  o `SlotFiller` consulta; equipamento só entra como peso no `score`. O limiar `< 3` passa a ser
  medido em alvo × papel. A tabela de padrão × equipamento continua útil como diagnóstico, não como
  critério de aceite.

### Medido em 2026-10-10: ranking real do `score()` no COMPOSTO_PESADO

`score = foco(+4) + nível(+2 se `ex.level == userLevel`) + papel(+2 se composto) + equipamento`
(BARBELL/MACHINE/CABLE 1.0 · DUMBBELL 0.8 · BODYWEIGHT 0.2), mais `jitter` de **0 a 0,35**.
Com foco e papel iguais entre candidatos do mesmo slot, sobram nível e equipamento.

| alvo | BEGINNER | INTERMEDIATE | ADVANCED |
|---|---|---|---|
| CHEST | 2 cand., **sorteio entre 1** (Flexão inalcançável) | 9 cand., sorteio entre 6 | 9 cand., sorteio entre 7 |
| BACK | 5, sorteio entre 5 | 14, sorteio entre 7 | 14, **sorteio entre 12** |
| QUADS | 2, sorteio entre 2 | 4, sorteio entre 2 | 4, **sorteio entre 4** |
| POSTERIOR | **VAZIO** | 3, sorteio entre 3 | 3, sorteio entre 3 |
| SHOULDERS | 2, sorteio entre 2 | 4, sorteio entre 2 | 4, sorteio entre 4 |
| GLUTES | 3, sorteio entre 3 | 6, sorteio entre 3 | 6, sorteio entre 6 |

**Três fatos que saem daí:**

1. **O bônus de nível (+2.0) é o critério primário, não o equipamento** (spread máximo 0,8).
   Para BEGINNER e INTERMEDIATE isso resolve bem: no quadríceps intermediário o Agachamento Livre
   e o Afundo com Barra disputam o topo e o Leg Press fica fora, que é o desejado. Em troca, 5 das
   14 costas compostas (Puxada Alta, Remada Sentada no Cabo, Remada Cavalinho na Máquina, Pulldown,
   Remada Sentada na Máquina — todas BEGINNER) ficam **inalcançáveis para o intermediário** enquanto
   houver remada INTERMEDIATE não usada. É o nível decidindo rotação.
2. **ADVANCED é o nível quebrado.** Nenhum exercício da lista é ADVANCED → ninguém recebe o +2.0 →
   o equipamento decide → e o empate em 1.0 entre BARBELL/MACHINE/CABLE produz **sorteio entre 12
   candidatos nas costas e entre 4 no quadríceps, com Agachamento Livre e Leg Press no mesmo
   sorteio do slot mais pesado do dia**. Para o avançado o motor é praticamente aleatório.
3. **BODYWEIGHT é deterministicamente morto na academia.** `1.0 − 0.2 = 0.8` e o jitter vai só até
   0,35, então a diferença nunca é fechada. **Barra Fixa e Chin-Up não ganham slot de costas em
   nível nenhum** (no intermediário fazem 4,2 contra 5,0 das remadas), e Flexão e Mergulho nas
   Paralelas idem no peito. Essas 5 linhas das 79 só entram por esgotamento do `alreadyUsed`. O
   peso 0.2 foi escrito como preferência e funciona como exclusão.

### Bloqueadores que sobraram (nível mantido)

| # | furo | medido | efeito com falha rígida |
|---|---|---|---|
| B1 | POSTERIOR composto, BEGINNER | **0** | **derruba a geração** de todo iniciante com composto de posterior |
| B2 | BACK isolamento, BEGINNER | **0** | derruba quando o dia tem 3+ slots de costas |
| B3 | CHEST composto, BEGINNER | 2, efetivos **1** | sem rotação: sempre Supino Inclinado com Halteres |
| B4 | QUADS isolamento (todos os níveis) | 1 | só Cadeira Extensora |
| B5 | GLUTES isolamento (todos os níveis) | 1 | só Cadeira Abdutora |
| B6 | CALVES (todos os níveis) | 2 | abaixo do limiar |
| B7 | FOREARMS (depois da regra da Rosca Inversa) | 2 | abaixo do limiar — **tem solução trivial** |
| B8 | ADVANCED | 0 exercícios | motor aleatório para o avançado |

**B7 resolve de graça:** os 270 têm **14 exercícios FOREARMS**, todos BEGINNER e isolamento, dois
deles já `is_base = t`: **Rosca de Punho com Barra sobre o Banco** (BARBELL) e **Rosca de Punho com
Halteres** (DUMBBELL). Entrando os dois, FOREARMS vai a 4.

**B1 tem três saídas:** (a) rebaixar um dos Stiffs para BEGINNER; (b) entrar um hinge BEGINNER dos
270 na lista; (c) POSTERIOR composto fora da falha rígida para BEGINNER. (b) é a menos invasiva —
não mexe em dado existente.

### [PROPOSTA] Exigência técnica no lugar de nível na seleção — levantado pelo Rafael, 2026-10-10

**Argumento do Rafael (procede):** "para avançado às vezes é mais interessante um exercício básico e
bem desenvolvido do que um avançado — supino máquina é nível fácil, porém existem atletas e usuários
avançados que utilizam. O determinante é a carga do usuário. E é relevante aparelho livre vs máquina:
máquina é mais fácil, exercício livre tende a ser mais difícil."

**O `level` do catálogo confunde dois eixos diferentes:**

| eixo | de quem é | o que deveria decidir |
|---|---|---|
| experiência do usuário (`profile.level`) | da pessoa | volume semanal, reps/descanso/RIR, gate do foco |
| exigência técnica do exercício | do exercício | se a pessoa consegue executar, e o que o slot pesado prefere |

Hoje o motor casa um contra o outro (`ex.level == userLevel` → +2.0; `level inList levelsUpTo`), o
que produz os efeitos medidos acima: supino máquina vira "coisa de iniciante" e desaparece para o
intermediário, e o avançado não casa com nada.

**Dois fatos do código que fecham o argumento:**

1. **O `equipmentWeight` não codifica livre vs máquina — ele inverte o princípio.**
   `BARBELL = MACHINE = CABLE = 1.0` e `DUMBBELL = 0.8`: hoje **máquina vence halter** no desempate,
   e barra não tem vantagem nenhuma sobre máquina. O que esse peso expressa é "quanta carga externa
   cabe", não "quão guiado é o movimento". A preferência por livre que o Rafael descreve **não existe
   em lugar nenhum do motor**.
2. **O motor nunca prescreve carga.** `WorkoutExerciseDto` carrega `sets`, `restSeconds` e `rir` — não
   há campo de peso. Então a carga, que é a variável real de dificuldade, já é do usuário e do log de
   progressão (fatia J). O que o motor pode julgar é exigência de execução.

**Modelo proposto:**

- `profile.level` **continua** mandando em `VolumeTable.weeklySets`, `RoleParams.paramsFor` e no
  `effectiveFocus`. Sai do `ExercisePreFilter` (filtro) e do `SlotFiller.score` (bônus +2.0).
- Entra **exigência técnica do exercício**, com dois usos: teto por experiência (iniciante não recebe
  agachamento livre e terra na primeira semana) e preferência por papel — **`COMPOSTO_PESADO` prefere
  o mais livre**, `COMPOSTO_ACESSORIO` neutro, `ISOLAMENTO` aceita guiado. Substitui o
  `equipmentWeight` atual.
- **Mora em código, não no banco**, pelo mesmo motivo já ratificado no `EquipmentEnvironmentMap`
  ("regra em código, não no banco: 'onde esse equipamento roda' é julgamento nosso e muda sem
  migration"). Base por equipamento + **override por exercício**, porque BODYWEIGHT não é uniforme:
  Flexão é exigência baixa, Barra Fixa e Mergulho nas Paralelas são altas (têm pré-requisito de
  força). Barra e Terra também pedem override acima da base BARBELL.

**Risco a ratificar junto (o que me faz insistir que as duas mudanças andam juntas):** tirar o nível
do filtro **sem** substituir o sinal de ranking transforma o caos do ADVANCED medido acima no
comportamento de **todos** os usuários — sorteio entre 12 candidatos nas costas, Agachamento Livre e
Leg Press no mesmo sorteio do slot pesado. O +2.0 do nível é hoje o único desempate forte que existe.

**Reforço:** o `level` atual já é dado em que não se deve confiar — o ESTADO.md lista
"74% da base em INTERMEDIATE" como débito aberto do catálogo.

**O que isso resolve da tabela de bloqueadores:** B1 (POSTERIOR composto iniciante vazio) deixa de
existir — Stiff com Halteres é hinge de halter, exigência baixa, e passa a estar disponível ao
iniciante enquanto Terra e Stiff com Barra ficam travados por exigência, não por rótulo de nível.
B3 (peito iniciante com 1 alcançável) e B8 (avançado sem exercício) também caem.

**Escopo: é decisão de arquitetura (ARCH novo) e fatia própria de motor, não curadoria.** Não
implementar na fatia da lista principal. Aguardando o ok do Rafael para abrir.

#### Como ficaria — modelo medido em 2026-10-10 (ainda [PROPOSTA], nada codado)

**Escala de exigência técnica (4 níveis), em código.** Base por equipamento + override por exercício:

| nível | base | nas 79 |
|---|---|---|
| 1 GUIADO | MACHINE, CABLE | 30 |
| 2 ASSISTIDO | DUMBBELL, BODYWEIGHT simples | 26 |
| 3 LIVRE | BARBELL | 18 |
| 4 TÉCNICO | override | 5 — Barra Fixa, Chin-Up, Mergulho nas Paralelas, Agachamento Livre, Levantamento Terra |

**Uso 1 — teto por experiência** (substitui `levelsUpTo` no `ExercisePreFilter`):
BEGINNER ≤ ASSISTIDO (pool 56) · INTERMEDIATE ≤ LIVRE (74) · ADVANCED ≤ TÉCNICO (79).

**Uso 2 — preferência por papel** (substitui `equipmentWeight` e o bônus `+2.0` de nível no `score`):
`COMPOSTO_PESADO: +0.5 × (exigência − 1)` (prefere o mais livre) · `COMPOSTO_ACESSORIO: 0` ·
`ISOLAMENTO: +0.5 × (4 − exigência)` (prefere o guiado). Foco (+4), papel (+2) e jitter (0,35)
ficam como são.

**Falhas duras antes × depois** (alvo × papel sem candidato, com a V64 e a regra da Rosca Inversa):

| | hoje (nível) | proposto (exigência) |
|---|---|---|
| POSTERIOR composto · BEGINNER | **0 — falha** | 1 (Stiff com Halteres) |
| BACK isolamento · BEGINNER | **0 — falha** | 2 |
| CHEST composto · BEGINNER | 2, só 1 alcançável | 4 |
| FOREARMS · BEGINNER | 1 | **0 — falha** (as duas Roscas Inversas são BARBELL = LIVRE) |
| ADVANCED | pool = do intermediário, ranking aleatório | 79, ranking determinístico |

**O 1º do COMPOSTO_PESADO passa a progredir com a experiência:**

| alvo | BEGINNER | INTERMEDIATE | ADVANCED |
|---|---|---|---|
| CHEST | Supino Reto com Halteres | Supino Reto com Barra | Mergulho nas Paralelas |
| BACK | Remada Serrote com Halter | Remada Curvada com Barra | Barra Fixa |
| QUADS | Afundo com Halteres | Afundo com Barra | Agachamento Livre com Barra |
| POSTERIOR | Stiff com Halteres | Stiff com Barra | Levantamento Terra |
| SHOULDERS | Desenvolvimento Sentado c/ Halteres | Desenvolvimento Militar | Desenvolvimento Militar |
| GLUTES | Afundo com Halteres | Elevação Pélvica com Barra | Levantamento Terra |

**Custo: zero migration.** A exigência mora em código; a coluna `level` do banco fica intacta,
só deixa de ser lida pelo motor (vira vitrine no app + débito de limpeza). Mudam
`ExercisePreFilter`, `SlotFiller` e os testes dos dois. Treinos já gerados estão persistidos, então
nada reescreve histórico — só gerações novas mudam. (Correção de 2026-10-11: o
`DeterministicWorkoutGenerator` usa `kotlin.random.Random.nextLong()` por geração — "gerar de novo"
com o mesmo perfil JÁ produz seleção diferente hoje; a estrutura é que é determinística pelo perfil.
O KDoc do `SlotFiller` ainda diz "mesma seed + perfil = mesmo treino", que é verdade do `SlotFiller`,
não do gerador. DOC a corrigir.)

**Três julgamentos que são seus:**

1. **Teto do iniciante em ASSISTIDO deixa o iniciante sem nenhuma barra.** Subir para LIVRE lhe dá
   supino reto com barra e remada curvada, mas também expõe o iniciante a hinge com barra. Qual?
2. **No peito avançado o 1º virou Mergulho nas Paralelas, não Supino Reto com Barra.** Exigência
   técnica e "quanto dá para carregar" não são o mesmo eixo, e o modelo só conhece o primeiro. Numa
   semana de 2 dias de peito o `alreadyUsed` entrega os dois, então pode ser aceitável — mas se
   incomodar, a saída é empatar TÉCNICO com LIVRE no bônus do slot pesado (o custo é a Barra Fixa
   deixar de ser a 1ª das costas no avançado).
3. **POSTERIOR composto do iniciante fica com 1 candidato** — resolve a falha, não a variedade. E
   **FOREARMS do iniciante vai a 0**: a Rosca de Punho com Halteres (DUMBBELL, `is_base`) dos 270
   deixa de ser opcional e passa a ser requisito. Os 270 têm 22 HINGE para escolher um segundo
   hinge assistido.

**Achado de dado que apareceu na simulação:** **Face Pull está `is_compound = sim`** no banco.
Depois da V64 (primary BACK) ele entra no pool de **composto** de costas e pode ocupar slot de
composto pesado. Fica por último no ranking (exigência GUIADO), mas é acessório, não composto —
avaliar `is_compound = false` junto da V64.

## Como o motor escolhe, passo a passo — traçado em 2026-10-11

Rafael, 2026-10-11: **gostou da escala de 4 níveis de exigência técnica.** Pediu o traçado da
geração. Simulação abaixo replica `DeterministicWorkoutGenerator` → `StructureEngine` →
`ExercisePreFilter` → `SlotFiller`, com o pool = as 79 da lista principal (pós-V64 e pós-regra da
Rosca Inversa), regras de hoje (nível mantido).

### O que o usuário escolhe (campos do `ProfileDto` que o motor lê)

| campo | onde pesa |
|---|---|
| `goal` | `VolumeTable.weeklySets` (GENERAL_HEALTH usa o piso MEV) e `RoleParams` (modo conservador) |
| `level` | volume semanal, RIR conservador, **filtro do pool**, **bônus +2.0 no score**, gate do foco |
| `daysPerWeek` (2–6) | nº de dias e, via `SplitCatalog`, o split |
| `splitPreference` | split, se válido pro nº de dias; senão cai no recomendado |
| `focusAreas` | +3 séries/semana no alvo e **+4.0 no score**. Só vale p/ INTER/ADVANCED e nunca GENERAL_HEALTH; máx 2 grupos |
| `age` | fator de volume (1.0 / 0.9 / 0.75) e RIR conservador em 60+ |
| `environment` | filtro de equipamento (CASA = DUMBBELL/BODYWEIGHT/BAND). **Obrigatório** — null lança |
| `limitations` | remove do pool quem tem a contraindicação |

O usuário **não** escolhe exercício, carga nem padrão. A seed é `Random.nextLong()` por geração.

### Os 5 passos

1. **Alvo semanal por músculo** = `VolumeTable[músculo][nível]` × fator de idade, `+3` se foco,
   teto no MRV.
2. **Split → dias** (`SplitCatalog.resolve`) e **frequência** = em quantos dias cada músculo cai.
3. **Séries por sessão** = alvo ÷ frequência, preso em 3..8. **Nº de exercícios** = 1 por músculo +
   sobra para quem tem mais volume, teto 6 por sessão. **Séries por exercício** = séries ÷
   exercícios, preso em 3..5. Depois o **teto de 20 séries totais** corta de quem tem mais.
4. **Papel por posição**: índice 0 = COMPOSTO_PESADO, 1 = COMPOSTO_ACESSORIO, 2+ = ISOLAMENTO;
   CALVES/CORE/BICEPS/TRICEPS/FOREARMS são sempre ISOLAMENTO. Papel → reps/descanso/RIR.
   Slots ordenados por papel (pesados primeiro).
5. **`SlotFiller`**: pool filtrado por alvo e papel, menos o que já foi usado **no programa**;
   `is_base` primeiro; escolhe o maior `score` = foco(+4) + nível(+2) + papel(+2) + equipamento
   + penalidade de padrão repetido no dia (−2,5) + jitter(0..0,35).

### Exemplo traçado — INTERMEDIATE · GAIN_MUSCLE · 4 dias · foco Costas · 28 anos · academia

**Passo 1:** CHEST 14 · **BACK 19** (16+3 do foco) · SHOULDERS 12 · BICEPS 9 · TRICEPS 10 ·
FOREARMS 6 · QUADS 12 · POSTERIOR 10 · CALVES 9 · GLUTES 8 · CORE 8.
**Passo 2:** 4 dias → UPPER_LOWER → Upper A, Lower A, Upper B, Lower B. Frequência 2× em tudo.

**Upper A** — séries/sessão: CHEST 7 · BACK 8 · SHOULDERS 6 · BICEPS 5 · TRICEPS 5 · FOREARMS 3.
Exercícios: **1 para cada** (6 músculos = o teto, o `while` do `allocateExercises` nem roda).
Teto de 20 séries corta CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, CHEST, BACK, SHOULDERS → final
**CHEST 1×3 · BACK 1×3 · SHOULDERS 1×3 · BICEPS 1×4 · TRICEPS 1×4 · FOREARMS 1×3**.

| slot | papel | prescrição | cand. | topo | sorteio entre |
|---|---|---|---|---|---|
| CHEST | COMPOSTO_PESADO | 3×5-8, 150s, RIR2 | 9 | 5.0 | 6 (Supino Reto c/ Barra, Inclinado, Declinado, Pegada Fechada…) |
| BACK | COMPOSTO_PESADO | 3×5-8, 150s, RIR2 | 15 | **9.0** | 7 (Remada Curvada, Sentada Pegada Fechada, T-Bar, Alta no Cabo…) |
| SHOULDERS | COMPOSTO_PESADO | 3×5-8, 150s, RIR2 | 4 | 5.0 | 2 (Militar, Arnold) |
| BICEPS | ISOLAMENTO | 4×12-15, 75s, RIR1 | 12 | 2.8 | 2 (Rosca Spider ×2) |
| TRICEPS | ISOLAMENTO | 4×12-15, 75s, RIR1 | 9 | 0.5 | 2 (Testa c/ Barra, Francês Sentado) |
| FOREARMS | ISOLAMENTO | 3×12-15, 75s, RIR1 | 2 | **−1.5** | 2 (as duas Roscas Inversas) |

O 9.0 das costas = 4 (foco) + 2 (nível) + 2 (papel) + 1 (equipamento). O −1.5 do antebraço =
0 + 0 + 0 + 1.0 − 2.5 (padrão NONE já usado no dia). Score negativo é inofensivo — o `maxByOrNull`
é relativo.

**Lower A** — QUADS 2×3 · POSTERIOR 1×3 · GLUTES 1×3 · CALVES 1×4 · CORE 1×4 →
QUADS pesado (4 cand., sorteio entre Agachamento Livre e Afundo c/ Barra) · POSTERIOR pesado
(3: Terra, Stiff Barra, Stiff Halteres) · GLUTES pesado (Afundo c/ Barra) · QUADS acessório
(Leg Press, Afundo Halteres) · CALVES (as 2) · **CORE sem candidato → slot descartado**.

**Upper B** segue com o que sobrou: SHOULDERS cai para 1 candidato (Arnold), FOREARMS para 1.
**Lower B** é o pior: QUADS pesado tem **1** candidato (Afundo com Halteres), **QUADS acessório
fica sem nenhum** e CORE de novo sem nenhum → o dia entrega **4 exercícios em vez de 6**.

### Cinco defeitos que o traçado revelou (nenhum é da lista — são do `StructureEngine`)

1. **[INV violado] O bônus de foco é somado no passo 1 e destruído no passo 3.** O corte do teto de
   20 séries percorre a ordem canônica (grandes primeiro), então CHEST/BACK/SHOULDERS levam 2 cortes
   cada e os pequenos 1. Resultado no exemplo: **costas, que é o foco, entrega 6 séries/semana
   contra um alvo de 19 — e o bíceps entrega 8 contra um alvo de 9.** O músculo priorizado pelo
   usuário recebe menos volume que o bíceps.
2. **Volume real muito abaixo do alvo, com a hierarquia invertida:** BACK 6/19 (32%) · CHEST 6/14
   (43%) · SHOULDERS 6/12 (50%) · POSTERIOR 6/10 (60%) · QUADS 9/12 · GLUTES 6/8 · TRICEPS 8/10 ·
   CALVES 8/9 · BICEPS 8/9 (89%) · FOREARMS 6/6 (100%) · CORE 0/8. Músculo pequeno bate o alvo,
   grande não chega perto.
3. **Em Upper/Lower o `allocateExercises` é inerte.** UPPER tem exatamente 6 músculos =
   `SESSION_MAX_EXERCISES`, então `alloc` fica 1 para cada e o `while` nunca executa — o "natural"
   (CHEST 2, BACK 3, SHOULDERS 2) é calculado e descartado. Consequência: **em dia Upper o papel
   COMPOSTO_ACESSORIO nunca existe**; todo peito/costas/ombro é COMPOSTO_PESADO a 5-8 reps.
4. **`alreadyUsed` é por programa e o pool de perna não aguenta 2 dias.** QUADS tem 4 compostos na
   lista e o programa pede 4 slots (2 por dia × 2 dias) → o 4º fica sem candidato. É o limiar
   "< 3 por alvo × papel" cobrando na prática.
5. **A penalidade de padrão repetido distorce o isolamento.** NONE é tratado como padrão real, então
   depois do primeiro isolamento todos os outros NONE levam −2,5 igualmente (inofensivo, é uniforme)
   — **mas um isolamento que TEM padrão real é punido contra os NONE**. Num dia com slot de
   isolamento de peito depois do supino (HORIZONTAL_PUSH), Crucifixo, Crossover e Pec Deck levam
   −2,5 e o **Pullover ganha só porque é VERTICAL_PULL**. O desempate deixa de ser mérito.

**Nada disso é da fatia da lista principal** — é `StructureEngine`/`SlotFiller`. Registrar como
débito ou abrir fatia é decisão do Rafael. O defeito 1 é o mais grave: quebra a promessa do foco,
que é escolha explícita do usuário no quiz.
