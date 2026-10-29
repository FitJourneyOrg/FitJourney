# Banco de dados — tabelas de exercícios

> ⚠️ **[DESATUALIZADO — atualizado em 2026-10-06, schema ainda para na V16]** As migrations hoje
> vão até a **V60**. Desde a versão anterior deste aviso (V33): V28–V33 refizeram a taxonomia
> (hoje 923 exercícios, não 965 — curadoria V54 consolidou/removeu duplicatas), e a **V49 criou
> `exercise_translations`** (seção 4) — essa é uma mudança de ESTRUTURA, não só de dado, e por
> isso foi incorporada abaixo em vez de só constar neste aviso. A estrutura do resto segue válida
> em linhas gerais; os **dados e as contagens, não**.
>
> Para o estado real do catálogo, use `FitJourney_CATALOGO.pdf` (gerado do banco, com dado
> vivo). Este documento vale como referência de *relacionamento entre tabelas*, não de conteúdo.

> Documento local (docs/ gitignored). Fonte da verdade: migrations Flyway
> (`server/src/main/resources/db/migration/`) e o espelho Exposed `ExercisesTable.kt`.
> Schema montado de: **V3** (cria `exercises`) → **V8** (taxonomia) → **V16** (`is_base`).
> A **V21** (Fase 0) só altera DADO, não o schema.

## 1. Relacionamento entre as tabelas

```
exercises (catálogo, read-only p/ o usuário)
   ▲              ▲                         ▲
   │ FK (CASCADE) │ FK (RESTRICT)           │ ref SOFT (sem FK)
   │              │                         │
exercise_translations   workout_exercises   session_set_logs
(tradução, V49)             ▲               (log de execução — Fase 5)
                             │ FK (CASCADE)
                         workout_sets
```

- `workout_exercises.exercise_id` → **FK real** para `exercises(id)`, **sem `ON DELETE`** ⇒ default
  `RESTRICT`: não dá pra apagar um exercício que esteja em algum treino. **É por isso que o dedup
  (V22) precisa repontar `workout_exercises` ANTES do `DELETE`** (padrão da V15).
- `session_set_logs.exercise_id` → **referência solta** (sem FK), de propósito: o log de treino é um
  snapshot histórico e não pode quebrar se o catálogo mudar.
- `exercise_translations.exercise_id` → **FK real, `ON DELETE CASCADE`** (V49): ao contrário de
  `workout_exercises`, apagar o exercício-base é correto apagar a tradução junto — ela não tem
  vida própria fora do exercício que traduz.

## 2. Tabela `exercises`

DDL efetiva (V3 + V8 + V16):

```sql
CREATE TABLE exercises (
    id                 UUID          PRIMARY KEY,
    name               VARCHAR(200)  NOT NULL,
    category           VARCHAR(32)   NOT NULL,
    description        TEXT,
    video_ref          VARCHAR(300)  NOT NULL,
    thumb_ref          VARCHAR(300)  NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- taxonomia (V8) — NULL nos não-curados (invisíveis ao motor)
    modality           VARCHAR(16),
    movement_pattern   VARCHAR(24),
    secondary_pattern  VARCHAR(24),
    is_compound        BOOLEAN,
    equipment          VARCHAR(24),
    primary_muscles    TEXT[],
    secondary_muscles  TEXT[],
    unilateral         BOOLEAN,
    prescription_type  VARCHAR(12),
    level              VARCHAR(16),
    contraindications  TEXT[],
    -- V16
    is_base            BOOLEAN       NOT NULL DEFAULT false
);
```

### Colunas — o que cada uma armazena e quais valores aceita

**`id`** — `UUID`, chave primária. Identidade única do exercício. Valores: qualquer UUID; no
FitJourney são determinísticos (gerados do catálogo). Imutável — é ele que `workout_exercises` e
`session_set_logs` referenciam.

**`name`** — `VARCHAR(200)`, obrigatório. Nome exibido (ex.: "Cadeira extensora"). Valores: qualquer
string até 200 caracteres; nomes curados na V14. Sem restrição de enum.

