-- V69 -- progressao por exercicio, mesociclo por pessoa e o RIR da serie (motor v3, Fatia 10b).
--
-- ============================================================================
-- O QUE E (desenho aprovado em 2026-10-28: docs/trabalho/motor-v3-fatia-10-schema.md)
-- ============================================================================
-- D-1  A chave e (usuario, exercicio) DE PROPOSITO: a progressao acompanha a pessoa; `programa_id`
--      na chave faria o aluno voltar a calibracao a cada programa novo com o mesmo exercicio.
-- D-2  `decidir()` NAO e idempotente. Reenviar a sessao depois de um timeout subiria a carga em
--      dobro. A guarda e a PK de `progression_event` (usuario, exercicio, sessao): a segunda
--      tentativa nao insere e o estado NAO e regravado. `workout_sessions.id` ja nasce no cliente
--      "para idempotencia do sync" (V20) -- e a mesma chave.
-- D-3  `session_set_logs.rir` NULLABLE. [REGRA] RIR nulo = "nao informado" e aplica SO as regras por
--      repeticao (sem salto, sem tolerancia pos-salto, sem -4%, sem 1RM). Sem backfill: o historico
--      antigo nao tem como ser inventado.
-- D-4  Mesociclo por PESSOA: `mesocicloAtual` entra no pipeline como atributo dela e e o que tira o
--      60+ do modo conservador permanente.
--
-- Nenhum dado existente e apagado ou reescrito: tres tabelas NOVAS e uma coluna NULLABLE.
-- Exercicios nao sao apagados (V20), por isso o FK de exercise_id nao tem ON DELETE.

CREATE TABLE exercise_progression (
    user_id      UUID             NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id  UUID             NOT NULL REFERENCES exercises(id),
    carga_kg     DOUBLE PRECISION,                                   -- NULL = calibracao pendente
    exposicoes   INT              NOT NULL DEFAULT 0 CHECK (exposicoes >= 0),
    pos_salto    BOOLEAN          NOT NULL DEFAULT FALSE,             -- regra 3: tolerancia ativa
    um_rm_kg     DOUBLE PRECISION,                                   -- NULL em isolamento / sem dado
    sem_subir    INT              NOT NULL DEFAULT 0 CHECK (sem_subir >= 0),
    updated_at   TIMESTAMP        NOT NULL DEFAULT now(),             -- relogio do SERVIDOR
    PRIMARY KEY (user_id, exercise_id),
    CHECK (carga_kg IS NULL OR carga_kg > 0),
    CHECK (um_rm_kg IS NULL OR um_rm_kg > 0)
);

CREATE TABLE user_mesocycle (
    user_id      UUID      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    numero       INT       NOT NULL DEFAULT 1 CHECK (numero >= 1),
    semana       INT       NOT NULL DEFAULT 1 CHECK (semana BETWEEN 1 AND 4),
    -- 60+ que voltou depois de >28 dias: modo conservador explicito ate o fim do mesociclo.
    conservador  BOOLEAN   NOT NULL DEFAULT FALSE,
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- Append-only. Alimenta a ancora do iniciante (AncoraDoIniciante.situacao) e e a chave de
-- idempotencia do D-2. Apagar a sessao apaga o evento mas NAO desfaz o estado (ja avancou).
CREATE TABLE progression_event (
    user_id      UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id  UUID      NOT NULL REFERENCES exercises(id),
    session_id   UUID      NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    tipo         TEXT      NOT NULL CHECK (tipo IN ('SOBE', 'MANTEM', 'DESCE', 'DELOAD')),
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    -- Ordem de REGISTRO no servidor. `created_at` NAO ordena: relogio pode empatar ou recuar, e teste
    -- que depende de relogio fica vermelho intermitente. A sequencia e monotona e nunca empata.
    seq          BIGINT    GENERATED ALWAYS AS IDENTITY,
    PRIMARY KEY (user_id, exercise_id, session_id)
);

CREATE INDEX idx_progression_event_ancora ON progression_event (user_id, exercise_id, seq DESC);

ALTER TABLE session_set_logs
    ADD COLUMN rir SMALLINT CHECK (rir IS NULL OR rir BETWEEN 0 AND 10);

COMMENT ON COLUMN session_set_logs.rir IS
    'RIR informado pela pessoa (0-10). NULL = nao informado: a progressao aplica SO as regras por '
    'repeticao. Sem backfill.';
