-- Fase 5 (execução): registro de treinos executados (o log). Base p/ progresso/gamificação.
-- Snapshot: auto-contida — program_id/workout_id são só referência (sem FK/cascade), então
-- editar/apagar o template NÃO some com o histórico.
CREATE TABLE workout_sessions (
    id           UUID      PRIMARY KEY,                     -- gerado no cliente (idempotência do sync)
    user_id      UUID      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    program_id   UUID,
    workout_id   UUID,
    workout_name TEXT      NOT NULL,                        -- snapshot do nome do treino
    started_at   TIMESTAMP NOT NULL,                        -- relógio do CLIENTE (treino pode ter sido offline)
    finished_at  TIMESTAMP NOT NULL,                        -- idem
    created_at   TIMESTAMP NOT NULL DEFAULT now()           -- relógio do SERVIDOR (momento do sync)
);

CREATE INDEX idx_sessions_user ON workout_sessions (user_id, finished_at DESC);

-- Uma linha por SÉRIE: prescrito (target_reps) vs feito (reps_done) + carga.
CREATE TABLE session_set_logs (
    id          UUID    PRIMARY KEY,
    session_id  UUID    NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id UUID    NOT NULL,                           -- ref ao catálogo (exercícios não são apagados)
    order_index INT     NOT NULL,
    set_index   INT     NOT NULL,
    target_reps INT     NOT NULL,
    reps_done   INT     NOT NULL,
    weight_kg   DOUBLE PRECISION,                           -- nullable (peso corporal)
    done        BOOLEAN NOT NULL
);

CREATE INDEX idx_set_logs_session ON session_set_logs (session_id);