**`category`** — `VARCHAR(32)`, obrigatório. Categoria do catálogo. Valores (`ExerciseCategory`, 17):
`CORE, FUNCTIONAL_HIT, MOBILITY, CALISTHENICS, LEGS, SHOULDERS, CHEST, BACK, CROSSFIT, BICEPS,
TRICEPS, TRAPEZIUS, GLUTES, CALVES, FOREARMS, CARDIO, LOWER_BACK`. Usada pelo motor p/ casar
CALVES/CORE/BICEPS/TRICEPS. **Único campo cuja leitura quebra se vier valor fora do enum** (`valueOf`
sem proteção).

**`description`** — `TEXT`, nullable. Descrição (HTML convertido em texto limpo). Valores: qualquer
texto ou `NULL` (os 23 inseridos na V16 têm `NULL`). Não usado pelo motor.

**`video_ref`** — `VARCHAR(300)`, obrigatório. Caminho do vídeo (ex.: `"Pernas/Cadeira
extensora.mp4"`). Valores: qualquer string; pode ser `''` (vazio) nos inseridos sem mídia.

**`thumb_ref`** — `VARCHAR(300)`, obrigatório. Caminho da thumbnail. Mesmas regras do `video_ref`.

**`created_at`** — `TIMESTAMP`, obrigatório, default `CURRENT_TIMESTAMP`. Momento de criação da linha.

> A partir daqui é a **taxonomia (V8)** — tudo nullable. `NULL` = "não curado" → **invisível ao
> motor** (mas segue no catálogo p/ treino manual).

**`modality`** — `VARCHAR(16)`, nullable. Natureza do exercício e **gate de visibilidade**: só
`STRENGTH` entra no motor. Valores (`Modality`): `STRENGTH, MOBILITY, CARDIO, PLYOMETRIC, SKILL` ou
`NULL`.

**`movement_pattern`** — `VARCHAR(24)`, nullable. Padrão mecânico (casa QUADS/POSTERIOR). Valores
(`MovementPattern`): `NONE, SQUAT, HINGE, LUNGE, HORIZONTAL_PUSH, VERTICAL_PUSH, HORIZONTAL_PULL,
VERTICAL_PULL, ROTATION, CARRY, GAIT, STRIKE, STRETCH, KNEE_FLEXION, KNEE_EXTENSION` ou `NULL`.

**`secondary_pattern`** — `VARCHAR(24)`, nullable. Padrão secundário (mesmo domínio; o match atual
não usa). Quase sempre `NULL`.

**`is_compound`** — `BOOLEAN`, nullable. `true` = composto (multiarticular), `false` = isolamento,
`NULL` = indefinido. Decide se preenche slot de composto pesado/acessório (`true`) ou isolamento
(`false`).

**`equipment`** — `VARCHAR(24)`, nullable, **string livre** (não é enum). Equipamento. Valores usados:
`BARBELL, DUMBBELL, MACHINE, CABLE, BAND, BODYWEIGHT, KETTLEBELL, GYMSTICK, IMPROVISED,
AGILITY_LADDER` ou `NULL`. Define ambiente (casa = `DUMBBELL/BODYWEIGHT/BAND`; academia = todos) e o
peso no score.

**`primary_muscles`** — `TEXT[]`, nullable. Músculos primários (casa CHEST/BACK/SHOULDERS/GLUTES/CORE;
filtrado em SQL via GIN). Valores por elemento (`MuscleGroup`): `CHEST, BACK, ARMS, SHOULDERS, LEGS,
GLUTES, CORE`. Pode ser `[]` ou `NULL`. ⚠️ Há `TRAPEZIUS` gravado em ~9 linhas, mas o valor não existe
no enum → é descartado na leitura.

**`secondary_muscles`** — `TEXT[]`, nullable. Músculos secundários (mesmo domínio; contexto, não
filtrado pelo motor).

**`unilateral`** — `BOOLEAN`, nullable. `true` = um lado por vez (ex.: extensão unilateral),
`false`/`NULL` caso contrário. Só contexto.

