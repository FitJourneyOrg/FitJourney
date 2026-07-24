-- V11: programa semanal como entidade (ARCH #22).
-- O motor (Fatia F) gera um ProgramDto; a G.1 persiste aqui.
--
-- Modelo: 1 programa ATIVO por usuário no v1 (gerar um novo substitui o anterior).
-- "Histórico de programas" é feature futura (débito).
--
-- SUPOSIÇÕES a confirmar contra o schema real:
--   - users.id é UUID (PK) — a FK user_id aponta pra cá.
--   - workouts.id é UUID (PK), workouts tem user_id.
--   - workouts NÃO tem program_id ainda (esta migration adiciona).

CREATE TABLE programs (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    days_per_week INT  NOT NULL,
    split        VARCHAR(64)  NOT NULL,   -- ex.: "Upper/Lower + PPL"
    rationale    TEXT NOT NULL,           -- explicação do híbrido (revelação)
    locked       BOOLEAN NOT NULL DEFAULT FALSE,  -- posse/blur (ARCH #23)
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- 1 programa ativo por usuário: índice único garante que não haja dois.
-- (Se um dia virar "histórico", troca por índice parcial WHERE active=true.)
CREATE UNIQUE INDEX idx_programs_user ON programs(user_id);

-- Liga os treinos ao programa. Nullable: treino manual não pertence a programa.
ALTER TABLE workouts
    ADD COLUMN program_id UUID REFERENCES programs(id) ON DELETE CASCADE;

-- Agendamento: dia da semana de cada treino do programa (1=segunda ... 7=domingo).
-- Nullable: treino manual (sem programa) não tem dia agendado.
ALTER TABLE workouts
    ADD COLUMN day_of_week INT;

-- Índice pra buscar os treinos de um programa rapidamente.
CREATE INDEX idx_workouts_program ON workouts(program_id) WHERE program_id IS NOT NULL;