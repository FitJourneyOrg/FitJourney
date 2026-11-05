-- V75: papel do exercício persistido no treino (fatia "rir-chega-e-registra",
-- docs/trabalho/rir-plano.md, passo 5 do desenho). Nullable: treino MANUAL não tem papel
-- (sem prescrição automática); só a geração (produção ou v3, quando ligado) preenche.
-- Enum como VARCHAR(.name) — mesmo padrão de `origin` (V7) e sem CHECK, pelo mesmo motivo:
-- o valor sempre passa pelo tipo Kotlin (Papel), nunca é dado de curadoria externa como a V74.
ALTER TABLE workout_exercises ADD COLUMN papel VARCHAR(16);
