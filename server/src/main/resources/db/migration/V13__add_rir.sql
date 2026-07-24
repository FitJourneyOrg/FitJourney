-- V13: RIR alvo por exercício (ARCH #27, Fatia A — motor de volume).
-- Nullable: treino MANUAL não prescreve RIR; só a geração por IA preenche.
ALTER TABLE workout_exercises ADD COLUMN rir SMALLINT;
