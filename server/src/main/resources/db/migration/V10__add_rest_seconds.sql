-- V10: descanso por exercício (decisão da F.2 — motor gera descanso por nível).
-- O descanso é propriedade do EXERCÍCIO (não da série): não se descansa diferente
-- entre séries do mesmo exercício. Por isso a coluna vai em workout_exercises.
--
-- NOT NULL com default 90: 90s é um descanso neutro seguro; treinos manuais
-- existentes (sem descanso definido) herdam um valor sensato em vez de NULL.

ALTER TABLE workout_exercises
    ADD COLUMN rest_seconds INT NOT NULL DEFAULT 90;