-- V27: idade no perfil (#24 — fecha a Fase 4). Gate de menor: <18 exige reconhecimento de
-- supervisão de um responsável (`minor_supervised`). ≥69 é só informativo. Idade nula (perfis
-- legados) não bloqueia. O gate é revalidado no servidor em /programs/generate.
ALTER TABLE profiles ADD COLUMN age INT;
ALTER TABLE profiles ADD COLUMN minor_supervised BOOLEAN NOT NULL DEFAULT false;
