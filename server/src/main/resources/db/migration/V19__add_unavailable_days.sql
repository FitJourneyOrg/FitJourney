-- Estágio 2 (descanso): dias da semana que o usuário NÃO quer treinar (1=Seg..7=Dom).
-- JSON em TEXT (mesmo padrão de focus_areas/limitations). NULL/ausente = sem preferência.
ALTER TABLE profiles ADD COLUMN unavailable_days TEXT;