**`prescription_type`** — `VARCHAR(12)`, nullable. Como é prescrito. Valores (`PrescriptionType`):
`REPS, TIME, DISTANCE` ou `NULL`.

**`level`** — `VARCHAR(16)`, nullable. Nível mínimo do praticante. Valores (`Level`): `BEGINNER,
INTERMEDIATE, ADVANCED` ou `NULL`. O pré-filtro pega exercícios ≤ nível do usuário.

**`contraindications`** — `TEXT[]`, nullable. Limitações que **excluem** o exercício (exclusão dura do
PAR-Q; GIN). Valores por elemento (`BodyLimitation`): `SHOULDER, KNEE, LUMBAR, WRIST, IMPACT`. Pode
ser `[]` ou `NULL`. ⚠️ Há `LOWER_BACK` gravado (V16), que não existe no enum (o certo é `LUMBAR`) → é
descartado → o exercício não é excluído p/ quem tem limitação lombar.

**`is_base`** — `BOOLEAN`, obrigatório, default `false`. `true` = exercício principal, `false` =
variação. O motor escolhe **base primeiro** (ARCH #28); variações só entram se as bases acabarem.

### Índices

```sql
CREATE INDEX idx_exercises_category  ON exercises(category);                       -- V3
CREATE INDEX idx_exercises_engine    ON exercises (modality, movement_pattern, level)
    WHERE modality IS NOT NULL;                                                    -- V8 (parcial, p/ o pré-filtro)
CREATE INDEX idx_exercises_contraind ON exercises USING GIN (contraindications);  -- V8
CREATE INDEX idx_exercises_primary   ON exercises USING GIN (primary_muscles);    -- V8
```

## 3. Domínios dos campos (enums no código)

As colunas string/array guardam texto; a validação vive no código (enums). O mapper
`ResultRow.toExercise()` converte com `runCatching { X.valueOf(it) }.getOrNull()` (exceto
`category`, que usa `valueOf` direto) — **valor fora do enum vira `null` / é descartado**.

**`category`** — `ExerciseCategory` (17 valores; o comentário do enum diz "16", está desatualizado):
CORE, FUNCTIONAL_HIT, MOBILITY, CALISTHENICS, LEGS, SHOULDERS, CHEST, BACK, CROSSFIT, BICEPS,
TRICEPS, TRAPEZIUS, GLUTES, CALVES, FOREARMS, CARDIO, LOWER_BACK.
> `category` usa `valueOf` **sem** `runCatching` no mapper — uma categoria fora do enum **quebra** a
> leitura. Por isso o enum precisa conter todos os valores presentes no banco.

**`modality`** — `Modality`: STRENGTH, MOBILITY, CARDIO, PLYOMETRIC, SKILL. (Motor só vê `STRENGTH`.)

**`movement_pattern` / `secondary_pattern`** — `MovementPattern`:
NONE, SQUAT, HINGE, LUNGE, HORIZONTAL_PUSH, VERTICAL_PUSH, HORIZONTAL_PULL, VERTICAL_PULL,
ROTATION, CARRY, GAIT, STRIKE, STRETCH, **KNEE_FLEXION** (posterior), **KNEE_EXTENSION** (quadríceps).
(Estado-alvo Fatia B: + ELBOW_FLEXION/ELBOW_EXTENSION, se optar por essa via.)

**`equipment`** — string livre (não é enum). Valores usados: BARBELL, DUMBBELL, MACHINE, CABLE,
BAND, BODYWEIGHT, KETTLEBELL, GYMSTICK, IMPROVISED, AGILITY_LADDER.
Ambiente (`EquipmentEnvironmentMap`): CASA = {DUMBBELL, BODYWEIGHT, BAND}; ACADEMIA = todos.

**`primary_muscles` / `secondary_muscles`** — `MuscleGroup`:
CHEST, BACK, ARMS, SHOULDERS, LEGS, GLUTES, CORE.
> ⚠️ **Data-quality:** o banco tem `TRAPEZIUS` em `primary_muscles` de ~9 exercícios, mas
> `MuscleGroup` **não** tem TRAPEZIUS → o mapper descarta → esses exercícios ficam sem primário
> reconhecido (podem sumir do match). LEGS é genérico (não separa quadríceps/posterior — por isso o
> motor usa `movement_pattern` p/ perna).

**`prescription_type`** — `PrescriptionType`: REPS, TIME, DISTANCE.

**`level`** — `Level`: BEGINNER, INTERMEDIATE, ADVANCED.

**`contraindications`** — `BodyLimitation`: SHOULDER, KNEE, LUMBAR, WRIST, IMPACT.
> ⚠️ **Data-quality:** a V16 gravou `LOWER_BACK` em várias contraindicações (Stiff, RDL, Abdominal
> infra, Pallof…), mas o enum é **`LUMBAR`** — o mapper descarta `LOWER_BACK`. Resultado: esses
> exercícios **não são excluídos** para usuários com limitação lombar. Corrigir num passe de catálogo.

## 4. Tabelas relacionadas

### `workout_exercises` (V5) — exercícios de um treino salvo

```sql
CREATE TABLE workout_exercises (
    id           UUID  PRIMARY KEY,
    workout_id   UUID  NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    exercise_id  UUID  NOT NULL REFERENCES exercises(id),   -- RESTRICT (sem ON DELETE)
    order_index  INT   NOT NULL
);
```

### `workout_sets` (V5) — séries prescritas de cada exercício do treino

```sql
CREATE TABLE workout_sets (
    id                   UUID  PRIMARY KEY,
    workout_exercise_id  UUID  NOT NULL REFERENCES workout_exercises(id) ON DELETE CASCADE,
    reps                 INT   NOT NULL,
    order_index          INT   NOT NULL
);
```
> `rest_seconds` foi adicionado na V10 e `rir` na V13 (a `workout_exercises`/`workout_sets`).

### `session_set_logs` (V20) — log de execução (Fase 5), referência solta ao catálogo

```sql
CREATE TABLE session_set_logs (
    id          UUID    PRIMARY KEY,
    session_id  UUID    NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id UUID    NOT NULL,          -- ref SOFT (sem FK): histórico não quebra se o catálogo mudar
    order_index INT     NOT NULL,
    set_index   INT     NOT NULL,
    target_reps INT     NOT NULL,
    reps_done   INT     NOT NULL,
    weight_kg   DOUBLE PRECISION,          -- nullable (peso corporal)
    done        BOOLEAN NOT NULL
);
```

### `exercise_translations` (V49) — tradução do catálogo (fatia H, ARCH #37)

```sql
CREATE TABLE exercise_translations (
    exercise_id UUID         NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    locale      VARCHAR(5)   NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description TEXT,                      -- nullable: nome e descrição foram cargas separadas
    PRIMARY KEY (exercise_id, locale)
);
-- + CHECK (locale IN ('pt-BR', 'en')) e índice por locale (a consulta que domina é
-- "todas as traduções de um locale", porque o cliente baixa o catálogo inteiro de uma vez).
```

Tabela, não colunas `name_en`/`name_es` — **de propósito**: o app vai ter mais de quatro idiomas, e
colunas por idioma viraria vinte colunas em `exercises` e um build vermelho bloqueando lançamento
enquanto o último idioma não estivesse traduzido. **`exercises.name` continua sendo o PISO**
(`Idioma.PADRAO`): a leitura é `LEFT JOIN` com `COALESCE`, nunca lida como fonte única. A guarda
que o compilador daria é feita em teste (`CatalogoTraduzidoIntegrationTest`, percorre
`Idioma.TODOS`).

## 5. Espelho no código (Exposed)

`ExercisesTable.kt` espelha 1:1 a DDL acima (`Table("exercises")`). A leitura acontece em
`ResultRow.toExercise()` (`ExerciseRepositoryImpl.kt`), que converte os textos para os enums com
`runCatching { … }.getOrNull()`. O motor consome `Exercise` (server-only); o cliente recebe só o
`ExerciseDto` enxuto (`id, name, category, description, video_ref, thumb_ref`) — a taxonomia **não
vaza** para o app.
