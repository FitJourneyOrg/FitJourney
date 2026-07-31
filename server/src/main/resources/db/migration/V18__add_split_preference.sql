-- V18: preferência de split escolhida no onboarding (ARCH #29).
-- Null = usuário aceitou o recomendado (o motor deriva pelo nº de dias, #26).
ALTER TABLE profiles ADD COLUMN split_preference VARCHAR(24);
