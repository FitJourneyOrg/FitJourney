-- V12: nome do programa + múltiplos programas por usuário (ARCH #26).
--
-- Muda o modelo de "1 programa ativo, substitui ao gerar" pra "N programas por
-- usuário, sem substituição". O teto de geração vira contagem por origin=AI
-- (grátis <= 2, premium sem teto) — ver ProgramService.generate().
--
-- DÉBITO ASSUMIDO: workouts pré-existentes com program_id NULL (órfãos, criados
-- antes da V11 existir) NÃO são religados automaticamente aqui. Ambiente é de
-- desenvolvimento (app não lançado); tratar manualmente se necessário.

ALTER TABLE programs ADD COLUMN name VARCHAR(100);
ALTER TABLE programs ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'AI';

-- Backfill dos programas já existentes: usa o split como nome provisório
-- (usuário pode renomear via PUT /programs/{id}).
UPDATE programs SET name = split WHERE name IS NULL;

ALTER TABLE programs ALTER COLUMN name SET NOT NULL;

-- Multi-programa: remove o índice único (era 1:1 usuário↔programa) e recria
-- como índice normal (ainda útil pra buscar todos os programas de um usuário).
DROP INDEX IF EXISTS idx_programs_user;
CREATE INDEX idx_programs_user ON programs(user_id);
