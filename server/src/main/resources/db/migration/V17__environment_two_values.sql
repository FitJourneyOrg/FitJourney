-- V17: TrainingEnvironment de 4 -> 2 valores (ARCH #28).
-- Produto passa a suportar só Academia e Casa. Remapeia perfis existentes:
--   ACADEMIA_COMPLETA           -> ACADEMIA
--   HALTERES_CASA/PESO_CORPORAL/ELASTICOS -> CASA
UPDATE profiles SET environment = 'ACADEMIA'
  WHERE environment = 'ACADEMIA_COMPLETA';

UPDATE profiles SET environment = 'CASA'
  WHERE environment IN ('HALTERES_CASA', 'PESO_CORPORAL', 'ELASTICOS');
