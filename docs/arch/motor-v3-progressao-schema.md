# Motor v3 — schema de progressão por exercício, mesociclo por pessoa e RIR (ARCH · D-1 a D-4 aprovados em 2026-10-28)

Estado: **implementado e verde (Fatia 10b)**: `V69__progressao_por_exercicio.sql`, `ProgressaoTables.kt`, `*RepositoryImpl`, `ProgressaoIntegrationTest` (11 testes contra Postgres real). O SQL abaixo é o rascunho original; **vale a migration** (acrescentou `progression_event.seq` IDENTITY, que ordena o histórico sem depender de relógio, e o CHECK de `um_rm_kg > 0`).
Nenhum dado de usuário é apagado ou reescrito: são tabelas NOVAS (medido: não há `ALTER` em tabela existente neste desenho, exceto a decisão D-3 abaixo).

## O que o código já exige

| Fato | Onde | Consequência no schema |
|---|---|---|
| Progressão é **por exercício**, não por vaga | `ProgressaoRepository` | chave `(user_id, exercise_id)` |
| `Estado(carga?, exposicoes, posSalto, umRm?, semSubir)` | `Progressao.Estado` | 5 colunas, `carga` NULL = calibração pendente |
| Mesociclo é **por pessoa** (`mesocicloAtual` do pipeline) | `MesocicloRepository` | 1 linha por usuário |
| A âncora do iniciante precisa do **histórico de decisões** depois do último deload | `AncoraDoIniciante.situacao(historico, diag)` | `Estado` NÃO basta: só guarda `semSubir`, não a sequência nem o deload |
| Pausa = dias parado, relógio do **servidor** | `RetornoAposPausa.apos` + [INV] "nunca o relógio do cliente" | derivar de `created_at` (servidor), nunca de `started_at`/`finished_at` |

## Tabelas propostas

```sql
-- V69 (rascunho)
CREATE TABLE exercise_progression (
    user_id      UUID             NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id  UUID             NOT NULL REFERENCES exercises(id),   -- exercícios não são apagados (V20)
    carga_kg     DOUBLE PRECISION,                                     -- NULL = calibração pendente
    exposicoes   INT              NOT NULL DEFAULT 0 CHECK (exposicoes >= 0),
    pos_salto    BOOLEAN          NOT NULL DEFAULT FALSE,
    um_rm_kg     DOUBLE PRECISION,                                     -- NULL em isolamento / sem dado
    sem_subir    INT              NOT NULL DEFAULT 0 CHECK (sem_subir >= 0),
    updated_at   TIMESTAMP        NOT NULL DEFAULT now(),              -- relógio do SERVIDOR
    PRIMARY KEY (user_id, exercise_id),
    CHECK (carga_kg IS NULL OR carga_kg > 0)
);

CREATE TABLE user_mesocycle (
    user_id     UUID      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    numero      INT       NOT NULL DEFAULT 1 CHECK (numero >= 1),
    semana      INT       NOT NULL DEFAULT 1 CHECK (semana BETWEEN 1 AND 4),
    conservador BOOLEAN   NOT NULL DEFAULT FALSE,   -- 60+ que voltou após >28 dias; vale até o fim do mesociclo
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE progression_event (            -- append-only; alimenta a âncora do iniciante
    user_id      UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id  UUID      NOT NULL REFERENCES exercises(id),
    session_id   UUID      NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    tipo         TEXT      NOT NULL CHECK (tipo IN ('SOBE','MANTEM','DESCE','DELOAD')),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, exercise_id, session_id)   -- = chave de idempotência (ver D-2)
);
CREATE INDEX idx_prog_event_ancora ON progression_event (user_id, exercise_id, created_at DESC);
```

## Decisões que preciso de você (D-1 a D-4) — e meu voto

**D-1. Chave `(usuário, exercício)` sem `programa_id`.** Voto: sim, como aprovado. Custo: trocar de
exercício na vaga começa a história do novo do zero (calibração) e deixa a do antigo parada —
correto, é o comportamento que a regra de continuidade quer. Dado de exercício nunca apagado:
**sem** `ON DELETE` no FK de `exercises` (a V20 já afirma que catálogo não apaga).

**D-2 (APROVADO). `decidir()` NÃO é idempotente, e o repositório sozinho não resolve.** O fake prova que
gravar o mesmo *estado* duas vezes é inofensivo — mas o cliente reenvia a *sessão* depois de um
timeout, e aplicar `decidir` duas vezes incrementa `exposicoes` duas vezes. A guarda é
`progression_event(user_id, exercise_id, session_id)` com PK: a segunda tentativa viola a PK e o
serviço (Fatia 11) devolve o estado atual sem decidir de novo. A `workout_sessions.id` já é gerada
no cliente "para idempotência do sync" (V20) — é a mesma chave. **Risco real se ficar de fora:**
carga subindo em dobro por toque duplo. Voto: a tabela de eventos entra.

**D-3 (APROVADO, com `[REGRA]` do RIR nulo). `session_set_logs` não tem RIR.** `decidir()` consome `Serie(reps, rir)`; a V20 guarda
`reps_done` e `weight_kg`, **não o RIR relatado**. Duas saídas: (a) `ALTER TABLE session_set_logs ADD
COLUMN rir SMALLINT` (nullable, sem backfill — o histórico antigo não tem como ser inventado); (b)
o cliente manda o RIR só no endpoint de progressão e ele não persiste. Decidido **(a)** — sem RIR
guardado não dá para auditar uma decisão depois.

**`[REGRA]` RIR nulo (explícita, não implícita):** a coluna é NULLABLE e `rir IS NULL` significa "não informado". `decidir()` com RIR nulo aplica **só as regras por repetição**: sem exigência de RIR no topo, sem salto proporcional, sem tolerância pós-salto, sem a redução de 4% e sem 1RM; a calibração, que é feita de RIR, não age. Nada é inferido nem preenchido por padrão. Está implementada nos dois lados (`Serie.rir: Int?`) e na grade contra o oráculo. É a única mudança em tabela existente, e é
aditiva (coluna nula): nenhum dado é reescrito.

**D-4. Mesociclo por pessoa, não por programa.** Voto: por pessoa. Motivo: `mesocicloAtual` é
atributo da pessoa no pipeline e é o que tira o 60+ do modo conservador; um programa novo não
zera isso. Custo: trocar de programa no meio do mesociclo mantém a semana — aceitável, e é a
mesma semântica do "retorno após pausa" (15–28 dias: semana 1 do MESMO mesociclo). A única coluna nova é `conservador` (o 60+ que volta de pausa longa: modo conservador explícito até o fim do mesociclo, extensão declarada além da lista literal da spec). A pausa **não
ganha coluna**: `dias parado = now() − max(workout_sessions.created_at)` (servidor), uma fonte só.

## O que fica de fora (dívida registrada)

- **Diagnóstico de estagnação** (`diagnosticoFeito` da âncora): vem de fora, Fatia 11.
- **Cliente (SQLDelight):** só cópia otimista do `Estado`; a autoridade é o servidor. Sem schema de
  cliente nesta fatia.
- **Locking:** last-write-wins no upsert basta se a PK de `progression_event` serializar a sessão
  (D-2). Sem coluna `version`.
