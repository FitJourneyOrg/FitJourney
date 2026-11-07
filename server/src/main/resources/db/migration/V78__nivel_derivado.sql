-- fatia 12 (questionário e triagem): nível deixa de ser autodeclarado. Duas perguntas novas
-- alimentam a derivação (docs/regras/16-onboarding-e-triagem.md); aditiva, nullable, nenhum
-- dado existente muda.
ALTER TABLE profiles
    ADD COLUMN training_consistency VARCHAR(32),
    ADD COLUMN time_off_training VARCHAR(32);

COMMENT ON COLUMN profiles.training_consistency IS
    'TrainingConsistency -- junto com time_off_training deriva o nivel (nivelDerivado). '
    'NULL = cliente ainda nao pergunta (pre-fatia-12); nivel cai no fallback de dto.level.';
COMMENT ON COLUMN profiles.time_off_training IS
    'TimeOffTraining -- ver comentario de training_consistency.';
