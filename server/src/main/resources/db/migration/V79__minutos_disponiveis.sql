-- fatia 12 (resto): minutos disponiveis para a sessao, alimenta o teto por tempo do motor v3
-- (CapacityModel.tetoPorTempo, dormente -- nenhuma rota chama o pipeline v3 ainda). Aditiva,
-- nullable, nenhum dado existente muda.
ALTER TABLE profiles
    ADD COLUMN minutes_available VARCHAR(32);

COMMENT ON COLUMN profiles.minutes_available IS
    'MinutesAvailable (FORTY_FIVE/SIXTY/SEVENTY_FIVE/NINETY_PLUS) -- NULL = nao perguntado ainda. '
    'So alimenta o motor v3 (dormente); a geracao em producao hoje nao le esta coluna.';
