-- fatia 12 (resto): preferencia explicita "quer comecar no modo leve?" -- gatilho extra do
-- modo conservador (SafetyRules.modoConservadorAutomatico, dormente -- motor v3 ainda nao
-- roda em producao). Aditiva, nullable, nenhum dado existente muda.
ALTER TABLE profiles
    ADD COLUMN prefer_light_mode BOOLEAN;

COMMENT ON COLUMN profiles.prefer_light_mode IS
    'NULL = nao perguntado ainda. TRUE = pediu modo leve no onboarding -- so vale no 1o '
    'mesociclo (CapacityModel.primeiroMesociclo), igual ao gatilho de idade 60+.';
